package com.termux.app.captioning;

import static android.content.Context.POWER_SERVICE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.dailydriver.NotificationListener;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.tooz.FrameDriver;
import com.termux.view.ToozConstants;
import com.termux.app.tooz.ToozDriver;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;


/** Captioning Libraries */


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.termux.terminal.TerminalSession;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CaptioningFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class CaptioningFragment extends Fragment {

    /** Captioning Library stuff */
    private static final String SHARE_PREF_API_KEY = "api_key";
    private static final String SHARE_PREF_CAPTIONING_TEXT_SIZE = "captioning_text_size";
    private static final String SHARE_PREF_TEXT_COLOR_HEX = "captioning_text_color_hex";
    private static final String NOTIFICATIONS_ENABLED = "notifications_enabled";
    private final int PHYS_SCREEN_WIDTH = 400;
    private final int PHYS_SCREEN_HEIGHT = 640;

    private int currentLanguageCodePosition;
    private String currentLanguageCode;

    private TextView apiKeyEditView;
    private TextView textSizeTextView;
    private boolean captioningOn = false;
    private boolean dailyDriverOn = false;

    private int x = 100;
    private int y = 0;
    private int screen_height = 600;
    private int screen_width = 200;

    public static ToozDriver toozDriver;

    private static CaptioningFragment captioningFragment;

    public String testString = "test";  //Marker for where we start notifications.
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

    public static synchronized CaptioningFragment getInstance() {
        return captioningFragment;
    }

    public CaptioningFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLanguageLocale();
        captioningFragment = this;
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
                textSizeTextView.setText(saveTextSize(getActivity(), textSizeTextView.getText().toString()));
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
                toggleDailyDriverButton();
            }
        });


        //Color customizibility
        EditText redColorEditText = view.findViewById(R.id.edit_text_color_red);
        EditText greenColorEditText = view.findViewById(R.id.edit_text_color_green);
        EditText blueColorEditText = view.findViewById(R.id.edit_text_color_blue);

        //Load existing color

        String existingColor = getTextColor(getActivity());
        redColorEditText.setText("" + Integer.parseInt(existingColor.substring(0, 2), 16));
        greenColorEditText.setText("" + Integer.parseInt(existingColor.substring(2, 4), 16));
        blueColorEditText.setText("" + Integer.parseInt(existingColor.substring(4, 6), 16));


        redColorEditText.addTextChangedListener(new TextWatcher(){
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                saveTextColor(getActivity(), s.toString(), greenColorEditText.getText().toString(), blueColorEditText.getText().toString());

                String textColor = getTextColor(getActivity());
                String redHex = textColor.substring(0, 2);
                try{
                    Log.w("CaptioningFragmentTest", "TextColor : " + textColor);
                    Log.w("CaptioningFragmentTest", "Red : "  + redHex + " " + Integer.parseInt(redHex, 16));
                }
                catch(NumberFormatException e) {
                    e.printStackTrace();
                }

                if(s.toString().equals("") || Integer.parseInt(s.toString()) < 0 || Integer.parseInt(s.toString()) > 255) {
                    redColorEditText.removeTextChangedListener(this);
                    redColorEditText.setText("" + Integer.parseInt(redHex, 16));
                    redColorEditText.addTextChangedListener(this);
                }

                if(toozDriver != null) {
                    toozDriver.setTextColor(0xff000000 + Integer.parseInt(textColor, 16));
                }


            }
        });
        greenColorEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                saveTextColor(getActivity(), redColorEditText.getText().toString(), s.toString(), blueColorEditText.getText().toString());

                String textColor = getTextColor(getActivity());
                String greenHex = textColor.substring(2, 4);

                if(s.toString().equals("") || Integer.parseInt(s.toString()) < 0 || Integer.parseInt(s.toString()) > 255) {
                    greenColorEditText.removeTextChangedListener(this);
                    greenColorEditText.setText("" + Integer.parseInt(greenHex, 16));
                    greenColorEditText.addTextChangedListener(this);
                }

                if(toozDriver != null) {
                    toozDriver.setTextColor(0xff000000 + Integer.parseInt(textColor, 16));
                }

            }
        });
        blueColorEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                saveTextColor(getActivity(), redColorEditText.getText().toString(),  greenColorEditText.getText().toString(), s.toString());

                String textColor = getTextColor(getActivity());
                String blueHex = textColor.substring(4, 6);

                if(s.toString().equals("") || Integer.parseInt(s.toString()) < 0 || Integer.parseInt(s.toString()) > 255) {
                    blueColorEditText.removeTextChangedListener(this);
                    blueColorEditText.setText("" + Integer.parseInt(blueHex, 16));
                    blueColorEditText.addTextChangedListener(this);
                }
                if(toozDriver != null) {
                    toozDriver.setTextColor(0xff000000 + Integer.parseInt(textColor,16));
                }

            }
        });

        //Notifications
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
                toggleNotificationButton();
            }
        });
        notificationSessionButton.setText(getNotificationsEnabled(getContext()) ? "Disable Notifications" : "Enable Notifications");

        NotificationListener.clearNotificationQueue();

        Button clearNotificationsButton = view.findViewById(R.id.btn_clear_notifications);
        clearNotificationsButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                NotificationListener.clearNotificationQueue();
            }
        });

        ToggleButton notificationModeToggleButton = view.findViewById(R.id.notification_mode_toggle_button);
        notificationModeToggleButton.setChecked(NotificationListener.mode == NotificationListener.Mode.TOSS_TWICE);
        notificationModeToggleButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(notificationModeToggleButton.isChecked()) {
                    NotificationListener.mode = NotificationListener.Mode.TOSS_TWICE;
                } else {
                    NotificationListener.mode = NotificationListener.Mode.TOSS_ONCE;
                }
            }
        });

        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        NotificationListener.setTerminalSessionClient(termuxActivity.getTermuxTerminalSessionClient());

        notificationBuilder = new NotificationCompat.Builder(this.getActivity(), CHANNEL_ID)
            .setSmallIcon(R.drawable.banner)
            .setContentTitle("Message")
            .setContentText("Hey Thad, see you at dinner!")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Hey Thad, see you at dinner!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return view;
    }

    @Override
    public void onDestroy() {
        if(captioningOn) stopCaptioning();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        FrameDriver frameDriver = FrameDriver.getInstance();
        if (!frameDriver.isConnected()) {
            frameDriver.searchAndConnect();
        }
    }

    /** Handle permissions */
    private ActivityResultLauncher<String> requestAudioPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                saveApiKey(getActivity(), apiKeyEditView.getText().toString());
                textSizeTextView.setText(saveTextSize(getActivity(), textSizeTextView.getText().toString()));
                startCaptioning();
            } else {
                //Can't launch captioning without audio permissions
                return;
            }
        });

    private void initLanguageLocale() {
        // The default locale is en-US.
        currentLanguageCode = "en-US";
        currentLanguageCodePosition = 22;
    }

    private void toggleNotificationButton() {
        if(getNotificationsEnabled(getContext())) {
            Button button = getActivity().findViewById(R.id.btn_toggle_notifications);
            saveNotificationsEnabled(getContext(), false);
            NotificationListener.setEnabled(false);
            button.setText("Enable Notifications");
        } else {
            Button button = getActivity().findViewById(R.id.btn_toggle_notifications);
            startNotificationSession();
            NotificationListener.setEnabled(true);
            saveNotificationsEnabled(getContext(), true);
            button.setText("Disable Notifications Notifications");
        }

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
                    textSizeTextView.setText(saveTextSize(getActivity(), textSizeTextView.getText().toString()));
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
                    textSizeTextView.setText(saveTextSize(getActivity(), textSizeTextView.getText().toString()));
                    stopCaptioning();
                    toggleCaptioningButton();
                }
            });
        }
    }

    private void toggleDailyDriverButton() {
        if(dailyDriverOn == false) {
            Button button = getActivity().findViewById(R.id.btn_run_daily_driver);
            button.setText("Resume Daily Driver!");
            button.setOnClickListener(new View.OnClickListener()
            {
                @RequiresApi(api = Build.VERSION_CODES.P)
                @Override
                public void onClick(View v)
                {
                    startDailyDriver();
                    toggleDailyDriverButton();
                }
            });
        } else {
            Button button = getActivity().findViewById(R.id.btn_run_daily_driver);
            button.setText("Pause Daily Driver!");
            button.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    stopDailyDriver();
                    toggleDailyDriverButton();
                }
            });
        }
    }

    public void startNotificationListener() {
        Intent notificationIntent = new Intent(getActivity(), NotificationListener.class);
        getActivity().startService(notificationIntent);
    }

    public void startCaptioning() {
        Log.w("Start", "Captioning");
        if(dailyDriverOn) {
            stopDailyDriver();
            toggleDailyDriverButton();
        }
        captioningOn = true;
        if(ContextCompat.checkSelfPermission(this.getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            TermuxActivity termuxActivity = (TermuxActivity) getActivity();
            TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
            TermuxService termuxService = termuxActivity.getTermuxService();

            //Find a captioning session. If none exists make it.
            TerminalSession terminalSession = null;
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == ToozConstants.CAPTIONING_TERMUX_SESSION_NAME) {
                    terminalSession = termuxSession.getTerminalSession();
                }
            }
            //Find a captioning session. If none exists, we are at max sessions, and we can't start captioining.
            if(terminalSession == null) {
                termuxTerminalSessionClient.addNewSession(false, ToozConstants.CAPTIONING_TERMUX_SESSION_NAME);
            }
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == ToozConstants.CAPTIONING_TERMUX_SESSION_NAME) {
                    terminalSession = termuxSession.getTerminalSession();
                }
            }
            if(terminalSession == null) {
                termuxActivity.showToast("Too many Terminal Sessions open, close one!", false);
                return;
            }else {
                if(!termuxTerminalSessionClient.getCurrentSession().equals(terminalSession)) {
                    termuxTerminalSessionClient.setCurrentSession(terminalSession);
                }
            }
            int textSize;
            try {
                textSize = Integer.parseInt(textSizeTextView.getText().toString());
            } catch(Exception e) {
                Log.w("CaptioningFragment", "Failed to parse text size.");
                return;
            }


            //Get screen size and position here from UI.
            //Use in calculate paint to determine number of rows + columns.
            //Set x and y as parameters in ToozDriver
            //Update bitmap sizes in ToozDriver

            Paint captionPaint = makePaint(textSize);
            terminalSession.updateSize(calculateColsInScreen(captionPaint), calculateRowsInScreen(captionPaint));
            CaptioningService.textSize = textSize;
            CaptioningService.setTerminalEmulator(terminalSession.getEmulator());
            CaptioningService.setTerminalSession(terminalSession);

            toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize, 0xff000000 + Integer.parseInt(getTextColor(getActivity()), 16), this.getContext(), screen_width, screen_height, x, y);
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
        Log.w("Start", "Daily Driver");

        if(captioningOn) {
            stopCaptioning();
            toggleCaptioningButton();
        }
        dailyDriverOn = true;

        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
        TermuxService termuxService = termuxActivity.getTermuxService();

        TerminalSession terminalSession = null;
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == ToozConstants.DAILY_DRIVER_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxTerminalSessionClient.addNewSession(false, ToozConstants.DAILY_DRIVER_SESSION_NAME);
        }
        for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = termuxService.getTermuxSession(i);
            if(termuxSession.getTerminalSession().mSessionName == ToozConstants.DAILY_DRIVER_SESSION_NAME) {
                terminalSession = termuxSession.getTerminalSession();
            }
        }
        if(terminalSession == null) {
            termuxActivity.showToast("Too many Terminal Sessions open, close one!", false);
            return;
        } else {
            termuxTerminalSessionClient.setCurrentSession(terminalSession);
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
        toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize, 0xff000000 + Integer.parseInt(getTextColor(getActivity()), 16), getContext(), screen_width, screen_height, x, y);
        termuxActivity.getTermuxTerminalSessionClient().setToozDriver(toozDriver);
    }

    public void startNotificationSession() {
        if(NotificationManagerCompat.getEnabledListenerPackages(this.getContext()).contains(this.getContext().getPackageName())) {
            TermuxActivity termuxActivity = (TermuxActivity) getActivity();
            TermuxTerminalSessionClient termuxTerminalSessionClient = termuxActivity.getTermuxTerminalSessionClient();
            TermuxService termuxService = termuxActivity.getTermuxService();

            TerminalSession terminalSession = null;
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == ToozConstants.NOTIFICATION_SESSION_NAME) {
                    terminalSession = termuxSession.getTerminalSession();
                }
            }
            if(terminalSession == null) {
                termuxTerminalSessionClient.addNewSessionWithoutSwitching(false, ToozConstants.NOTIFICATION_SESSION_NAME);
            }
            for(int i = 0; i < termuxService.getTermuxSessionsSize(); i++) {
                TermuxSession termuxSession = termuxService.getTermuxSession(i);
                if(termuxSession.getTerminalSession().mSessionName == ToozConstants.NOTIFICATION_SESSION_NAME) {
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
            toozDriver = new ToozDriver(terminalSession.getEmulator(), textSize, 0xff000000 + Integer.parseInt(getTextColor(getActivity()), 16), getContext(), screen_width, screen_height, x, y);

            NotificationListener.setTerminalSession(terminalSession);
            NotificationListener.setToozDriver(toozDriver);
            NotificationListener.setTerminalSessionClient(termuxActivity.getTermuxTerminalSessionClient());
            Log.w("Captioning Fragment", "Starting Notifications");
            startNotificationListener();
        } else {
            final AlertDialog.Builder b = new AlertDialog.Builder(this.getActivity());
            b.setIcon(android.R.drawable.ic_dialog_alert);
            b.setTitle("App does not have permission to access notifications.");
            b.setMessage("Please grant it notifications from Settings and try again.");
            b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                }
            });
            b.setNegativeButton(android.R.string.no, null);
            b.show();

        }

    }

    private Paint makePaint(int textSize) {
        Paint paint = new Paint();
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);
        return paint;
    }

    private int calculateColsInScreen(Paint paint) {
        return (int)(screen_width / paint.measureText("X"));
    }

    private int calculateRowsInScreen(Paint paint) {
        return (screen_height - (int) Math.ceil(paint.getFontSpacing()) - (int) Math.ceil(paint.ascent())) / (int) Math.ceil(paint.getFontSpacing());
    }

    public void stopCaptioning() {
        captioningOn = false;
        Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);
        getActivity().stopService(captioningIntent);
        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        termuxActivity.getTermuxTerminalSessionClient().setToozDriver(null);
    }

    public void stopDailyDriver() {
        dailyDriverOn = false;
        Log.w("Stop", "Daily Driver");
        TermuxActivity termuxActivity = (TermuxActivity) getActivity();
        termuxActivity.getTermuxTerminalSessionClient().setToozDriver(null);
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
    private static String saveTextSize(Context context, String size) {
        try{
            if(Integer.parseInt(size) < 20) {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(SHARE_PREF_CAPTIONING_TEXT_SIZE, "20")
                    .commit();
                return "20";
            }
            else if(Integer.parseInt(size) > 50) {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(SHARE_PREF_CAPTIONING_TEXT_SIZE, "50")
                    .commit();
                return "50";
            }
            else {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(SHARE_PREF_CAPTIONING_TEXT_SIZE, size)
                    .commit();
                return size;
            }
        }catch(NumberFormatException e) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(SHARE_PREF_CAPTIONING_TEXT_SIZE, size)
                .commit();
            return "50";
        }
    }

    /** Gets the API key from shared preference. */
    private static String getTextSize(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_CAPTIONING_TEXT_SIZE, "40");
    }

    /** Saves the API Key in user shared preference. */
    private static String saveTextColor(Context context, String red, String green, String blue) {
        String hexCode = "";
        String redHex;
        String greenHex;
        String blueHex;

        try{
            if(red.equals("")) {
                redHex = "00";
            }
            else if(Integer.parseInt(red) < 0) {
                redHex = "00";
            }
            else if(Integer.parseInt(red) > 255) {
                redHex = "FF";
            }
            else {
                redHex = String.format("%1$02X", Integer.parseInt(red));
            }
            if(green.equals("")) {
                greenHex = "00";
            }
            else if(Integer.parseInt(green) < 0) {
                greenHex = "00";
            }
            else if(Integer.parseInt(green) > 255) {
                greenHex = "FF";
            }
            else {
                greenHex = String.format("%1$02X", Integer.parseInt(green));
            }
            if(blue.equals("")) {
                blueHex = "00";
            }
            else if(Integer.parseInt(blue) < 0) {
                blueHex = "00";
            }
            else if(Integer.parseInt(blue) > 255) {
                blueHex = "FF";
            }
            else {
                blueHex = String.format("%1$02X", Integer.parseInt(blue));
            }
        }catch(NumberFormatException e) {
            e.printStackTrace();
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(SHARE_PREF_TEXT_COLOR_HEX, "00FF00")
                .commit();
            return "00FF00";

        }
        hexCode += redHex;
        hexCode += greenHex;
        hexCode += blueHex;

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SHARE_PREF_TEXT_COLOR_HEX, hexCode)
            .commit();

        return hexCode;
    }

    private static String getTextColor(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_TEXT_COLOR_HEX, "00FF00");
    }

    private static boolean saveNotificationsEnabled(Context context, boolean notificationsEnabled) {
        if(notificationsEnabled) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(NOTIFICATIONS_ENABLED, "true")
                .commit();
        } else {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(NOTIFICATIONS_ENABLED, "false")
                .commit();
        }
        return notificationsEnabled;
    }

    public static boolean getNotificationsEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(NOTIFICATIONS_ENABLED, "true").equals("true");
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
