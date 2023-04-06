package com.termux.app.tooz;


import static android.content.Context.POWER_SERVICE;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;

import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.view.ToozConstants;

import org.apache.commons.lang3.ArrayUtils;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FrameDriver {
    InputStream connectionInputStream;
    OutputStream connectionOutputStream;
    ConnectThread connectThread;
    BluetoothSocket connectionSocket;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    boolean searching = false;
    String SERIAL_PORT_UUID= "00001101-0000-1000-8000-00805f9b34fb";
    int framesSent = 0;
    int messageCount = 1;
    String currFrame;

    final int SLIDING_WINDOW_SIZE = 40;


    Context context;

    private static FrameDriver frameDriver;

    public FrameDriver(){
        if(bluetoothAdapter==null) {
            Log.w("Error", "Device doesn't support Bluetooth");
        }
    }

    public static FrameDriver getInstance() {
        if(frameDriver == null) {
            frameDriver = new FrameDriver();
        }
        return frameDriver;
    }

    public class AccelerometerRunnable implements Runnable {
        private int status;
        private TermuxTerminalSessionClient termuxTerminalSessionClient;
        private ToozDriver notificationDriver;

        public AccelerometerRunnable(TermuxTerminalSessionClient termuxTerminalSessionClient, ToozDriver notificationDriver){
            this.status= 0;
            this.termuxTerminalSessionClient = termuxTerminalSessionClient;
            this.notificationDriver = notificationDriver;
        }

        @Override
        public void run()
        {
            Log.w("FrameDriver", "Running Accelerometer Reader");
            boolean done = false;
            try{
                if (connectionInputStream != null) {
                    Log.w("FrameDriver", "Connection Input Stream Not Null");
                    Log.w("FrameDriver", "Skipped " + connectionInputStream.skip(connectionInputStream.available()) + "bytes \n");
                } else {
                    Log.w("FrameDriver", "Connection Input Stream Null");

                }
            }catch (Exception e) {
                e.printStackTrace();
            }

            Deque<Pair<Long, Double>> accelerometerData;
            Deque<Pair<Long, Double>> accelerometerMaxDeque;
            Deque<Pair<Long, Double>> accelerometerMinDeque;
            long accelerometerMessagesReceived;

            accelerometerData = new ArrayDeque<>();
            accelerometerMaxDeque = new ArrayDeque<>();
            accelerometerMinDeque = new ArrayDeque<>();
            accelerometerMessagesReceived = 0;

            byte[] bytesToToss = new byte[100000];
            if (connectionInputStream != null) {
                try {
                    connectionInputStream.read(bytesToToss);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            while(!done) {
                try {

                    if(Thread.interrupted()) {
                        this.status = -2;
                        throw new InterruptedException();
                    }

                    byte[] input = new byte[300];
                    if (connectionInputStream != null) {
                        connectionInputStream.read(input);
                    }
                    String str = new String(input, StandardCharsets.UTF_8);
                    //Search for message inside input
                    int numOpeningBrackets = 0;
                    int numClosingBrackets = 0;
                    int i = 2;
                    int j;
                    while(i < str.length() && numOpeningBrackets == 0) {
                        if(str.charAt(i) == 's' && str.charAt(i-1) == '\"' && str.charAt(i-2) == '{') {
                            numOpeningBrackets++;
                            i -= 2;
                            break;
                        }
                        i++;
                    }
                    for(j = i + 1; j < str.length(); j++) {
                        if(str.charAt(j) == '{') numOpeningBrackets++;
                        if(str.charAt(j) == '}') numClosingBrackets++;
                        if(numClosingBrackets == numOpeningBrackets) {
                            j++;
                            break;
                        }
                    }
                    if(i >= str.length()) continue;
                    Log.w("RequestGyroData", "Indices: " + i + " " + j);
                    String message = str.substring(i, j);
                    Log.w("RequestGyroData", "Message: " + message);
                    try {
                        JSONObject messageJson = new JSONObject(message);
                        double accelerationReadingX = messageJson.getJSONArray("sensors").getJSONObject(0).getJSONObject("reading").getJSONObject("acceleration").getDouble("x");
                        double accelerationReadingY = messageJson.getJSONArray("sensors").getJSONObject(0).getJSONObject("reading").getJSONObject("acceleration").getDouble("y");
                        double accelerationReadingZ = messageJson.getJSONArray("sensors").getJSONObject(0).getJSONObject("reading").getJSONObject("acceleration").getDouble("z");
                        double angle = Math.atan2(accelerationReadingY,  accelerationReadingZ);
                        Log.w("Request Accelerometer Data", "Angle: " + Math.toDegrees(angle));

                                /*Run a Sliding Window Maximum and a Sliding Window Minimum, compute the difference between
                                the max and min angles and use this to determine if we should dismiss the notification.
                                 */
                        accelerometerMessagesReceived++;
                        if(accelerometerData.size() < SLIDING_WINDOW_SIZE) {
                            accelerometerData.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                            while(accelerometerMaxDeque.size() > 0 && accelerometerMaxDeque.getLast().second <= angle) {
                                accelerometerMaxDeque.removeLast();
                            }
                            accelerometerMaxDeque.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                            while(accelerometerMinDeque.size() > 0 && accelerometerMinDeque.getLast().second >= angle) {
                                accelerometerMinDeque.removeLast();
                            }
                            accelerometerMinDeque.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                        } else {
                            accelerometerData.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                            if(accelerometerMaxDeque.getFirst().first == accelerometerData.getFirst().first) {
                                accelerometerMaxDeque.removeFirst();
                            }
                            while(accelerometerMaxDeque.size() > 0 && accelerometerMaxDeque.getLast().second <= angle) {
                                accelerometerMaxDeque.removeLast();
                            }
                            accelerometerMaxDeque.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                            if(accelerometerMinDeque.getFirst().first == accelerometerData.getFirst().first) {
                                accelerometerMinDeque.removeFirst();
                            }
                            while(accelerometerMinDeque.size() > 0 && accelerometerMinDeque.getLast().second >= angle) {
                                accelerometerMinDeque.removeLast();
                            }
                            accelerometerMinDeque.addLast(new Pair<Long, Double>(accelerometerMessagesReceived, angle));
                            accelerometerData.removeFirst();
                        }

                        if(Math.toDegrees(accelerometerMaxDeque.getFirst().second - accelerometerMinDeque.getFirst().second) > 15) {
                            Log.w("Request Accelerometer Data", "Greatest Difference in angle: " + Math.toDegrees(accelerometerMaxDeque.getFirst().second - accelerometerMinDeque.getFirst().second));
                            done = true;
                        }

                    }catch (JSONException err){
                        Log.d("Error", err.toString());
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        connectionInputStream.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                    return;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    done = true;
                }

            }
        }

        public int getStatus() {
            return status;
        }
    }



    public void setContext(Context c) {
        context = c;
    }

    public int sendFullFrame(String imageHexString) {
        //Connection code - see if we can optimize this later.
        if(!isConnected()) {
            currFrame = imageHexString;
            if (!searching) searchAndConnect(SERIAL_PORT_UUID);
            return -1;
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
                        if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                        return;
                    }
                }});
            t1.start();
            return 0;
        }
        return -1;
    }

    public void sendRows(String imageHexString) {
        //Connection code - see if we can optimize this later.
        if(!isConnected()) {
            currFrame = imageHexString;
            if (!searching) searchAndConnect(SERIAL_PORT_UUID);
        }
        if(isConnected())
        {
            FrameBlock frameBlock = new FrameBlock(framesSent++);
            frameBlock.setY(200);
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
                        if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                        return;
                    }
                }});
            t1.start();
        }
    }

    public void sendBox(String imageHexString, int x, int y) {
        //Connection code - see if we can optimize this later.
        if(!isConnected()) {
            currFrame = imageHexString;
            if (!searching) searchAndConnect(SERIAL_PORT_UUID);
        }
        if(isConnected())
        {
            FrameBlock frameBlock = new FrameBlock(framesSent++);
            frameBlock.setX(x);
            frameBlock.setY(y);
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
                        if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                        return;
                    }
                }});
            t1.start();
        }
    }

    public int requestAccelerometerData(int millisecondsDelay, int timeout, TermuxTerminalSessionClient termuxTerminalSessionClient, ToozDriver notificationDriver) {
        if(!isConnected()) {
            if (!searching) searchAndConnect(SERIAL_PORT_UUID);
        }
        if(isConnected()) {
            Date date = new java.util.Date();
            String time = new SimpleDateFormat("yyyy-MM-dd").format(date) + "T" + new SimpleDateFormat("hh:mm:ss.SSS").format(date) + "-0400"; //Currently hardcoded to EST timezone
            String requestBlock = String.format("{\"time\":\"" + time + "\",\"sois\":[{\"name\":\"acceleration\",\"delay\":%d}],\"variables\":[]}", millisecondsDelay);

            byte[] headerBytes = {0x12};
            byte[] messageCountBytes = ByteBuffer.allocate(4).putInt(0, messageCount++).array();
            byte[] idBytes = {0x04};
            byte[] timestampBytes =  ByteBuffer.allocate(9).putLong(0, System.currentTimeMillis()).array();
            byte[] sizeOfRequestBytes = {(byte) requestBlock.length(),0, 0, 0, 0};
            byte[] requestBlockBytes = requestBlock.getBytes(StandardCharsets.UTF_8);
            byte[] endingBytes = {0x13};

            byte[] byteStream = ArrayUtils.addAll(headerBytes, messageCountBytes);
            byteStream = ArrayUtils.addAll(byteStream, idBytes);
            byteStream = ArrayUtils.addAll(byteStream, timestampBytes);
            byteStream = ArrayUtils.addAll(byteStream, sizeOfRequestBytes);
            byteStream = ArrayUtils.addAll(byteStream, requestBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, endingBytes);

            byte[] finalByteStream = byteStream;
            Thread t1 = new Thread(new Runnable() {
                public void run()
                {
                    try {
                        if (connectionOutputStream != null) {
                            connectionOutputStream.write(finalByteStream);
                            Log.w("Connection", "Sent Data");
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
                        if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                        return;
                    }
                }});
            t1.start();

            AccelerometerRunnable accelerationReader = new AccelerometerRunnable(termuxTerminalSessionClient, notificationDriver);
            Thread accelerationThread = new Thread(accelerationReader);
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

            Runnable runnable = new Runnable() {
                public void run() {
                    // Do something
                    if(accelerationThread.isAlive()) {
                        accelerationThread.interrupt();
                    }
                }
            };
            if(timeout > 0) {
                worker.schedule(runnable, timeout, TimeUnit.MILLISECONDS);
            }
            accelerationThread.start();

            try {
                accelerationThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }

            //If there is a tooz session currently running:
            //If the screen is on or the session is captioning, reinitialize screen tracking and send frame.
            PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
            boolean screenInteractive = powerManager.isInteractive();
            if(termuxTerminalSessionClient.getToozDriver() != null) {
                if(screenInteractive || (termuxTerminalSessionClient.getCurrentSession().mSessionName != null && termuxTerminalSessionClient.getCurrentSession().mSessionName.equals(ToozConstants.CAPTIONING_TERMUX_SESSION_NAME))) {
                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                } else {
                    termuxTerminalSessionClient.getToozDriver().clearScreen();
                }
            } else {
                notificationDriver.clearScreen();
            }
            termuxTerminalSessionClient.setToozEnabled(true);

            Log.w("Read Acceleration", "Status: " + accelerationReader.getStatus());
            return accelerationReader.getStatus();
        }
        return -1;
    }

    public int requestAccelerometerDataSilent(int millisecondsDelay, int timeout, TermuxTerminalSessionClient termuxTerminalSessionClient, ToozDriver notificationDriver) {
        if(!isConnected()) {
            if (!searching) searchAndConnect(SERIAL_PORT_UUID);
        }
        if(isConnected()) {
            Date date = new java.util.Date();
            String time = new SimpleDateFormat("yyyy-MM-dd").format(date) + "T" + new SimpleDateFormat("hh:mm:ss.SSS").format(date) + "-0400"; //Currently hardcoded to EST timezone
            String requestBlock = String.format("{\"time\":\"" + time + "\",\"sois\":[{\"name\":\"acceleration\",\"delay\":%d}],\"variables\":[]}", millisecondsDelay);

            byte[] headerBytes = {0x12};
            byte[] messageCountBytes = ByteBuffer.allocate(4).putInt(0, messageCount++).array();
            byte[] idBytes = {0x04};
            byte[] timestampBytes =  ByteBuffer.allocate(9).putLong(0, System.currentTimeMillis()).array();
            byte[] sizeOfRequestBytes = {(byte) requestBlock.length(),0, 0, 0, 0};
            byte[] requestBlockBytes = requestBlock.getBytes(StandardCharsets.UTF_8);
            byte[] endingBytes = {0x13};

            byte[] byteStream = ArrayUtils.addAll(headerBytes, messageCountBytes);
            byteStream = ArrayUtils.addAll(byteStream, idBytes);
            byteStream = ArrayUtils.addAll(byteStream, timestampBytes);
            byteStream = ArrayUtils.addAll(byteStream, sizeOfRequestBytes);
            byteStream = ArrayUtils.addAll(byteStream, requestBlockBytes);
            byteStream = ArrayUtils.addAll(byteStream, endingBytes);

            byte[] finalByteStream = byteStream;
            Thread t1 = new Thread(new Runnable() {
                public void run()
                {
                    try {
                        if (connectionOutputStream != null) {
                            connectionOutputStream.write(finalByteStream);
                            Log.w("Connection", "Sent Data");
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
                        if(!searching) searchAndConnect(SERIAL_PORT_UUID);
                        return;
                    }
                }});
            t1.start();

            AccelerometerRunnable accelerationReader = new AccelerometerRunnable(termuxTerminalSessionClient, notificationDriver);
            Thread accelerationThread = new Thread(accelerationReader);
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

            Runnable runnable = new Runnable() {
                public void run() {
                    // Do something
                    if(accelerationThread.isAlive()) {
                        accelerationThread.interrupt();
                    }
                }
            };
            if(timeout > 0) {
                worker.schedule(runnable, timeout, TimeUnit.MILLISECONDS);
            }
            accelerationThread.start();

            try {
                accelerationThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.w("Read Acceleration", "Status: " + accelerationReader.getStatus());
            return accelerationReader.getStatus();
        }
        return -1;
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
                        //connectThread = new ConnectThread(device,  device.getUuids()[0].getUuid().toString(), true);
                        connectThread = new ConnectThread(device,  str_UUID, true);
                        Log.w("UUID HardCoded", str_UUID);
                        Log.w("UUIDS Gotten", "" + device.getUuids());
                        for(ParcelUuid id: device.getUuids()) {
                            Log.w("UUID List", id.toString());
                        }
                        Log.w("UUID getUuids", device.getUuids()[0].getUuid().toString());
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
