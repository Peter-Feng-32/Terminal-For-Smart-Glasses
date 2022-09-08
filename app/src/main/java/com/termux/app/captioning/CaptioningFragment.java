package com.termux.app.captioning;

import android.os.Bundle;
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

    private static final int MIC_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int MIC_CHANNEL_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int MIC_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SIZE_SAMPLES = 1280;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final String SHARE_PREF_API_KEY = "api_key";

    private int currentLanguageCodePosition;
    private String currentLanguageCode;

    private AudioRecord audioRecord;
    private final byte[] buffer = new byte[BYTES_PER_SAMPLE * CHUNK_SIZE_SAMPLES];

    // This class was intended to be used from a thread where timing is not critical (i.e. do not
    // call this in a system audio callback). Network calls will be made during all of the functions
    // that RepeatingRecognitionSession inherits from SampleProcessorInterface.
    private RepeatingRecognitionSession recognizer;
    private NetworkConnectionChecker networkChecker;
    private CloudSpeechSessionFactory factory;

    //My start-stop implementation
    private boolean currentlyCaptioning = false;
    private boolean pausedCaptioning = false;
    private TextView apiKeyEditView;

    private TranscriptionResultUpdatePublisher.UpdateType prevUpdateType;

    private final TranscriptionResultUpdatePublisher transcriptUpdater =
        (formattedTranscript, updateType) -> {
            getActivity().runOnUiThread(
                () -> {

                    if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_UPDATED) {
                        if(prevUpdateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                            ((TermuxActivity)getActivity()).getTerminalView().viewDriver.clearGlassesView();
                            prevUpdateType = null;
                        }
                        caption(formattedTranscript.toString());
                    }
                     if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                         recognizer.resetAndClearTranscript();
                         prevUpdateType = TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED;
                     }
                });
        };

    private Runnable readMicData =
        () -> {
            Log.w("CaptioningFragment", "readMicData");
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                return;
            }
            recognizer.init(CHUNK_SIZE_SAMPLES);
            while (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.read(buffer, 0, CHUNK_SIZE_SAMPLES * BYTES_PER_SAMPLE);
                recognizer.processAudioBytes(buffer);
            }
            recognizer.stop();
        };


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

    public void caption(String caption) {
        String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
        ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(escapeSeq.getBytes(), escapeSeq.length());
        //Let's write only 3 rows.
        final int NUM_ROWS = 3;
        int ROW_LENGTH = ((TermuxActivity)getActivity()).getTerminalView().mEmulator.mColumns;
        final int MAX_LENGTH_TO_WRITE = ROW_LENGTH * (NUM_ROWS - 1);

        String captionSubstring;
        if(caption.length() < MAX_LENGTH_TO_WRITE) {
            captionSubstring = caption;
        } else {
            int i = caption.length() - MAX_LENGTH_TO_WRITE;
            while(caption.getBytes(StandardCharsets.UTF_8)[i] != ' ') {
                i++;
            }
            captionSubstring = caption.substring(i);
        }
        //Get only enough words to fit on 3 lines.
        //Split substring into words
        String[] splited = captionSubstring.split(" ");
        ArrayList<String> toSendArray = new ArrayList<>();
        int numCharsWritten = 0;
        int numCharsWrittenInRow = 0;
        int currIndex = 0;
        while(currIndex < splited.length && numCharsWritten < MAX_LENGTH_TO_WRITE) {
            String currWord = splited[currIndex];
            if(currWord.length() > ROW_LENGTH) {
                //If the word is bigger than a row, just send it.
                toSendArray.add(currWord);
                numCharsWritten += currWord.length();
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % ROW_LENGTH;
                if(numCharsWrittenInRow % ROW_LENGTH != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % ROW_LENGTH;
                    numCharsWritten++;
                }
            } else {
                int numCharsRemainingInRow = ROW_LENGTH - numCharsWrittenInRow;
                if(currWord.length() > numCharsRemainingInRow) {
                    //Move to next row.
                    String spaces = "";
                    for(int i = 0; i < numCharsRemainingInRow; i++) {
                        spaces = spaces + " ";
                        numCharsWritten++;
                    }
                    toSendArray.add(spaces);
                    numCharsWrittenInRow = 0;
                }
                toSendArray.add(currWord);
                numCharsWritten += currWord.length();
                numCharsWrittenInRow = (numCharsWrittenInRow + currWord.length()) % ROW_LENGTH;
                if(numCharsWrittenInRow % ROW_LENGTH != 0) {
                    toSendArray.add(" ");
                    numCharsWrittenInRow = (numCharsWrittenInRow + 1) % ROW_LENGTH;
                    numCharsWritten++;
                }
            }
            currIndex++;
        }
        for(int i = 0; i < toSendArray.size(); i++) {
            String toSend = toSendArray.get(i);
            ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(toSend.getBytes(StandardCharsets.UTF_8),toSend.getBytes(StandardCharsets.UTF_8).length);

        }
        //Send 1 row if toSend takes up 1 row, send 2 if it takes up 2, send 3 if it takes up 3...
        ((TermuxActivity)getActivity()).getTerminalView().viewDriver.redrawGlassesRows(((TermuxActivity)getActivity()).getTerminalView().getTopRow(), NUM_ROWS);

        //Note: this doesn't update the screen.
        ((TermuxActivity)getActivity()).getTerminalView().invalidate();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.w("ONCREATEVIEW", "CREATED");
        View view = inflater.inflate(R.layout.fragment_captioning, container, false);

        apiKeyEditView = view.findViewById(R.id.captioning_api_key_input);
        apiKeyEditView.setText(getApiKey(getActivity()));
        Button button = view.findViewById(R.id.btn_test_captioning);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startRecognitionSession();
            }
        });

        return view;
    }


    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();
        if(currentlyCaptioning) {
            pauseRecognitionSession();
            Log.w("CaptioningFragment", "Stopped Captioning");
            pausedCaptioning = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(currentlyCaptioning) {
            pauseRecognitionSession();
            Log.w("CaptioningFragment", "Paused Captioning");
            pausedCaptioning = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(pausedCaptioning) {
            startRecognitionSession();
            Log.w("CaptioningFragment", "Resumed Captioning");
            pausedCaptioning = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w("ONDESTROY", "DESTROYED");
        if (recognizer != null) {
            recognizer.unregisterCallback(transcriptUpdater);
            networkChecker.unregisterNetworkCallback();
        }

        if(factory != null) {
            factory.cleanup();
        }
    }

    /** Captioning Functions */

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_RECORD_AUDIO:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveApiKey(getActivity(), apiKeyEditView.getText().toString());
                    constructRepeatingRecognitionSession();
                    startRecording();
                    currentlyCaptioning = true;
                    Button button = getActivity().findViewById(R.id.btn_test_captioning);
                    button.setText("Pause Captioning");
                    button.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            pauseRecognitionSession();
                        }
                    });
                } else {
                    // This should nag user again if they launch without the permissions.
                    Toast.makeText(
                        getActivity(),
                        "This app does not work without the Microphone permission.",
                        Toast.LENGTH_SHORT)
                        .show();
                    getActivity().finish();
                }
                return;
            default: // Should not happen. Something we did not request.
        }
    }

    private void initLanguageLocale() {
        // The default locale is en-US.
        currentLanguageCode = "en-US";
        currentLanguageCodePosition = 22;
    }

    private void constructRepeatingRecognitionSession() {
        SpeechRecognitionModelOptions options =
            SpeechRecognitionModelOptions.newBuilder()
                .setLocale(currentLanguageCode)
                // As of 7/18/19, Cloud Speech's video model supports en-US only.
                .setModel(currentLanguageCode.equals("en-US") ? VIDEO : DICTATION_DEFAULT)
                .build();
        CloudSpeechSessionParams cloudParams =
            CloudSpeechSessionParams.newBuilder()
                .setObserverParams(
                    CloudSpeechStreamObserverParams.newBuilder().setRejectUnstableHypotheses(false))
                .setFilterProfanity(true)
                .setEncoderParams(
                    CloudSpeechSessionParams.EncoderParams.newBuilder()
                        .setEnableEncoder(true)
                        .setAllowVbr(true)
                        .setCodec(CodecAndBitrate.UNDEFINED))
                .build();
        networkChecker = new NetworkConnectionChecker(getActivity());
        networkChecker.registerNetworkCallback();
        Log.w("API Key", getApiKey(getActivity()));
        // There are lots of options for formatting the text. These can be useful for debugging
        // and visualization, but it increases the effort of reading the transcripts.
        TranscriptionResultFormatterOptions formatterOptions =
            TranscriptionResultFormatterOptions.newBuilder()
                .setTranscriptColoringStyle(NO_COLORING)
                .build();
        factory = new CloudSpeechSessionFactory(cloudParams, getApiKey(getActivity()));
        RepeatingRecognitionSession.Builder recognizerBuilder =
            RepeatingRecognitionSession.newBuilder()
                .setSpeechSessionFactory(factory)
                .setSampleRateHz(SAMPLE_RATE)
                .setTranscriptionResultFormatter(new SafeTranscriptionResultFormatter(formatterOptions))
                .setSpeechRecognitionModelOptions(options)
                .setNetworkConnectionChecker(networkChecker);
        recognizer = recognizerBuilder.build();
        recognizer.registerCallback(transcriptUpdater, ResultSource.WHOLE_RESULT);
    }

    private void startRecording() {
        if (audioRecord == null) {

            audioRecord =
                new AudioRecord(
                    MIC_SOURCE,
                    SAMPLE_RATE,
                    MIC_CHANNELS,
                    MIC_CHANNEL_ENCODING,
                    CHUNK_SIZE_SAMPLES * BYTES_PER_SAMPLE);
        }

        audioRecord.startRecording();
        new Thread(readMicData).start();
    }

    /** The API won't work without a valid API key. This prompts the user to enter one. */
    private void startRecognitionSession() {

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                getActivity(), new String[] {Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            saveApiKey(getActivity(), apiKeyEditView.getText().toString());
            constructRepeatingRecognitionSession();
            startRecording();
            currentlyCaptioning = true;
            ((TermuxActivity)getActivity()).getTerminalView().viewDriver.clearGlassesView();
            Button button = getActivity().findViewById(R.id.btn_test_captioning);
            button.setText("Pause Captioning");
            button.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    pauseRecognitionSession();
                }
            });
        }


    }
    private void pauseRecognitionSession() {
        if(audioRecord != null) {
            audioRecord.stop();
        }
        currentlyCaptioning = false;
        Button button = getActivity().findViewById(R.id.btn_test_captioning);
        button.setText("Resume Captioning");
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                resumeRecognitionSession();
            }
        });
    }

    private void resumeRecognitionSession() {
        if(audioRecord != null) {
            startRecording();
        }
        currentlyCaptioning = true;
        Button button = getActivity().findViewById(R.id.btn_test_captioning);
        button.setText("Pause Captioning");
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                pauseRecognitionSession();
            }
        });
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


}
