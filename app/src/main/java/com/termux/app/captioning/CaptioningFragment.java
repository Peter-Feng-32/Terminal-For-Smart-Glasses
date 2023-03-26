package com.termux.app.captioning;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.audio.CodecAndBitrate;
import com.google.audio.NetworkConnectionChecker;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.dailydriver.NotificationDrawer;
import com.termux.app.dailydriver.NotificationListener;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.tooz.ToozDriver;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalBuffer;



/** Captioning Libraries */


import static com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.DICTATION_DEFAULT;
import static com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.VIDEO;
import static com.google.audio.asr.TranscriptionResultFormatterOptions.TranscriptColoringStyle.NO_COLORING;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.audio.asr.CloudSpeechSessionParams;
import com.google.audio.asr.CloudSpeechStreamObserverParams;
import com.google.audio.asr.RepeatingRecognitionSession;
import com.google.audio.asr.SafeTranscriptionResultFormatter;
import com.google.audio.asr.SpeechRecognitionModelOptions;
import com.google.audio.asr.TranscriptionResultFormatterOptions;
import com.google.audio.asr.TranscriptionResultUpdatePublisher;
import com.google.audio.asr.TranscriptionResultUpdatePublisher.ResultSource;
import com.google.audio.asr.cloud.CloudSpeechSessionFactory;
import com.termux.terminal.TerminalSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CaptioningFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class CaptioningFragment extends Fragment {

    /** Captioning Library stuff */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final String SHARE_PREF_API_KEY = "api_key";
    private static final String SHARE_PREF_CAPTIONING_TEXT_SIZE = "12";
    public String CAPTIONING_TERMUX_SESSION_NAME = "CAPTIONING_SESSION";
    public String DAILY_DRIVER_SESSION_NAME = "DAILY_DRIVER_SESSION";
    public String NOTIFICATION_SESSION_NAME = "NOTIFICATION_SESSION";
    private final int SCREEN_WIDTH = 400;
    private final int SCREEN_HEIGHT = 640;

    private int currentLanguageCodePosition;
    private String currentLanguageCode;

    private TextView apiKeyEditView;
    private TextView textSizeTextView;
    private boolean captioningOn = false;

    public static ToozDriver toozDriver;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment CaptioningFragment.
     */
    public static CaptioningFragment newInstance() {
        CaptioningFragment fragment = new CaptioningFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public CaptioningFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLanguageLocale();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_captioning, container, false);

        apiKeyEditView = view.findViewById(R.id.captioning_api_key_input);
        apiKeyEditView.setText(getApiKey(getActivity()));
        textSizeTextView = view.findViewById(R.id.editTextSize);
        textSizeTextView.setText(getTextSize(getActivity()));
        Button captioningButton = view.findViewById(R.id.btn_test_captioning);
        captioningButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                saveApiKey(getActivity(), apiKeyEditView.getText().toString());
                saveTextSize(getActivity(), textSizeTextView.getText().toString());
                startCaptioning();
            }
        });

        Button dailyDriverButton = view.findViewById(R.id.btn_run_daily_driver);
        dailyDriverButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startDailyDriver();
            }
        });

        Button testNotificationButton = view.findViewById(R.id.btn_test_notification);
        testNotificationButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendNotification();
            }
        });

        Button notificationSessionButton = view.findViewById(R.id.btn_toggle_notifications);
        notificationSessionButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startNotificationSession();
            }
        });

        CAPTIONING_TERMUX_SESSION_NAME = getActivity().getResources().getString(R.string.captioning_terminal_session_name);
        DAILY_DRIVER_SESSION_NAME = getActivity().getResources().getString(R.string.daily_driver_terminal_session_name);
        NOTIFICATION_SESSION_NAME = getActivity().getResources().getString(R.string.notification_terminal_session_name);

        notificationBuilder = new NotificationCompat.Builder(this.getActivity(), CHANNEL_ID)
            .setSmallIcon(R.drawable.banner)
            .setContentTitle("My notification")
            .setContentText("Much longer text that cannot fit one line...")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Much longer text that cannot fit one line..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return view;
    }

    @Override
    public void onDestroy() {
        if(captioningOn) stopCaptioning();
        super.onDestroy();
    }

    /** Handle permissions */
    private ActivityResultLauncher<String> requestAudioPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);
                getActivity().startService(captioningIntent);
                toggleCaptioningButton();
            } else {
                //Can't launch captioning without audio permissions
                return;
            }
        });

    /** Handle permissions */
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startNotificationListener();
            } else {
                //Can't use notifications
                return;
            }
        });

    private void initLanguageLocale() {
        // The default locale is en-US.
        currentLanguageCode = "en-US";
        currentLanguageCodePosition = 22;
    }

    private void toggleCaptioningButton() {
        if(captioningOn == false) {
            Button button = getActivity().findViewById(R.id.btn_test_captioning);
            button.setText("Resume Captioning!");
            button.setOnClickListener(new View.OnClickListener()
            {
                @RequiresApi(api = Build.VERSION_CODES.P)
                @Override
                public void onClick(View v)
                {
                    saveApiKey(getActivity(), apiKeyEditView.getText().toString());
                    saveTextSize(getActivity(), textSizeTextView.getText().toString());
                    startCaptioning();
                }
            });
        } else {
            Button button = getActivity().findViewById(R.id.btn_test_captioning);
            button.setText("Pause Captioning!");
            button.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    saveApiKey(getActivity(), apiKeyEditView.getText().toString());
                    saveTextSize(getActivity(), textSizeTextView.getText().toString());
                    stopCaptioning();
                    toggleCaptioningButton();
                }
            });
        }
    }

    public void startNotificationListener() {
        //if(ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE) == PackageManager.PERMISSION_GRANTED) {
            Intent notificationIntent = new Intent(getActivity(), NotificationListener.class);
            getActivity().startService(notificationIntent);
        //    Log.w("Test", "Notification Listener Started");
        //} else {
        //    Log.w("Test", "Notification Listener Not Started");
        //    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        //}
    }

    public void startCaptioning() {
        Log.w("Start", "Captioning");
        captioningOn = true;
        if(ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            TermuxActivity termuxActivity = (TermuxActivity) getActivity();
            TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
            TermuxService termuxService = termuxActivity.getTermuxService();

            //Find a captioning session. If none exists make it.
            TerminalSession terminalSession = null;
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == CAPTIONING_TERMUX_SESSION_NAME) {
                    terminalSession = termuxSession.getTerminalSession();
                }
            }
            //Find a captioning session. If none exists, we are at max sessions, and we can't start captioining.
            if(terminalSession == null) {
                termuxTerminalSessionClient.addNewSession(false, CAPTIONING_TERMUX_SESSION_NAME);
            }
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == CAPTIONING_TERMUX_SESSION_NAME) {
                    terminalSession = termuxSession.getTerminalSession();
                }
            }
            if(terminalSession == null) {
                termuxActivity.showToast("Too many Terminal Sessions open, close one!", false);
                return;
            }
            int textSize;
            try {
                textSize = Integer.parseInt(textSizeTextView.getText().toString());
            } catch(Exception e) {
                Log.w("CaptioningFragment", "Failed to parse text size.");
                return;
            }

            Paint captionPaint = makePaint(textSize);
            terminalSession.updateSize(calculateColsInScreen(captionPaint), calculateRowsInScreen(captionPaint));
            CaptioningService.textSize = textSize;
            CaptioningService.setTerminalEmulator(terminalSession.getEmulator());
            CaptioningService.setTerminalSession(terminalSession);
            Log.w("CaptioningFragment", "columns: " + SCREEN_WIDTH/textSize + " rows: " + SCREEN_HEIGHT/textSize);

            toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize);
            termuxActivity.getTermuxTerminalSessionClient().setToozDriver(toozDriver);
            Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);
            getActivity().startService(captioningIntent);
            toggleCaptioningButton();
        } else {
            requestAudioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO);
        }
    }

    public void startDailyDriver() {
        //startNotificationListener();
        Log.w("Start", "Daily Driver");
        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
        TermuxService termuxService = termuxActivity.getTermuxService();

        TerminalSession terminalSession = null;
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == DAILY_DRIVER_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxTerminalSessionClient.addNewSession(false, DAILY_DRIVER_SESSION_NAME);
        }
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == DAILY_DRIVER_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxActivity.showToast("Too many Terminal Sessions open, close one!", false);
            return;
        }
        int textSize;
        try {
            textSize = Integer.parseInt(textSizeTextView.getText().toString());
        } catch(Exception e) {
            Log.w("DailyDriverFragment", "Failed to parse text size.");
            return;
        }

        Paint toozPaint = makePaint(textSize);
        terminalSession.updateSize(calculateColsInScreen(toozPaint), calculateRowsInScreen(toozPaint));
        toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize);
        termuxActivity.getTermuxTerminalSessionClient().setToozDriver(toozDriver);

    }


    public void startNotificationSession() {
        Log.w("Start", "Notification Session");
        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
        TermuxService termuxService = termuxActivity.getTermuxService();

        TerminalSession terminalSession = null;
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == NOTIFICATION_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxTerminalSessionClient.addNewSessionWithoutSwitching(false, NOTIFICATION_SESSION_NAME);
        }
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == NOTIFICATION_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxActivity.showToast("Too many Terminal Sessions open, close one!", false);
            return;
        }

        int textSize;
        try {
            textSize = Integer.parseInt(textSizeTextView.getText().toString());
        } catch(Exception e) {
            Log.w("DailyDriverFragment", "Failed to parse text size.");
            return;
        }

        Paint toozPaint = makePaint(textSize);
        terminalSession.updateSize(calculateColsInScreen(toozPaint), calculateRowsInScreen(toozPaint));
        toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize);

        NotificationListener.setTerminalSession(terminalSession);
        NotificationListener.setToozDriver(toozDriver);
        NotificationListener.setTerminalSessionClient(termuxActivity.getTermuxTerminalSessionClient());
        NotificationListener.setEnabled(true);
        startNotificationListener();

    }

    private Paint makePaint(int textSize) {
        Paint paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);
        return paint;
    }

    private int calculateColsInScreen(Paint paint) {
        return (int)(SCREEN_WIDTH / paint.measureText("X"));
    }

    private int calculateRowsInScreen(Paint paint) {
        return (SCREEN_HEIGHT - (int) Math.ceil(paint.getFontSpacing()) - (int) Math.ceil(paint.ascent())) / (int) Math.ceil(paint.getFontSpacing());
    }

    public void stopCaptioning() {
        captioningOn = false;
        Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);
        getActivity().stopService(captioningIntent);
    }

    /** Saves the API Key in user shared preference. */
    private static void saveApiKey(Context context, String key) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SHARE_PREF_API_KEY, key)
            .commit();
    }

    /** Gets the API key from shared preference. */
    private static String getApiKey(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_API_KEY, "");
    }

    /** Saves the API Key in user shared preference. */
    private static void saveTextSize(Context context, String key) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SHARE_PREF_CAPTIONING_TEXT_SIZE, key)
            .commit();
    }

    /** Gets the API key from shared preference. */
    private static String getTextSize(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_CAPTIONING_TEXT_SIZE, "");
    }


    /** Notification Testing **/

    String CHANNEL_ID = "Notification Channel";
    int notificationId = 0;
    NotificationCompat.Builder notificationBuilder;

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "TestNotificationChannel";
            String description = "Channel to test notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getActivity().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void sendNotification() {
        createNotificationChannel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this.getActivity());
        notificationManager.notify(notificationId, notificationBuilder.build());
        notificationId++;
    }

}
