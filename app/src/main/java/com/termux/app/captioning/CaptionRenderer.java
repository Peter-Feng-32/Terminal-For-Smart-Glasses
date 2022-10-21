package com.termux.app.captioning;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
        Log.w("Buffer Height: ", "" + height);
        if (height == 0) return;
        setupPaint();
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
        paint.setTextSize(40f); //* context.resources.displayMetrics.density
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        //paint.textAlign = Paint.Align.
        paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY);
    }
}
