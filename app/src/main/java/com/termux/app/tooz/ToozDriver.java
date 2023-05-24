package com.termux.app.tooz;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ToozDriver {
    Paint paint;
    private int screen_width = 400;
    private int screen_height = 640;
    int x = 0;
    int y = 0;
    int messageTime = 5000;//ms
    boolean timedMessagesEnabled = true;

    Timer timer;

    TerminalEmulator terminalEmulator;
    ToozRenderer toozRenderer;
    FrameDriver frameDriver;
    char[][] currScreenChars;

    final int FULL_FRAME_PROCESSING_TIME = 1000;
    boolean frameDropped = false;
    final long MAX_FRAME_TIME_THRESHOLD = 1000;
    long currTimeThreshold = -1;
    int numFramesSent = 0;

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(20);


    private synchronized void processFrame() {
        numFramesSent--;
        if(frameDropped) {
            frameDropped = false;
            processUpdate();
        }
    }

    private synchronized void refreshTimer() {
        if(!timedMessagesEnabled) return;

        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                clearScreenAndBuffer();
            }
        };

        timer.schedule(timerTask, messageTime);

    }

    public ToozDriver(TerminalEmulator terminalEmulator, int textSize, int textColor, android.content.Context context, int screenWidth, int screenHeight, int x, int y) {
        paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setAntiAlias(true);
        //Todo: Figure out why textsize needs to be slightly smaller, without relying on empirical testing.
        paint.setTextSize(textSize - 5);

        toozRenderer = new ToozRenderer(paint, textColor);
        frameDriver = FrameDriver.getInstance();
        frameDriver.setContext(context);

        this.screen_height = screenHeight;
        this.screen_width = screenWidth;
        this.x = x;
        this.y = y;

        //Setup screen tracking.
        this.terminalEmulator = terminalEmulator;
        initializeScreenTrackingSilent();
        lockResizing();
    }

    public void setTextColor(int color) {
        if(toozRenderer != null) toozRenderer.setToozColor(color);
    }

    public void setTerminalEmulator(TerminalEmulator terminalEmulator) {
        this.terminalEmulator = terminalEmulator;
    }

    public TerminalEmulator getTerminalEmulator() {
        return terminalEmulator;
    }

    public int requestAccelerometerData(int millisecondsDelay, int timeout, TermuxTerminalSessionClient termuxTerminalSessionClient) {
        return frameDriver.requestAccelerometerData(millisecondsDelay, timeout, termuxTerminalSessionClient, this);
    }

    public int requestAccelerometerDataSilent(int millisecondsDelay, int timeout, TermuxTerminalSessionClient termuxTerminalSessionClient) {
        return frameDriver.requestAccelerometerDataSilent(millisecondsDelay, timeout, termuxTerminalSessionClient, this);
    }

    public synchronized void processUpdate() {
        Log.w("Tooz", "Process Update");
        if(currTimeThreshold - System.currentTimeMillis() > MAX_FRAME_TIME_THRESHOLD || numFramesSent > 2) {
            //Log.w("ViewDriver", "FRAME DROPPED");
            frameDropped = true;
            return;
        }
        boolean[][] changes = findChanges();
        int[] bounds = getBoundingBox(changes);
        if(bounds[0] == 0) return;
        int topRow = bounds[1];
        int bottomRow = bounds[2];
        int leftCol = bounds[3];
        int rightCol = bounds[4];
        //render
        Log.w("DailyDriver", "HeightOfBitmap " + (toozRenderer.mFontLineSpacingTooz) * (bottomRow - topRow + 1));
        Bitmap bitmap = Bitmap.createBitmap(screen_width, Integer.min(screen_height, toozRenderer.mFontLineDescentTooz + (toozRenderer.mFontLineSpacingTooz) * (bottomRow - topRow + 1)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        toozRenderer.renderBoxToTooz(terminalEmulator, canvas, -1, -1,-1,-1, topRow, bottomRow + 1, leftCol, rightCol);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);

        int preWidth = (int) toozRenderer.getWidthBeforeTooz(leftCol);
        int preHeight = (int) toozRenderer.getHeightBeforeTooz(0, topRow);

        Log.w("DailyDriver", "Drive");
        Log.w("DailyDriver", "PreWidth: " + preWidth + " PreHeight: " + preHeight + " topRow: " + topRow + " bottomRow: " + bottomRow + " leftCol: " + leftCol + " rightCol: " + rightCol);

        frameDriver.sendBox(s, x, y+preHeight);
        refreshTimer();

        numFramesSent++;
        if(topRow == 0 && bottomRow == changes.length - 1) {
            Log.w("DailyDriver", "Schedule Full Frame");
            scheduleProcessFullFrame();
        } else {
            Log.w("DailyDriver", "Schedule Partial Frame, Proportion: " + Integer.max((int) (FULL_FRAME_PROCESSING_TIME * 0.1), (int) (FULL_FRAME_PROCESSING_TIME * (float) (bottomRow - topRow + 1) / changes.length ) ) );
            scheduleProcessFrame( Integer.max((int) (FULL_FRAME_PROCESSING_TIME * 0.1), (int) (FULL_FRAME_PROCESSING_TIME * (float) (bottomRow - topRow + 1) / changes.length ) ) );
        }
    }

    public void initializeScreenTracking() {
        Log.w("DailyDriver", "Initialize Screen Tracking");
        TerminalBuffer screen = terminalEmulator.getScreen();

        currScreenChars = new char[terminalEmulator.mRows][terminalEmulator.mColumns];
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                currScreenChars[i][j] = screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
            }
        }
        sendFullFrame();
        refreshTimer();
    }

    public void initializeScreenTrackingSilent() {
        Log.w("DailyDriver", "Initialize Screen Tracking");
        TerminalBuffer screen = terminalEmulator.getScreen();

        currScreenChars = new char[terminalEmulator.mRows][terminalEmulator.mColumns];
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                currScreenChars[i][j] = screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
            }
        }
        //sendFullFrame();
    }


    private boolean[][] findChanges() {
        boolean[][] changes = new boolean[terminalEmulator.mRows][terminalEmulator.mColumns];
        TerminalBuffer screen = terminalEmulator.getScreen();

        for(int i = 0; i < terminalEmulator.mRows; i++) {
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                changes[i][j] = false;
            }
        }
        if(currScreenChars.length != terminalEmulator.mRows || currScreenChars[0].length != terminalEmulator.mColumns) {
            Log.w("DailyDriver", "currScreen: " + currScreenChars.length + ' ' + currScreenChars[0].length + " terminalEmulator " + terminalEmulator.mRows + ' ' + terminalEmulator.mColumns);
            initializeScreenTracking();
            return changes;
        }

        for(int i = 0; i < terminalEmulator.mRows; i++) {
            String s = "";
            for (int j = 0; j < terminalEmulator.mColumns; j++) {
                changes[i][j] = (boolean) (screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j] != currScreenChars[i][j]);
                currScreenChars[i][j] = screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
                s = s + (currScreenChars[i][j]);

            }
            Log.w("Curr Screen", s);
        }

        return changes;
    }

    private int[] getBoundingBox(boolean[][] changes) {
        //Find the smallest box that bounds all the changes made.
        int m = changes.length;
        int n = changes[0].length;

        int changesExist = 0;
        int leftBound = n;
        int rightBound = -1;
        int topBound = m;
        int bottomBound = -1;

        for(int i = 0; i < m; i++) {
            for(int j = 0; j < n; j++) {
                if(changes[i][j]) {
                    changesExist = 1;
                    leftBound = Integer.min(leftBound, j);
                    rightBound = Integer.max(rightBound, j);
                    topBound = Integer.min(topBound, i);
                    bottomBound = Integer.max(bottomBound, i);
                }
            }
        }
        Log.w("DailyDriver" , "BoundingBox: " + changesExist + " " + topBound + ' ' + bottomBound + ' ' + leftBound + ' ' + rightBound);
        return new int[] {changesExist, topBound, bottomBound, leftBound, rightBound};
    }

    private void lockResizing(){};

    public synchronized void scheduleProcessFullFrame() {
        if(System.currentTimeMillis() > currTimeThreshold) {
            currTimeThreshold = System.currentTimeMillis();
        }
        currTimeThreshold += FULL_FRAME_PROCESSING_TIME;
        long processingDelay = Long.max(0, (long) (currTimeThreshold - System.currentTimeMillis()));
        scheduledExecutorService.schedule(() -> processFrame(), processingDelay, TimeUnit.MILLISECONDS);

    }

    public synchronized void scheduleProcessFrame(int processingTime) {
        if(System.currentTimeMillis() > currTimeThreshold) {
            currTimeThreshold = System.currentTimeMillis();
        }
        currTimeThreshold += processingTime;
        long processingDelay = Long.max(0, (long) (currTimeThreshold - System.currentTimeMillis()));
        scheduledExecutorService.schedule(() -> processFrame(), processingDelay, TimeUnit.MILLISECONDS);

    }

    public int sendFullFrame() {
        Log.w("Send Full Frame", "");
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            Log.w("StackTrace", ste.toString());
        }
        Bitmap bitmap = Bitmap.createBitmap(screen_width, screen_height, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        toozRenderer.renderToTooz(terminalEmulator, toozCanvas, 0, -1,-1,-1,-1);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        return frameDriver.sendFullFrame(s, x, y);

    }

    public void clearScreen() {
        Log.w("Clear Screen", "");
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            Log.w("StackTrace", ste.toString() );
        }
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        toozCanvas.drawARGB(255, 0, 0, 0);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);

    }

    //Todo: replace clearScreen once we confirm that emptying the buffer doesn't break anything.
    public void clearScreenAndBuffer() {
        Log.w("Clear Screen", "");
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            Log.w("StackTrace", ste.toString() );
        }
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        toozCanvas.drawARGB(255, 0, 0, 0);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);

        currScreenChars = new char[terminalEmulator.mRows][terminalEmulator.mColumns];
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                currScreenChars[i][j] = 0;
            }
        }
    }

}
