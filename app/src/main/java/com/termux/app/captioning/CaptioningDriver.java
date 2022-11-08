package com.termux.app.captioning;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.view.TerminalRenderer;
import com.termux.view.TerminalView;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import smartglasses.DriverHelper;
import smartglasses.FrameDriver;
import smartglasses.TerminalRendererTooz;


public class CaptioningDriver implements Serializable {
    TerminalRendererTooz mRenderer;
    public static CaptionRenderer captionRenderer;
    FrameDriver frameDriver;
    TerminalEmulator emulator;
    TerminalBuffer buffer;
    int textSize;
    int currTopRow;
    int fullFramesSentProcessing = 0;
    final int MAX_FRAMES_PROCESSING = 2;
    final int FRAME_PROCESSING_TIME = 1500;
    //I think we need to keep track of the time between the current frame and the first frame.
    boolean frameDropped = false;
    long lastFrameProcessedTime = -1;

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(MAX_FRAMES_PROCESSING + 1);
    Runnable processFrame = () -> {
        fullFramesSentProcessing--;
        if(fullFramesSentProcessing == MAX_FRAMES_PROCESSING - 1) {
            if(frameDropped) {
                frameDropped = false;
                checkAndHandle(currTopRow);
            }
        }
        lastFrameProcessedTime = System.currentTimeMillis();
    };


    /**
     *
     * This class should track any changes in the Terminal Emulator.
     */
    char[][] oldScreen;
    char[][] currScreen;
    Pair<Integer, Integer> cursorCoordsPrev;
    Pair<Integer, Integer> cursorCoordsCurr;

    @RequiresApi(api = Build.VERSION_CODES.P)
    public CaptioningDriver(TerminalEmulator emulator, int textSize) {
        this.frameDriver = FrameDriver.getInstance();
        this.mRenderer = new TerminalRendererTooz(textSize, Typeface.MONOSPACE);
        this.emulator = emulator;
        this.textSize = textSize;
    }

    private void updateReferences() {
        if(emulator == null) return;
    }

    public void updateBuffers(){
        oldScreen = currScreen;
        if(buffer == null) return;
        currScreen = new char[buffer.getActiveRows() - buffer.getActiveTranscriptRows()][buffer.getmLines()[buffer.externalToInternalRow(0)].getmText().length];
        for(int i = 0; i < buffer.getActiveRows() - buffer.getActiveTranscriptRows(); i++) {
            TerminalRow row = buffer.getmLines()[buffer.externalToInternalRow(0 + i)];
            for(int j = 0; j < row.getmText().length; j++) {
                currScreen[i][j] = row.getmText()[j];
            }
        }
        cursorCoordsPrev = cursorCoordsCurr;
        cursorCoordsCurr = new ImmutablePair<>(emulator.getCursorRow(), emulator.getCursorCol());
    }

    public int countDiffChars() {
        if(currScreen == null || oldScreen == null) return -1;
        if(currScreen.length != oldScreen.length) {
            return 999999;
        }
        int diffs = 0;
        for(int i = 0; i < currScreen.length; i++) {
            if(currScreen[i].length != oldScreen[i].length) return 999999;
            for(int j = 0; j < currScreen[i].length; j++) {
                //Log.w("" + i + ' ' + j, ""  + oldScreen[i][j] + " " + currScreen[i][j]);
                if(oldScreen[i][j] != currScreen[i][j]) diffs++;
            }
        }
        return diffs;
    }
    public ArrayList<Pair<Integer, Integer>> findDiffChars() {
        ArrayList<Pair<Integer, Integer>> diffCoords = new ArrayList<>();
        for(int i = 0; i < currScreen.length; i++) {
            for(int j = 0; j < currScreen[i].length; j++) {
                if(oldScreen[i][j] != currScreen[i][j]) diffCoords.add(new ImmutablePair<>(i, j));
            }
        }
        return diffCoords;
    }

    public synchronized void checkAndHandle(int topRow) {
        currTopRow = topRow;
        Log.w("ScheduleProcessFullFrame", "CheckAndHandle");
        if(fullFramesSentProcessing == MAX_FRAMES_PROCESSING) {
            //Log.w("ViewDriver", "FRAME DROPPED");
            frameDropped = true;
            return;
        }

        updateReferences();
        updateBuffers();

        int diffChars = countDiffChars();
        //Log.w("TopRow DiffChars", "" + topRow + " " + diffChars);
        //if only 1 character change, draw that single character.
        if(diffChars == -1) return;
        if(diffChars == 0) {
            redrawGlassesDelta(cursorCoordsPrev.getLeft(), cursorCoordsPrev.getRight(), 0, topRow);
            redrawGlassesDelta(cursorCoordsCurr.getLeft(), cursorCoordsCurr.getRight(), 1, topRow);
            return;
        };
        if(diffChars == 1) {
            ArrayList<Pair<Integer, Integer>> diffCharCoords = findDiffChars();
            // Log.w("PrevCursor", String.valueOf(cursorCoordsPrev.getLeft()) + ' ' + cursorCoordsPrev.getRight());
            //Log.w("CurrCursor", String.valueOf(cursorCoordsCurr.getLeft()) + ' ' + cursorCoordsCurr.getRight());

            redrawGlassesDelta(diffCharCoords.get(0).getLeft(), diffCharCoords.get(0).getRight(), 0, topRow);
            redrawGlassesDelta(cursorCoordsPrev.getLeft(), cursorCoordsPrev.getRight(), 0, topRow);
            redrawGlassesDelta(cursorCoordsCurr.getLeft(), cursorCoordsCurr.getRight(), 1, topRow);
        }
        else {
            //Else redraw the whole screen.
            //Log.w("REDRAWING FULL", "" + diffChars);
            redrawGlassesFull(topRow);
        }

        //Check if the cursor position changed and redraw the old+new cursor positions.
    }

    public void scheduleProcessFullFrame() {
        if(fullFramesSentProcessing == 0) {
            lastFrameProcessedTime = System.currentTimeMillis();
        }
        int processingDelay = (int) ((fullFramesSentProcessing + 1) * FRAME_PROCESSING_TIME - (System.currentTimeMillis() - lastFrameProcessedTime));
        scheduledExecutorService.schedule(processFrame, processingDelay, TimeUnit.MILLISECONDS);
        fullFramesSentProcessing++;
        Log.w("ScheduleProcessFullFrame", "Done scheduling " + fullFramesSentProcessing + ' ' + processingDelay);
    }

    public void redrawGlassesFull(int topRow) {
        updateReferences();
        scheduleProcessFullFrame();
        //Render full bitmap
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        mRenderer.renderToTooz(emulator, toozCanvas, topRow, -1,-1,-1,-1);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);
    }

    public void redrawGlassesDelta(int row, int col, int cursor, int topRow) {
        updateReferences();
        //Render delta update bitmap
        Bitmap mySmallBitmap = Bitmap.createBitmap(19, 59, Bitmap.Config.ARGB_8888);
        Canvas mySmallToozCanvas = new Canvas(mySmallBitmap);
        if(cursor != 0){
            mySmallToozCanvas.drawColor(Color.WHITE);
        }

        char charToRender = emulator.getScreen().getmLines()[emulator.getScreen().externalToInternalRow(row)].getmText()[col];
        //Log.w("charToRender", "" + charToRender + " " + row + ' ' + col);
        mRenderer.renderToToozSingleChar(emulator, mySmallToozCanvas, topRow, -1,-1,-1,-1, charToRender, cursor, row, col);
        //Send delta update bitmap to Tooz
        ByteArrayOutputStream mySmallOut = new ByteArrayOutputStream();
        mySmallBitmap.compress(Bitmap.CompressFormat.JPEG, 90, mySmallOut);
        byte[] mySmallByteArray = mySmallOut.toByteArray();
        String mySmallS = DriverHelper.bytesToHex(mySmallByteArray);
        //Log.w("Small Size", String.valueOf(mySmallBitmap.getWidth()));
        //Log.w("Col", ""+col);
        //Log.w("Row", "" + row);
        int cellX = (int) mRenderer.getWidthBeforeTooz(emulator, topRow, col, row);
        int cellY = (int) mRenderer.getHeightBeforeTooz(emulator, topRow, row);
        //Log.w("Cell X", "" + cellX);
        //Log.w("Cell Y", "" + cellY);
        //Log.w("Bitmap Size", "X: " + mySmallBitmap.getWidth() + " Y: " + mySmallBitmap.getHeight());
        boolean connected = frameDriver.sendFrameDelta(mySmallS, cellX + TerminalRenderer.leftOffsetTooz-3, cellY);
    }

    public void redrawGlassesRows(int topRow, int numRows) {
        updateReferences();
        //Render delta update bitmap
        Bitmap bitmap = Bitmap.createBitmap(400, 80*numRows, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        captionRenderer.renderRowsToTooz(emulator, toozCanvas, topRow, -1,-1,-1,-1, numRows);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);
    }

    public void clearGlasses() {
        updateReferences();
        //Render delta update bitmap
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);
    }

    /*
    public void testTextSize(int textSize) {
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(Color.RED);
        float scaledSizeInPixels = textSize * 2;
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(scaledSizeInPixels);
        c.drawText("TESTABCDE", 0,50,p);
        //1.64
        Log.w("Paint", "" + p.measureText("TESTABCDE"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);

    }
    */

}
