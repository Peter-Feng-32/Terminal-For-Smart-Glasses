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

    Context context;

    public FrameToGlasses(Context context){
        if(bluetoothAdapter==null) {
            Log.w("Error", "Device doesn't support Bluetooth");
        } else{
            searchAndConnect(MY_UUID);
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


    public void sendChar(char c, int row, int col, int totalRows, int totalCols){
        final int SCREENWIDTH = 400;
        final int SCREENHEIGHT = 640;
        //Note: Currently terminal screen is fixed at 18 characters wide, 10 characters tall.
        //If terminal screen is made taller by hiding keyboard, we just show the bottom 10 rows.
        //Also consider spacing.
        //Let's have 2px between each x and 10px between each y.
        int xSpacing = 2;
        int ySpacing = 10;

        int charXSize = ((SCREENWIDTH - (totalCols - 1) * xSpacing) / totalCols);
        int x = (charXSize + xSpacing) * col;
        int charYSize = ((SCREENHEIGHT - (totalRows - 1) * ySpacing) / totalRows);
        int y = (charYSize + ySpacing) * row;

        //Then each image should be about 20px by 50px
        setX(x);
        setY(y);
        sendFrame(hexChars['c' - 'a']);

    }


    public void sendFrame(String imageHexString) {
        if(!isConnected()) {
            //searchAndConnect(MY_UUID);
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
                e.printStackTrace();
            }
        }
    }

    public void sendFrame(String imageHexString, int x, int y) {
        if(!isConnected()) {
            //searchAndConnect(MY_UUID);
        } else {
            int oldX = this.x;
            int oldY = this.y;
            setX(x);
            setY(y);
            byte[] headerBytes = generateHeader(imageHexString);
            byte[] frameIDBlockBytes = generateFrameIDBlock();
            byte[] imageBytes = hexStringToByteArray(imageHexString);
            byte[] ending = {0x13};
            byte[] byteStream = ArrayUtils.addAll(headerBytes, frameIDBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, imageBytes);
            byteStream = ArrayUtils.addAll(byteStream, ending);
            setX(oldX);
            setY(oldY);

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
            }

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
                Log.w(deviceName, deviceHardwareAddress);

                for(int i = 0; i < device.getUuids().length; i++) {
                    Log.w("UUID " + i, device.getUuids()[i].getUuid().toString());
                }

                if(deviceHardwareAddress.equals("B4:A9:FC:CA:C3:0C")) {
                    connectThread = new ConnectThread(device, str_UUID);
                    Log.w("Log", "Trying to connect to device address: " + deviceHardwareAddress + "using UUID: " + str_UUID);
                    connectThread.start();
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
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device, String myUUID) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

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
                Log.w("Failure", "Unable to connect " + connectException.toString());
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("Tag", "Could not close the client socket", closeException);
                }
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
