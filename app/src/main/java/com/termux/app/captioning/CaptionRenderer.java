package com.termux.app.captioning;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import com.termux.view.TerminalRenderer;

import org.apache.commons.io.output.ByteArrayOutputStream;

import smartglasses.DriverHelper;
import smartglasses.FrameDriver;

public class CaptionRenderer {
    private TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    FrameDriver frameDriver = FrameDriver.getInstance();

    public void render(char[][] buffer, Bitmap bmp) {
        Canvas canvas = new Canvas(bmp);
        canvas.save();
        for(int i = 0; i < buffer.length; i++) {
            String line = String.valueOf(buffer[i]);
            StaticLayout textLayout = new StaticLayout(line, paint, canvas.getWidth() , Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            canvas.translate(0f,textLayout.getHeight() - 0.0f);
            textLayout.draw(canvas);
            Log.w("Buffer Drawing Line", "" + i);
        }
        canvas.restore();
    }

    public void processCaption(char[][] buffer) {
        int height = (int) measureLineHeight(buffer) * buffer.length;
        Log.w("Buffer Height", "" + height);
        if (height == 0) return;
        setupPaint();
        Log.w("Buffer width", "" + buffer[0].length );
        setTextSizeForWidth(paint, 400, buffer);
        Bitmap bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
        render(buffer, bmp);
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 20, byteOut);
        byte[] imageBytes = byteOut.toByteArray();
        String imageHex = DriverHelper.bytesToHex(imageBytes);
        Log.w("Buffer Size", "" + imageHex.length());
        frameDriver.sendFullFrame(imageHex);
    }

    public float measureHeight(char[][] buffer) {
        if(buffer.length == 0) return 0;
        String line = String.valueOf(buffer[0]);
        StaticLayout textLayout = new StaticLayout(line, paint, 400 , Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        return textLayout.getHeight() * buffer.length;
    }
    public float measureLineHeight(char[][] buffer) {
        if(buffer.length == 0) return 0;
        String line = String.valueOf(buffer[0]);
        StaticLayout textLayout = new StaticLayout(line, paint, 400 , Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        Log.w("Buffer Rows", buffer.length + "");
        return textLayout.getHeight();
    }

    private void setupPaint() {
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        //paint.setTextSize(40f); //* context.resources.displayMetrics.density
        paint.setTypeface(Typeface.MONOSPACE);
        //paint.textAlign = Paint.Align.
        paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY);
    }

    public void clearGlasses() {
        //Render delta update bitmap
        Bitmap bitmap = Bitmap.createBitmap(400, 640, Bitmap.Config.ARGB_8888);
        //Send full bitmap to tooz
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 1, out);
        byte[] byteArray = out.toByteArray();
        String s = DriverHelper.bytesToHex(byteArray);
        frameDriver.sendFullFrame(s);

    }

    private void setTextSizeForWidth(Paint paint, float desiredWidth,
                                            char[][]buffer) {

        // Pick a reasonably large value for the test. Larger values produce
        // more accurate results, but may cause problems with hardware
        // acceleration. But there are workarounds for that, too; refer to
        // http://stackoverflow.com/questions/6253528/font-size-too-large-to-fit-in-cache
        final float testTextSize = 48f;

        // Get the bounds of the text, using our testTextSize.
        paint.setTextSize(testTextSize);
        Rect bounds = new Rect();
        String line = String.valueOf(buffer[0]);
        Log.w("Buffer line", line);

        // Calculate the desired size as a proportion of our testTextSize.
        Log.w("Buffer desiredWidth", ""+desiredWidth);
        Log.w("Buffer bounds.width()", ""+bounds.width());
        float desiredTextSize = testTextSize * desiredWidth / paint.measureText(line);
        Log.w("Buffer desiredTextSize","" + desiredTextSize);
        // Set the paint for that size.
        paint.setTextSize(desiredTextSize);
    }

}
