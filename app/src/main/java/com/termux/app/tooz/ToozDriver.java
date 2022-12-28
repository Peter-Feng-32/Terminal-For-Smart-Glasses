package com.termux.app.tooz;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ToozDriver {
    Paint paint;
    private final int SCREEN_WIDTH = 400;
    private final int SCREEN_HEIGHT = 640;

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




    public ToozDriver(TerminalEmulator terminalEmulator, int textSize) {
        paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);

        toozRenderer = new ToozRenderer(paint);
        frameDriver = FrameDriver.getInstance();

        //Setup screen tracking.
        this.terminalEmulator = terminalEmulator;
        initializeScreenTracking();
        lockResizing();
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
        Bitmap bitmap = Bitmap.createBitmap(400, Integer.min(640, (toozRenderer.mFontLineSpacingTooz) * (bottomRow - topRow + 1)), Bitmap.Config.ARGB_8888);
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

        frameDriver.sendBox(s, 0, preHeight);

        numFramesSent++;
        if(topRow == 0 && bottomRow == changes.length - 1) {
            Log.w("DailyDriver", "Schedule Full Frame");
            scheduleProcessFullFrame();
        } else {
            Log.w("DailyDriver", "Schedule Partial Frame, Proportion: " + Integer.max((int) (FULL_FRAME_PROCESSING_TIME * 0.1), (int) (FULL_FRAME_PROCESSING_TIME * (float) (bottomRow - topRow + 1) / changes.length ) ) );
            scheduleProcessFrame( Integer.max((int) (FULL_FRAME_PROCESSING_TIME * 0.1), (int) (FULL_FRAME_PROCESSING_TIME * (float) (bottomRow - topRow + 1) / changes.length ) ) );
        }


    }

    private void initializeScreenTracking() {
        Log.w("DailyDriver", "Initialize Screen Tracking");
        TerminalBuffer screen = terminalEmulator.getScreen();

        currScreenChars = new char[terminalEmulator.mRows][terminalEmulator.mColumns];
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                currScreenChars[i][j] = screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
            }
        }
        sendFullFrame();
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
            for (int j = 0; j < terminalEmulator.mColumns; j++) {
                changes[i][j] = (boolean) (screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j] != currScreenChars[i][j]);
                currScreenChars[i][j] = screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
            }
        }

        /*
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            String s = "";
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                s += screen.getmLines()[screen.externalToInternalRow(i)].getmText()[j];
            }
            Log.w("DailyDriver", "TerminalEmulator: " + s);
        }
        for(int i = 0; i < terminalEmulator.mRows; i++) {
            String s = "";
            for(int j = 0; j < terminalEmulator.mColumns; j++) {
                s += currScreenChars[i][j];
            }
            Log.w("DailyDriver", "currScreenChars: " + s);
        }*/

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

    private void sendFullFrame() {
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        toozRenderer.renderToTooz(terminalEmulator, toozCanvas, 0, -1,-1,-1,-1);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);
    }




}
