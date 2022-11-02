package smartglasses;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

public class FrameDriver {
    Context context;

    InputStream connectionInputStream;
    OutputStream connectionOutputStream;
    ConnectThread connectThread;
    BluetoothSocket connectionSocket;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    boolean searching = false;
    String UUID0= "00001101-0000-1000-8000-00805f9b34fb";
    public String MY_UUID = UUID0;
    int framesSent = 0;
    String currFrame;

    public FrameDriver(Context context){
        if(bluetoothAdapter==null) {
            Log.w("Error", "Device doesn't support Bluetooth");
        }
        this.context = context;
    }

    public void sendFullFrame(String imageHexString) {
        //Connection code - see if we can optimize this later.
        if(!isConnected()) {
            currFrame = imageHexString;
            if (!searching) searchAndConnect(MY_UUID);
        }
        if(isConnected())
        {
            FrameBlock frameBlock = new FrameBlock(framesSent++);
            byte[] headerBytes = generateHeader(imageHexString, frameBlock);
            byte[] frameIDBlockBytes = frameBlock.serialize();
            byte[] imageBytes = DriverHelper.hexStringToByteArray(imageHexString);
            byte[] ending = {0x13};
            byte[] byteStream = ArrayUtils.addAll(headerBytes, frameIDBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, imageBytes);
            byteStream = ArrayUtils.addAll(byteStream, ending);

            byte[] finalByteStream = byteStream;
            Thread t1 = new Thread(new Runnable() {
                public void run()
                {
                    try {
                        if (connectionOutputStream != null) {
                            connectionOutputStream.write(finalByteStream);
                            //Log.w("Sent Data", "Sent sendBuffer successfully");
                            //Log.w("Image", imageHexString);
                            framesSent++;
                        } else {
                            Log.w("Connection", "Not connected, can't send data.");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            connectionOutputStream.close();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                        if(!searching) searchAndConnect(MY_UUID);
                        return;
                    }
                }});
            t1.start();
        }
    }

    public boolean sendFrameDelta(String imageHexString, int x, int y) {
        if(!isConnected()) {
            return false;
        } else {
            FrameBlock frameBlock = new FrameBlock(framesSent++);
            frameBlock.setX(x);
            frameBlock.setY(y);
            frameBlock.setOverlay(true);
            byte[] headerBytes = generateHeader(imageHexString, frameBlock);
            byte[] frameIDBlockBytes = frameBlock.serialize();
            byte[] imageBytes = DriverHelper.hexStringToByteArray(imageHexString);
            byte[] ending = {0x13};
            byte[] byteStream = ArrayUtils.addAll(headerBytes, frameIDBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, imageBytes);
            byteStream = ArrayUtils.addAll(byteStream, ending);

            byte[] finalByteStream = byteStream;
            Thread t1 = new Thread(new Runnable() {
                 public void run()
                {
                    try {
                        if (connectionOutputStream != null) {
                            connectionOutputStream.write(finalByteStream);
                            //Log.w("Sent Data", "Sent sendBuffer successfully");
                            //Log.w("Image", imageHexString);
                            framesSent++;
                        } else {
                            Log.w("Connection", "Not connected, can't send data.");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            connectionOutputStream.close();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                        if(!searching) searchAndConnect(MY_UUID);
                        return;
                    }
                }});
            t1.start();
/*
            try {
                if (connectionOutputStream != null) {
                    connectionOutputStream.write(byteStream);
                    //Log.w("Sent Data", "Sent sendBuffer successfully");
                    //Log.w("Image", imageHexString);
                    framesSent++;
                } else {
                    Log.w("Connection", "Not connected, can't send data.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                if(!searching) searchAndConnect(MY_UUID);
                return false;
            }*/


            return true;
        }
    }

    private byte[] generateHeader(String jpegStream, FrameBlock frameBlock) {
        byte[] frameIDBlock = frameBlock.serialize();
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

    synchronized protected void searchAndConnect(String str_UUID) {
        searching = true;
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
                        if(device.getUuids() == null) {
                            Log.w("deviceName", "" + deviceName);
                            continue;
                        }
                        connectThread = new ConnectThread(device,  str_UUID, true);
                        Log.w("Log", "Trying to connect to device address: " + deviceHardwareAddress + "using UUID: " + str_UUID);
                        connectThread.start();
                    }
                }
            }
        }
        searching = false;
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
            sendFullFrame(currFrame);

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
