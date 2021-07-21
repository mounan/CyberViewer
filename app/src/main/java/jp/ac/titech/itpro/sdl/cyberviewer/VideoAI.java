package jp.ac.titech.itpro.sdl.cyberviewer;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.videointelligence.v1.AnnotateVideoProgress;
import com.google.cloud.videointelligence.v1.AnnotateVideoRequest;
import com.google.cloud.videointelligence.v1.AnnotateVideoResponse;
import com.google.cloud.videointelligence.v1.Feature;
import com.google.cloud.videointelligence.v1.VideoAnnotationResults;
import com.google.cloud.videointelligence.v1.VideoIntelligenceServiceClient;
import com.google.cloud.videointelligence.v1.VideoIntelligenceServiceSettings;
import com.google.protobuf.ByteString;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class VideoAI {
    private final static String TAG = "videoAI";

    /**
     * Track objects in a video.
     */
    @SuppressLint("DefaultLocale")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static VideoAnnotationResults trackObjects(InputStream credentialIs, Path path) throws Exception {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentialIs));

        VideoIntelligenceServiceSettings settings = VideoIntelligenceServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create(settings)) {
            // Read file

            byte[] data = Files.readAllBytes(path);

            // Create the request
            AnnotateVideoRequest request =
                    AnnotateVideoRequest.newBuilder()
                            .setInputContent(ByteString.copyFrom(data))
                            .addFeatures(Feature.OBJECT_TRACKING)
                            .build();

            // asynchronously perform object tracking on videos
            OperationFuture<AnnotateVideoResponse, AnnotateVideoProgress> future =
                    client.annotateVideoAsync(request);

            // The first result is retrieved because a single video was processed.
            AnnotateVideoResponse response = future.get();
            return response.getAnnotationResults(0);
        }
    }

    /**
     * Detect text in a video.
     *
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static VideoAnnotationResults detectText(InputStream credentialIs, Path path) throws Exception {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentialIs));

        VideoIntelligenceServiceSettings settings = VideoIntelligenceServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
        try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create(settings)) {
            // Read file
            byte[] data = Files.readAllBytes(path);

            // Create the request
            AnnotateVideoRequest request =
                    AnnotateVideoRequest.newBuilder()
                            .setInputContent(ByteString.copyFrom(data))
                            .addFeatures(Feature.TEXT_DETECTION)
                            .build();

            // asynchronously perform object tracking on videos
            OperationFuture<AnnotateVideoResponse, AnnotateVideoProgress> future =
                    client.annotateVideoAsync(request);

            // The first result is retrieved because a single video was processed.
            AnnotateVideoResponse response = future.get(300, TimeUnit.SECONDS);

            return response.getAnnotationResults(0);
        }
    }


}
