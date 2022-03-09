package com.termux.terminal;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

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

    int framesSent;
    int x;
    int y;
    boolean overlay;
    boolean important;
    String format;
    int timeToLive;
    int loop;

    public FrameToGlasses(){
        if(bluetoothAdapter==null) {
            Log.w("Error", "Device doesn't support Bluetooth");
        } else{
            searchAndConnect(MY_UUID);
        }
        framesSent = 0;
        configureFrameIDBlock(0, 0, false, false, "jpeg", -1, 1);
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
        if(!isConnected()) {
            searchAndConnect(MY_UUID);
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
