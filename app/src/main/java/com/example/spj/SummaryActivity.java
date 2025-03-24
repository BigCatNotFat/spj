package com.example.spj;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.adapter.SummaryAdapter;
import com.example.spj.database.AppDatabase;
import com.example.spj.database.SummaryDao;
import com.example.spj.database.VideoDao;
import com.example.spj.model.Summary;
import com.example.spj.model.Video;
import com.example.spj.util.DeepseekUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class SummaryActivity extends AppCompatActivity implements SummaryAdapter.OnSummaryClickListener {

    private static final String TAG = "SummaryActivity";
    public static final String EXTRA_ENTRY_ID = "com.example.spj.EXTRA_ENTRY_ID";
    public static final String EXTRA_ENTRY_NAME = "com.example.spj.EXTRA_ENTRY_NAME";

    private int entryId;
    private String entryName;
    private AppDatabase database;
    private VideoDao videoDao;
    private SummaryDao summaryDao;
    private static final int REQUEST_FULLSCREEN_EDIT = 1001;
    private Button buttonFullscreenEdit;
    // UI组件
    private RecyclerView recyclerViewSummaries;
    private TextView textViewEmpty;
    private Button buttonPreviewNotes;
    private Button buttonGenerateSummary;
    private Button buttonCancelPreview;
    private Button buttonConfirmPreview;
    private LinearLayout layoutProgress;
    private LinearLayout layoutPreview;
    private LinearLayout layoutGenerateStage;
    private TextView textViewProgress;
    private EditText editTextNotesPreview;
    private ProgressBar progressBar;
    private TextView textViewSummaryContent;

    private SummaryAdapter summaryAdapter;

    // 存储格式化后的笔记内容
    private String formattedNotes;
    private String originalFormattedNotes; // 保存原始格式化文本，用于重置功能

    // 标记是否已经加载过笔记内容
    private boolean hasLoadedNotes = false;
    // 标记笔记是否已经编辑过
    private boolean notesEdited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        // 获取传递的参数
        entryId = getIntent().getIntExtra(EXTRA_ENTRY_ID, -1);
        entryName = getIntent().getStringExtra(EXTRA_ENTRY_NAME);

        if (entryId == -1) {
            Log.e(TAG, "Invalid entry ID, finishing activity");
            finish();
            return;
        }
        buttonFullscreenEdit = findViewById(R.id.buttonFullscreenEdit);
        buttonFullscreenEdit.setOnClickListener(v -> openFullscreenEditor());
        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.summary) + " - " + entryName);
        }

        // 初始化数据库
        database = AppDatabase.getDatabase(this);
        videoDao = database.videoDao();
        summaryDao = database.summaryDao();

        // 初始化视图
        recyclerViewSummaries = findViewById(R.id.recyclerViewSummaries);
        textViewEmpty = findViewById(R.id.textViewEmpty);

        // 初始化预览相关组件
        buttonPreviewNotes = findViewById(R.id.buttonPreviewNotes);
        buttonGenerateSummary = findViewById(R.id.buttonGenerateSummary);
        buttonCancelPreview = findViewById(R.id.buttonCancelPreview);
        buttonConfirmPreview = findViewById(R.id.buttonConfirmPreview);
        layoutProgress = findViewById(R.id.layoutProgress);
        layoutPreview = findViewById(R.id.layoutPreview);
        layoutGenerateStage = findViewById(R.id.layoutGenerateStage);
        textViewProgress = findViewById(R.id.textViewProgress);
        editTextNotesPreview = findViewById(R.id.editTextNotesPreview);
        progressBar = findViewById(R.id.progressBar);
        textViewSummaryContent = findViewById(R.id.textViewSummaryContent);

        // 配置EditText的垂直滚动行为
        editTextNotesPreview.setVerticalScrollBarEnabled(true);

        // 设置RecyclerView
        recyclerViewSummaries.setLayoutManager(new LinearLayoutManager(this));
        summaryAdapter = new SummaryAdapter();
        summaryAdapter.setOnSummaryClickListener(this);
        recyclerViewSummaries.setAdapter(summaryAdapter);

        // 设置预览笔记按钮点击事件
        buttonPreviewNotes.setOnClickListener(v -> previewNotes());

        // 设置取消预览按钮点击事件
        buttonCancelPreview.setOnClickListener(v -> {
            // 隐藏预览布局
            layoutPreview.setVisibility(View.GONE);
            // 显示生成阶段布局
            layoutGenerateStage.setVisibility(View.VISIBLE);
        });

        // 设置确认预览按钮点击事件
        buttonConfirmPreview.setOnClickListener(v -> {
            // 获取用户编辑后的内容
            String editedText = editTextNotesPreview.getText().toString().trim();
            if (editedText.isEmpty()) {
                Toast.makeText(this, "笔记内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 保存编辑后的内容
            formattedNotes = editedText;
            notesEdited = true;

            // 隐藏预览布局
            layoutPreview.setVisibility(View.GONE);
            // 显示生成阶段布局
            layoutGenerateStage.setVisibility(View.VISIBLE);
            // 启用生成总结按钮
            buttonGenerateSummary.setEnabled(true);
            Toast.makeText(this, "编辑内容已保存，可以生成总结", Toast.LENGTH_SHORT).show();
        });

        // 设置生成总结按钮点击事件
        buttonGenerateSummary.setOnClickListener(v -> {
            if (formattedNotes == null || formattedNotes.isEmpty()) {
                Toast.makeText(this, R.string.preview_first, Toast.LENGTH_SHORT).show();
                return;
            }
            generateSummary();
        });

        // 加载历史总结
        loadSummaries();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 添加重置为原始内容的菜单项
        if (layoutPreview.getVisibility() == View.VISIBLE) {
            getMenuInflater().inflate(R.menu.menu_preview, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_reset) {
            // 重置为原始内容
            if (originalFormattedNotes != null) {
                editTextNotesPreview.setText(originalFormattedNotes);
                Toast.makeText(this, "已重置为原始内容", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 预览笔记内容
     */
    private void previewNotes() {
        // 如果已经编辑过内容，直接显示编辑过的内容
        if (notesEdited && formattedNotes != null && !formattedNotes.isEmpty()) {
            editTextNotesPreview.setText(formattedNotes);
            layoutPreview.setVisibility(View.VISIBLE);
            layoutGenerateStage.setVisibility(View.GONE);
            invalidateOptionsMenu();
            return;
        }

        // 如果已经加载过笔记但未编辑，直接显示原始内容
        if (hasLoadedNotes && originalFormattedNotes != null && !originalFormattedNotes.isEmpty()) {
            editTextNotesPreview.setText(originalFormattedNotes);
            formattedNotes = originalFormattedNotes;
            layoutPreview.setVisibility(View.VISIBLE);
            layoutGenerateStage.setVisibility(View.GONE);
            invalidateOptionsMenu();
            return;
        }

        // 显示进度条
        layoutProgress.setVisibility(View.VISIBLE);
        layoutGenerateStage.setVisibility(View.GONE);
        textViewProgress.setText(R.string.loading_notes);

        // 在后台线程中加载所有视频的笔记
        new Thread(() -> {
            try {
                // 获取所有带笔记的视频
                List<Video> videos = videoDao.getAllVideosWithNotesForEntrySync(entryId);

                if (videos == null || videos.isEmpty()) {
                    runOnUiThread(() -> {
                        layoutProgress.setVisibility(View.GONE);
                        layoutGenerateStage.setVisibility(View.VISIBLE);
                        Toast.makeText(SummaryActivity.this,
                                R.string.no_notes_to_summarize, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 按日期对笔记进行分组
                formattedNotes = formatNotesText(videos);
                // 保存原始格式化文本
                originalFormattedNotes = formattedNotes;

                // 标记已加载笔记
                hasLoadedNotes = true;

                runOnUiThread(() -> {
                    // 隐藏进度条
                    layoutProgress.setVisibility(View.GONE);

                    // 显示预览部分
                    editTextNotesPreview.setText(formattedNotes);
                    layoutPreview.setVisibility(View.VISIBLE);

                    // 更新选项菜单
                    invalidateOptionsMenu();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading notes: ", e);
                runOnUiThread(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    layoutGenerateStage.setVisibility(View.VISIBLE);
                    Toast.makeText(SummaryActivity.this,
                            "加载笔记失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 加载历史总结记录
     */
    private void loadSummaries() {
        LiveData<List<Summary>> summariesLiveData = summaryDao.getSummariesForEntry(entryId);
        summariesLiveData.observe(this, summaries -> {
            summaryAdapter.setSummaries(summaries);

            // 显示或隐藏空视图
            if (summaries == null || summaries.isEmpty()) {
                recyclerViewSummaries.setVisibility(View.GONE);
                textViewEmpty.setVisibility(View.VISIBLE);
            } else {
                recyclerViewSummaries.setVisibility(View.VISIBLE);
                textViewEmpty.setVisibility(View.GONE);

                // 如果有最新的总结，显示在当前总结部分
                Summary latestSummary = summaries.get(0); // 已按时间降序排序
                displayCurrentSummary(latestSummary);
            }
        });
    }

    /**
     * 显示当前总结内容
     */
    private void displayCurrentSummary(Summary summary) {
        if (summary != null && summary.getContent() != null && !summary.getContent().isEmpty()) {
            textViewSummaryContent.setVisibility(View.VISIBLE);
            textViewSummaryContent.setText(summary.getContent());
        } else {
            textViewSummaryContent.setVisibility(View.GONE);
        }
    }

    /**
     * 生成总结
     */
    private void generateSummary() {
        // 显示进度条
        layoutProgress.setVisibility(View.VISIBLE);
        layoutGenerateStage.setVisibility(View.GONE);
        textViewProgress.setText(R.string.generating_summary);

        // 调用DeepSeek API生成总结 - 使用合集总结方法
        DeepseekUtils.generateEntrySummary(formattedNotes, entryName, new DeepseekUtils.DeepseekCallback() {
            @Override
            public void onSuccess(String summaryContent) {
                // 保存总结到数据库
                saveSummary(summaryContent, formattedNotes);

                // 更新UI
                layoutProgress.setVisibility(View.GONE);
                layoutGenerateStage.setVisibility(View.VISIBLE);
                textViewSummaryContent.setVisibility(View.VISIBLE);
                textViewSummaryContent.setText(summaryContent);

                Toast.makeText(SummaryActivity.this,
                        R.string.summary_generated, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMsg) {
                layoutProgress.setVisibility(View.GONE);
                layoutGenerateStage.setVisibility(View.VISIBLE);
                Toast.makeText(SummaryActivity.this,
                        getString(R.string.summary_generation_failed) + ": " + errorMsg,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 按日期格式化笔记文本
     */
    private String formatNotesText(List<Video> videos) {
        // 为了更好地排序，使用TreeMap按日期分组
        TreeMap<Date, List<String>> notesByDate = new TreeMap<>(Collections.reverseOrder());

        // 收集所有笔记，按日期分组
        for (Video video : videos) {
            if (video.getNotes() != null && !video.getNotes().isEmpty()) {
                Date recordDate = video.getRecordedAt();
                if (recordDate == null) {
                    recordDate = new Date(0); // 未知日期放到最后
                }

                if (!notesByDate.containsKey(recordDate)) {
                    notesByDate.put(recordDate, new ArrayList<>());
                }

                notesByDate.get(recordDate).add(video.getNotes());
            }
        }

        // 构建格式化的笔记文本
        StringBuilder builder = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Date date : notesByDate.keySet()) {
            String dateStr = date.getTime() == 0 ? "未知日期" : dateFormat.format(date);
            builder.append("===== ").append(dateStr).append(" =====\n\n");

            List<String> notes = notesByDate.get(date);
            for (String note : notes) {
                builder.append(note).append("\n\n");
            }

            builder.append("\n");
        }

        return builder.toString();
    }

    /**
     * 保存总结到数据库
     */
    private void saveSummary(String summaryContent, String rawText) {
        // 在后台线程中保存
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 创建新的总结记录
                Summary summary = new Summary(entryId, null, summaryContent, rawText);

                // 添加到数据库
                long id = summaryDao.insert(summary);

                Log.d(TAG, "Summary saved with ID: " + id);
            } catch (Exception e) {
                Log.e(TAG, "Error saving summary: ", e);
            }
        });
    }

    /**
     * 显示总结详情对话框
     */
    private void showSummaryDetailDialog(Summary summary) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_summary_detail, null);

        TextView textViewDialogTitle = dialogView.findViewById(R.id.textViewDialogTitle);
        TextView textViewDialogDate = dialogView.findViewById(R.id.textViewDialogDate);
        TextView textViewDialogContent = dialogView.findViewById(R.id.textViewDialogContent);
        TextView textViewDialogRawText = dialogView.findViewById(R.id.textViewDialogRawText);

        // 设置标题，如果没有则使用默认标题
        String title = summary.getTitle();
        if (title == null || title.isEmpty()) {
            title = "总结 #" + summary.getId();
        }
        textViewDialogTitle.setText(title);

        // 格式化日期
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String dateStr = summary.getCreatedAt() != null ?
                dateFormat.format(summary.getCreatedAt()) : "未知日期";
        textViewDialogDate.setText(dateStr);

        // 设置内容
        String content = summary.getContent();
        if (content != null && !content.isEmpty()) {
            textViewDialogContent.setText(content);
        } else {
            textViewDialogContent.setText("无内容");
        }

        // 设置原始笔记
        String rawText = summary.getRawText();
        if (rawText != null && !rawText.isEmpty()) {
            textViewDialogRawText.setText(rawText);
        } else {
            textViewDialogRawText.setText("无原始笔记");
        }

        // 创建并显示对话框
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.close, null)
                .create()
                .show();
    }

    @Override
    public void onSummaryClick(Summary summary) {
        showSummaryDetailDialog(summary);
    }


    /**
     * 打开全屏编辑器
     */
    private void openFullscreenEditor() {
        // 如果尚未加载笔记内容，先加载
        if (!hasLoadedNotes) {
            Toast.makeText(this, R.string.loading_notes, Toast.LENGTH_SHORT).show();
            previewNotes();
            return;
        }

        // 获取当前编辑区的内容（已编辑或原始）
        String currentContent = notesEdited ? formattedNotes : originalFormattedNotes;

        // 启动全屏编辑器活动
        Intent intent = new Intent(this, FullscreenEditorActivity.class);
        intent.putExtra(FullscreenEditorActivity.EXTRA_TEXT_CONTENT, currentContent);
        startActivityForResult(intent, REQUEST_FULLSCREEN_EDIT);
    }

    /**
     * 处理从全屏编辑器返回的结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FULLSCREEN_EDIT && resultCode == RESULT_OK && data != null) {
            // 获取编辑后的内容
            String editedContent = data.getStringExtra(FullscreenEditorActivity.EXTRA_EDITED_CONTENT);
            if (editedContent != null && !editedContent.isEmpty()) {
                // 更新编辑内容
                formattedNotes = editedContent;
                notesEdited = true;

                // 如果当前在预览模式，更新预览内容
                if (layoutPreview.getVisibility() == View.VISIBLE) {
                    editTextNotesPreview.setText(formattedNotes);
                }

                // 启用生成总结按钮
                buttonGenerateSummary.setEnabled(true);
            }
        }
    }
}