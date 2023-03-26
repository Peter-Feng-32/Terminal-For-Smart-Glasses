package com.termux.app.dailydriver;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.service.notification.StatusBarNotification;

public class NotificationDrawer {
    public static Bitmap drawNotification(StatusBarNotification sbn) {
        Bitmap bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(Color.GREEN);
        p.setTextSize(60);
        //canvas.drawRect(0,0,400,200, p);
        canvas.drawText("NOTIFICATION", 0, 90, p);
        return bitmap;
    }
}
