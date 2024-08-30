package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import com.termux.terminal.TerminalEmulator;
import com.vuzix.ultralite.Layout;
import com.vuzix.ultralite.UltraliteSDK;

public class Z100Renderer extends TerminalRenderer{
    UltraliteSDK ultraliteSDK;
    public Z100Renderer(int textSize, Typeface typeface, UltraliteSDK ultraliteSDK) {
        super(textSize, typeface);
        this.ultraliteSDK = ultraliteSDK;
        ultraliteSDK.requestControl();
        ultraliteSDK.setLayout(Layout.CANVAS, 0, true);
        ultraliteSDK.getCanvas().clearBackground();
        ultraliteSDK.getCanvas().commit();
    }

    private final Object lockFrames = new Object();
    private final Object lockBitmap = new Object();

    int numFramesInTransit = 0;
    int frameLimit = 2;
    int callbackId = 0;

    /*
    * Todo: fix potential bug here with missing callbacks.  Do this by adding a time-to-live to the callbacks.
    *
    * */

    class CallbackHandler implements UltraliteSDK.Canvas.CommitCallback {
        TerminalEmulator terminalEmulator;
        int topRow;
        int callbackId;
        public CallbackHandler(TerminalEmulator terminalEmulator, int topRow, int callbackId) {
            this.callbackId = callbackId;
            this.terminalEmulator = terminalEmulator;
            this.topRow = topRow;
        }

        @Override
        public void done() {
            synchronized (lockFrames) {
                numFramesInTransit--;
            }

            if (droppedBitmap) {
                droppedBitmap = false;
                renderToZ100(terminalEmulator, topRow);
            }
        }
    }

    private Bitmap original = null;
    private boolean droppedBitmap = false;

    public void renderToZ100(TerminalEmulator terminalEmulator, int topRow) {
        Bitmap bitmap = Bitmap.createBitmap(UltraliteSDK.Canvas.WIDTH, UltraliteSDK.Canvas.HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        this.render(terminalEmulator, canvas, topRow, -1, -1, -1, -1);

        Rect boundingBox;
        synchronized (lockBitmap) {
            boundingBox = findBoundingBoxOfDifferences(original, bitmap);
            if (boundingBox.bottom == boundingBox.top || boundingBox.left == boundingBox.right) {
                return; // No changes
            }

            Bitmap glassesBitmap = Bitmap.createBitmap(bitmap, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());

            synchronized (lockFrames) {
                if (numFramesInTransit == frameLimit) {
                    droppedBitmap = true;
                    return;
                }

                boolean rendered = ultraliteSDK.getCanvas().drawBackground(glassesBitmap, boundingBox.left, boundingBox.top);
                numFramesInTransit++;
                ultraliteSDK.getCanvas().commit(new CallbackHandler(terminalEmulator, topRow, callbackId++));
            }

            original = bitmap;
        }
    }

    public static Rect findBoundingBoxOfDifferences(Bitmap original, Bitmap modified) {
        if(original == null) {
            return new Rect(0, 0, modified.getWidth() - 1, modified.getHeight() - 1);
        }
        if (original.getWidth() != modified.getWidth() || original.getHeight() != modified.getHeight()) {
            return new Rect(0, 0, modified.getWidth() - 1, modified.getHeight() - 1);
        }

        int width = original.getWidth();
        int height = original.getHeight();

        int minX = width; // Initialize to width to find the smallest x
        int minY = height; // Initialize to height to find the smallest y
        int maxX = 0; // Initialize to 0 to find the largest x
        int maxY = 0; // Initialize to 0 to find the largest y

        boolean changesDetected = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (original.getPixel(x, y) != modified.getPixel(x, y)) {
                    changesDetected = true;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (!changesDetected) {
            // No changes detected; return an empty rect or a rect covering the entire bitmap.
            return new Rect(0, 0, 0, 0);
        }

        // Return the bounding box with inclusive coordinates.
        return new Rect(minX, minY, maxX, maxY);
    }


}
