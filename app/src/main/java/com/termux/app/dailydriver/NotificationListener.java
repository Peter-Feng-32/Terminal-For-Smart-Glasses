package com.termux.app.dailydriver;

import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TITLE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.tooz.FrameDriver;
import com.termux.app.tooz.ToozDriver;
import com.termux.terminal.TerminalSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.os.Vibrator;
import android.util.Pair;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLEngineResult;

public class NotificationListener extends NotificationListenerService {
    private static TerminalSession terminalSession;
    private static ToozDriver toozDriver;
    private static TermuxTerminalSessionClient termuxTerminalSessionClient;
    private static boolean enabled = false;
    public static String TAG = "NotificationListener";

    public enum Mode {
        TOSS_ONCE,
        TOSS_TWICE,
    }
    public static Mode mode = Mode.TOSS_TWICE;
    //TODO: Decide how to handle multiple incoming notifications while one is still displayed.
    //Figure out the vibration problem and if there is a solution

    public static BlockingQueue<StatusBarNotification> notifications = new ArrayBlockingQueue<StatusBarNotification>(100);
    public static Lock notificationDisplayLock = new ReentrantLock();
    NotificationHandler notificationHandler = new NotificationHandler();
    Thread notificationHandlerThread = new Thread(notificationHandler);

    public class NotificationHandler implements Runnable {
        public NotificationHandler(){
        }

        @Override
        public void run()
        {
            while(true) {
                try {
                    Log.w(TAG, "Before Take");

                    StatusBarNotification sbn = notifications.take();
                    Log.w(TAG, "After Take");
                    String pack = sbn.getPackageName();
                    final PackageManager pm = getApplicationContext().getPackageManager();
                    ApplicationInfo ai;
                    try {
                        ai = pm.getApplicationInfo(pack, 0);
                    } catch (final PackageManager.NameNotFoundException e) {
                        ai = null;
                    }
                    final String applicationName = (String) (ai != null ?
                        pm.getApplicationLabel(ai) : "(unknown)");

                    String title = sbn.getNotification().extras.get(EXTRA_TITLE) == null ? "(unknown title)" : sbn.getNotification().extras.get(EXTRA_TITLE).toString();
                    String text = sbn.getNotification().extras.get(EXTRA_TEXT) == null ? "(unknown text)" : sbn.getNotification().extras.get(EXTRA_TEXT).toString();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (sbn.getNotification().getChannelId().equals(TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID)) {
                            continue;
                        }
                    }

                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        //deprecated in API 26
                        v.vibrate(100);
                    }

                    Log.w(TAG, "Handling Notification");

                    notificationDisplayLock.lock();
                    if (mode == Mode.TOSS_ONCE) {
                        termuxTerminalSessionClient.setToozEnabled(false);
                        String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left
                        terminalSession.getEmulator().append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                        terminalSession.getEmulator().append(applicationName.getBytes(StandardCharsets.UTF_8), applicationName.getBytes(StandardCharsets.UTF_8).length);
                        String escapeSeqNextLine = "\033[E"; //Go to next line
                        terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
                        terminalSession.getEmulator().append(title.getBytes(StandardCharsets.UTF_8), title.getBytes(StandardCharsets.UTF_8).length);
                        terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
                        terminalSession.getEmulator().append(text.getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8).length);
                        if (toozDriver.sendFullFrame() == -1) {
                            termuxTerminalSessionClient.setToozEnabled(true);
                            if(termuxTerminalSessionClient.getToozDriver() != null) {
                                termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                            }
                            notificationDisplayLock.unlock();
                            return;
                        }
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        int response = toozDriver.requestAccelerometerData(40, 0, termuxTerminalSessionClient);
                        if (response == -1) {
                            termuxTerminalSessionClient.setToozEnabled(true);
                            if(termuxTerminalSessionClient.getToozDriver() != null) {
                                termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                            }
                        }
                        notificationDisplayLock.unlock();
                    } else if (mode == Mode.TOSS_TWICE) {
                        int response = toozDriver.requestAccelerometerDataSilent(40, 5000, termuxTerminalSessionClient);
                        if (response == -1 || response == -2) {
                            notificationDisplayLock.unlock();
                            return;
                        } else {
                            termuxTerminalSessionClient.setToozEnabled(false);
                            String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left
                            terminalSession.getEmulator().append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                            terminalSession.getEmulator().append(applicationName.getBytes(StandardCharsets.UTF_8), applicationName.getBytes(StandardCharsets.UTF_8).length);
                            String escapeSeqNextLine = "\033[E"; //Go to next line
                            terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
                            terminalSession.getEmulator().append(title.getBytes(StandardCharsets.UTF_8), title.getBytes(StandardCharsets.UTF_8).length);
                            terminalSession.getEmulator().append(escapeSeqNextLine.getBytes(), escapeSeqNextLine.getBytes(StandardCharsets.UTF_8).length);
                            terminalSession.getEmulator().append(text.getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8).length);

                            if (toozDriver.sendFullFrame() == -1) {
                                termuxTerminalSessionClient.setToozEnabled(true);
                                if(termuxTerminalSessionClient.getToozDriver() != null) {
                                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                                }
                                notificationDisplayLock.unlock();
                                return;
                            }
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                termuxTerminalSessionClient.setToozEnabled(true);
                                if(termuxTerminalSessionClient.getToozDriver() != null) {
                                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                                }
                                notificationDisplayLock.unlock();
                                return;
                            }
                            int secondResponse = toozDriver.requestAccelerometerData(40, 50000, termuxTerminalSessionClient);
                            if (secondResponse == -1) {
                                termuxTerminalSessionClient.setToozEnabled(true);
                                if(termuxTerminalSessionClient.getToozDriver() != null) {
                                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                                }
                            }
                            notificationDisplayLock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        TAG = "onNotificationPosted";
        Log.w(TAG, "Notification ");


        if(!enabled) return;
        if(terminalSession == null || toozDriver == null || termuxTerminalSessionClient == null) {
            enabled = false;
            stopSelf();
            return;
        }
        boolean notificationInserted = notifications.offer(sbn);
        Log.w(TAG, "New Notification Added : " + notificationInserted);

        if(!notificationHandlerThread.isAlive() && !notificationHandlerThread.isInterrupted()) {
            notificationHandlerThread = new Thread(notificationHandler);
            notificationHandlerThread.start();
        }

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        TAG = "onNotificationRemoved";
        Log.d(TAG, "id = " + sbn.getId() + "Package Name" + sbn.getPackageName() +
            "Post time = " + sbn.getPostTime() + "Tag = " + sbn.getTag());

        if(!enabled) return;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w("onNotificationListenerStart", "Start");
        cancelAllNotifications();
        return super.onStartCommand(intent, flags, startId);
    }

    public static void setTerminalSessionClient(TermuxTerminalSessionClient client) { termuxTerminalSessionClient = client; }
    public static void setTerminalSession(TerminalSession session) {
        terminalSession = session;
    }
    public static void setToozDriver(ToozDriver driver) {
        toozDriver = driver;
    }
    public static void setEnabled(boolean en) { enabled = en; }
    public static boolean getEnabled() {
        return enabled;
    }

    public static void clearNotificationQueue() {
        notifications.clear();
    }


}
