package jp.ac.titech.itpro.sdl.cyberviewer;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognizeResponse;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.cloud.speech.v1p1beta1.SpeechSettings;
import com.google.cloud.speech.v1p1beta1.WordInfo;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class SubtitleGen extends Application {
    private final static String TAG = SubtitleGen.class.getSimpleName();



    /**
     * Transcribe a short audio file using synchronous speech recognition
     *
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static List<SpeechRecognitionResult> Recognize(InputStream credentialIs, Path outputPath) throws IOException {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentialIs));
        SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
        // Instantiates a client
        try (SpeechClient speechClient = SpeechClient.create(settings)) {
            byte[] data = Files.readAllBytes(outputPath);
            Log.d(TAG, "Transcript: " + outputPath);
            // The language of the supplied audio
            ArrayList<String> languageList = new ArrayList<>();
//            languageList.add("zh");
//            languageList.add("en-US");

            // Sample rate in Hertz of the audio data sent
            int sampleRateHertz = 48000;

            // Encoding of audio data sent. This sample sets this explicitly.
            // This field is optional for FLAC and WAV audio formats.
            RecognitionConfig.AudioEncoding encoding = RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED;
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setLanguageCode("ja-JP")
//                            .addAllAlternativeLanguageCodes(languageList)
                            .setSampleRateHertz(sampleRateHertz)
                            .setEncoding(encoding)
                            .setAudioChannelCount(2)
                            .setEnableAutomaticPunctuation(true)
                            .setEnableWordTimeOffsets(true)
                            .build();
            ByteString content = ByteString.copyFrom(data);
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(content).build();
//            RecognizeRequest request = RecognizeRequest.newBuilder().setConfig(config).setAudio(audio).build();
            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response = speechClient.longRunningRecognizeAsync(config, audio);

            List<SpeechRecognitionResult> results = response.get().getResultsList();

            for (SpeechRecognitionResult result : results) {
                // First alternative is the most probable result
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                Log.d(TAG, "Transcript: " + alternative.getTranscript());

                for (WordInfo wordInfo : alternative.getWordsList()) {
                    Log.d("Transcript", "Word: " + wordInfo.getWord());
                    Log.d("Transcript", "Time interval: " + wordInfo.getStartTime().getSeconds()
                            + "." + wordInfo.getStartTime().getNanos() / 100000000 + " ~ " + wordInfo.getEndTime().getSeconds() + "." + wordInfo.getEndTime().getNanos() / 100000000);
                }
            }

            Log.d(TAG, "Transcript: nothing?");
            return results;
        } catch (Exception exception) {
            Log.d(TAG, "Transcript: Failed to create the client due to: " + exception);
        }

        return null;
    }

    public static void test(InputStream is) throws Exception {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(is));
        SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
        // Instantiates a client
        try (SpeechClient speechClient = SpeechClient.create(settings)) {

            // The path to the audio file to transcribe
            String gcsUri = "gs://cloud-samples-data/speech/brooklyn_bridge.raw";

            // Builds the sync recognize request
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            .setSampleRateHertz(16000)
                            .setLanguageCode("en-US")
                            .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gcsUri).build();

            // Performs speech recognition on the audio file
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            for (SpeechRecognitionResult result : results) {
                // There can be several alternative transcripts for a given chunk of speech. Just use the
                // first (most likely) one here.
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                Log.d(TAG, "Transcription: " + alternative.getTranscript());
            }
        }
    }



}
