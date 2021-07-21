package jp.ac.titech.itpro.sdl.cyberviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.MediaController;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.cloud.speech.v1p1beta1.WordInfo;
import com.google.cloud.videointelligence.v1.Entity;
import com.google.cloud.videointelligence.v1.ObjectTrackingAnnotation;
import com.google.cloud.videointelligence.v1.TextAnnotation;
import com.google.cloud.videointelligence.v1.VideoAnnotationResults;
import com.google.cloud.videointelligence.v1.VideoSegment;
import com.google.protobuf.Duration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

public class VideoPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener,
        MediaController.MediaPlayerControl {
    private final static String TAG = VideoPlayerActivity.class.getSimpleName();
    public final static String VIDEO_EXTRA = "selectedVideoIntent";
    private MediaController controller;
    private MediaPlayer mp;
    private SurfaceHolder sh;
    private SurfaceView videoView;
    private Uri selectedVideoUri;
    private final Handler handler = new Handler();
    private final Handler videoHandler = new Handler();
    private TextView subtitle;
    private HashMap<Long, String> subtitleHm;
    private VideoAnnotationResults trackObjectResults;
    private InputStream credentialIs;
    private Path videoPath;
    private TextView infoView;
    private TextView textView;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.video_view);

        credentialIs = this.getResources().openRawResource(R.raw.credential);
        selectedVideoUri = getIntent().getParcelableExtra(VIDEO_EXTRA);
        Log.d("uri", selectedVideoUri.getPath());

        subtitle = findViewById(R.id.subtitle);
        subtitle.setMovementMethod(new ScrollingMovementMethod());
        subtitle.setEnabled(false);
        subtitle.setEnabled(true);

        videoView = findViewById(R.id.video_view);
        sh = videoView.getHolder();
        sh.addCallback(this);
        videoPath = getVideoPath(this, selectedVideoUri);

        try {
            List<SpeechRecognitionResult> subtitleRaws = SubtitleGen.Recognize(credentialIs, getAudioPath(videoPath));
            assert subtitleRaws != null;
            subtitleHm = parseSubtitle(subtitleRaws);
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (Map.Entry mapElement : subtitleHm.entrySet()) {
            long key = (long) mapElement.getKey();
            String value = (String)mapElement.getValue();
            Log.d("subtitleHm: ", "key: " + key + " value: " + value);
        }

        videoView.setOnTouchListener((v, event) -> {
            if (controller != null) {
                controller.show();
            }
            return false;
        });

        infoView = findViewById(R.id.info_view);
        infoView.setMovementMethod(new ScrollingMovementMethod());
        infoView.append("\n\n");

        textView = findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        textView.append("\n\n");

        new TrackObjectTask().execute(this);
        new ExtractTextTask().execute(this);

    }


    @SuppressLint("StaticFieldLeak")
    private class ExtractTextTask extends AsyncTask<Context, Void, String> {

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        protected String doInBackground(Context... params) {
            try {
                credentialIs = params[0].getResources().openRawResource(R.raw.credential);
                trackObjectResults = VideoAI.detectText(credentialIs, videoPath);
                for (TextAnnotation annotation : trackObjectResults.getTextAnnotationsList()) {
                    textView.post(() -> textView.append("Text: \"" + annotation.getText() + "\"\n"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class TrackObjectTask extends AsyncTask<Context, Void, String> {

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        protected String doInBackground(Context... params) {
            try {
                credentialIs = params[0].getResources().openRawResource(R.raw.credential);
                trackObjectResults = VideoAI.trackObjects(credentialIs, videoPath);
                for (ObjectTrackingAnnotation annotation : trackObjectResults.getObjectAnnotationsList()) {

                    if (annotation.hasEntity()) {
                        Entity entity = annotation.getEntity();
                        infoView.post(() -> infoView.append("Entity description: <" + entity.getDescription() + ">\n"));
                    }

                    if (annotation.hasSegment()) {
                        VideoSegment videoSegment = annotation.getSegment();
                        Duration startTimeOffset = videoSegment.getStartTimeOffset();
                        Duration endTimeOffset = videoSegment.getEndTimeOffset();
                        // Display the segment time in seconds, 1e9 converts nanos to seconds
                        infoView.post(() -> infoView.append("Entity segment: " + (startTimeOffset.getSeconds() / 10 +
                                startTimeOffset.getNanos() / 1e9) + " ~ " + (endTimeOffset.getSeconds() + endTimeOffset.getNanos() / 1e9) + "\n\n"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    private HashMap<Long, String> parseSubtitle(List<SpeechRecognitionResult> subtitleRaws) {
        HashMap<Long, String> subtitleHm = new HashMap<>();

        for (SpeechRecognitionResult result : subtitleRaws) {
            // First alternative is the most probable result
            SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);


            for (WordInfo wordInfo : alternative.getWordsList()) {
                long key = wordInfo.getStartTime().getSeconds() * 10 + wordInfo.getStartTime().getNanos() / 100000000;
                if (subtitleHm.containsKey(key)){
                    String curValue = subtitleHm.get(key);
                    assert curValue != null;
                    subtitleHm.put(key, curValue + wordInfo.getWord().split("\\|")[0]);
                } else {
                    subtitleHm.put(key, wordInfo.getWord().split("\\|")[0]);
                }
            }
        }
        return subtitleHm;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Path getVideoPath(Context context, Uri uri){
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC) + File.separator + "final.mp4");

        String outputVideo = file.getAbsolutePath();
        try {
            Log.d(TAG, "outputFile: " + outputVideo);
            MultimediaExtractor.extractTrack(context, uri, outputVideo, true, true);
        } catch (IOException e) {
            Log.d(TAG, "Transcription: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Paths.get(outputVideo);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Path getAudioPath(Path inputMP4){
        File audioFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC) + File.separator + "final2.mp3");
        String outputAudio = audioFile.getAbsolutePath();

        int rc = FFmpeg.execute(" -i " + inputMP4 + " -y " + outputAudio);
        if (rc == RETURN_CODE_SUCCESS) {
            Log.i(TAG, "Command execution completed successfully.");
        } else if (rc == RETURN_CODE_CANCEL) {
            Log.i(TAG, "Command execution cancelled by user.");
        } else {
            Log.i(TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
            Config.printLastCommandOutput(Log.INFO);
        }

        return Paths.get(outputAudio);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        mp = new MediaPlayer();
        mp.setDisplay(sh);
        mp.setOnPreparedListener(this);
        try {
            mp.setDataSource(this, selectedVideoUri, null);
            mp.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        controller = new MediaController(this);

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        mp.setDisplay(null);
        if (mp.isPlaying()) mp.stop();
        mp.reset();
        mp.release();
        mp = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    try {
                        if (mp.isPlaying()) {
                            long currentPosition = mp.getCurrentPosition();
                            Log.d("subtitle mp current pos", String.valueOf(currentPosition / 100));
                            subtitle.post(() -> {
                                if (subtitleHm.get(currentPosition / 100) != null) {
                                    subtitle.append(subtitleHm.get(currentPosition / 100));
                                }
                            });
                        } else {
                            timer.cancel();
                            timer.purge();
                        }
                    } catch (Exception e){
                        Log.e(TAG, "run: ", e);
                    }
                });
            }
        }, 0, 100);
        controller.setMediaPlayer(this);
        controller.setAnchorView(videoView);
        controller.setEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        subtitle.setEnabled(true);
        subtitle.setTextIsSelectable(true);
        infoView.setEnabled(true);
        infoView.setTextIsSelectable(true);
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }



    @Override
    public void start() {
        mp.start();
    }

    @Override
    public void pause() {
        mp.pause();
    }

    @Override
    public int getDuration() {
        return mp.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return mp.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        mp.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return mp.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }


    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }


}
