package com.example.spj;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.adapter.SectionedVideoAdapter;
import com.example.spj.adapter.VideoAdapter;
import com.example.spj.database.AppDatabase;
import com.example.spj.database.EntryDao;
import com.example.spj.database.VideoDao;
import com.example.spj.model.Entry;
import com.example.spj.model.Video;
import com.example.spj.util.BatchExportUtils;
import com.example.spj.util.CameraUtils;
import com.example.spj.util.FileUtils;
import com.example.spj.util.MediaUtils;
import com.example.spj.util.PermissionUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EntryDetailActivity extends AppCompatActivity
        implements SectionedVideoAdapter.OnVideoClickListener, SectionedVideoAdapter.OnVideoLongClickListener,
        VideoAdapter.OnMultiSelectListener {

    private static final String TAG = "EntryDetailActivity";
    public static final String EXTRA_ENTRY_ID = "com.example.spj.EXTRA_ENTRY_ID";
    public static final String EXTRA_ENTRY_NAME = "com.example.spj.EXTRA_ENTRY_NAME";
    private static final int REQUEST_VIDEO_CAPTURE = 1;
    private static final int REQUEST_CUSTOM_CAMERA = 2;
    private static final int REQUEST_PERMISSIONS = 3;
    private static final int REQUEST_SETTINGS = 4;
    private static final int REQUEST_IMPORT_VIDEO = 5;
    private static final int REQUEST_STORAGE_PERMISSION_IMPORT = 6;
    private static final int REQUEST_STORAGE_PERMISSION_EXPORT = 7;
    private static final int REQUEST_IMPORT_IMAGE = 8;
    private static final int REQUEST_IMAGE_CAPTURE = 9;

    private int entryId;
    private String entryName;
    private AppDatabase database;
    private EntryDao entryDao;
    private VideoDao videoDao;
    private SectionedVideoAdapter sectionedAdapter;
    private RecyclerView recyclerViewVideos;
    private TextView textViewEmpty;
    private File currentVideoFile;
    private File currentPhotoFile;
    private Video currentExportVideo; // 用于存储当前要导出的视频
    private boolean isMultiSelectionEnabled = false;
    private static final int REQUEST_BATCH_IMPORT = 10;
    // 底部选择栏组件
    private LinearLayout bottomSelectionBar;
    private TextView textViewSelectionCount;
    private ImageButton buttonSelectAll, buttonShare, buttonDownload, buttonDelete;
    private FloatingActionButton fabRecord;

    // 长按计时器相关变量
    private static final long LONG_PRESS_DURATION = 500; // 长按时间阈值，毫秒
    private Runnable mLongPressRunnable;
    private boolean mHasPerformedLongPress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry_detail);

        Log.d(TAG, "onCreate called");

        // 获取传递的参数
        Intent intent = getIntent();
        if (intent != null) {
            entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1);
            entryName = intent.getStringExtra(EXTRA_ENTRY_NAME);

            Log.d(TAG, "Received entry ID: " + entryId + ", name: " + entryName);

            if (entryId == -1) {
                Log.e(TAG, "Invalid entry ID, finishing activity");
                finish();
                return;
            }
        }

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(entryName);

        // 初始化视图
        recyclerViewVideos = findViewById(R.id.recyclerViewVideos);
        textViewEmpty = findViewById(R.id.textViewEmpty);
        fabRecord = findViewById(R.id.fabRecord);

        // 初始化底部选择栏
        bottomSelectionBar = findViewById(R.id.bottomSelectionBar);
        textViewSelectionCount = findViewById(R.id.textViewSelectionCount);
        buttonSelectAll = findViewById(R.id.buttonSelectAll);
        buttonShare = findViewById(R.id.buttonShare);
        buttonDownload = findViewById(R.id.buttonDownload);
        buttonDelete = findViewById(R.id.buttonDelete);

        // 设置底部操作栏点击事件
        buttonSelectAll.setOnClickListener(v -> {
            sectionedAdapter.selectAll();
            updateSelectionCount(sectionedAdapter.getSelectedCount());
        });

        buttonShare.setOnClickListener(v -> {
            List<Video> selectedVideos = sectionedAdapter.getSelectedVideos();
            if (!selectedVideos.isEmpty()) {
                shareSelectedVideos(selectedVideos);
            }
        });

        buttonDownload.setOnClickListener(v -> {
            List<Video> selectedVideos = sectionedAdapter.getSelectedVideos();
            if (!selectedVideos.isEmpty()) {
                exportSelectedVideosToGallery(selectedVideos);
            }
        });

        buttonDelete.setOnClickListener(v -> {
            List<Video> selectedVideos = sectionedAdapter.getSelectedVideos();
            if (!selectedVideos.isEmpty()) {
                showDeleteSelectedVideosDialog(selectedVideos);
            }
        });

        // 初始化数据库
        database = AppDatabase.getDatabase(this);
        entryDao = database.entryDao();
        videoDao = database.videoDao();

        // 设置初始视图状态
        recyclerViewVideos.setVisibility(View.GONE);
        textViewEmpty.setVisibility(View.VISIBLE);

        // 使用新的分组适配器
        sectionedAdapter = new SectionedVideoAdapter(this);
        sectionedAdapter.setOnVideoClickListener(this);
        sectionedAdapter.setOnVideoLongClickListener(this);

        // 设置RecyclerView
        setupRecyclerView();

        // 加载视频列表
        loadVideos();

        // 创建长按Runnable
        mLongPressRunnable = new Runnable() {
            @Override
            public void run() {
                mHasPerformedLongPress = true;
                // 执行拍照操作
                if (checkPhotoPermissions()) {
                    takePicture();
                }
            }
        };

        // 设置录制按钮触摸事件，处理点击和长按
        fabRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // 按下时，安排长按任务
                    mHasPerformedLongPress = false;
                    v.postDelayed(mLongPressRunnable, LONG_PRESS_DURATION);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    // 松开时，如果没有执行长按，则视为普通点击
                    v.removeCallbacks(mLongPressRunnable);
                    if (!mHasPerformedLongPress) {
                        // 执行普通录制视频逻辑
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+: 只检查必要的权限
                            boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                            boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                            boolean hasMediaVideo = false;

                            // Android 15+ 的权限处理
                            if (Build.VERSION.SDK_INT >= 35) { // Android 15 (API 35)
                                // 使用新的照片和视频访问权限
                                hasMediaVideo = ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED;
                            } else {
                                hasMediaVideo = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
                            }

                            if (!hasCamera || !hasAudio || !hasMediaVideo) {
                                // 申请缺少的权限
                                List<String> missingPermissions = new ArrayList<>();
                                if (!hasCamera) missingPermissions.add(Manifest.permission.CAMERA);
                                if (!hasAudio) missingPermissions.add(Manifest.permission.RECORD_AUDIO);
                                if (!hasMediaVideo) {
                                    if (Build.VERSION.SDK_INT >= 35) { // Android 15 (API 35)
                                        missingPermissions.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
                                    } else {
                                        missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO);
                                    }
                                }

                                ActivityCompat.requestPermissions(EntryDetailActivity.this,
                                        missingPermissions.toArray(new String[0]),
                                        REQUEST_PERMISSIONS);
                                return true;
                            }

                            // 启动自定义相机活动
                            Intent cameraIntent = new Intent(EntryDetailActivity.this, CameraActivity.class);
                            cameraIntent.putExtra(CameraActivity.EXTRA_ENTRY_ID, entryId);
                            startActivityForResult(cameraIntent, REQUEST_CUSTOM_CAMERA);
                        } else {
                            // 旧版Android: 使用原有逻辑但改为启动自定义相机
                            if (PermissionUtils.checkAndRequestPermissions(EntryDetailActivity.this, REQUEST_PERMISSIONS)) {
                                // 启动自定义相机活动
                                Intent cameraIntent = new Intent(EntryDetailActivity.this, CameraActivity.class);
                                cameraIntent.putExtra(CameraActivity.EXTRA_ENTRY_ID, entryId);
                                startActivityForResult(cameraIntent, REQUEST_CUSTOM_CAMERA);
                            }
                        }
                    }
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    // 取消长按
                    v.removeCallbacks(mLongPressRunnable);
                    return true;
            }
            return false;
        });

        // 延迟刷新，确保观察者已附加
        recyclerViewVideos.post(() -> {
            Log.d(TAG, "Refreshing adapter by post method");
            if (sectionedAdapter != null) {
                // 触发更新
                sectionedAdapter.getVideoAdapter().notifyDataSetChanged();
            }
        });
        enableDebugLogging();
    }

    // 更新选择计数和底部工具栏按钮状态
    private void updateSelectionCount(int count) {
        textViewSelectionCount.setText(getString(R.string.selected_count, count));

        // 禁用操作按钮，如果没有选择任何项目
        boolean hasSelection = count > 0;
        buttonShare.setEnabled(hasSelection);
        buttonDownload.setEnabled(hasSelection);
        buttonDelete.setEnabled(hasSelection);

        // 视觉上指示已禁用的按钮
        float alpha = hasSelection ? 1.0f : 0.5f;
        buttonShare.setAlpha(alpha);
        buttonDownload.setAlpha(alpha);
        buttonDelete.setAlpha(alpha);
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
     * 启动系统相机拍照
     */
    private void takePicture() {
        try {
            Log.d(TAG, "Starting system camera for taking photo for entry ID: " + entryId);

            // 使用FileUtils创建图片文件
            currentPhotoFile = FileUtils.createImageFile(this, entryId);

            // 创建拍照Intent
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // 确保有相机应用处理该Intent
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // 设置保存照片的URI
                Uri photoURI = FileUtils.getUriForFile(this, currentPhotoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // 授予URI写入权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // 启动系统相机应用
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } else {
                Toast.makeText(this, "没有找到可用的相机应用", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating photo file: ", e);
            Toast.makeText(this, "无法创建图片文件", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 设置RecyclerView的布局和适配器
     */
    private void setupRecyclerView() {
        // 创建网格布局管理器
        int spanCount = 2; // 每行显示的视频数量
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);

        // 设置跨度尺寸查找器 - 确保日期头部占据整行
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // 查询项目类型
                int viewType = sectionedAdapter.getItemViewType(position);
                // 日期标题占据整行
                if (viewType == SectionedVideoAdapter.TYPE_HEADER) {
                    return spanCount; // 头部占据全部列
                }
                // 视频项目占据一个网格单元
                return 1;
            }
        });

        // 设置布局管理器到RecyclerView
        recyclerViewVideos.setLayoutManager(layoutManager);

        // 移除所有内边距
        recyclerViewVideos.setPadding(0, 0, 0, 0);
        recyclerViewVideos.setClipToPadding(false);

        // 添加自定义ItemDecoration以控制项目间距
        recyclerViewVideos.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (position == RecyclerView.NO_POSITION) return;

                int viewType = sectionedAdapter.getItemViewType(position);

                if (viewType == SectionedVideoAdapter.TYPE_HEADER) {
                    // 日期头部没有额外间距
                    outRect.set(0, 0, 0, 0);
                } else {
                    // 视频项有小边距
                    int margin = 2; // dp
                    int marginPx = (int) (margin * getResources().getDisplayMetrics().density);
                    outRect.set(marginPx, marginPx, marginPx, marginPx);
                }
            }
        });

        // 设置适配器
        recyclerViewVideos.setAdapter(sectionedAdapter);

        // 为RecyclerView添加触摸监听器以支持滑动选择
        recyclerViewVideos.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private boolean isProcessingSwipe = false;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // 只在多选模式下拦截触摸事件
                if (!isMultiSelectionEnabled) {
                    return false;
                }

                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 不拦截ACTION_DOWN，让它传递给子视图处理长按
                        isProcessingSwipe = false;
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        // 如果已经在处理滑动选择，拦截后续的移动事件
                        return isProcessingSwipe;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 结束滑动选择
                        boolean wasProcessing = isProcessingSwipe;
                        isProcessingSwipe = false;
                        return wasProcessing;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // 处理滑动过程中的项目
                if (isMultiSelectionEnabled) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_MOVE:
                            // 查找手指位置下的子视图
                            View childView = rv.findChildViewUnder(e.getX(), e.getY());
                            if (childView != null) {
                                int position = rv.getChildAdapterPosition(childView);
                                if (position != RecyclerView.NO_POSITION) {
                                    // 只处理视频项，排除标题项
                                    if (sectionedAdapter.getItemViewType(position) != SectionedVideoAdapter.TYPE_HEADER) {
                                        // 通知适配器处理该位置的项目
                                        Video video = sectionedAdapter.getVideoAtPosition(position);
                                        if (video != null) {
                                            isProcessingSwipe = true;
                                            // 触发该项目的选择/取消选择
                                            sectionedAdapter.handleSwipeSelection(video);
                                        }
                                    }
                                }
                            }
                            break;
                    }
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // 不需要额外处理
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_entry_detail, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 如果处于多选模式，隐藏某些菜单项
        if (isMultiSelectionEnabled) {
            MenuItem multiSelectItem = menu.findItem(R.id.action_multi_select);
            if (multiSelectItem != null) {
                multiSelectItem.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_delete_entry) {
            showDeleteEntryDialog();
            return true;
        } else if (item.getItemId() == R.id.action_import_video) {
            importVideoFromGallery();
            return true;
        } else if (item.getItemId() == R.id.action_import_image) {
            importImageFromGallery();
            return true;
        } else if (item.getItemId() == R.id.action_multi_select) {
            // 启动多选模式
            startMultiSelectMode();
            return true;

        }
        else if (item.getItemId() == R.id.action_batch_import) {
            batchImportMedia();
            return true;
        }
        else if (item.getItemId() == R.id.action_summary) {
            // 跳转到总结活动
            Intent intent = new Intent(this, SummaryActivity.class);
            intent.putExtra(SummaryActivity.EXTRA_ENTRY_ID, entryId);
            intent.putExtra(SummaryActivity.EXTRA_ENTRY_NAME, entryName);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 从相册导入图片
     */
    private void importImageFromGallery() {
        // 检查存储权限
        if (!FileUtils.checkStoragePermission(this, REQUEST_STORAGE_PERMISSION_IMPORT)) {
            return;
        }

        // 创建选择图片的Intent
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 启动文件选择器
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                    REQUEST_IMPORT_IMAGE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 启动多选模式
     */
    private void startMultiSelectMode() {
        if (sectionedAdapter != null) {
            sectionedAdapter.startMultiSelectMode();
            isMultiSelectionEnabled = true;

            // 显示底部操作栏
            bottomSelectionBar.setVisibility(View.VISIBLE);
            updateSelectionCount(0);

            // 添加轻微的背景着色以指示多选模式
            ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor("#803F51B5")); // 使用半透明蓝色
            colorDrawable.setAlpha(20); // 非常轻微的着色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                recyclerViewVideos.setForeground(colorDrawable);
            }

            // 隐藏FAB按钮
            if (fabRecord != null) {
                fabRecord.hide();
            }

            // 隐藏冗余的菜单项
            supportInvalidateOptionsMenu();
        }
    }

    /**
     * 退出多选模式
     */
    private void exitMultiSelectMode() {
        isMultiSelectionEnabled = false;

        // 隐藏底部操作栏
        bottomSelectionBar.setVisibility(View.GONE);

        // 移除背景着色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recyclerViewVideos.setForeground(null);
        }

        // 显示FAB按钮
        if (fabRecord != null) {
            fabRecord.show();
        }

        // 退出适配器的多选模式
        if (sectionedAdapter != null) {
            sectionedAdapter.exitMultiSelectMode();
        }

        // 恢复菜单
        supportInvalidateOptionsMenu();
    }

    /**
     * 分享选中的视频/图片
     */
    private void shareSelectedVideos(List<Video> selectedVideos) {
        if (selectedVideos.isEmpty()) {
            return;
        }

        // 显示处理中提示
        Toast.makeText(this, R.string.sharing, Toast.LENGTH_SHORT).show();

        try {
            if (selectedVideos.size() == 1) {
                // 单个媒体分享
                shareMedia(selectedVideos.get(0));
            } else {
                // 多个媒体分享
                ArrayList<Uri> mediaUris = new ArrayList<>();
                boolean hasVideos = false;
                boolean hasImages = false;

                for (Video video : selectedVideos) {
                    File mediaFile = new File(video.getFilePath());
                    if (mediaFile.exists()) {
                        Uri uri = FileProvider.getUriForFile(
                                this,
                                "com.example.spj.fileprovider",
                                mediaFile);
                        mediaUris.add(uri);

                        if (video.isImage()) {
                            hasImages = true;
                        } else {
                            hasVideos = true;
                        }
                    }
                }

                if (mediaUris.isEmpty()) {
                    Toast.makeText(this, "没有可分享的媒体文件", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 创建分享Intent
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);

                // 设置MIME类型
                if (hasVideos && hasImages) {
                    // 混合类型
                    shareIntent.setType("*/*");
                } else if (hasImages) {
                    // 只有图片
                    shareIntent.setType("image/*");
                } else {
                    // 只有视频
                    shareIntent.setType("video/*");
                }

                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, mediaUris);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // 检查是否有应用可以处理此Intent
                if (shareIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
                } else {
                    Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sharing media: ", e);
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 分享单个媒体文件
     */
    private void shareMedia(Video media) {
        File mediaFile = new File(media.getFilePath());
        if (!mediaFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    "com.example.spj.fileprovider",
                    mediaFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);

            // 根据媒体类型设置MIME类型
            if (media.isImage()) {
                shareIntent.setType("image/*");
            } else {
                shareIntent.setType("video/*");
            }

            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 检查是否有应用可以处理此Intent
            List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (!resInfoList.isEmpty()) {
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
            } else {
                Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sharing media: ", e);
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示删除条目对话框
     */
    private void showDeleteEntryDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_entry)
                .setMessage(R.string.delete_entry_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteEntry())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 删除条目及其所有视频
     */
    private void deleteEntry() {
        Log.d(TAG, "Deleting entry with ID: " + entryId);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 获取所有视频
            List<Video> videos = videoDao.getVideosForEntry(entryId).getValue();

            if (videos != null) {
                Log.d(TAG, "Found " + videos.size() + " videos to delete");

                // 删除所有视频文件和缩略图文件
                for (Video video : videos) {
                    FileUtils.deleteFile(video.getFilePath());
                    FileUtils.deleteFile(video.getThumbnailPath());
                    Log.d(TAG, "Deleted video file: " + video.getFilePath());
                }
            } else {
                Log.d(TAG, "No videos found for this entry");
            }

            // 获取条目
            Entry entry = entryDao.getEntryByIdSync(entryId);

            if (entry != null) {
                // 删除条目缩略图
                FileUtils.deleteFile(entry.getThumbnailPath());
                Log.d(TAG, "Deleted entry thumbnail: " + entry.getThumbnailPath());

                // 从数据库中删除条目（会级联删除所有相关视频）
                entryDao.delete(entry);
                Log.d(TAG, "Deleted entry from database");

                // 返回主页面
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                Log.e(TAG, "Entry not found in database");
            }
        });
    }

    /**
     * 加载视频列表
     */
    private void loadVideos() {
        Log.d(TAG, "Loading videos for entry ID: " + entryId);

        // 直接查询视频数量
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = videoDao.getVideoCountForEntry(entryId);
            Log.d(TAG, "Video count for entry " + entryId + " is: " + count);
        });

        LiveData<List<Video>> videosLiveData = videoDao.getVideosForEntry(entryId);
        videosLiveData.observe(this, videos -> {
            Log.d(TAG, "Videos loaded: " + (videos != null ? videos.size() : 0));

            if (videos != null && !videos.isEmpty()) {
                // 记录每个视频的信息
                for (Video video : videos) {
                    File videoFile = new File(video.getFilePath());
                    File thumbnailFile = null;
                    if (video.getThumbnailPath() != null) {
                        thumbnailFile = new File(video.getThumbnailPath());
                    }

                    Log.d(TAG, "Video ID: " + video.getId() +
                            ", Path: " + video.getFilePath() +
                            ", File exists: " + videoFile.exists() +
                            ", Thumbnail: " + video.getThumbnailPath() +
                            ", Thumbnail exists: " + (thumbnailFile != null ? thumbnailFile.exists() : "N/A") +
                            ", Media Type: " + (video.isImage() ? "Image" : "Video"));
                }
            }

            // 设置视频到分组适配器
            sectionedAdapter.setVideos(videos);

            // 显示或隐藏空视图
            if (videos == null || videos.isEmpty()) {
                Log.d(TAG, "No videos found, showing empty view");
                recyclerViewVideos.setVisibility(View.GONE);
                textViewEmpty.setVisibility(View.VISIBLE);
            } else {
                Log.d(TAG, "Videos found, showing recycler view");
                recyclerViewVideos.setVisibility(View.VISIBLE);
                textViewEmpty.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        // 重新加载视频，以防其他活动可能修改了它们
        loadVideos();
    }

    /**
     * 从相册导入视频
     */
    private void importVideoFromGallery() {
        // 检查存储权限
        if (!FileUtils.checkStoragePermission(this, REQUEST_STORAGE_PERMISSION_IMPORT)) {
            return;
        }

        // 创建选择视频的Intent
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 启动文件选择器
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_video)),
                    REQUEST_IMPORT_VIDEO);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 导出选中的视频/图片到相册
     */
    private void exportSelectedVideosToGallery(List<Video> selectedVideos) {
        if (selectedVideos.isEmpty()) {
            return;
        }

        // 检查存储权限（Android 9及以下需要写入权限）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!FileUtils.checkStoragePermission(this, REQUEST_STORAGE_PERMISSION_EXPORT)) {
                return;
            }
        }

        // 显示处理中提示
        Toast.makeText(this, R.string.exporting_media, Toast.LENGTH_SHORT).show();

        // 在后台线程中处理导出
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;

            for (Video video : selectedVideos) {
                try {
                    if (video.isImage()) {
                        // 导出图片
                        Uri exportedUri = MediaUtils.exportImageToGallery(this, video.getFilePath());
                        if (exportedUri != null) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } else {
                        // 导出视频
                        Uri exportedUri = MediaUtils.exportVideoToGallery(this, video.getFilePath());
                        if (exportedUri != null) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "导出媒体文件失败: " + video.getFilePath(), e);
                    failCount++;
                }
            }

            // 在UI线程显示结果
            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;

            runOnUiThread(() -> {
                if (finalFailCount == 0) {
                    Toast.makeText(this,
                            getString(R.string.export_complete_success, finalSuccessCount),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.export_complete_with_errors, finalSuccessCount, finalFailCount),
                            Toast.LENGTH_LONG).show();
                }

                // 关闭多选模式
                exitMultiSelectMode();
            });
        }).start();
    }

    // 在onActivityResult方法中添加的部分
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        // 处理全屏编辑结果
        // 找到当前可见的所有ViewHolder，检查是否有与请求码匹配的标签
        boolean handled = false;
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) { // 1001是FlippedVideoViewHolder中的REQUEST_FULLSCREEN_EDIT
            for (int i = 0; i < recyclerViewVideos.getChildCount(); i++) {
                View childView = recyclerViewVideos.getChildAt(i);
                if (childView != null && childView.getTag(R.id.tag_video_position) != null) {
                    // 尝试让适配器处理结果
                    handled = sectionedAdapter.handleActivityResult(requestCode, resultCode, data, childView);
                    if (handled) {
                        break;
                    }
                }
            }
        }

        // 如果已处理，返回
        if (handled) {
            return;
        }

        // 处理批量导入结果
        if (requestCode == REQUEST_BATCH_IMPORT && resultCode == RESULT_OK && data != null) {
            List<Uri> mediaUris = new ArrayList<>();

            // 处理多选结果
            if (data.getClipData() != null) {
                // 多个文件被选择
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    mediaUris.add(uri);
                }
            } else if (data.getData() != null) {
                // 单个文件被选择
                mediaUris.add(data.getData());
            }

            if (!mediaUris.isEmpty()) {
                // 显示日期选择对话框，批量处理所有文件
                showDateSelectionDialogForBatch(mediaUris);
            }

            return;
        }

        // 处理拍照结果
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (currentPhotoFile != null && currentPhotoFile.exists() && currentPhotoFile.length() > 0) {
                Log.d(TAG, "Photo captured successfully: " + currentPhotoFile.getAbsolutePath());
                savePhotoToDatabase(currentPhotoFile);
            } else {
                Log.e(TAG, "Photo file is null or does not exist");
                if (currentPhotoFile != null) {
                    Log.e(TAG, "Photo file path: " + currentPhotoFile.getAbsolutePath());
                    Log.e(TAG, "Photo file exists: " + currentPhotoFile.exists());
                    if (currentPhotoFile.exists()) {
                        Log.e(TAG, "Photo file size: " + currentPhotoFile.length());
                    }
                }
                Toast.makeText(this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
            }
            return;
        } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
            Log.d(TAG, "Photo capture canceled or failed");
            // 清理可能的空文件
            if (currentPhotoFile != null && currentPhotoFile.exists() && currentPhotoFile.length() == 0) {
                currentPhotoFile.delete();
                Log.d(TAG, "Deleted empty photo file");
            }
            return;
        }

        // 处理导入图片的结果
        if (requestCode == REQUEST_IMPORT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                // 显示处理中提示
                Toast.makeText(this, R.string.processing_image, Toast.LENGTH_SHORT).show();

                // 在后台线程中获取图片信息
                new Thread(() -> {
                    try {
                        // 先获取图片的原始创建日期（如果可用）
                        Date originalDate = MediaUtils.getImageCreationDate(this, selectedImageUri);

                        // 在UI线程上显示日期选择对话框
                        runOnUiThread(() -> showDateSelectionDialog(selectedImageUri, originalDate, true));
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting image info: ", e);
                        runOnUiThread(() -> {
                            // 无法获取日期信息，直接使用当前日期导入
                            showDateSelectionDialog(selectedImageUri, null, true);
                        });
                    }
                }).start();

                return;
            }
        }

        if (requestCode == REQUEST_CUSTOM_CAMERA) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Video successfully recorded with custom camera");
                // CameraActivity has already saved the video to the database
                // Just refresh the video list
                loadVideos();
            } else {
                Log.d(TAG, "Custom camera recording canceled or failed");
            }
            return;
        }

        // 处理导入视频的结果
        if (requestCode == REQUEST_IMPORT_VIDEO && resultCode == RESULT_OK && data != null) {
            Uri selectedVideoUri = data.getData();
            if (selectedVideoUri != null) {
                // 显示处理中提示
                Toast.makeText(this, R.string.processing_video, Toast.LENGTH_SHORT).show();

                // 在后台线程中获取视频信息
                new Thread(() -> {
                    try {
                        // 先获取视频的原始创建日期（如果可用）
                        Date originalDate = MediaUtils.getVideoCreationDate(this, selectedVideoUri);

                        // 在UI线程上显示日期选择对话框
                        runOnUiThread(() -> showDateSelectionDialog(selectedVideoUri, originalDate, false));
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting video info: ", e);
                        runOnUiThread(() -> {
                            // 无法获取日期信息，直接使用当前日期导入
                            showDateSelectionDialog(selectedVideoUri, null, false);
                        });
                    }
                }).start();

                return;
            }
        }
    }

    /**
     * 为批量导入显示日期选择对话框
     */
    private void showDateSelectionDialogForBatch(List<Uri> mediaUris) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_select, null);

        // 对话框组件与单文件导入相同
        RadioGroup radioGroup = dialogView.findViewById(R.id.radioGroupDateOptions);
        RadioButton radioCurrentDate = dialogView.findViewById(R.id.radioCurrentDate);
        RadioButton radioOriginalDate = dialogView.findViewById(R.id.radioOriginalDate);
        RadioButton radioCustomDate = dialogView.findViewById(R.id.radioCustomDate);
        LinearLayout layoutCustomDate = dialogView.findViewById(R.id.layoutCustomDate);
        Button buttonPickDate = dialogView.findViewById(R.id.buttonPickDate);
        TextView textViewSelectedDate = dialogView.findViewById(R.id.textViewSelectedDate);

        // 获取当前日期和格式化工具
        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // 自定义日期初始设置为当前日期
        final Date[] customDate = {currentDate};
        textViewSelectedDate.setText(getString(R.string.selected_date, dateFormat.format(customDate[0])));

        // 添加单选按钮变化监听
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            layoutCustomDate.setVisibility(checkedId == R.id.radioCustomDate ? View.VISIBLE : View.GONE);
        });

        // 日期选择按钮点击事件
        buttonPickDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(customDate[0]);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    EntryDetailActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        customDate[0] = calendar.getTime();
                        textViewSelectedDate.setText(getString(R.string.selected_date, dateFormat.format(customDate[0])));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));

            datePickerDialog.show();
        });

        // 创建并显示对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.batch_import_date_selection)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialogInterface, which) -> {
                    // 确定按钮点击处理
                    Date selectedDate;

                    if (radioCurrentDate.isChecked()) {
                        selectedDate = currentDate;
                        processBatchImport(mediaUris, selectedDate, false);
                    } else if (radioOriginalDate.isChecked()) {
                        // 每个文件使用其原始日期
                        processBatchImport(mediaUris, null, true);
                    } else if (radioCustomDate.isChecked()) {
                        selectedDate = customDate[0];
                        processBatchImport(mediaUris, selectedDate, false);
                    } else {
                        // 默认使用当前日期
                        processBatchImport(mediaUris, currentDate, false);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }
    /**
     * 处理批量导入
     * @param mediaUris 媒体URI列表
     * @param fixedDate 固定日期（如果useOriginalDate为false）
     * @param useOriginalDate 是否使用每个文件的原始日期
     */
    private void processBatchImport(List<Uri> mediaUris, Date fixedDate, boolean useOriginalDate) {
        int totalFiles = mediaUris.size();

        // 创建并显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.importing_media);
        progressDialog.setMessage(getString(R.string.processing_files, 0, totalFiles));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(totalFiles);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 在后台线程中处理文件
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < mediaUris.size(); i++) {
                Uri uri = mediaUris.get(i);
                final int currentIndex = i + 1;

                // 更新进度对话框
                runOnUiThread(() -> {
                    progressDialog.setProgress(currentIndex);
                    progressDialog.setMessage(getString(R.string.processing_files, currentIndex, totalFiles));
                });

                try {
                    // 确定当前文件的日期
                    Date fileDate;
                    if (useOriginalDate) {
                        // 尝试获取原始日期
                        String mimeType = getContentResolver().getType(uri);
                        if (mimeType != null && mimeType.startsWith("video/")) {
                            fileDate = MediaUtils.getVideoCreationDate(this, uri);
                        } else if (mimeType != null && mimeType.startsWith("image/")) {
                            fileDate = MediaUtils.getImageCreationDate(this, uri);
                        } else {
                            fileDate = new Date(); // 默认为当前日期
                        }

                        if (fileDate == null) {
                            fileDate = new Date(); // 如果无法获取原始日期，使用当前日期
                        }
                    } else {
                        fileDate = fixedDate; // 使用固定日期
                    }

                    // 导入文件
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType != null && mimeType.startsWith("video/")) {
                        // 导入视频
                        File importedFile = MediaUtils.importVideoFromUri(this, uri, entryId);
                        if (importedFile != null) {
                            saveVideoToDatabaseWithDate(importedFile, fileDate);
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } else if (mimeType != null && mimeType.startsWith("image/")) {
                        // 导入图片
                        File importedFile = MediaUtils.importImageFromUri(this, uri, entryId);
                        if (importedFile != null) {
                            saveImageToDatabaseWithDate(importedFile, fileDate);
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } else {
                        // 不支持的文件类型
                        failCount++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "导入文件失败: " + uri, e);
                    failCount++;
                }
            }

            // 关闭进度对话框并显示结果
            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;

            runOnUiThread(() -> {
                progressDialog.dismiss();

                if (finalFailCount == 0) {
                    Toast.makeText(EntryDetailActivity.this,
                            getString(R.string.import_complete_success, finalSuccessCount),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(EntryDetailActivity.this,
                            getString(R.string.import_complete_with_errors, finalSuccessCount, finalFailCount),
                            Toast.LENGTH_LONG).show();
                }

                // 刷新媒体列表
                loadVideos();
            });
        }).start();
    }
    /**
     * 批量导入媒体文件
     */
    private void batchImportMedia() {
        // 检查存储权限
        if (!FileUtils.checkStoragePermission(this, REQUEST_STORAGE_PERMISSION_IMPORT)) {
            return;
        }

        // 创建支持多选的Intent
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        // 关键：允许多选
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] mimeTypes = {"image/*", "video/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        // 启动文件选择器
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_media_files)),
                    REQUEST_BATCH_IMPORT);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 保存照片到数据库
     */
    private void savePhotoToDatabase(File photoFile) {
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
                        Toast.makeText(EntryDetailActivity.this,
                                R.string.photo_captured, Toast.LENGTH_SHORT).show();
                        loadVideos(); // 重新加载视频列表
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving photo: ", e);
                runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
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
            Bitmap original = BitmapFactory.decodeFile(photoPath);
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
            Bitmap thumbnail = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, false);

            // 保存缩略图
            FileOutputStream out = new FileOutputStream(thumbnailPath);
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, out);
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
                // 如果是长按操作，执行拍照；否则执行录像
                if (mHasPerformedLongPress) {
                    takePicture();
                } else {
                    // 启动自定义相机活动
                    Intent cameraIntent = new Intent(this, CameraActivity.class);
                    cameraIntent.putExtra(CameraActivity.EXTRA_ENTRY_ID, entryId);
                    startActivityForResult(cameraIntent, REQUEST_CUSTOM_CAMERA);
                }
            } else {
                Toast.makeText(this, "需要相机、麦克风和存储权限才能录制视频", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION_IMPORT) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                importVideoFromGallery();
            } else {
                Toast.makeText(this, R.string.permission_required_for_import, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION_EXPORT) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted && currentExportVideo != null) {
                exportVideoToGallery(currentExportVideo);
                currentExportVideo = null; // 清除引用
            } else {
                Toast.makeText(this, R.string.permission_required_for_export, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 显示日期选择对话框
     */
    private void showDateSelectionDialog(Uri mediaUri, Date originalDate, boolean isImage) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_select, null);

        RadioGroup radioGroup = dialogView.findViewById(R.id.radioGroupDateOptions);
        RadioButton radioCurrentDate = dialogView.findViewById(R.id.radioCurrentDate);
        RadioButton radioOriginalDate = dialogView.findViewById(R.id.radioOriginalDate);
        RadioButton radioCustomDate = dialogView.findViewById(R.id.radioCustomDate);
        LinearLayout layoutCustomDate = dialogView.findViewById(R.id.layoutCustomDate);
        Button buttonPickDate = dialogView.findViewById(R.id.buttonPickDate);
        TextView textViewSelectedDate = dialogView.findViewById(R.id.textViewSelectedDate);

        // 获取当前日期
        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // 如果没有原始日期，禁用该选项
        if (originalDate == null) {
            radioOriginalDate.setEnabled(false);
            radioOriginalDate.setText(getString(R.string.use_original_date) + " (不可用)");
        } else {
            radioOriginalDate.setText(getString(R.string.use_original_date) + " (" + dateFormat.format(originalDate) + ")");
        }

        // 自定义日期初始设置为当前日期
        final Date[] customDate = {currentDate};
        textViewSelectedDate.setText(getString(R.string.selected_date, dateFormat.format(customDate[0])));

        // 添加单选按钮变化监听
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            layoutCustomDate.setVisibility(checkedId == R.id.radioCustomDate ? View.VISIBLE : View.GONE);
        });

        // 日期选择按钮点击事件
        buttonPickDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(customDate[0]);

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    EntryDetailActivity.this,
                    (view, selectedYear, selectedMonth, selectedDayOfMonth) -> {
                        calendar.set(selectedYear, selectedMonth, selectedDayOfMonth);
                        customDate[0] = calendar.getTime();
                        textViewSelectedDate.setText(getString(R.string.selected_date, dateFormat.format(customDate[0])));
                    },
                    year, month, day);

            datePickerDialog.show();
        });

        // 创建并显示对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialogInterface, which) -> {
                    // 确定按钮点击处理
                    Date selectedDate;

                    if (radioCurrentDate.isChecked()) {
                        selectedDate = currentDate;
                    } else if (radioOriginalDate.isChecked() && originalDate != null) {
                        selectedDate = originalDate;
                    } else if (radioCustomDate.isChecked()) {
                        selectedDate = customDate[0];
                    } else {
                        selectedDate = currentDate; // 默认使用当前日期
                    }

                    // 进行导入操作
                    if (isImage) {
                        importImageWithDate(mediaUri, selectedDate);
                    } else {
                        importVideoWithDate(mediaUri, selectedDate);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }

    /**
     * 使用指定日期导入图片
     */
    private void importImageWithDate(Uri imageUri, Date date) {
        // 显示处理中提示
        Toast.makeText(this, R.string.processing_image, Toast.LENGTH_SHORT).show();

        // 在后台线程中处理导入
        new Thread(() -> {
            try {
                // 导入图片文件
                File importedFile = MediaUtils.importImageFromUri(this, imageUri, entryId);

                // 保存图片到数据库，使用指定的日期
                if (importedFile != null) {
                    saveImageToDatabaseWithDate(importedFile, date);
                    runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                            R.string.image_imported_success, Toast.LENGTH_SHORT).show());
                } else {
                    throw new IOException("导入的文件为空");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error importing image: ", e);
                runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                        R.string.image_import_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * 使用指定日期导入视频
     */
    private void importVideoWithDate(Uri videoUri, Date date) {
        // 显示处理中提示
        Toast.makeText(this, R.string.processing_video, Toast.LENGTH_SHORT).show();

        // 在后台线程中处理导入
        new Thread(() -> {
            try {
                // 导入视频文件
                File importedFile = MediaUtils.importVideoFromUri(this, videoUri, entryId);

                // 保存视频到数据库，使用指定的日期
                if (importedFile != null) {
                    saveVideoToDatabaseWithDate(importedFile, date);
                    runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                            R.string.video_imported_success, Toast.LENGTH_SHORT).show());
                } else {
                    throw new IOException("导入的文件为空");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error importing video: ", e);
                runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                        R.string.video_import_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * 导出单个媒体到相册
     */
    private void exportVideoToGallery(Video video) {
        // 保存要导出的媒体
        currentExportVideo = video;

        // 检查存储权限（Android 9及以下需要写入权限）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!FileUtils.checkStoragePermission(this, REQUEST_STORAGE_PERMISSION_EXPORT)) {
                return;
            }
        }

        // 显示处理中提示
        Toast.makeText(this, video.isImage() ?
                R.string.processing_image : R.string.processing_video, Toast.LENGTH_SHORT).show();

        // 在后台线程中处理导出
        new Thread(() -> {
            try {
                // 根据媒体类型导出
                Uri exportedUri;
                if (video.isImage()) {
                    // 导出图片
                    exportedUri = MediaUtils.exportImageToGallery(this, video.getFilePath());
                } else {
                    // 导出视频
                    exportedUri = MediaUtils.exportVideoToGallery(this, video.getFilePath());
                }

                if (exportedUri != null) {
                    runOnUiThread(() -> {
                        Toast.makeText(EntryDetailActivity.this,
                                video.isImage() ?
                                        R.string.image_exported_success : R.string.video_exported_success,
                                Toast.LENGTH_SHORT).show();
                    });
                } else {
                    throw new IOException("导出结果URI为空");
                }
            } catch (IOException e) {
                Log.e(TAG, "导出媒体文件失败: ", e);
                runOnUiThread(() -> {
                    Toast.makeText(EntryDetailActivity.this,
                            video.isImage() ?
                                    R.string.image_export_failed : R.string.video_export_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 保存图片到数据库（使用指定日期）
     */
    private void saveImageToDatabaseWithDate(File imageFile, Date recordedDate) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Log.d(TAG, "Saving image to database with date: " + recordedDate);

                // 创建缩略图文件
                File thumbnailFile = FileUtils.createThumbnailFile(this, entryId);
                Log.d(TAG, "Created thumbnail file: " + thumbnailFile.getAbsolutePath());

                // 生成缩略图
                boolean thumbnailGenerated = generatePhotoThumbnail(
                        imageFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());
                Log.d(TAG, "Thumbnail generated: " + thumbnailGenerated);

                // 创建视频记录（实际是图片，但使用统一的Video模型）
                Video video = new Video(entryId, imageFile.getAbsolutePath());

                // 设置为图片类型
                video.setMediaType(Video.TYPE_IMAGE);

                // 设置缩略图路径
                if (thumbnailGenerated) {
                    video.setThumbnailPath(thumbnailFile.getAbsolutePath());
                }

                // 设置用户选择的录制日期
                video.setRecordedAt(recordedDate);

                // 设置文件大小
                long fileSize = FileUtils.getFileSize(imageFile.getAbsolutePath());
                video.setFileSize(fileSize);

                Log.d(TAG, "Image size: " + fileSize + " bytes");

                // 保存到数据库
                long videoId = videoDao.insert(video);
                Log.d(TAG, "Image saved to database with ID: " + videoId);

                if (videoId > 0) {
                    // 更新条目的视频计数
                    int count = videoDao.getVideoCountForEntry(entryId);
                    entryDao.updateVideoCount(entryId, count);
                    Log.d(TAG, "Updated entry video count to: " + count);

                    // 如果条目没有缩略图，使用新图片的缩略图
                    Entry entry = entryDao.getEntryByIdSync(entryId);
                    if (entry != null && (entry.getThumbnailPath() == null || entry.getThumbnailPath().isEmpty())) {
                        if (thumbnailGenerated) {
                            entryDao.updateThumbnail(entryId, thumbnailFile.getAbsolutePath());
                            Log.d(TAG, "Updated entry thumbnail");
                        }
                    }

                    // 通知用户
                    runOnUiThread(() -> {
                        loadVideos(); // 重新加载视频列表
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving image: ", e);
                runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                        R.string.image_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 保存视频到数据库（使用指定日期）
     */
    private void saveVideoToDatabaseWithDate(File videoFile, Date recordedDate) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Log.d(TAG, "Saving video to database with date: " + recordedDate);

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

                // 设置用户选择的录制日期
                video.setRecordedAt(recordedDate);

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
                    runOnUiThread(() -> {
                        loadVideos(); // 重新加载视频列表
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving video: ", e);
                runOnUiThread(() -> Toast.makeText(EntryDetailActivity.this,
                        R.string.error_recording_video, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void enableDebugLogging() {
        // 记录初始适配器状态
        Log.d(TAG, "==== 调试: 初始适配器状态 ====");
        Log.d(TAG, "视频数量: " + sectionedAdapter.getItemCount());

        // 监控 RecyclerView 上的所有触摸
        recyclerViewVideos.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();

                Log.d(TAG, "触摸按下，坐标 x=" + x + ", y=" + y);

                // 在此位置查找视图
                View childView = recyclerViewVideos.findChildViewUnder(x, y);
                if (childView != null) {
                    int position = recyclerViewVideos.getChildAdapterPosition(childView);
                    Log.d(TAG, "在适配器位置找到子视图: " + position);

                    if (position != RecyclerView.NO_POSITION) {
                        int viewType = sectionedAdapter.getItemViewType(position);
                        Log.d(TAG, "视图类型: " + (viewType == SectionedVideoAdapter.TYPE_HEADER ? "标题" : "项目"));

                        if (viewType == SectionedVideoAdapter.TYPE_ITEM) {
                            Video video = sectionedAdapter.getVideoAtPosition(position);
                            if (video != null) {
                                Log.d(TAG, "找到视频: " + (video.isImage() ? "图片" : "视频") +
                                        ", 路径: " + video.getFilePath());
                            } else {
                                Log.e(TAG, "位置 " + position + " 的视频为空");
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "在此位置未找到子视图");
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                Log.d(TAG, "触摸抬起");
            }

            // 不消费事件
            return false;
        });
    }

    // 视频点击事件处理
    @Override
    public void onVideoClick(Video video) {
        // 如果处于多选模式，则切换选中状态
        if (sectionedAdapter.isMultiSelectMode()) {
            return; // 选择逻辑在适配器中处理
        }
        if (video.isImage()) {
            // 打开系统图片查看器
            Log.d(TAG, "Image clicked: " + video.getFilePath());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri imageUri = FileUtils.getUriForFile(this, new File(video.getFilePath()));
            intent.setDataAndType(imageUri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                Log.d(TAG, "Starting image viewer");
                startActivity(intent);
            } else {
                Log.e(TAG, "No image viewer app available");
                Toast.makeText(this, "没有找到可用的图片查看器", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 打开系统视频播放器
            Log.d(TAG, "Video clicked: " + video.getFilePath());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri videoUri = FileUtils.getUriForFile(this, new File(video.getFilePath()));
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                Log.d(TAG, "Starting video player");
                startActivity(intent);
            } else {
                Log.e(TAG, "No video player app available");
                Toast.makeText(this, "没有找到可用的视频播放器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 多选状态变化回调
     */
    @Override
    public void onMultiSelectModeChanged(boolean active) {
        if (active) {
            startMultiSelectMode();
        } else {
            exitMultiSelectMode();
        }
    }

    /**
     * 选择数量变化回调
     */
    @Override
    public void onSelectionChanged(int count) {
        updateSelectionCount(count);

        // 如果选择数量为0，更新UI状态但不自动退出
        // 如果想自动退出多选模式，可以取消下面的注释
        // if (count == 0 && isMultiSelectionEnabled) {
        //     exitMultiSelectMode();
        // }
    }

    /**
     * 视频长按事件处理 - 不再启动多选模式，而是显示操作菜单
     */
    @Override
    public boolean onVideoLongClick(Video video) {
        // 即使在多选模式，长按也显示菜单
        showVideoOptionsDialog(video);
        return true;
    }

    /**
     * 显示视频操作菜单
     */
    private void showVideoOptionsDialog(Video video) {
        // 添加分享选项
        String[] options = {
                getString(R.string.add_notes),
                getString(R.string.export_video_to_gallery),
                getString(R.string.share),
                getString(R.string.delete_video)
        };

        new AlertDialog.Builder(this)
                .setTitle(video.isImage() ? R.string.image_options : R.string.video_options)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 添加注释
                        showAddNotesDialog(video);
                    } else if (which == 1) {
                        // 导出到相册
                        exportVideoToGallery(video);
                    } else if (which == 2) {
                        // 分享选项
                        shareMedia(video);
                    } else if (which == 3) {
                        // 删除视频
                        showDeleteVideoDialog(video);
                    }
                })
                .show();
    }

    /**
     * 显示删除选中视频的确认对话框
     */
    private void showDeleteSelectedVideosDialog(List<Video> selectedVideos) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_selected_videos)
                .setMessage(getString(R.string.delete_selected_videos_confirm, selectedVideos.size()))
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteSelectedVideos(selectedVideos))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 删除选中的视频
     */
    private void deleteSelectedVideos(List<Video> selectedVideos) {
        if (selectedVideos.isEmpty()) {
            return;
        }

        // 在后台线程中处理删除
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int deletedCount = 0;

            for (Video video : selectedVideos) {
                // 删除视频文件和缩略图
                FileUtils.deleteFile(video.getFilePath());
                FileUtils.deleteFile(video.getThumbnailPath());

                // 从数据库中删除
                videoDao.delete(video);
                deletedCount++;
            }

            // 更新条目的视频计数
            int count = videoDao.getVideoCountForEntry(entryId);
            entryDao.updateVideoCount(entryId, count);

            // 如果这是条目的最后一个视频，清除条目缩略图
            if (count == 0) {
                entryDao.updateThumbnail(entryId, null);
            }
            // 如果删除的视频中包含条目的缩略图，则更新为最新视频的缩略图
            else {
                Entry entry = entryDao.getEntryByIdSync(entryId);
                if (entry != null) {
                    boolean needUpdateThumbnail = false;
                    String entryThumbnail = entry.getThumbnailPath();

                    for (Video video : selectedVideos) {
                        if (video.getThumbnailPath() != null &&
                                video.getThumbnailPath().equals(entryThumbnail)) {
                            needUpdateThumbnail = true;
                            break;
                        }
                    }

                    if (needUpdateThumbnail) {
                        Video latestVideo = videoDao.getLatestVideoForEntry(entryId);
                        if (latestVideo != null && latestVideo.getThumbnailPath() != null) {
                            entryDao.updateThumbnail(entryId, latestVideo.getThumbnailPath());
                        }
                    }
                }
            }

            // 通知用户
            final int finalDeletedCount = deletedCount;
            runOnUiThread(() -> {
                Toast.makeText(EntryDetailActivity.this,
                        getString(R.string.deleted_videos, finalDeletedCount), Toast.LENGTH_SHORT).show();

                // 关闭多选模式
                exitMultiSelectMode();

                // 重新加载视频列表
                loadVideos();
            });
        });
    }

    /**
     * 显示添加注释对话框
     */
    private void showAddNotesDialog(Video video) {
        // 创建一个EditText用于输入注释
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(R.string.notes_hint);
        input.setMinLines(3);
        input.setMaxLines(6);

        // 设置当前注释（如果有）
        if (video.getNotes() != null) {
            input.setText(video.getNotes());
        }

        // 设置布局参数
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(lp);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_notes)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String notes = input.getText().toString().trim();
                    saveVideoNotes(video, notes);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 保存视频注释
     */
    private void saveVideoNotes(Video video, String notes) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 更新视频注释
            video.setNotes(notes);
            videoDao.update(video);

            // 通知用户
            runOnUiThread(() -> {
                Toast.makeText(EntryDetailActivity.this,
                        R.string.notes_saved, Toast.LENGTH_SHORT).show();
                loadVideos(); // 重新加载视频列表以显示注释
            });
        });
    }

    /**
     * 显示删除视频确认对话框
     */
    private void showDeleteVideoDialog(Video video) {
        new AlertDialog.Builder(this)
                .setTitle(video.isImage() ? R.string.delete_image : R.string.delete_video)
                .setMessage(video.isImage() ? R.string.delete_image_confirm : R.string.delete_video_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteVideo(video))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 删除视频
     */
    private void deleteVideo(Video video) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 删除视频文件和缩略图
            FileUtils.deleteFile(video.getFilePath());
            FileUtils.deleteFile(video.getThumbnailPath());

            // 从数据库中删除
            videoDao.delete(video);

            // 更新条目的视频计数
            int count = videoDao.getVideoCountForEntry(entryId);
            entryDao.updateVideoCount(entryId, count);

            // 如果这是条目的最后一个视频，清除条目缩略图
            if (count == 0) {
                entryDao.updateThumbnail(entryId, null);
            }
            // 如果删除的视频是条目的缩略图，则更新为最新视频的缩略图
            else if (video.getThumbnailPath() != null) {
                Entry entry = entryDao.getEntryByIdSync(entryId);
                if (entry != null && video.getThumbnailPath().equals(entry.getThumbnailPath())) {
                    Video latestVideo = videoDao.getLatestVideoForEntry(entryId);
                    if (latestVideo != null && latestVideo.getThumbnailPath() != null) {
                        entryDao.updateThumbnail(entryId, latestVideo.getThumbnailPath());
                    }
                }
            }

            // 通知用户
            runOnUiThread(() -> {
                Toast.makeText(EntryDetailActivity.this,
                        video.isImage() ? R.string.image_deleted : R.string.video_deleted,
                        Toast.LENGTH_SHORT).show();
                loadVideos(); // 重新加载视频列表
            });
        });
    }


}