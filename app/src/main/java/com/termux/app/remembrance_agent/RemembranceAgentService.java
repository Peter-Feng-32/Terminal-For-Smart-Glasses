package com.termux.app.remembrance_agent;

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
import com.termux.app.captioning.CaptioningFormatter;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.BufferedReader;
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RemembranceAgentService extends Service {

    public static TerminalEmulator terminalEmulator;
    public static TerminalSession terminalSession;
    public static InformationRetriever informationRetriever;
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
    private int numUpdates = 0;
    private final int QUERY_DELAY = 1;

    //My start-stop implementation
    private TranscriptionResultUpdatePublisher.UpdateType prevUpdateType;

    CaptioningFormatter formatter = null;
    private final TranscriptionResultUpdatePublisher transcriptUpdater =
        (formattedTranscript, updateType) -> {

            if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_UPDATED) {
                if(prevUpdateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                    prevUpdateType = null;
                    // String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
                    formatter.resetSavedCaption();
                    // terminalEmulator.append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                }
                if (numUpdates == QUERY_DELAY) {
                    Log.w("RemembranceAgent", "Query: " + formattedTranscript.toString());
                    String formatted = formatter.process(formattedTranscript.toString());
                    queryRemembranceAgent(formatted);
                    numUpdates = 0;
                } else {
                    numUpdates += 1;
                }
            }
            if(updateType == TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED) {
                recognizer.resetAndClearTranscript();
                prevUpdateType = TranscriptionResultUpdatePublisher.UpdateType.TRANSCRIPT_FINALIZED;
            }
        };

    private Runnable readMicData =
        () -> {
            Log.w("RemembranceAgent", "readMicData");
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

    String prevRetrieved = "";

    public RemembranceAgentService() {
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
        RA_INDEX_EXEC = getApplicationContext().getFilesDir().getAbsolutePath() + "/home/remembrance-agent/bin/ra-index";
        RA_RETRIEVE_EXEC = getApplicationContext().getFilesDir().getAbsolutePath() + "/home/remembrance-agent/bin/ra-retrieve";
        RA_INDEX_PATH = getApplicationContext().getFilesDir().getAbsolutePath() + "/home/remembrance-agent/index";
        RA_FILES_PATH = getApplicationContext().getFilesDir().getAbsolutePath() + "/home/remembrance-agent/files";

        initLanguageLocale();
        indexRemembranceAgent();

        startRemembranceAgent();
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
        releaseRemembranceAgent();
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
        return "YOUR API KEY";
        // return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARE_PREF_API_KEY, "");
    }

    public static void setTerminalEmulator(TerminalEmulator emulator) {
        terminalEmulator = emulator;
    }
    public static void setTerminalSession(TerminalSession session) {
        terminalSession = session;
    }

    public static void setInformationRetriever(InformationRetriever informationRetriever) {
        informationRetriever = informationRetriever;
    }

    private OutputStream remembranceAgentOutputStream = null; // Write to RA here
    private InputStream remembranceAgentInputStream = null; // Read from RA here
    private OutputStream indexingOutputStream = null; // Write to RA here
    private InputStream indexingInputStream = null; // Read from RA here

    private Thread remembranceAgentThread = null;
    private Thread indexingThread = null;

    private String RA_INDEX_EXEC = null;
    private String  RA_RETRIEVE_EXEC = null;
    private String  RA_INDEX_PATH = null;
    private String  RA_FILES_PATH = null;

    public void startRemembranceAgent() {
        Map<String, String> env = System.getenv();

        String command = RA_RETRIEVE_EXEC + " " + RA_INDEX_PATH;
        remembranceAgentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Send script into runtime process
                    Process child = Runtime.getRuntime().exec(command);
                    // Get input and output streams

                    remembranceAgentOutputStream = child.getOutputStream();
                    remembranceAgentInputStream = child.getInputStream();

                    Thread outputListenerThread = new Thread(
                        new Runnable() {
                                @Override
                                public void run() {
                                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(remembranceAgentInputStream))) {
                                        String line;
                                        // Continuously read from the InputStream
                                        while ((line = reader.readLine()) != null) {
                                            Log.w("Remembrance Agent", "Read: " + line);  // Process the data from the stream

                                            String[] queryResult = parseResult(line);
                                            if (queryResult.length == 2) {
                                                String header = queryResult[0];
                                                String title = queryResult[1];
                                                String escapeSeq = "\033[2J\033[H"; //Clear screen and move cursor to top left.
                                                Log.w("Remembrance Agent", header);
                                                Log.w("Remembrance Agent", title);
                                                terminalEmulator.append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                                                terminalEmulator.append(header.getBytes(), header.getBytes(StandardCharsets.UTF_8).length);

                                                escapeSeq = "\033[E"; //Go to new line
                                                terminalEmulator.append(escapeSeq.getBytes(), escapeSeq.getBytes(StandardCharsets.UTF_8).length);
                                                terminalEmulator.append(title.getBytes(), title.getBytes(StandardCharsets.UTF_8).length);
                                            }

                                            terminalSession.notifyScreenUpdate();

                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        );
                    outputListenerThread.start();

                    child.waitFor();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (remembranceAgentOutputStream != null) {
                            remembranceAgentOutputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (remembranceAgentInputStream != null) {
                            remembranceAgentInputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        remembranceAgentThread.start();
    }

    public void indexRemembranceAgent() {
        String command = RA_INDEX_EXEC + " -v " + RA_INDEX_PATH + " " + RA_FILES_PATH;
        indexingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process child = Runtime.getRuntime().exec(new String[]{"su","-c", command});

                    // Get input and output streams
                    indexingOutputStream = child.getOutputStream();
                    indexingInputStream = child.getInputStream();

                    Thread outputListenerThread = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexingInputStream))) {
                                    String line;
                                    // Continuously read from the InputStream
                                    while ((line = reader.readLine()) != null) {
                                        Log.w("Remembrance Agent", "Indexing read: " + line);  // Process the data from the stream
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    );
                    outputListenerThread.start();

                    child.waitFor();
                } catch (IOException e) {
                    Log.w("Remembrance Agent", "IOException problem");
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    Log.w("Remembrance Agent", "Interrupted problem");
                    e.printStackTrace();
                } finally {
                    try {
                        if (indexingOutputStream != null) {
                            indexingOutputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (indexingInputStream != null) {
                            indexingInputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        indexingThread.start();
    }


    private void queryRemembranceAgent(final String query) {
        String querycommand = "query\n";
        String testquery = query + "\n";

        if (remembranceAgentOutputStream != null) {
            try {
                remembranceAgentOutputStream.write(querycommand.getBytes(StandardCharsets.UTF_8));
                remembranceAgentOutputStream.flush();

                remembranceAgentOutputStream.write(testquery.getBytes(StandardCharsets.UTF_8));
                remembranceAgentOutputStream.flush();
                Log.w("Remembrance agent", "Flushed: " + testquery);

                remembranceAgentOutputStream.write(5);
                remembranceAgentOutputStream.write('\n');
                remembranceAgentOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String[] parseResult(String result) {
        String[] output = {};
        if (result == ".") {
            return output;
        }
        try {
            String[] split = result.split("\\|");
            String header = split[0];
            String title = split[2];

            return new String[]{header, title};
        } catch (IndexOutOfBoundsException e) {
            return output;
        }
    }

    private void releaseRemembranceAgent() {
        try {
            if (remembranceAgentOutputStream != null) {
                remembranceAgentOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (remembranceAgentInputStream != null) {
                remembranceAgentInputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (remembranceAgentThread != null) {
            remembranceAgentThread.interrupt();
        }
        if (indexingThread != null) {
            indexingThread.interrupt();
        }
    }



}

