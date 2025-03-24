package com.example.spj.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.FullscreenEditorActivity;
import com.example.spj.R;
import com.example.spj.database.AppDatabase;
import com.example.spj.model.Video;
import com.example.spj.util.DeepseekUtils;
import com.example.spj.util.XunfeiASR;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SectionedVideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements VideoAdapter.OnMultiSelectListener {
    private static final String TAG = "SectionedAdapter";
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;
    public static final int TYPE_ITEM_FLIPPED = 2;  // 新增：翻转的卡片类型
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // 毫秒

    private VideoAdapter videoAdapter;
    private List<String> sections;
    private Map<String, List<Video>> sectionedVideos;

    // 自定义接口，解决接口依赖问题
    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    public interface OnVideoLongClickListener {
        boolean onVideoLongClick(Video video);
    }

    private OnVideoClickListener videoClickListener;
    private OnVideoLongClickListener videoLongClickListener;
    private final VideoAdapter.OnMultiSelectListener multiSelectListener;

    // 记录每个项目的最后点击时间
    private Map<Integer, Long> lastClickTimes = new HashMap<>();

    // 双击处理相关字段
    private Handler doubleClickHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSingleClickRunnable;
    private int pendingClickPosition = -1;

    // 跟踪哪些卡片是翻转状态（展示背面）
    private Set<Integer> flippedPositions = new HashSet<>();

    // Track original positions and maintain a position mapping
    private List<Integer> positionMapping;

    public SectionedVideoAdapter(VideoAdapter.OnMultiSelectListener listener) {
        this.videoAdapter = new VideoAdapter();
        this.videoAdapter.setOnMultiSelectListener(this);
        this.sections = new ArrayList<>();
        this.sectionedVideos = new HashMap<>();
        this.multiSelectListener = listener;
        this.positionMapping = new ArrayList<>();
    }

    public VideoAdapter getVideoAdapter() {
        return videoAdapter;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_ITEM_FLIPPED) {
            // 创建翻转后的视图(卡片背面)
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_video_back, parent, false);
            return new FlippedVideoViewHolder(view);
        } else {
            // 获取原始ViewHolder
            VideoAdapter.VideoViewHolder holder =
                    (VideoAdapter.VideoViewHolder) videoAdapter.onCreateViewHolder(parent, viewType);

            // 移除原始点击监听器（可能使用了错误的位置）
            holder.itemView.setOnClickListener(null);

            // 设置我们自己的点击监听器，使用正确的位置处理并添加双击检测
            holder.itemView.setOnClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                // 获取实际视频
                Video video = getVideoAtPosition(position);
                if (video == null) {
                    Log.e(TAG, "位置 " + position + " 的视频为空");
                    return;
                }

                // 检查是否是双击
                long clickTime = System.currentTimeMillis();
                Long lastClickTime = lastClickTimes.get(position);

                if (lastClickTime != null && clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    // 是双击 - 取消任何待处理的单击操作
                    if (pendingSingleClickRunnable != null && pendingClickPosition == position) {
                        doubleClickHandler.removeCallbacks(pendingSingleClickRunnable);
                        pendingSingleClickRunnable = null;
                        pendingClickPosition = -1;
                    }

                    // 执行双击操作 - 翻转卡片
                    flipCard(v, position, video);
                    lastClickTimes.remove(position); // 重置以防止连续触发
                } else {
                    // 这是潜在双击的第一次点击
                    lastClickTimes.put(position, clickTime);

                    // 取消任何现有的待处理点击
                    if (pendingSingleClickRunnable != null) {
                        doubleClickHandler.removeCallbacks(pendingSingleClickRunnable);
                    }

                    // 创建新的待处理单击操作
                    final Video clickedVideo = video;
                    pendingClickPosition = position;
                    pendingSingleClickRunnable = () -> {
                        // 在双击窗口后执行单击操作
                        if (videoAdapter.isMultiSelectMode()) {
                            // 多选模式下，切换选择状态
                            videoAdapter.toggleVideoSelection(clickedVideo);
                            notifyItemChanged(pendingClickPosition);
                        } else if (videoClickListener != null) {
                            Log.d(TAG, "执行单击操作，对象为：" +
                                    (clickedVideo.isImage() ? "图片" : "视频") +
                                    ": " + clickedVideo.getFilePath());
                            videoClickListener.onVideoClick(clickedVideo);
                        }

                        pendingSingleClickRunnable = null;
                        pendingClickPosition = -1;
                    };

                    // 安排单击操作在双击窗口后执行
                    doubleClickHandler.postDelayed(pendingSingleClickRunnable, DOUBLE_CLICK_TIME_DELTA);
                }
            });

            // 长按也是同样的处理
            holder.itemView.setOnLongClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return false;

                Video video = getVideoAtPosition(position);
                if (video == null) return false;

                if (videoLongClickListener != null) {
                    return videoLongClickListener.onVideoLongClick(video);
                }
                return false;
            });

            return holder;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int itemType = getItemViewType(position);
        Log.d(TAG, "onBindViewHolder position=" + position + ", type=" + itemType);

        if (itemType == TYPE_HEADER) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            String dateSection = getSectionForPosition(position);
            if (dateSection != null) {
                headerHolder.textViewDateHeader.setText(dateSection);
            }
        } else if (itemType == TYPE_ITEM_FLIPPED) {
            // 绑定卡片背面数据
            FlippedVideoViewHolder flippedHolder = (FlippedVideoViewHolder) holder;
            Video video = getVideoAtPosition(position);
            if (video != null) {
                flippedHolder.bind(video, position);
            }
        } else {
            // 绑定卡片正面数据
            int videoPosition = getVideoPositionForAdapterPosition(position);
            if (videoPosition >= 0 && videoPosition < videoAdapter.getItemCount()) {
                videoAdapter.onBindViewHolder((VideoAdapter.VideoViewHolder) holder, videoPosition);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isSectionHeader(position)) {
            return TYPE_HEADER;
        } else if (flippedPositions.contains(position)) {
            return TYPE_ITEM_FLIPPED;
        }
        return TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return sections.size() + getVideoCount();
    }

    /**
     * 卡片翻转处理
     */
    private void flipCard(View view, int position, Video video) {
        Context context = view.getContext();

        // 检查位置是否已经翻转
        if (flippedPositions.contains(position)) {
            // 已经翻转，恢复正面
            flippedPositions.remove(position);
        } else {
            // 未翻转，翻到背面
            flippedPositions.add(position);
        }

        // 通知数据变化，触发重新绑定
        notifyItemChanged(position);
    }

    public void setVideos(List<Video> videos) {
        if (videos == null) {
            videos = new ArrayList<>();
        }

        Log.d(TAG, "setVideos called with " + videos.size() + " videos");

        // Clear position mapping and flipped positions
        positionMapping.clear();
        flippedPositions.clear();

        // 清除上次点击时间记录
        lastClickTimes.clear();

        // Group videos by date
        this.sectionedVideos = groupVideosByDate(videos);
        Log.d(TAG, "Grouped into " + sectionedVideos.size() + " sections");

        // Extract date sections and sort
        this.sections = new ArrayList<>(sectionedVideos.keySet());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.sections.sort((s1, s2) -> s2.compareTo(s1)); // Sort descending - newest first
        }

        // Create flattened video list for the underlying adapter
        List<Video> flattenedVideos = new ArrayList<>();

        // Rebuild position mapping
        int flatIndex = 0;
        for (String section : sections) {
            List<Video> sectionVideos = sectionedVideos.get(section);
            Log.d(TAG, "Section: " + section + " contains " + sectionVideos.size() + " videos");

            for (int i = 0; i < sectionVideos.size(); i++) {
                flattenedVideos.add(sectionVideos.get(i));
                // Store the mapping from sectioned position to flat position
                positionMapping.add(flatIndex);
                flatIndex++;
            }
        }

        Log.d(TAG, "Final flattened list has " + flattenedVideos.size() + " videos");
        videoAdapter.setVideos(flattenedVideos);
        notifyDataSetChanged();
    }

    // 设置接口监听器
    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.videoClickListener = listener;
    }

    public void setOnVideoLongClickListener(OnVideoLongClickListener listener) {
        this.videoLongClickListener = listener;
    }

    // Get Video object at a specific position
    public Video getVideoAtPosition(int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            Log.d(TAG, "尝试在标题位置获取视频: " + position);
            return null;
        }

        // 计算实际视频位置
        int videoIndex = getVideoPositionForAdapterPosition(position);

        if (videoIndex >= 0 && videoIndex < videoAdapter.getItemCount()) {
            Video video = videoAdapter.getVideoAt(videoIndex);
            if (video == null) {
                Log.e(TAG, "videoIndex " + videoIndex + " 的视频为空");
            }
            return video;
        }
        Log.e(TAG, "无效的 videoIndex: " + videoIndex);
        return null;
    }

    /**
     * 卡片背面的ViewHolder
     */
    /**
     * 卡片背面的ViewHolder
     */
    class FlippedVideoViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewBackTitle;
        private final TextView textViewBackDuration;
        private final TextView textViewLastEditedTimestamp;
        private final EditText editTextDetailedNotes;
        private final Button buttonSaveNotes;
        private final Button buttonRecognizeText;
        private final Button buttonAddTimestamp;
        private final Button buttonFullscreenEdit; // 新增全屏编辑按钮
        private final ImageButton buttonFlipBack;

        // 全屏编辑请求码
        private static final int REQUEST_FULLSCREEN_EDIT = 1001;

        // 新增进度控件
        private final LinearLayout layoutRecognitionProgress;
        private final TextView textViewProgressStatus;
        private final ProgressBar progressBarRecognition;

        FlippedVideoViewHolder(View itemView) {
            super(itemView);
            textViewBackTitle = itemView.findViewById(R.id.textViewBackTitle);
            textViewBackDuration = itemView.findViewById(R.id.textViewBackDuration);
            textViewLastEditedTimestamp = itemView.findViewById(R.id.textViewLastEditedTimestamp);
            editTextDetailedNotes = itemView.findViewById(R.id.editTextDetailedNotes);
            buttonSaveNotes = itemView.findViewById(R.id.buttonSaveNotes);
            buttonRecognizeText = itemView.findViewById(R.id.buttonRecognizeText);
            buttonAddTimestamp = itemView.findViewById(R.id.buttonAddTimestamp);
            buttonFullscreenEdit = itemView.findViewById(R.id.buttonFullscreenEdit); // 初始化全屏编辑按钮
            buttonFlipBack = itemView.findViewById(R.id.buttonFlipBack);

            // 初始化进度控件
            layoutRecognitionProgress = itemView.findViewById(R.id.layoutRecognitionProgress);
            textViewProgressStatus = itemView.findViewById(R.id.textViewProgressStatus);
            progressBarRecognition = itemView.findViewById(R.id.progressBarRecognition);

            // 保存笔记按钮点击事件
            buttonSaveNotes.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Video video = getVideoAtPosition(position);
                if (video == null) return;

                String notes = editTextDetailedNotes.getText().toString().trim();
                saveNotes(video, notes);
            });

            // 全屏编辑按钮点击事件
            buttonFullscreenEdit.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Video video = getVideoAtPosition(position);
                if (video == null) return;

                openFullscreenEditor(video);
            });

            // 修改后的识别文本按钮点击事件处理
            buttonRecognizeText.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Video video = getVideoAtPosition(position);
                if (video != null) {
                    // 检查权限
                    Context context = v.getContext();
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;

                        // Android 10 (API 29)以下需要存储权限
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            if (ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                // 请求权限
                                ActivityCompat.requestPermissions(activity,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        100); // 请求码
                                Toast.makeText(context, "需要存储权限来处理音频", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }

                    // 显示进度布局
                    layoutRecognitionProgress.setVisibility(View.VISIBLE);
                    textViewProgressStatus.setText("正在处理音频...");
                    progressBarRecognition.setIndeterminate(true);

                    // 禁用识别按钮，防止重复点击
                    buttonRecognizeText.setEnabled(false);

                    // 使用线程处理耗时操作
                    new Thread(() -> {
                        try {
                            // 分离音频
                            File audioFile = extractAudioFromVideo(video.getFilePath());

                            // 创建一个Handler用于在主线程更新UI
                            Handler mainHandler = new Handler(Looper.getMainLooper());

                            // 更新状态
                            mainHandler.post(() -> {
                                textViewProgressStatus.setText("音频分离成功，开始识别...");
                            });

                            // 科大讯飞API参数
                            String appId = "9d3a9de4"; // 替换为您的实际AppID
                            String secretKey = "333dc410a0da0b0e0dfa947bc484b2ae"; // 替换为您的实际Secret Key

                            // 创建XunfeiASR实例并开始识别
                            XunfeiASR asr = new XunfeiASR(appId, secretKey, audioFile);
                            asr.executeRecognition(new XunfeiASR.XunfeiASRCallback() {
                                @Override
                                public void onSuccess(String result) {
                                    // 解析返回的JSON结果
                                    try {
                                        JSONArray resultArray = new JSONArray(result);
                                        StringBuilder recognizedText = new StringBuilder();

                                        // 拼接所有识别的文本
                                        for (int i = 0; i < resultArray.length(); i++) {
                                            JSONObject item = resultArray.getJSONObject(i);
                                            if (item.has("onebest")) {
                                                recognizedText.append(item.getString("onebest")).append("\n");
                                            }
                                        }

                                        // 获取识别的文本
                                        final String recognizedContent = recognizedText.toString().trim();

                                        // 更新UI，显示正在生成摘要
                                        mainHandler.post(() -> {
                                            textViewProgressStatus.setText("识别完成，正在生成摘要...");
                                        });

                                        // 使用DeepSeek API生成摘要
                                        DeepseekUtils.generateSummary(recognizedContent, new DeepseekUtils.DeepseekCallback() {
                                            @Override
                                            public void onSuccess(String summary) {
                                                // 获取当前笔记内容
                                                String currentNotes = video.getNotes() != null ? video.getNotes() : "";

                                                // 添加摘要和识别结果到笔记
                                                String updatedNotes;
                                                if (currentNotes.isEmpty()) {
                                                    updatedNotes = "【" + summary + "】\n\n" + recognizedContent;
                                                } else {
                                                    updatedNotes = "【" + summary + "】\n\n" + recognizedContent + "\n\n" + currentNotes;
                                                }

                                                // 更新UI和保存笔记
                                                mainHandler.post(() -> {
                                                    // 隐藏进度布局
                                                    layoutRecognitionProgress.setVisibility(View.GONE);

                                                    // 更新EditText显示
                                                    editTextDetailedNotes.setText(updatedNotes);

                                                    // 保存笔记到数据库
                                                    saveNotes(video, updatedNotes);

                                                    // 显示简短的成功提示
                                                    Toast.makeText(v.getContext(),
                                                            "识别并生成摘要成功！", Toast.LENGTH_SHORT).show();

                                                    // 重新启用按钮
                                                    buttonRecognizeText.setEnabled(true);
                                                });
                                            }

                                            @Override
                                            public void onFailure(String errorMsg) {
                                                // 摘要生成失败，仅使用识别结果
                                                String currentNotes = video.getNotes() != null ? video.getNotes() : "";

                                                // 添加识别结果到笔记（无摘要）
                                                String updatedNotes;
                                                if (currentNotes.isEmpty()) {
                                                    updatedNotes = recognizedContent;
                                                } else {
                                                    updatedNotes = recognizedContent + "\n\n" + currentNotes;
                                                }

                                                mainHandler.post(() -> {
                                                    // 隐藏进度布局
                                                    layoutRecognitionProgress.setVisibility(View.GONE);

                                                    // 更新EditText显示
                                                    editTextDetailedNotes.setText(updatedNotes);

                                                    // 保存笔记到数据库
                                                    saveNotes(video, updatedNotes);

                                                    // 显示提示
                                                    Toast.makeText(v.getContext(),
                                                            "识别成功！但摘要生成失败: " + errorMsg, Toast.LENGTH_SHORT).show();

                                                    // 重新启用按钮
                                                    buttonRecognizeText.setEnabled(true);
                                                });
                                            }
                                        });

                                        // 音频文件识别完成后可以删除
                                        audioFile.delete();

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        mainHandler.post(() -> {
                                            // 隐藏进度布局
                                            layoutRecognitionProgress.setVisibility(View.GONE);

                                            // 显示错误信息
                                            Toast.makeText(v.getContext(),
                                                    "解析识别结果失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                                            // 重新启用按钮
                                            buttonRecognizeText.setEnabled(true);
                                        });
                                    }
                                }

                                @Override
                                public void onProgress(String progress) {
                                    // 更新进度信息
                                    mainHandler.post(() -> {
                                        textViewProgressStatus.setText("识别进度: " + progress);

                                        // 如果包含了完成状态的数字，可以尝试提取并更新进度条
                                        if (progress.contains("任务状态:")) {
                                            try {
                                                String statusStr = progress.replace("任务状态:", "").trim();
                                                int status = Integer.parseInt(statusStr);

                                                // 科大讯飞的任务状态通常是0-9，9表示完成
                                                if (status >= 0 && status <= 9) {
                                                    progressBarRecognition.setIndeterminate(false);
                                                    progressBarRecognition.setMax(9);
                                                    progressBarRecognition.setProgress(status);
                                                }
                                            } catch (NumberFormatException e) {
                                                // 忽略解析错误
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    // 显示错误信息
                                    mainHandler.post(() -> {
                                        // 隐藏进度布局
                                        layoutRecognitionProgress.setVisibility(View.GONE);

                                        // 显示错误信息
                                        Toast.makeText(v.getContext(),
                                                "识别失败: " + errorMsg, Toast.LENGTH_LONG).show();

                                        // 重新启用按钮
                                        buttonRecognizeText.setEnabled(true);
                                    });

                                    // 识别失败后删除临时音频文件
                                    audioFile.delete();
                                }
                            });

                        } catch (Exception e) {
                            e.printStackTrace();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                // 隐藏进度布局
                                layoutRecognitionProgress.setVisibility(View.GONE);

                                // 显示错误信息
                                Toast.makeText(v.getContext(),
                                        "处理失败：" + e.getMessage(), Toast.LENGTH_LONG).show();

                                // 重新启用按钮
                                buttonRecognizeText.setEnabled(true);
                            });
                        }
                    }).start();
                }
            });



            // 添加时间戳按钮点击事件
            buttonAddTimestamp.setOnClickListener(v -> {
                // 这里可以添加时间戳相关功能
                Toast.makeText(v.getContext(), "时间戳功能尚未实现", Toast.LENGTH_SHORT).show();
            });

            // 翻转回正面按钮点击事件
            buttonFlipBack.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Video video = getVideoAtPosition(position);
                if (video == null) return;

                // 翻转卡片回正面
                flipCard(itemView, position, video);
            });

            // 设置卡片双击事件（翻回正面）
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                Video video = getVideoAtPosition(position);
                if (video == null) return;

                // 检查是否是双击
                long clickTime = System.currentTimeMillis();
                Long lastClickTime = lastClickTimes.get(position);

                if (lastClickTime != null && clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    // 双击翻转回正面
                    flipCard(v, position, video);
                    lastClickTimes.remove(position);
                } else {
                    // 单击进入编辑模式（已在背面，直接编辑）
                    lastClickTimes.put(position, clickTime);
                    editTextDetailedNotes.requestFocus();
                }
            });
        }

        /**
         * 打开全屏编辑器
         */
        private void openFullscreenEditor(Video video) {
            Context context = itemView.getContext();
            if (!(context instanceof Activity)) {
                Toast.makeText(context, "无法打开编辑器", Toast.LENGTH_SHORT).show();
                return;
            }

            Activity activity = (Activity) context;

            // 获取当前编辑框中的笔记内容
            String currentNotes = editTextDetailedNotes.getText().toString();

            // 启动全屏编辑器活动
            Intent intent = new Intent(context, FullscreenEditorActivity.class);
            intent.putExtra(FullscreenEditorActivity.EXTRA_TEXT_CONTENT, currentNotes);
            activity.startActivityForResult(intent, REQUEST_FULLSCREEN_EDIT);

            // 保存当前视频的引用以便在活动结果中使用
            // 注意：正常情况下我们需要在活动中保存这个视频的引用
            // 但由于我们在适配器中，这里示意一种实现思路
            Tag tag = new Tag();
            tag.video = video;
            tag.position = getAdapterPosition();

            // 使用ViewTag保存，以便稍后在活动结果中检索
            itemView.setTag(R.id.tag_video_position, tag);
        }

        // 用于保存视频和位置的辅助类
        static class Tag {
            Video video;
            int position;
        }

        void bind(Video video, int position) {
            // 设置标题（日期）
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String dateStr = video.getRecordedAt() != null ?
                    dateFormat.format(video.getRecordedAt()) : "未知日期";
            textViewBackTitle.setText(dateStr);

            // 设置时长（如果是视频）
            if (video.isVideo()) {
                long durationMs = video.getDuration();
                long minutes = (durationMs / 1000) / 60;
                long seconds = (durationMs / 1000) % 60;
                String durationStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
                textViewBackDuration.setText(durationStr);
                textViewBackDuration.setVisibility(View.VISIBLE);
            } else {
                textViewBackDuration.setVisibility(View.GONE);
            }

            // 设置最后编辑时间（这里只是一个示例，实际上需要存储最后编辑时间）
            textViewLastEditedTimestamp.setText("笔记编辑");

            // 设置笔记内容
            if (!TextUtils.isEmpty(video.getNotes())) {
                editTextDetailedNotes.setText(video.getNotes());
            } else {
                editTextDetailedNotes.setText("");
                editTextDetailedNotes.setHint("双击添加详细笔记...");
            }

            // 确保进度布局是隐藏的
            layoutRecognitionProgress.setVisibility(View.GONE);
            // 确保按钮是可用的
            buttonRecognizeText.setEnabled(true);
        }
        /**
         * 从视频中提取音频文件 - 修改后的版本
         */
        private File extractAudioFromVideo(String videoPath) throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);

            // 查找音频轨道
            int audioTrackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                throw new IOException("未找到音频轨道");
            }

            // 使用应用特定目录而不是公共外部存储
            Context context = itemView.getContext();
            File outputDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "ExtractedAudio");

            // 确保目录存在
            if (!outputDir.exists()) {
                boolean dirCreated = outputDir.mkdirs();
                if (!dirCreated) {
                    Log.e(TAG, "无法创建目录: " + outputDir.getAbsolutePath());
                    // 尝试使用缓存目录作为备选
                    outputDir = new File(context.getCacheDir(), "ExtractedAudio");
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                }
            }

            File audioFile = new File(outputDir, System.currentTimeMillis() + ".aac");
            Log.d(TAG, "提取音频到: " + audioFile.getAbsolutePath());

            // 配置Muxer
            MediaMuxer muxer = new MediaMuxer(audioFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            extractor.selectTrack(audioTrackIndex);
            int writeAudioIndex = muxer.addTrack(format);
            muxer.start();

            // 读取和写入数据
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                bufferInfo.size = sampleSize;

                muxer.writeSampleData(writeAudioIndex, buffer, bufferInfo);
                extractor.advance();
            }

            // 释放资源
            muxer.stop();
            muxer.release();
            extractor.release();

            return audioFile;
        }

        /**
         * 保存笔记到数据库
         */
        private void saveNotes(Video video, String notes) {
            Context context = itemView.getContext();

            // 更新内存中的备注
            video.setNotes(notes);

            // 更新数据库
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase database = AppDatabase.getDatabase(context);
                    database.videoDao().update(video);

                    // 主线程中显示成功消息
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(context, "笔记已保存", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "保存笔记失败: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(context, "保存笔记失败", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textViewDateHeader;

        HeaderViewHolder(View itemView) {
            super(itemView);
            textViewDateHeader = itemView.findViewById(R.id.textViewDateHeader);
        }
    }

    // 实现接口方法
    @Override
    public void onMultiSelectModeChanged(boolean active) {
        if (multiSelectListener != null) {
            multiSelectListener.onMultiSelectModeChanged(active);
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(count);
        }
    }

    // Multi-select mode methods
    public void startMultiSelectMode() {
        videoAdapter.startMultiSelectMode();
        // 多选模式下，清除所有翻转状态
        flippedPositions.clear();
        notifyDataSetChanged();
    }

    public void exitMultiSelectMode() {
        videoAdapter.exitMultiSelectMode();
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() {
        return videoAdapter.isMultiSelectMode();
    }

    public void selectAll() {
        videoAdapter.selectAll();
    }

    public List<Video> getSelectedVideos() {
        return videoAdapter.getSelectedVideos();
    }

    public int getSelectedCount() {
        return videoAdapter.getSelectedCount();
    }

    // Handle swipe selection
    public void handleSwipeSelection(Video video) {
        if (videoAdapter != null) {
            videoAdapter.handleSwipeSelection(video);
        }
    }

    // Check if a position is a section header
    private boolean isSectionHeader(int position) {
        if (position < 0 || sections.isEmpty()) {
            return false;
        }

        int currentPos = 0;
        for (String section : sections) {
            if (position == currentPos) {
                return true;
            }

            List<Video> sectionVideos = sectionedVideos.get(section);
            if (sectionVideos != null) {
                currentPos += 1 + sectionVideos.size();
            } else {
                currentPos++;
            }
        }

        return false;
    }

    // Get section title for a position
    private String getSectionForPosition(int position) {
        if (position < 0 || sections.isEmpty()) {
            return null;
        }

        int currentPos = 0;
        for (String section : sections) {
            if (position == currentPos) {
                return section;
            }

            List<Video> sectionVideos = sectionedVideos.get(section);
            if (sectionVideos != null) {
                currentPos += 1 + sectionVideos.size();
            } else {
                currentPos++;
            }

            if (position < currentPos) {
                return section;
            }
        }

        return null;
    }

    // Get position of a section
    public int getSectionPosition(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= sections.size()) {
            return -1;
        }

        int position = 0;
        for (int i = 0; i < sectionIndex; i++) {
            String section = sections.get(i);
            List<Video> sectionVideos = sectionedVideos.get(section);
            if (sectionVideos != null) {
                position += 1 + sectionVideos.size();
            } else {
                position++;
            }
        }

        return position;
    }

    // Calculate adapter position to VideoAdapter position
    private int getVideoPositionForAdapterPosition(int adapterPosition) {
        if (adapterPosition < 0 || sections.isEmpty()) {
            return -1;
        }

        int headerCount = 0;
        int videoCount = 0;

        for (String section : sections) {
            // Skip the header
            if (adapterPosition == headerCount) {
                return -1; // It's a header
            }

            List<Video> sectionVideos = sectionedVideos.get(section);
            if (sectionVideos != null) {
                int sectionSize = sectionVideos.size();

                // Check if the position is in this section
                if (adapterPosition > headerCount && adapterPosition <= headerCount + sectionSize) {
                    return videoCount + (adapterPosition - headerCount - 1);
                }

                videoCount += sectionSize;
                headerCount += 1 + sectionSize;
            } else {
                headerCount++;
            }
        }

        return -1;
    }

    // Get total video count (excluding headers)
    private int getVideoCount() {
        int count = 0;
        for (List<Video> videos : sectionedVideos.values()) {
            if (videos != null) {
                count += videos.size();
            }
        }
        return count;
    }

    // Group videos by date
    private Map<String, List<Video>> groupVideosByDate(List<Video> videos) {
        Map<String, List<Video>> grouped = new TreeMap<>((s1, s2) -> s2.compareTo(s1)); // Sort descending
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Video video : videos) {
            Date recordedDate = video.getRecordedAt();
            String dateKey = recordedDate != null ? dateFormat.format(recordedDate) : "未知日期";

            if (!grouped.containsKey(dateKey)) {
                grouped.put(dateKey, new ArrayList<>());
            }
            grouped.get(dateKey).add(video);
        }

        return grouped;
    }

    /**
     * 处理全屏编辑器返回的结果
     *
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 意图数据
     * @param itemView 相关的视图项
     * @return 是否处理了结果
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data, View itemView) {
        if (requestCode == FlippedVideoViewHolder.REQUEST_FULLSCREEN_EDIT && resultCode == Activity.RESULT_OK && data != null) {
            // 获取编辑后的内容
            String editedContent = data.getStringExtra(FullscreenEditorActivity.EXTRA_EDITED_CONTENT);
            if (editedContent == null || editedContent.isEmpty()) {
                return false;
            }

            // 从视图标签中获取相关视频和位置
            FlippedVideoViewHolder.Tag tag = (FlippedVideoViewHolder.Tag) itemView.getTag(R.id.tag_video_position);
            if (tag == null || tag.video == null) {
                return false;
            }

            // 更新视频笔记
            Video video = tag.video;
            int position = tag.position;

            // 更新笔记内容
            video.setNotes(editedContent);

            // 保存到数据库
            Context context = itemView.getContext();
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase database = AppDatabase.getDatabase(context);
                    database.videoDao().update(video);

                    // 主线程中更新UI
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // 如果位置还有效，重新绑定ViewHolder
                        if (position != RecyclerView.NO_POSITION) {
                            notifyItemChanged(position);
                        }

                        // 显示成功消息
                        Toast.makeText(context, "笔记已保存", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "保存笔记失败: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(context, "保存笔记失败", Toast.LENGTH_SHORT).show();
                    });
                }
            });

            return true;
        }

        return false;
    }
}