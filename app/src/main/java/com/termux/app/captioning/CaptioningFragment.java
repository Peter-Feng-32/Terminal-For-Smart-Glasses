package com.termux.app.captioning;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

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
import com.termux.app.dailydriver.DailyDriver;
import com.termux.app.terminal.TermuxTerminalSessionClient;
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
    private final String CAPTIONING_TERMUX_SESSION_NAME = "Captioning Session";
    private final int SCREEN_WIDTH = 400;
    private final int SCREEN_HEIGHT = 640;

    private int currentLanguageCodePosition;
    private String currentLanguageCode;

    private TextView apiKeyEditView;
    private TextView textSizeTextView;
    private boolean captioningOn = false;

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
        return view;
    }

    @Override
    public void onDestroy() {
        if(captioningOn) stopCaptioning();
        super.onDestroy();
    }

    /** Captioning Functions */

    /** Handle permissions */
    private ActivityResultLauncher<String> requestAudioPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);
                getActivity().startService(captioningIntent);
                toggleButton();
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

    private void toggleButton() {
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
                    toggleButton();
                }
            });
        }

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

            Paint captionPaint = captionPaint(textSize);
            terminalSession.updateSize(calculateColsInScreen(captionPaint) - 1, calculateRowsInScreen(captionPaint));
            CaptioningService.textSize = textSize;
            CaptioningService.setTerminalEmulator(terminalSession.getEmulator());
            CaptioningDriver.captionRenderer = new CaptionRenderer(captionPaint);
            Log.w("CaptioningFragment", "columns: " + SCREEN_WIDTH/textSize + " rows: " + SCREEN_HEIGHT/textSize);
            Intent captioningIntent = new Intent(getActivity(), CaptioningService.class);

            termuxActivity.getTermuxTerminalSessionClient().setDailyDriver(new DailyDriver(terminalSession.getEmulator(), textSize));


            getActivity().startService(captioningIntent);
            toggleButton();
        } else {
            requestAudioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO);
        }
    }

    private Paint captionPaint(int textSize) {
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

}
