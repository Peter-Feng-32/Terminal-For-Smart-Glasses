package com.termux.app.dailydriver;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;


import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import android.content.Context;
import android.preference.PreferenceManager;
import android.widget.TextView;

import com.termux.terminal.TerminalSession;




/**
 * A simple {@link Fragment} subclass.
 * Use the {@link com.termux.app.dailydriver.DailyDriverFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DailyDriverFragment extends Fragment {


    private static final String SHARE_PREF_DAILY_DRIVER_TEXT_SIZE = "12";
    private final String DAILY_DRIVER_SESSION_NAME = "Daily Driver Session";
    private final int SCREEN_WIDTH = 400;
    private final int SCREEN_HEIGHT = 640;

    private TextView textSizeTextView;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DailyDriverFragment.
     */
    public static DailyDriverFragment newInstance() {
        DailyDriverFragment fragment = new DailyDriverFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public DailyDriverFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_daily_driver, container, false);

        textSizeTextView = view.findViewById(R.id.edit_text_size_daily_driver);
        textSizeTextView.setText(getTextSize(getActivity()));
        Button dailyDriverButton = view.findViewById(R.id.btn_daily_driver_start);
        dailyDriverButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                saveTextSize(getActivity(), textSizeTextView.getText().toString());
                startDailyDriver();
            }
        });
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    public void startDailyDriver() {
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
        terminalSession.updateSize(calculateColsInScreen(toozPaint) - 1, calculateRowsInScreen(toozPaint));
        termuxActivity.getTermuxTerminalSessionClient().setDailyDriver(new DailyDriver(terminalSession.getEmulator(), textSize));
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


    /** Saves the API Key in user shared preference. */
    private static void saveTextSize(Context context, String key) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SHARE_PREF_DAILY_DRIVER_TEXT_SIZE, key)
            .commit();
    }

    /** Gets the API key from shared preference. */
    private static String getTextSize(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_DAILY_DRIVER_TEXT_SIZE, "");
    }

}
