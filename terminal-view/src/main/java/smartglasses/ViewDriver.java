package smartglasses;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.view.TerminalRenderer;
import com.termux.view.TerminalView;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;

public class ViewDriver {
    TerminalRendererTooz mRenderer;
    FrameDriver frameDriver;
    TerminalView view;
    TerminalEmulator emulator;
    TerminalBuffer buffer;

    /**
     *
     * This class should track any changes in the Terminal View.
     */
    char[][] oldScreen;
    char[][] currScreen;
    Pair<Integer, Integer> cursorCoordsPrev;
    Pair<Integer, Integer> cursorCoordsCurr;

    public ViewDriver(TerminalView view, TerminalRendererTooz renderer, Context context) {
        this.frameDriver = new FrameDriver(context);

        this.mRenderer = renderer;
        this.view = view;
        this.emulator = view.mEmulator;
    }

    private void updateReferences() {
        this.mRenderer = this.view.rendererTooz;
        this.emulator = view.mEmulator;
        if(emulator == null) return;
        this.buffer = view.mEmulator.getScreen();
    }

    public void updateBuffers(){
        oldScreen = currScreen;
        if(buffer == null) return;

        currScreen = new char[buffer.getActiveRows() - buffer.getActiveTranscriptRows()][buffer.getmLines()[buffer.externalToInternalRow(view.getTopRow())].getmText().length];
        for(int i = 0; i < buffer.getActiveRows() - buffer.getActiveTranscriptRows(); i++) {
            TerminalRow row = buffer.getmLines()[buffer.externalToInternalRow(view.getTopRow() + i)];
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

    public void checkAndHandle(int topRow) {
        updateReferences();
        updateBuffers();

        int diffChars = countDiffChars();
        Log.w("TopRow DiffChars", "" + topRow + " " + diffChars);
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

    public void redrawGlassesFull(int topRow) {
        updateReferences();
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
        Log.w("charToRender", "" + charToRender + " " + row + ' ' + col);
        mRenderer.renderToToozSingleChar(emulator, mySmallToozCanvas, topRow, -1,-1,-1,-1, charToRender, cursor, row, col);
        //Send delta update bitmap to Tooz
        ByteArrayOutputStream mySmallOut = new ByteArrayOutputStream();
        mySmallBitmap.compress(Bitmap.CompressFormat.JPEG, 90, mySmallOut);
        byte[] mySmallByteArray = mySmallOut.toByteArray();
        String mySmallS = DriverHelper.bytesToHex(mySmallByteArray);
        Log.w("Small Size", String.valueOf(mySmallBitmap.getWidth()));
        Log.w("Col", ""+col);
        Log.w("Row", "" + row);
        int cellX = (int) mRenderer.getWidthBeforeTooz(emulator, topRow, col, row);
        int cellY = (int) mRenderer.getHeightBeforeTooz(emulator, topRow, row);
        Log.w("Cell X", "" + cellX);
        Log.w("Cell Y", "" + cellY);
        Log.w("Bitmap Size", "X: " + mySmallBitmap.getWidth() + " Y: " + mySmallBitmap.getHeight());
        boolean connected = frameDriver.sendFrameDelta(mySmallS, cellX + TerminalRenderer.leftOffsetTooz-3, cellY);
        if(!connected) {
            //Not connected.  Can't send delta update.  Send entire terminal screen.
            redrawGlassesFull(topRow);
        }
    }


    public void redrawGlassesRows(int topRow, int numRows) {

        updateReferences();
        //Render delta update bitmap
        Bitmap bitmap = Bitmap.createBitmap(400, 70 * numRows, Bitmap.Config.ARGB_8888);
        Canvas toozCanvas = new Canvas(bitmap);
        mRenderer.renderRowsToTooz(emulator, toozCanvas, topRow, -1,-1,-1,-1, numRows);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);
    }

    public void clearGlassesView() {

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



}
