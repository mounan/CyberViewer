package jp.ac.titech.itpro.sdl.cyberviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button selectSourceButton;
    public final static String VIDEO_EXTRA = "selectedVideoIntent";
    private final static String TAG = MainActivity.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        selectSourceButton = findViewById(R.id.select_source_button);
        selectSourceButton.setOnClickListener(v -> {
            Intent selectVideoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            selectVideoResultLauncher.launch(selectVideoIntent);
        });
    }

    ActivityResultLauncher<Intent> selectVideoResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    Uri selectedVideoUri = data.getData();
                    Intent intent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                    intent.putExtra(VIDEO_EXTRA, selectedVideoUri);
                    startActivity(intent);
                }
            }
    );
}