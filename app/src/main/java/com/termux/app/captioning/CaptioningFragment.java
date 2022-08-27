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
    private TextView transcript;

    private final TranscriptionResultUpdatePublisher transcriptUpdater =
        (formattedTranscript, updateType) -> {
            getActivity().runOnUiThread(
                () -> {

                    Log.w("Captioning Fragment", "transcriptUpdater");

                    if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_UPDATED) {
                        testCaptioning(formattedTranscript.toString());

                    }
                     if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                         recognizer.resetAndClearTranscript();
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
                Log.w("CaptioningFragment", "RECORDSTATE_RECORDING");
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


    public void testCaptioning() {
        Log.w("Test Captioning", "Success!!!");
        TerminalBuffer buffer = ((TermuxActivity)getActivity()).getTerminalView().mEmulator.getScreen();
        Log.w("Preparing to write", "Test");
        Log.w("Buffer", "" + buffer.getmLines()[buffer.externalToInternalRow(0)].getmText()[1]);
        ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(new byte[]{72},1);
        //Note: this doesn't update the screen.
        ((TermuxActivity)getActivity()).getTerminalView().viewDriver.checkAndHandle(((TermuxActivity)getActivity()).getTerminalView().getTopRow());
        ((TermuxActivity)getActivity()).getTerminalView().invalidate();
        Log.w("Write to emulator", "Test");
    }


    public void testCaptioning(String caption) {
        Log.w("Test Captioning", "Success!!!");
        TerminalBuffer buffer = ((TermuxActivity)getActivity()).getTerminalView().mEmulator.getScreen();
        Log.w("Preparing to write", "Test");
        Log.w("Buffer", "" + buffer.getmLines()[buffer.externalToInternalRow(0)].getmText()[1]);


        String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
        ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(escapeSeq.getBytes(), escapeSeq.length());
        //Let's write only 35 characters.
        String startWord;
        if(caption.length() < 35) {
            startWord = caption;
        } else {
            int i = caption.length() - 35;
            while(caption.getBytes(StandardCharsets.UTF_8)[i] != ' ') {
                i++;
            }
            startWord = caption.substring(i);
        }
        Log.w("Caption", caption + ' ' + startWord);

        ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(startWord.getBytes(StandardCharsets.UTF_8),startWord.getBytes(StandardCharsets.UTF_8).length);


        //Note: this doesn't update the screen.
        ((TermuxActivity)getActivity()).getTerminalView().viewDriver.checkAndHandle(((TermuxActivity)getActivity()).getTerminalView().getTopRow());
        ((TermuxActivity)getActivity()).getTerminalView().invalidate();
        Log.w("Write to emulator", "Test");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_captioning, container, false);

        Button button = (Button) view.findViewById(R.id.btn_test_captioning);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // do something
                testCaptioning();
            }
        });

        return view;
    }


    @Override
    public void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                getActivity(), new String[] {Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            showAPIKeyDialog();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (audioRecord != null) {
            audioRecord.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.unregisterCallback(transcriptUpdater);
            networkChecker.unregisterNetworkCallback();
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
                    showAPIKeyDialog();
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

        // There are lots of options for formatting the text. These can be useful for debugging
        // and visualization, but it increases the effort of reading the transcripts.
        TranscriptionResultFormatterOptions formatterOptions =
            TranscriptionResultFormatterOptions.newBuilder()
                .setTranscriptColoringStyle(NO_COLORING)
                .build();
        RepeatingRecognitionSession.Builder recognizerBuilder =
            RepeatingRecognitionSession.newBuilder()
                .setSpeechSessionFactory(new CloudSpeechSessionFactory(cloudParams, getApiKey(getActivity())))
                .setSampleRateHz(SAMPLE_RATE)
                .setTranscriptionResultFormatter(new SafeTranscriptionResultFormatter(formatterOptions))
                .setSpeechRecognitionModelOptions(options)
                .setNetworkConnectionChecker(networkChecker);
        recognizer = recognizerBuilder.build();
        recognizer.registerCallback(transcriptUpdater, ResultSource.WHOLE_RESULT);
        Log.w("CaptioningFragment", "registerCallback");
    }

    private void startRecording() {
        Log.w("CaptioningFragment", "Start Recording");

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
    private void showAPIKeyDialog() {
        saveApiKey(getActivity(), "AIzaSyAofDrGGuie_Y6OtQiIMF72bII8S7w_J9Y");
        Log.w("CaptioningFragment", "show API Key Dialogue");

        constructRepeatingRecognitionSession();
        startRecording();
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
