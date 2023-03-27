package com.termux.app.dailydriver;

import static android.app.Notification.EXTRA_PEOPLE_LIST;
import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TITLE;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.tooz.ToozDriver;
import com.termux.terminal.TerminalSession;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NotificationListener extends NotificationListenerService {
    private static TerminalSession terminalSession;
    private static ToozDriver toozDriver;
    private static TermuxTerminalSessionClient termuxTerminalSessionClient;
    private static boolean enabled = false;
    public static String TAG = "NotificationListener";

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        TAG = "onNotificationPosted";
        String pack = sbn.getPackageName();
        final PackageManager pm = getApplicationContext().getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo( pack, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            ai = null;
        }
        final String applicationName = (String) (ai != null ?
            pm.getApplicationLabel(ai) : "(unknown)");

        String title = sbn.getNotification().extras.get(EXTRA_TITLE) == null ? "(unknown title)" : sbn.getNotification().extras.get(EXTRA_TITLE).toString();
        String text = sbn.getNotification().extras.get(EXTRA_TEXT) == null ? "(unknown text)" : sbn.getNotification().extras.get(EXTRA_TEXT).toString();

        if(!enabled) return;
        if(terminalSession == null || toozDriver == null || termuxTerminalSessionClient == null) {
            Log.w(TAG, "Null object");
            return;
        }

        boolean prevEnabled = termuxTerminalSessionClient.getEnabled();

        termuxTerminalSessionClient.setToozEnabled(false);

        String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left
        terminalSession.getEmulator().append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
        terminalSession.getEmulator().append(applicationName.getBytes(StandardCharsets.UTF_8), applicationName.getBytes(StandardCharsets.UTF_8).length);
        String escapeSeqNextLine = "\033[E"; //Go to next line
        terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
        terminalSession.getEmulator().append(title.getBytes(StandardCharsets.UTF_8), title.getBytes(StandardCharsets.UTF_8).length);
        terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
        terminalSession.getEmulator().append(text.getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8).length);
        if(prevEnabled) {
            Log.w("TEST", "PREV ENABLED");
            toozDriver.sendFullFrame();
            toozDriver.requestGyroData(400, termuxTerminalSessionClient);
        }
        else {
            toozDriver.processUpdate();
        }

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        TAG = "onNotificationRemoved";
        Log.d(TAG, "id = " + sbn.getId() + "Package Name" + sbn.getPackageName() +
            "Post time = " + sbn.getPostTime() + "Tag = " + sbn.getTag());

        if(!enabled) return;
    }

    public static void setTerminalSessionClient(TermuxTerminalSessionClient client) { termuxTerminalSessionClient = client; }
    public static void setTerminalSession(TerminalSession session) {
        terminalSession = session;
    }
    public static void setToozDriver(ToozDriver driver) {
        toozDriver = driver;
    }
    public static void setEnabled(boolean en) { enabled = en; }

}
