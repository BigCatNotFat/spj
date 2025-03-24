package com.example.spj.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.spj.R;
import com.example.spj.model.Video;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    private static final String TAG = "VideoAdapter";
    private List<Video> videos;
    private List<Video> selectedVideos;
    private OnVideoClickListener videoClickListener;
    private OnVideoLongClickListener videoLongClickListener;
    private OnMultiSelectListener multiSelectListener;
    private boolean isMultiSelectMode = false;
    private boolean isInSwipeSelectionMode = false;
    private boolean isSelectingItems = true; // true表示选中, false表示取消选中

    public VideoAdapter() {
        this.videos = new ArrayList<>();
        this.selectedVideos = new ArrayList<>();
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        if (position >= 0 && position < videos.size()) {
            Video video = videos.get(position);
            holder.bind(video);
        }
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos != null ? videos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.videoClickListener = listener;
    }

    public void setOnVideoLongClickListener(OnVideoLongClickListener listener) {
        this.videoLongClickListener = listener;
    }

    public void setOnMultiSelectListener(OnMultiSelectListener listener) {
        this.multiSelectListener = listener;
    }

    public void startMultiSelectMode() {
        if (!isMultiSelectMode) {
            isMultiSelectMode = true;
            selectedVideos.clear();
            notifyDataSetChanged();
            if (multiSelectListener != null) {
                multiSelectListener.onMultiSelectModeChanged(true);
            }
        }
    }

    public void exitMultiSelectMode() {
        if (isMultiSelectMode) {
            isMultiSelectMode = false;
            selectedVideos.clear();
            notifyDataSetChanged();
            if (multiSelectListener != null) {
                multiSelectListener.onMultiSelectModeChanged(false);
            }
        }
    }

    public void selectAll() {
        if (isMultiSelectMode) {
            selectedVideos.clear();
            selectedVideos.addAll(videos);
            notifyDataSetChanged();
            notifySelectionChanged();
        }
    }

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public List<Video> getSelectedVideos() {
        return new ArrayList<>(selectedVideos);
    }

    public int getSelectedCount() {
        return selectedVideos.size();
    }

    // 添加处理滑动选择的方法
    public void handleSwipeSelection(Video video) {
        if (isMultiSelectMode && video != null) {
            if (isSelectingItems) {
                // 选择模式：如果还未选中则选中
                if (!selectedVideos.contains(video)) {
                    selectedVideos.add(video);
                    int position = videos.indexOf(video);
                    if (position != -1) {
                        notifyItemChanged(position);
                    }
                    notifySelectionChanged();
                }
            } else {
                // 取消选择模式：如果已选中则取消
                if (selectedVideos.contains(video)) {
                    selectedVideos.remove(video);
                    int position = videos.indexOf(video);
                    if (position != -1) {
                        notifyItemChanged(position);
                    }
                    notifySelectionChanged();
                }
            }
        }
    }

    // 新增：添加公共方法用于切换视频选择状态
    public void toggleVideoSelection(Video video) {
        if (isMultiSelectMode && video != null) {
            if (selectedVideos.contains(video)) {
                selectedVideos.remove(video);
            } else {
                selectedVideos.add(video);
            }
            int position = videos.indexOf(video);
            if (position != -1) {
                notifyItemChanged(position);
            }
            notifySelectionChanged();
        }
    }

    private void toggleSelection(Video video) {
        if (video != null) {
            if (selectedVideos.contains(video)) {
                selectedVideos.remove(video);
            } else {
                selectedVideos.add(video);
            }
            notifySelectionChanged();
        }
    }

    private void notifySelectionChanged() {
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(selectedVideos.size());
        }
    }

    // 提供访问特定索引的Video对象的方法
    public Video getVideoAt(int position) {
        if (position >= 0 && position < videos.size()) {
            return videos.get(position);
        }
        return null;
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageViewThumbnail;
        private final ImageView imageViewPlay;
        private final TextView textViewDuration; // 新增视频时长显示
        private final TextView textViewDate;
        private final TextView textViewNotes;
        private final View viewSelectionOverlay;
        private final CheckBox checkBoxSelected;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewThumbnail = itemView.findViewById(R.id.imageViewThumbnail);
            imageViewPlay = itemView.findViewById(R.id.imageViewPlay);
            textViewDuration = itemView.findViewById(R.id.textViewDuration); // 初始化时长控件
            textViewDate = itemView.findViewById(R.id.textViewDate);
            textViewNotes = itemView.findViewById(R.id.textViewNotes);
            viewSelectionOverlay = itemView.findViewById(R.id.viewSelectionOverlay);
            checkBoxSelected = itemView.findViewById(R.id.checkBoxSelected);

            // 添加触摸事件监听器以支持滑动选择
            itemView.setOnTouchListener((v, event) -> {
                if (!isMultiSelectMode) {
                    return false; // 非多选模式下不处理
                }

                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }

                // 添加边界检查，防止索引越界
                if (position < 0 || position >= videos.size()) {
                    return false;
                }

                Video video = videos.get(position);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 长按处理已在onLongClick中实现
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        if (isInSwipeSelectionMode) {
                            // 已在滑动选择模式，处理当前项目的选择/取消
                            handleSwipeSelection(video);
                            return true;
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isInSwipeSelectionMode) {
                            isInSwipeSelectionMode = false;
                            return true;
                        }
                        return false;
                }
                return false;
            });

            // 修改原有的点击处理以支持滑动选择
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < videos.size()) {
                    Video video = videos.get(position);
                    if (isMultiSelectMode) {
                        // 选择或取消选择当前项目
                        toggleSelection(video);
                        notifyItemChanged(position);
                    } else if (videoClickListener != null) {
                        videoClickListener.onVideoClick(video);
                    }
                }
            });

            // 修改长按处理以启动滑动选择模式，增加边界检查
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < videos.size()) {
                    Video video = videos.get(position);

                    if (isMultiSelectMode) {
                        // 已经在多选模式，启动滑动选择
                        isInSwipeSelectionMode = true;
                        isSelectingItems = !selectedVideos.contains(video); // 根据当前项决定是选择还是取消
                        handleSwipeSelection(video);
                        return true;
                    } else if (videoLongClickListener != null) {
                        return videoLongClickListener.onVideoLongClick(video);
                    }
                }
                return false;
            });
        }

        void bind(Video video) {
            if (video == null) {
                Log.e(TAG, "尝试绑定空的视频对象");
                return;
            }

            try {
                // 设置默认缩略图
                imageViewThumbnail.setImageResource(R.drawable.ic_videocam);

                // 加载缩略图 - 使用更安全的方式
                if (video.getThumbnailPath() != null && !video.getThumbnailPath().isEmpty()) {
                    File thumbnailFile = new File(video.getThumbnailPath());
                    if (thumbnailFile.exists()) {
                        Context context = itemView.getContext();
                        if (context != null) {
                            try {
                                // 使用更安全的Glide配置
                                RequestOptions options = new RequestOptions()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .centerCrop()
                                        .error(R.drawable.ic_videocam);

                                Glide.with(context)
                                        .load(thumbnailFile)
                                        .apply(options)
                                        .into(imageViewThumbnail);
                            } catch (Exception e) {
                                Log.e(TAG, "Glide加载缩略图失败: " + e.getMessage());
                                imageViewThumbnail.setImageResource(R.drawable.ic_videocam);
                            }
                        }
                    }
                }

                // 设置录制日期
                Date recordedDate = video.getRecordedAt();
                if (recordedDate != null) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    String dateStr = dateFormat.format(recordedDate);
                    textViewDate.setText(dateStr);
                } else {
                    textViewDate.setText("未知日期");
                }

                // 显示或隐藏播放按钮（图片不需要播放按钮）
                imageViewPlay.setVisibility(video.isImage() ? View.GONE : View.VISIBLE);

                // 显示或隐藏视频时长（新增）
                if (video.isVideo() && video.getDuration() > 0) {
                    long durationMs = video.getDuration();
                    long minutes = (durationMs / 1000) / 60;
                    long seconds = (durationMs / 1000) % 60;
                    String durationStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
                    textViewDuration.setText(durationStr);
                    textViewDuration.setVisibility(View.VISIBLE);
                } else {
                    textViewDuration.setVisibility(View.GONE);
                }

                // 显示或隐藏注释
                if (video.getNotes() != null && !video.getNotes().isEmpty()) {
                    textViewNotes.setVisibility(View.VISIBLE);
                    textViewNotes.setText(video.getNotes());
                } else {
                    textViewNotes.setVisibility(View.GONE);
                }

                // 设置选择状态
                if (isMultiSelectMode) {
                    checkBoxSelected.setVisibility(View.VISIBLE);
                    viewSelectionOverlay.setVisibility(selectedVideos.contains(video) ? View.VISIBLE : View.GONE);
                    checkBoxSelected.setChecked(selectedVideos.contains(video));
                } else {
                    checkBoxSelected.setVisibility(View.GONE);
                    viewSelectionOverlay.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "视频项绑定过程中发生错误: " + e.getMessage());
            }
        }
    }

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    public interface OnVideoLongClickListener {
        boolean onVideoLongClick(Video video);
    }

    public interface OnMultiSelectListener {
        void onMultiSelectModeChanged(boolean active);
        void onSelectionChanged(int count);
    }
}