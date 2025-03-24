package com.example.spj;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.spj.database.AppDatabase;
import com.example.spj.model.Entry;
import com.example.spj.model.Video;
import com.example.spj.render.GlRenderView;
import com.example.spj.render.GlRenderWrapper;
import com.example.spj.render.filters.FilterManager;
import com.example.spj.util.CameraUtils;
import com.example.spj.util.FileUtils;
import com.example.spj.util.ResolutionAdapter;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener, VideoCompletionCallback {
    private static final String TAG = "CameraActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 10;

    // Transferred from the original project
    public static final String EXTRA_ENTRY_ID = "com.example.spj.EXTRA_ENTRY_ID";

    // UI components
    private GlRenderView mGlSurfaceView;
    private ImageButton recordButton;
    private ImageButton switchCameraButton;
    private FilterManager filterManager;
    private ImageButton mirrorButton;
    private ImageButton settingsButton;
    private ImageButton effectsButton;  // New effects button
    private TextView recordingTimeText;
    private ToggleButton toggleFilter;  // Optional direct filter toggle

    // Status variables
    private boolean isRecording = false;
    private int entryId = -1;
    private File currentVideoFile;
    private long recordingStartTime;
    private Handler recordingTimeHandler;
    private Runnable recordingTimeRunnable;

    // Current filter selected (default to none/normal)
    private int currentFilterType = 0; // 0=none, 1=invert, (more to be added)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 添加屏幕常亮标志，防止屏幕息屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        filterManager = new FilterManager(this);
        // Get the entry ID from the intent
        entryId = getIntent().getIntExtra(EXTRA_ENTRY_ID, -1);
        if (entryId == -1) {
            Log.e(TAG, "No entry ID provided");
            Toast.makeText(this, "缺少条目ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI components
        initializeUI();

        // Check permissions
        if (allPermissionsGranted()) {
            // Camera will be initialized in onResume by GlRenderView
        } else {
            requestPermissions();
        }

        // Set up recording time handler
        recordingTimeHandler = new Handler(Looper.getMainLooper());
        recordingTimeRunnable = new Runnable() {
            @Override
            public void run() {
                updateRecordingTime();
                recordingTimeHandler.postDelayed(this, 1000);
            }
        };
    }

    private void initializeUI() {
        mGlSurfaceView = findViewById(R.id.glSurfaceView);
        recordButton = findViewById(R.id.recordButton);
        switchCameraButton = findViewById(R.id.switchCameraButton);
        mirrorButton = findViewById(R.id.mirrorButton);
        settingsButton = findViewById(R.id.settingsButton);
        effectsButton = findViewById(R.id.effectsButton);
        ImageButton closeButton = findViewById(R.id.closeButton);
        recordingTimeText = findViewById(R.id.recordingTimeText);
        toggleFilter = findViewById(R.id.toggleFilter);

        // Set click listeners
        recordButton.setOnClickListener(this);
        switchCameraButton.setOnClickListener(this);
        mirrorButton.setOnClickListener(this);
        settingsButton.setOnClickListener(this);
        effectsButton.setOnClickListener(this);
        if (toggleFilter != null) {
            toggleFilter.setOnClickListener(this);
        }

        // Set close button click handler
        closeButton.setOnClickListener(v -> {
            if (isRecording) {
                // Confirm closing while recording
                new AlertDialog.Builder(this)
                        .setTitle("正在录制")
                        .setMessage("您确定要取消正在进行的录制吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            if (isRecording) {
                                mGlSurfaceView.stopRecording();
                                isRecording = false;
                            }
                            finish();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                finish();
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.recordButton) {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        } else if (v.getId() == R.id.switchCameraButton) {
            mGlSurfaceView.switchCamera();
        } else if (v.getId() == R.id.mirrorButton) {
            toggleMirror();
        } else if (v.getId() == R.id.toggleFilter) {
            // Direct filter toggle
            mGlSurfaceView.enableInvertFilter(toggleFilter.isChecked());
        } else if (v.getId() == R.id.effectsButton) {
            showEffectsDialog();
        } else if (v.getId() == R.id.settingsButton) {

        }
    }

    private void showEffectsDialog() {
        // Get all effect names
        String[] effectNames = filterManager.getEffectNames();

        new AlertDialog.Builder(this)
                .setTitle("选择滤镜效果")
                .setSingleChoiceItems(effectNames, currentFilterType, (dialog, which) -> {
                    // Get the selected effect
                    FilterManager.ShaderEffect effect = filterManager.getEffectsList().get(which);

                    // Apply the filter
                    currentFilterType = effect.getId();
                    mGlSurfaceView.enableFilter(currentFilterType);

                    dialog.dismiss();
                    Toast.makeText(this, "已选择: " + effect.getName(), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleMirror() {
        mGlSurfaceView.toggleMirror();
        Toast.makeText(this, mGlSurfaceView.isMirrored() ? "镜像模式已开启" : "镜像模式已关闭", Toast.LENGTH_SHORT).show();
    }


    private boolean allPermissionsGranted() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                },
                PERMISSIONS_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                // Camera will be initialized in onResume by GlRenderView
            } else {
                Toast.makeText(this, "需要相机和录音权限", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startRecording() {
        try {
            // Create video file
            currentVideoFile = FileUtils.createVideoFile(this, entryId);
            Log.d(TAG, "Created video file: " + currentVideoFile.getAbsolutePath());

            // Start recording
            mGlSurfaceView.startRecording(currentVideoFile.getAbsolutePath());
            isRecording = true;
            recordButton.setImageResource(R.drawable.ic_stop);
            recordingTimeText.setVisibility(View.VISIBLE);

            // Start recording time counter
            recordingStartTime = System.currentTimeMillis();
            recordingTimeHandler.post(recordingTimeRunnable);

            Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: ", e);
            Toast.makeText(this, "无法开始录制: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        // Use the callback version to handle post-processing
        mGlSurfaceView.stopRecording(this);

        // Update UI state
        isRecording = false;
        recordButton.setImageResource(R.drawable.ic_record);

        // Stop timer
        recordingTimeHandler.removeCallbacks(recordingTimeRunnable);

        // Show processing message
        Toast.makeText(this, "正在处理视频...", Toast.LENGTH_SHORT).show();
    }

    private void updateRecordingTime() {
        if (!isRecording) return;

        long elapsedTime = System.currentTimeMillis() - recordingStartTime;
        long seconds = (elapsedTime / 1000) % 60;
        long minutes = (elapsedTime / 60000) % 60;
        long hours = elapsedTime / 3600000;

        String timeText;
        if (hours > 0) {
            timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }

        recordingTimeText.setText(timeText);
    }

    // Implementation of VideoCompletionCallback interface
    @Override
    public void onVideoSaved(String path) {
        // Video processing is complete, add to database
        saveVideoToDatabase(new File(path));
    }

    private void saveVideoToDatabase(File videoFile) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean success = false;
            String errorMessage = null;

            try {
                Log.d(TAG, "Saving video to database: " + videoFile.getAbsolutePath());

                // Check if video file is valid
                if (videoFile == null || !videoFile.exists()) {
                    errorMessage = "视频文件不存在";
                    Log.e(TAG, errorMessage);
                    return;
                }

                if (videoFile.length() == 0) {
                    errorMessage = "视频文件为空";
                    Log.e(TAG, errorMessage);
                    return;
                }

                // Create video record
                Video video = new Video(entryId, videoFile.getAbsolutePath());

                // Generate thumbnail
                File thumbnailFile = null;
                try {
                    thumbnailFile = FileUtils.createThumbnailFile(this, entryId);
                    Log.d(TAG, "Created thumbnail file: " + thumbnailFile.getAbsolutePath());

                    boolean thumbnailGenerated = CameraUtils.generateThumbnail(
                            videoFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());
                    Log.d(TAG, "Thumbnail generated: " + thumbnailGenerated);

                    if (thumbnailGenerated) {
                        video.setThumbnailPath(thumbnailFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error generating thumbnail, but continuing: ", e);
                    if (thumbnailFile != null && thumbnailFile.exists()) {
                        thumbnailFile.delete();
                    }
                }

                // Set video metadata
                try {
                    long duration = FileUtils.getVideoDuration(videoFile.getAbsolutePath());
                    long fileSize = FileUtils.getFileSize(videoFile.getAbsolutePath());
                    video.setDuration(duration);
                    video.setFileSize(fileSize);
                    Log.d(TAG, "Video duration: " + duration + "ms, size: " + fileSize + " bytes");
                } catch (Exception e) {
                    Log.w(TAG, "Error extracting video metadata, but continuing: ", e);
                    video.setDuration(0);
                    video.setFileSize(videoFile.length());
                }

                // Save to database
                AppDatabase database = AppDatabase.getDatabase(this);
                long videoId = database.videoDao().insert(video);

                if (videoId > 0) {
                    Log.d(TAG, "Video saved to database with ID: " + videoId);
                    success = true;

                    // Update entry video count and thumbnail
                    try {
                        int count = database.videoDao().getVideoCountForEntry(entryId);
                        database.entryDao().updateVideoCount(entryId, count);
                        Log.d(TAG, "Updated entry video count to: " + count);

                        if (video.getThumbnailPath() != null) {
                            Entry entry = database.entryDao().getEntryByIdSync(entryId);
                            if (entry != null && (entry.getThumbnailPath() == null || entry.getThumbnailPath().isEmpty())) {
                                database.entryDao().updateThumbnail(entryId, video.getThumbnailPath());
                                Log.d(TAG, "Updated entry thumbnail");
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error updating entry information, but video was saved: ", e);
                    }
                } else {
                    errorMessage = "数据库插入失败，返回的ID <= 0";
                    Log.e(TAG, errorMessage);
                }
            } catch (Exception e) {
                errorMessage = "保存视频过程中发生未知错误: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
            } finally {
                Log.d(TAG, "Video save result: " + (success ? "SUCCESS" : "FAILURE") +
                        (errorMessage != null ? ", error: " + errorMessage : ""));

                final boolean finalSuccess = success;

                runOnUiThread(() -> {
                    if (finalSuccess) {
                        Toast.makeText(CameraActivity.this,
                                R.string.video_recorded, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(CameraActivity.this,
                                R.string.error_recording_video, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGlSurfaceView != null) {
            mGlSurfaceView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) {
            mGlSurfaceView.stopRecording();
            isRecording = false;
            recordButton.setImageResource(R.drawable.ic_record);
            recordingTimeHandler.removeCallbacks(recordingTimeRunnable);
        }

        if (mGlSurfaceView != null) {
            mGlSurfaceView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop recording if active
        if (isRecording) {
            mGlSurfaceView.stopRecording();
        }

        // Remove timer callbacks
        if (recordingTimeHandler != null) {
            recordingTimeHandler.removeCallbacks(recordingTimeRunnable);
        }
    }
}