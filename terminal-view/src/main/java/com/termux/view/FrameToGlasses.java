package com.termux.view;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;


import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class FrameToGlasses {


    InputStream connectionInputStream;
    OutputStream connectionOutputStream;
    ConnectThread connectThread;
    BluetoothSocket connectionSocket;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    String UUID0= "00001101-0000-1000-8000-00805f9b34fb";
    String UUID1= "0000111e-0000-1000-8000-00805f9b34fb";
    String UUID2= "0000110b-0000-1000-8000-00805f9b34fb";
    String UUID3= "00000000-0000-1000-8000-00805f9b34fb";
    public String MY_UUID = UUID0;

    String[] hexChars = new String[26];

    int framesSent;
    int x;
    int y;
    boolean overlay;
    boolean important;
    String format;
    int timeToLive;
    int loop;

    String currFrame;
    boolean brokenPipe = false;

    Context context;

    public FrameToGlasses(Context context){
        if(bluetoothAdapter==null) {
            Log.w("Error", "Device doesn't support Bluetooth");
        } else{
        }
        framesSent = 0;
        configureFrameIDBlock(0, 0, false, false, "jpeg", -1, 1);
        this.context = context;

        AssetManager assetManager = context.getAssets();

        try {
            String[] files = assetManager.list("");
            for(String s : files) {
                Log.w("Files", s);
            }
            //Can send smaller jpegs, but anything bigger than 400x640 will crash the glasses.
            InputStream input = assetManager.open("20x50CharC.jpg");
            String charCHexString = Hex.encodeHexString(IOUtils.toByteArray(input));
            hexChars['c' - 'a'] = charCHexString;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* s must be an even-length string. */
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }



    public void sendFrame(String imageHexString) {



        if(!isConnected() || brokenPipe) {
            currFrame = imageHexString;
            searchAndConnect(MY_UUID);
            brokenPipe = false;

            if(isConnected() || brokenPipe){
                sendFrame(imageHexString);
            } else {
                if (!isConnected()) {
                    searchAndConnect(MY_UUID);
                }
            }
        Log.w("Image", imageHexString);


        } else {
            byte[] headerBytes = generateHeader(imageHexString);
            byte[] frameIDBlockBytes = generateFrameIDBlock();
            byte[] imageBytes = hexStringToByteArray(imageHexString);
            byte[] ending = {0x13};
            byte[] byteStream = ArrayUtils.addAll(headerBytes, frameIDBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, imageBytes);
            byteStream = ArrayUtils.addAll(byteStream, ending);

            try {
                if (connectionOutputStream != null) {
                    connectionOutputStream.write(byteStream);
                    Log.w("Sent Data", "Sent sendBuffer successfully");
                    framesSent++;
                } else {
                    Log.w("Connection", "Not connected, can't send data.");
                }
            } catch (IOException e) {
                Log.w("Exception", "test");
                e.printStackTrace();
                brokenPipe = true;
                sendFrame(imageHexString);
            }
        }
    }

    public boolean sendFrameDelta(String imageHexString, int x, int y) {
        if(!isConnected()) {
            return false;
        } else {
            int oldX = this.x;
            int oldY = this.y;
            boolean oldOverlay = this.overlay;
            setX(x);
            setY(y);
            setOverlay(true);
            byte[] headerBytes = generateHeader(imageHexString);
            byte[] frameIDBlockBytes = generateFrameIDBlock();
            byte[] imageBytes = hexStringToByteArray(imageHexString);
            byte[] ending = {0x13};
            byte[] byteStream = ArrayUtils.addAll(headerBytes, frameIDBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, imageBytes);
            byteStream = ArrayUtils.addAll(byteStream, ending);
            setX(oldX);
            setY(oldY);
            setOverlay(oldOverlay);

            try {
                if (connectionOutputStream != null) {
                    connectionOutputStream.write(byteStream);
                    Log.w("Sent Data", "Sent sendBuffer successfully");
                    framesSent++;
                } else {
                    Log.w("Connection", "Not connected, can't send data.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                searchAndConnect(MY_UUID);
            }
            return true;

        }
    }

    public void configureFrameIDBlock(int x, int y, boolean overlay, boolean important, String format, int timeToLive, int loop){
        setX(x);
        setY(y);
        setOverlay(overlay);
        setImportant(important);
        setFormat(format);
        setTimeToLive(timeToLive);
        setLoop(loop);
    }


    private byte[] generateFrameIDBlock() {
        int id = framesSent+1;
        String frameIDBlockString = String.format(
            "{\"frameId\":%d,\"x\":%d,\"y\":%d,\"overlay\":%b,\"timeToLive\":%d,\"important\":%b,\"format\":%s,\"loop\":%d}",
            id, x, y, overlay, timeToLive, important, format, loop
        );
        return frameIDBlockString.getBytes(StandardCharsets.UTF_8);
    }
    private byte[] generateHeader(String jpegStream) {
        byte[] frameIDBlock = generateFrameIDBlock();
        int frameIDBlockSize = frameIDBlock.length;
        int imageIndexDiff = jpegStream.length()/2;
        byte[] imageIndexDiffBytes = ByteBuffer.allocate(4).putInt(imageIndexDiff).array();

        byte[] header = new byte[20];
        for(int i = 0; i < header.length; i++){
            header[i] = 0;
        }
        header[0] = 0x12;
        header[5] = 0x05;
        header[15] = (byte) frameIDBlockSize;
        //imageIndexDif from int to string to byte array
        header[18] = imageIndexDiffBytes[2];
        header[19] = imageIndexDiffBytes[3];

        return header;
    }



    protected void searchAndConnect(String str_UUID) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.w("Device Class", String.valueOf(device.getBluetoothClass().getDeviceClass()));

                //Temporary solution until I can figure out how to save devices and have a pairing scheme.
                //Just check if device name starts with tooz.
                //Maybe send this data to all device classes 1048(AUDIO_VIDEO_HEADPHONES)?
                if(deviceName.length() >= 4 && deviceName.substring(0, 4).equals("tooz")) {
                    if(connectThread == null || connectThread.getState() == Thread.State.TERMINATED){
                        connectThread = new ConnectThread(device,  device.getUuids()[0].getUuid().toString(), true);
                        Log.w("Log", "Trying to connect to device address: " + deviceHardwareAddress + "using UUID: " + device.getUuids()[0].getUuid().toString());
                        connectThread.start();


                    }

                }
            }
        }
    }

    protected void searchAndConnectAgain(String str_UUID) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.w("Device Class", String.valueOf(device.getBluetoothClass().getDeviceClass()));

                //Temporary solution until I can figure out how to save devices and have a pairing scheme.
                //Just check if device name starts with tooz.
                //Maybe send this data to all device classes 1048(AUDIO_VIDEO_HEADPHONES)?
                if(deviceName.length() >= 4 && deviceName.substring(0, 4).equals("tooz")) {
                    if(connectThread == null || connectThread.getState() == Thread.State.TERMINATED){
                        connectThread = new ConnectThread(device,  device.getUuids()[0].getUuid().toString(), false);
                        Log.w("Log", "Trying to connect to device address: " + deviceHardwareAddress + "using UUID: " + device.getUuids()[0].getUuid().toString());
                        connectThread.start();


                    }

                }
            }
        }
    }

    public void setX(int x) {
        this.x = x;
    }
    public void setY(int y) {
        this.y = y;
    }
    public void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }
    public void setImportant(boolean important) {
        this.important = important;
    }
    public void setFormat(String format) {
        this.format = format;
    }
    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }
    public void setLoop(int loop) {
        this.loop = loop;
    }
    public boolean isConnected() {
        return connectionSocket != null && connectionSocket.isConnected();
    }


    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private boolean againIfFail;
        private String myUUID;
        public ConnectThread(BluetoothDevice device, String myUUID, boolean againIfFail) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.

            BluetoothSocket tmp = null;
            mmDevice = device;
            this.myUUID = myUUID;
            this.againIfFail = againIfFail;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(myUUID));
            } catch (IOException e) {
                Log.e("TAG", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
            connectionSocket = mmSocket;

        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.e("Failure", connectException.toString());
                try {
                    mmSocket.close();
                } catch (Exception e2) {
                    Log.e("Exception", e2.toString());

                }
                if(againIfFail) searchAndConnectAgain(myUUID);
                return;
            }

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = mmSocket.getInputStream();
                connectionInputStream = tmpIn;
            } catch (IOException e) {
                Log.e("Tag", "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = mmSocket.getOutputStream();
                connectionOutputStream = tmpOut;
            } catch (IOException e) {
                Log.e("Tag", "Error occurred when creating output stream", e);
            }

            Log.w("Success", "Connection Succeeded");
            sendFrame(currFrame);

            /*
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = tmpIn.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    String s = "";
                    for(int i = 0; i < numBytes; i++) {
                        s = s + (char)mmBuffer[i] ;
                    }
                    Log.w("Reading", s);
                    byte test = 22;
                    tmpOut.write(test);
                } catch (IOException e) {
                    Log.d("Tag", "Input stream was disconnected", e);
                    break;
                }
            }
                */
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("Tag", "Could not close the client socket", e);
            }
        }
    }
}
