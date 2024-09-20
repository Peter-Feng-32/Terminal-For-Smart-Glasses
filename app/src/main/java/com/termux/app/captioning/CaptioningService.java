package com.termux.app.captioning;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import android.util.Log;

import com.google.audio.CodecAndBitrate;
import com.google.audio.NetworkConnectionChecker;


/** Captioning Libraries */


import static com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.DICTATION_DEFAULT;
import static com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.VIDEO;
import static com.google.audio.asr.TranscriptionResultFormatterOptions.TranscriptColoringStyle.NO_COLORING;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.preference.PreferenceManager;

import com.google.audio.asr.CloudSpeechSessionParams;
import com.google.audio.asr.CloudSpeechStreamObserverParams;
import com.google.audio.asr.RepeatingRecognitionSession;
import com.google.audio.asr.SafeTranscriptionResultFormatter;
import com.google.audio.asr.SpeechRecognitionModelOptions;
import com.google.audio.asr.TranscriptionResultFormatterOptions;
import com.google.audio.asr.TranscriptionResultUpdatePublisher;
import com.google.audio.asr.TranscriptionResultUpdatePublisher.ResultSource;
import com.google.audio.asr.cloud.CloudSpeechSessionFactory;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;

import java.nio.charset.StandardCharsets;

public class CaptioningService extends Service {

    public static TerminalEmulator terminalEmulator;
    public static TerminalSession terminalSession;
    public static int textSize = 1;

    public static boolean logging = true;

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

    File logs;

    //My start-stop implementation
    private TranscriptionResultUpdatePublisher.UpdateType prevUpdateType;

    CaptioningFormatter formatter = null;
    private final TranscriptionResultUpdatePublisher transcriptUpdater =
        (formattedTranscript, updateType) -> {
            Log.w("Handle", "Transcript");

            if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_UPDATED) {
                if(prevUpdateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                    prevUpdateType = null;
                    String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
                    formatter.resetSavedCaption();
                    terminalEmulator.append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                }
                handleCaption(formattedTranscript.toString());
            }
            if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                recognizer.resetAndClearTranscript();
                prevUpdateType = TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED;
            }
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
                recognizer. processAudioBytes(buffer);
            }
            recognizer.stop();
        };

    private void handleCaption(String caption) {
        String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
        terminalEmulator.append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);

        Log.w("Handle", caption);

        String formatted = formatter.process(caption);
        for(int i = 0; i <= ((formatted.length() - 1)/ formatter.numChars); i++) {

            for(int j = 0; j < Integer.min(formatter.rows, ((formatted.length() - 1) / formatter.cols + 1) - (i * formatter.rows) ); j++) {
                String substr = formatted.substring((i * formatter.numChars) + (j * formatter.cols), Integer.min((i * formatter.numChars) + (j+1) * formatter.cols, formatted.length()));
                terminalEmulator.append(substr.getBytes(StandardCharsets.UTF_8),substr.getBytes(StandardCharsets.UTF_8).length);
                if(substr.length() < formatter.cols) {
                    String clearToRight = "\033[0K"; //Clear screen to the right.
                    terminalEmulator.append(clearToRight.getBytes(StandardCharsets.UTF_8),clearToRight.getBytes(StandardCharsets.UTF_8).length);
                }
                if(j < (formatter.rows - 1)) {
                    String clearNextLine = "\033[1B\033[2K\033[1A"; //Clear next line
                    terminalEmulator.append(clearNextLine.getBytes(StandardCharsets.UTF_8),clearNextLine.getBytes(StandardCharsets.UTF_8).length);
                    String moveToNextLine = "\033[E"; //Move to next line
                    terminalEmulator.append(moveToNextLine.getBytes(StandardCharsets.UTF_8),moveToNextLine.getBytes(StandardCharsets.UTF_8).length);
                } else {
                    String moveToTopLeft = "\033[H"; //Clear screen and move cursor to top left.
                    terminalEmulator.append(moveToTopLeft.getBytes(StandardCharsets.UTF_8),moveToTopLeft.getBytes(StandardCharsets.UTF_8).length);
                }
            }
        }
        terminalSession.notifyScreenUpdate();
    }

    public CaptioningService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
        //throw new UnsupportedOperationException("Not yet implemented");
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initLanguageLocale();
        constructRepeatingRecognitionSession();
        startRecording();

        formatter = new CaptioningFormatter(terminalEmulator.mRows, terminalEmulator.mColumns);

        logs = new File(this.getExternalFilesDir(null), String.valueOf(System.currentTimeMillis()) + ".txt");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(audioRecord != null) {
            audioRecord.stop();
        }
    }

    /** Captioning Functions */
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
        networkChecker = new NetworkConnectionChecker(this);
        networkChecker.registerNetworkCallback();
        Log.w("API Key", getApiKey(this));
        // There are lots of options for formatting the text. These can be useful for debugging
        // and visualization, but it increases the effort of reading the transcripts.
        TranscriptionResultFormatterOptions formatterOptions =
            TranscriptionResultFormatterOptions.newBuilder()
                .setTranscriptColoringStyle(NO_COLORING)
                .build();
        factory = new CloudSpeechSessionFactory(cloudParams, getApiKey(this));
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

    /** Gets the API key from shared preference. */
    private static String getApiKey(Context context) {
        return "AIzaSyDM2w_vhjkl36iOOd9Vr-1A7C7-1mb4j7A";
        // return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_API_KEY, "");
    }

    public static void setTerminalEmulator(TerminalEmulator emulator) {
        terminalEmulator = emulator;
    }
    public static void setTerminalSession(TerminalSession session) {
        terminalSession = session;
    }

}
