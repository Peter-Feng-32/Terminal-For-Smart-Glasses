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

    public static Lock notificationLock = new ReentrantLock();

    public class NotificationHandler implements Runnable {
        private StatusBarNotification sbn;
        public NotificationHandler(StatusBarNotification sbn){
            this.sbn = sbn;
        }

        @Override
        public void run()
        {
            try {
                notificationLock.lockInterruptibly();
                Log.w("Synchronized", "Test");
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
                        notificationLock.unlock();
                        return;
                    }
                }

                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    //deprecated in API 26
                    v.vibrate(100);
                }

                if (mode == Mode.TOSS_ONCE) {
                    Log.w("onNotificationPosted", "Toss Once");
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
                    if (prevEnabled) {
                        Log.w("TEST", "PREV ENABLED");
                        Log.w("TEST", "" + termuxTerminalSessionClient.getEnabled());
                        if (toozDriver.sendFullFrame() == -1) {
                            Log.w("TEST", "Full frame -1");
                            termuxTerminalSessionClient.setToozEnabled(true);
                            if(termuxTerminalSessionClient.getToozDriver() != null) {
                                termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                            }
                            notificationLock.unlock();
                            return;
                        }
                        try {
                            Log.w("TEST", "Sleeping");
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.w("TEST", "Requesting Accel data");

                        int response = toozDriver.requestAccelerometerData(40, 0, termuxTerminalSessionClient);
                        if (response == -1) {
                            Log.w("TEST", "Accel -1");
                            termuxTerminalSessionClient.setToozEnabled(true);
                            if(termuxTerminalSessionClient.getToozDriver() != null) {
                                termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                            }
                        } else {
                            Log.w("TEST", "Accel" + response);
                        }
                    } else {
                        Log.w("TEST", "Prev enabled. should not hit this.");
                        if(termuxTerminalSessionClient.getToozDriver() != null) {
                            termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                        }
                        termuxTerminalSessionClient.setToozEnabled(true);

                    }
                } else if (mode == Mode.TOSS_TWICE) {
                    Log.w("onNotificationPosted", "Toss Twice");
                    int response = toozDriver.requestAccelerometerDataSilent(40, 5000, termuxTerminalSessionClient);
                    if (response == -1) {
                        notificationLock.unlock();
                        return;
                    } else if (response == -2) {
                         notificationLock.unlock();
                        return;
                    } else {
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
                        if (prevEnabled) {
                            Log.w("TEST", "PREV ENABLED");
                            if (toozDriver.sendFullFrame() == -1) {
                                termuxTerminalSessionClient.setToozEnabled(true);
                                if(termuxTerminalSessionClient.getToozDriver() != null) {
                                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                                }
                                notificationLock.unlock();
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
                                notificationLock.unlock();
                                return;
                            }
                            int secondResponse = toozDriver.requestAccelerometerData(40, 50000, termuxTerminalSessionClient);
                            if (secondResponse == -1) {
                                termuxTerminalSessionClient.setToozEnabled(true);
                                if(termuxTerminalSessionClient.getToozDriver() != null) {
                                    termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                                }
                            }
                        } else {
                            toozDriver.sendFullFrame();
                            if(termuxTerminalSessionClient.getToozDriver() != null) {
                                termuxTerminalSessionClient.getToozDriver().initializeScreenTracking();
                            }
                            termuxTerminalSessionClient.setToozEnabled(true);
                        }
                    }
                }
            } catch (InterruptedException e) {
                //lockInterruptibly throws interruptedexception when not able to acquire lock and is interrupted.
                //We never acquired the lock and never entered the block.
                Log.w("InterruptedException", "NEVER entered block");
                return;
            }
            //Unlock
            notificationLock.unlock();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        Log.w(TAG, "Notification ");
        TAG = "onNotificationPosted";

        if(!enabled) return;
        if(terminalSession == null || toozDriver == null || termuxTerminalSessionClient == null) {
            enabled = false;
            stopSelf();
            return;
        }
        NotificationHandler notificationHandler = new NotificationHandler(sbn);
        Thread notificationHandlerThread = new Thread(notificationHandler);
        notificationHandlerThread.start();


        //After 60 seconds, if the notification is not accessed, interrupt it.
        //Maybe store all notification threads inside a deque, and interrupt all of them?  Would solve issue where they are being
        //Interrupted one by one, and vibrations are all going off.
        int timeout = 60000;
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        Runnable runnable = new Runnable() {
            public void run() {
                // Do something
                if(notificationHandlerThread.isAlive() && !notificationHandlerThread.isInterrupted()) {
                    notificationHandlerThread.interrupt();
                }
            }
        };
        if(timeout > 0) {
            worker.schedule(runnable, timeout, TimeUnit.MILLISECONDS);
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


}
