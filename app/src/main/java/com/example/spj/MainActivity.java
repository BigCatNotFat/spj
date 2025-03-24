package com.example.spj;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.spj.adapter.EntryAdapter;
import com.example.spj.database.AppDatabase;
import com.example.spj.database.EntryDao;
import com.example.spj.database.VideoDao;
import com.example.spj.model.Entry;
import com.example.spj.model.Video;
import com.example.spj.util.CameraUtils;
import com.example.spj.util.FileUtils;
import com.example.spj.util.ItemTouchHelperCallback;
import com.example.spj.util.PermissionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        EntryAdapter.OnEntryClickListener,
        EntryAdapter.OnEntryPinnedListener,
        EntryAdapter.OnEntryDeletedListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_VIDEO_CAPTURE = 1;
    private static final int REQUEST_CUSTOM_CAMERA = 2; // Added a separate request code for custom camera
    private static final int REQUEST_PERMISSIONS = 3;
    private static final int REQUEST_SETTINGS = 4;
    private static final int REQUEST_PHOTO_CAPTURE = 5; // 新增拍照请求码

    private AppDatabase database;
    private EntryDao entryDao;
    private VideoDao videoDao;
    private EntryAdapter entryAdapter;
    private RecyclerView recyclerViewEntries;
    private TextView textViewEmpty;
    private File currentVideoFile;
    private File currentPhotoFile; // 新增照片文件变量
    private int selectedEntryId = -1;
    private ItemTouchHelper itemTouchHelper;

    // 长按计时器相关变量
    private static final long LONG_PRESS_DURATION = 500; // 长按时间阈值，毫秒
    private Runnable mLongPressRunnable;
    private boolean mHasPerformedLongPress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate called");

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化视图
        recyclerViewEntries = findViewById(R.id.recyclerViewEntries);
        textViewEmpty = findViewById(R.id.textViewEmpty);
        FloatingActionButton fabRecord = findViewById(R.id.fabRecord);
        findViewById(R.id.btnAddEntry).setOnClickListener(v -> showAddEntryDialog());

        // 设置RecyclerView
        recyclerViewEntries.setLayoutManager(new LinearLayoutManager(this));
        entryAdapter = new EntryAdapter(this);
        entryAdapter.setOnEntryClickListener(this);
        entryAdapter.setOnEntryPinnedListener(this); // 设置置顶监听器
        entryAdapter.setOnEntryDeletedListener(this); // 设置删除监听器
        recyclerViewEntries.setAdapter(entryAdapter);

        // 设置拖拽支持
        ItemTouchHelperCallback callback = new ItemTouchHelperCallback(entryAdapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerViewEntries);

        // 初始化数据库
        database = AppDatabase.getDatabase(this);
        entryDao = database.entryDao();
        videoDao = database.videoDao();

        // 加载条目列表
        loadEntries();

        // 创建长按Runnable
        mLongPressRunnable = new Runnable() {
            @Override
            public void run() {
                mHasPerformedLongPress = true;
                // 执行拍照操作
                if (checkPhotoPermissions()) {
                    showSelectEntryForPhotoDialog();
                }
            }
        };

        // 设置录制按钮触摸事件，处理点击和长按
        fabRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 按下时，安排长按任务
                    mHasPerformedLongPress = false;
                    v.postDelayed(mLongPressRunnable, LONG_PRESS_DURATION);
                    return true;
                case MotionEvent.ACTION_UP:
                    // 松开时，如果没有执行长按，则视为普通点击
                    v.removeCallbacks(mLongPressRunnable);
                    if (!mHasPerformedLongPress) {
                        // 执行普通录制视频逻辑
                        handleRecordButtonClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    // 取消长按
                    v.removeCallbacks(mLongPressRunnable);
                    return true;
            }
            return false;
        });
    }

    // 处理录制按钮点击
    private void handleRecordButtonClick() {
        // 简化权限检查逻辑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 只检查必要的权限
            boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean hasMediaVideo = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;

            if (!hasCamera || !hasAudio || !hasMediaVideo) {
                // 申请缺少的权限
                List<String> missingPermissions = new ArrayList<>();
                if (!hasCamera) missingPermissions.add(Manifest.permission.CAMERA);
                if (!hasAudio) missingPermissions.add(Manifest.permission.RECORD_AUDIO);
                if (!hasMediaVideo) missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);

                ActivityCompat.requestPermissions(this,
                        missingPermissions.toArray(new String[0]),
                        REQUEST_PERMISSIONS);
                return;
            }

            // 权限都有了，检查存储可用性
            if (FileUtils.checkStorageAvailability(this)) {
                showSelectEntryDialog();
            } else {
                Toast.makeText(this, "无法访问存储空间", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 旧版Android: 使用原有逻辑
            if (PermissionUtils.checkAndRequestPermissions(this, REQUEST_PERMISSIONS)) {
                showSelectEntryDialog();
            }
        }
    }

    /**
     * 检查拍照所需的权限
     */
    private boolean checkPhotoPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean hasMediaImages = false;

            // Android 15+ 的权限处理
            if (Build.VERSION.SDK_INT >= 35) { // Android 15 (API 35)
                // 使用新的照片和视频访问权限
                hasMediaImages = ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED;
            } else {
                hasMediaImages = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            }

            if (!hasCamera || !hasMediaImages) {
                List<String> missingPermissions = new ArrayList<>();
                if (!hasCamera) missingPermissions.add(Manifest.permission.CAMERA);
                if (!hasMediaImages) {
                    if (Build.VERSION.SDK_INT >= 35) { // Android 15 (API 35)
                        missingPermissions.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
                    } else {
                        missingPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                    }
                }

                ActivityCompat.requestPermissions(this,
                        missingPermissions.toArray(new String[0]),
                        REQUEST_PERMISSIONS);
                return false;
            }
            return FileUtils.checkStorageAvailability(this);
        } else {
            return PermissionUtils.checkAndRequestPermissions(this, REQUEST_PERMISSIONS);
        }
    }

    /**
     * 显示选择条目对话框（拍照时）
     */
    private void showSelectEntryForPhotoDialog() {
        LiveData<List<Entry>> entriesLiveData = entryDao.getAllEntries();
        entriesLiveData.observe(this, new Observer<List<Entry>>() {
            @Override
            public void onChanged(List<Entry> entries) {
                entriesLiveData.removeObserver(this);

                if (entries == null || entries.isEmpty()) {
                    // 如果没有条目，先创建一个
                    showAddEntryDialog();
                    return;
                }

                // 创建条目名称数组
                String[] entryNames = new String[entries.size()];
                int[] entryIds = new int[entries.size()];

                for (int i = 0; i < entries.size(); i++) {
                    Entry entry = entries.get(i);
                    entryNames[i] = entry.getName();
                    entryIds[i] = entry.getId();
                }

                // 显示选择对话框
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("为哪个合集拍照?")
                        .setItems(entryNames, (dialog, which) -> {
                            selectedEntryId = entryIds[which];
                            takePicture(selectedEntryId);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    /**
     * 启动系统相机拍照
     */
    private void takePicture(int entryId) {
        try {
            Log.d(TAG, "Starting system camera for taking photo for entry ID: " + entryId);

            // 使用FileUtils创建图片文件
            currentPhotoFile = FileUtils.createImageFile(this, entryId);

            // 创建拍照Intent
            Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

            // 确保有相机应用处理该Intent
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // 设置保存照片的URI
                Uri photoURI = FileUtils.getUriForFile(this, currentPhotoFile);
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);

                // 授予URI写入权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // 启动系统相机应用
                startActivityForResult(takePictureIntent, REQUEST_PHOTO_CAPTURE);
            } else {
                Toast.makeText(this, "没有找到可用的相机应用", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating photo file: ", e);
            Toast.makeText(this, "无法创建图片文件", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 保存照片到数据库
     */
    private void savePhotoToDatabase(File photoFile, int entryId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Log.d(TAG, "Saving photo to database: " + photoFile.getAbsolutePath());

                // 创建缩略图文件
                File thumbnailFile = FileUtils.createThumbnailFile(this, entryId);
                Log.d(TAG, "Created thumbnail file: " + thumbnailFile.getAbsolutePath());

                // 生成缩略图
                boolean thumbnailGenerated = generatePhotoThumbnail(
                        photoFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());
                Log.d(TAG, "Thumbnail generated: " + thumbnailGenerated);

                // 创建视频记录（实际是图片，但使用统一的Video模型）
                Video video = new Video(entryId, photoFile.getAbsolutePath());

                // 设置为图片类型
                video.setMediaType(Video.TYPE_IMAGE);

                // 设置缩略图路径
                if (thumbnailGenerated) {
                    video.setThumbnailPath(thumbnailFile.getAbsolutePath());
                }

                // 设置文件大小
                long fileSize = FileUtils.getFileSize(photoFile.getAbsolutePath());
                video.setFileSize(fileSize);

                Log.d(TAG, "Photo size: " + fileSize + " bytes");

                // 保存到数据库
                long videoId = videoDao.insert(video);
                Log.d(TAG, "Photo saved to database with ID: " + videoId);

                if (videoId > 0) {
                    // 更新条目的视频计数
                    int count = videoDao.getVideoCountForEntry(entryId);
                    entryDao.updateVideoCount(entryId, count);
                    Log.d(TAG, "Updated entry video count to: " + count);

                    // 如果条目没有缩略图，使用新照片的缩略图
                    Entry entry = entryDao.getEntryByIdSync(entryId);
                    if (entry != null && (entry.getThumbnailPath() == null || entry.getThumbnailPath().isEmpty())) {
                        if (thumbnailGenerated) {
                            entryDao.updateThumbnail(entryId, thumbnailFile.getAbsolutePath());
                            Log.d(TAG, "Updated entry thumbnail");
                        }
                    }

                    // 通知用户并刷新列表
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                R.string.photo_captured, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving photo: ", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        R.string.error_capturing_photo, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 生成照片缩略图
     */
    private boolean generatePhotoThumbnail(String photoPath, String thumbnailPath) {
        try {
            // 加载原始图片
            android.graphics.Bitmap original = android.graphics.BitmapFactory.decodeFile(photoPath);
            if (original == null) {
                Log.e(TAG, "Failed to decode image file: " + photoPath);
                return false;
            }

            // 计算缩略图尺寸（保持宽高比）
            int width = original.getWidth();
            int height = original.getHeight();
            float ratio = (float) width / height;

            int targetWidth = 300;
            int targetHeight = Math.round(targetWidth / ratio);

            // 创建缩略图
            android.graphics.Bitmap thumbnail = android.graphics.Bitmap.createScaledBitmap(original, targetWidth, targetHeight, false);

            // 保存缩略图
            java.io.FileOutputStream out = new java.io.FileOutputStream(thumbnailPath);
            thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            // 释放资源
            thumbnail.recycle();
            original.recycle();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error generating image thumbnail: ", e);
            return false;
        }
    }

    /**
     * 加载条目列表
     */
    private void loadEntries() {
        LiveData<List<Entry>> entriesLiveData = entryDao.getAllEntries();
        entriesLiveData.observe(this, entries -> {
            entryAdapter.setEntries(entries);

            // 显示或隐藏空视图
            if (entries == null || entries.isEmpty()) {
                recyclerViewEntries.setVisibility(View.GONE);
                textViewEmpty.setVisibility(View.VISIBLE);
            } else {
                recyclerViewEntries.setVisibility(View.VISIBLE);
                textViewEmpty.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 显示添加条目对话框
     */
    private void showAddEntryDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_entry, null);
        EditText editTextEntryName = dialogView.findViewById(R.id.editTextEntryName);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_entry)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String entryName = editTextEntryName.getText().toString().trim();
                    if (!entryName.isEmpty()) {
                        Entry newEntry = new Entry(entryName);

                        // 设置新条目的默认显示顺序（最大值+1）
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            int maxOrder = entryDao.getMaxDisplayOrder();
                            newEntry.setDisplayOrder(maxOrder + 1);

                            long entryId = entryDao.insert(newEntry);
                            if (entryId > 0) {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                        R.string.entry_created, Toast.LENGTH_SHORT).show());
                            } else {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                        R.string.error_creating_entry, Toast.LENGTH_SHORT).show());
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this,
                                R.string.entry_name_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * 显示选择条目对话框（录制视频时）
     */
    private void showSelectEntryDialog() {
        LiveData<List<Entry>> entriesLiveData = entryDao.getAllEntries();
        entriesLiveData.observe(this, new Observer<List<Entry>>() {
            @Override
            public void onChanged(List<Entry> entries) {
                entriesLiveData.removeObserver(this);

                if (entries == null || entries.isEmpty()) {
                    // 如果没有条目，先创建一个
                    showAddEntryDialog();
                    return;
                }

                // 创建条目名称数组
                String[] entryNames = new String[entries.size()];
                int[] entryIds = new int[entries.size()];

                for (int i = 0; i < entries.size(); i++) {
                    Entry entry = entries.get(i);
                    entryNames[i] = entry.getName();
                    entryIds[i] = entry.getId();
                }

                // 显示选择对话框
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.record_for_which_entry)
                        .setItems(entryNames, (dialog, which) -> {
                            selectedEntryId = entryIds[which];
                            // 使用自定义相机
                            launchCustomCamera(selectedEntryId);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    /**
     * 启动自定义相机
     */
    private void launchCustomCamera(int entryId) {
        Intent cameraIntent = new Intent(this, CameraActivity.class);
        cameraIntent.putExtra(CameraActivity.EXTRA_ENTRY_ID, entryId);
        startActivityForResult(cameraIntent, REQUEST_CUSTOM_CAMERA);
    }

    /**
     * EntryAdapter.OnEntryPinnedListener接口实现
     */
    @Override
    public void onEntryPinned(Entry entry, boolean pinned) {
        // 当条目的置顶状态改变时刷新列表
        loadEntries();

        // 显示提示
        Toast.makeText(this,
                pinned ? R.string.entry_pinned : R.string.entry_unpinned,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * EntryAdapter.OnEntryDeletedListener接口实现
     */
    @Override
    public void onEntryDeleted(Entry entry) {
        // 删除合集相关的所有文件
        if (entry.getThumbnailPath() != null && !entry.getThumbnailPath().isEmpty()) {
            File thumbnailFile = new File(entry.getThumbnailPath());
            if (thumbnailFile.exists()) {
                thumbnailFile.delete();
            }
        }

        // 清理合集的视频文件目录
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 获取合集的所有视频
                List<Video> videos = videoDao.getAllVideosForEntrySync(entry.getId());
                if (videos != null) {
                    for (Video video : videos) {
                        // 删除视频文件
                        if (video.getFilePath() != null) {
                            File videoFile = new File(video.getFilePath());
                            if (videoFile.exists()) {
                                videoFile.delete();
                            }
                        }

                        // 删除缩略图文件
                        if (video.getThumbnailPath() != null) {
                            File thumbFile = new File(video.getThumbnailPath());
                            if (thumbFile.exists()) {
                                thumbFile.delete();
                            }
                        }
                    }
                }

                // 尝试删除合集目录
                File entryDir = FileUtils.getEntryDirectory(this, entry.getId());
                if (entryDir.exists()) {
                    FileUtils.deleteDirectory(entryDir);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up entry files: ", e);
            }
        });
    }

    /**
     * 使用系统相机开始视频录制
     */
    private void startVideoRecording(int entryId) {
        try {
            Log.d(TAG, "Starting video recording with system camera for entry ID: " + entryId);
            currentVideoFile = FileUtils.createVideoFile(this, entryId);
            CameraUtils.startVideoCapture(this, currentVideoFile, REQUEST_VIDEO_CAPTURE);
        } catch (IOException e) {
            Log.e(TAG, "Error creating video file: ", e);
            Toast.makeText(this, R.string.error_recording_video, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 权限请求结果处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            // 检查是否所有必要权限都已授予
            boolean allNeededGranted = true;
            boolean hasCamera = false;
            boolean hasAudio = false;
            boolean hasMedia = false;

            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;

                if (Manifest.permission.CAMERA.equals(permission)) {
                    hasCamera = granted;
                } else if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                    hasAudio = granted;
                } else if (Manifest.permission.READ_MEDIA_VIDEO.equals(permission) ||
                        Manifest.permission.READ_MEDIA_IMAGES.equals(permission) ||
                        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED".equals(permission) ||
                        Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) {
                    hasMedia = granted;
                }
            }

            // 在Android 13+上，我们只需要相机、麦克风和媒体权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                allNeededGranted = hasCamera && hasAudio && hasMedia;
            } else {
                // 在旧版Android上，检查所有请求的权限
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allNeededGranted = false;
                        break;
                    }
                }
            }

            // 检查存储可用性
            boolean storageAvailable = FileUtils.checkStorageAvailability(this);

            if (allNeededGranted && storageAvailable) {
                // 重新触发原始操作
                if (mHasPerformedLongPress) {
                    showSelectEntryForPhotoDialog();
                } else {
                    showSelectEntryDialog();
                }
            } else {
                Toast.makeText(this, "需要相机、麦克风和存储权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CUSTOM_CAMERA) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Video successfully recorded with custom camera");
                // 自定义相机已经保存视频，无需额外处理
                selectedEntryId = -1;
            } else {
                Log.d(TAG, "Custom camera recording canceled or failed");
            }
            return;
        }

        // 处理拍照结果
        if (requestCode == REQUEST_PHOTO_CAPTURE && resultCode == RESULT_OK) {
            if (currentPhotoFile != null && currentPhotoFile.exists() && currentPhotoFile.length() > 0 && selectedEntryId != -1) {
                Log.d(TAG, "Photo captured successfully: " + currentPhotoFile.getAbsolutePath());
                savePhotoToDatabase(currentPhotoFile, selectedEntryId);
                selectedEntryId = -1;
            } else {
                Log.e(TAG, "Photo file is null or does not exist or no entry selected");
                if (currentPhotoFile != null) {
                    Log.e(TAG, "Photo file path: " + currentPhotoFile.getAbsolutePath());
                    Log.e(TAG, "Photo file exists: " + currentPhotoFile.exists());
                    if (currentPhotoFile.exists()) {
                        Log.e(TAG, "Photo file size: " + currentPhotoFile.length());
                    }
                }
                Log.e(TAG, "Selected entry ID: " + selectedEntryId);
                Toast.makeText(this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
            }
            return;
        } else if (requestCode == REQUEST_PHOTO_CAPTURE) {
            Log.d(TAG, "Photo capture canceled or failed");
            // 清理可能的空文件
            if (currentPhotoFile != null && currentPhotoFile.exists() && currentPhotoFile.length() == 0) {
                currentPhotoFile.delete();
                Log.d(TAG, "Deleted empty photo file");
            }
            selectedEntryId = -1;
            return;
        }

        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            // 检查是否在Intent中返回了视频URI（某些相机应用可能这样做）
            Uri videoUri = null;
            if (data != null && data.getData() != null) {
                videoUri = data.getData();
                Log.d(TAG, "Video URI returned from camera app: " + videoUri);

                // 如果相机返回了不同的URI，需要把内容复制到我们的文件中
                if (currentVideoFile != null && (currentVideoFile.length() == 0)) {
                    boolean copied = FileUtils.copyUriToFile(this, videoUri, currentVideoFile);
                    if (!copied) {
                        Log.e(TAG, "Failed to copy video from URI to file");
                        Toast.makeText(this, R.string.error_recording_video, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Log.d(TAG, "Copied video from URI to file: " + currentVideoFile.getAbsolutePath());
                }
            }

            if (currentVideoFile != null && currentVideoFile.exists() && currentVideoFile.length() > 0 && selectedEntryId != -1) {
                Log.d(TAG, "Video captured successfully: " + currentVideoFile.getAbsolutePath());
                saveVideoToDatabase(currentVideoFile, selectedEntryId);
            } else {
                Log.e(TAG, "Video file is null, does not exist, or no entry selected");
                if (currentVideoFile != null) {
                    Log.e(TAG, "Video file path: " + currentVideoFile.getAbsolutePath());
                    Log.e(TAG, "Video file exists: " + currentVideoFile.exists());
                    if (currentVideoFile.exists()) {
                        Log.e(TAG, "Video file size: " + currentVideoFile.length());
                    }
                }
                Log.e(TAG, "Selected entry ID: " + selectedEntryId);
                Toast.makeText(this, R.string.error_recording_video, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_VIDEO_CAPTURE) {
            Log.d(TAG, "Video capture canceled or failed");
            // 清理可能的空文件
            if (currentVideoFile != null && currentVideoFile.exists() && currentVideoFile.length() == 0) {
                currentVideoFile.delete();
                Log.d(TAG, "Deleted empty video file");
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            // 用户从设置页面返回，再次检查权限
            if (PermissionUtils.checkAndRequestPermissions(this, REQUEST_PERMISSIONS)) {
                showSelectEntryDialog();
            }
        }
    }

    /**
     * 保存视频到数据库
     */
    private void saveVideoToDatabase(File videoFile, int entryId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Log.d(TAG, "Saving video to database: " + videoFile.getAbsolutePath());

                // 创建缩略图文件
                File thumbnailFile = FileUtils.createThumbnailFile(this, entryId);
                Log.d(TAG, "Created thumbnail file: " + thumbnailFile.getAbsolutePath());

                // 生成缩略图
                boolean thumbnailGenerated = CameraUtils.generateThumbnail(
                        videoFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());
                Log.d(TAG, "Thumbnail generated: " + thumbnailGenerated);

                // 创建视频记录
                Video video = new Video(entryId, videoFile.getAbsolutePath());

                // 设置缩略图路径
                if (thumbnailGenerated) {
                    video.setThumbnailPath(thumbnailFile.getAbsolutePath());
                }

                // 设置视频时长和文件大小
                long duration = FileUtils.getVideoDuration(videoFile.getAbsolutePath());
                long fileSize = FileUtils.getFileSize(videoFile.getAbsolutePath());
                video.setDuration(duration);
                video.setFileSize(fileSize);

                Log.d(TAG, "Video duration: " + duration + "ms, size: " + fileSize + " bytes");

                // 保存到数据库
                long videoId = videoDao.insert(video);
                Log.d(TAG, "Video saved to database with ID: " + videoId);

                if (videoId > 0) {
                    // 更新条目的视频计数
                    int count = videoDao.getVideoCountForEntry(entryId);
                    entryDao.updateVideoCount(entryId, count);
                    Log.d(TAG, "Updated entry video count to: " + count);

                    // 如果条目没有缩略图，使用最新视频的缩略图
                    Entry entry = entryDao.getEntryByIdSync(entryId);
                    if (entry != null && (entry.getThumbnailPath() == null || entry.getThumbnailPath().isEmpty())) {
                        if (thumbnailGenerated) {
                            entryDao.updateThumbnail(entryId, thumbnailFile.getAbsolutePath());
                            Log.d(TAG, "Updated entry thumbnail");
                        }
                    }

                    // 通知用户
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            R.string.video_recorded, Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving video: ", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        R.string.error_recording_video, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 条目点击事件处理
     */
    @Override
    public void onEntryClick(Entry entry) {
        // 跳转到条目详情页面
        Intent intent = new Intent(this, EntryDetailActivity.class);
        intent.putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, entry.getId());
        intent.putExtra(EntryDetailActivity.EXTRA_ENTRY_NAME, entry.getName());
        startActivity(intent);
    }
}