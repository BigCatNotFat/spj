package com.example.spj.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.spj.R;
import com.example.spj.model.Video;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量导出工具类
 */
public class BatchExportUtils {

    private static final String TAG = "BatchExportUtils";
    private static final int MAX_CONCURRENT_EXPORTS = 3;

    /**
     * 导出结果回调接口
     */
    public interface ExportCallback {
        void onExportProgress(int processed, int total);
        void onExportComplete(int succeeded, int failed);
    }

    /**
     * 批量导出视频到相册
     */
    public static void exportVideosToGallery(Context context, List<Video> videos, ExportCallback callback) {
        if (videos == null || videos.isEmpty()) {
            if (callback != null) {
                callback.onExportComplete(0, 0);
            }
            return;
        }

        // 创建进度对话框
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(R.string.exporting_videos);
        progressDialog.setMessage(context.getString(R.string.processing_videos));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(videos.size());
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 使用线程池限制并发导出数量
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_EXPORTS);

        // 跟踪进度
        final AtomicInteger processed = new AtomicInteger(0);
        final AtomicInteger succeeded = new AtomicInteger(0);
        final List<String> failedVideos = new ArrayList<>();

        // 处理结果的Handler
        final Handler handler = new Handler(Looper.getMainLooper());

        // 为每个视频创建导出任务
        for (Video video : videos) {
            executor.execute(() -> {
                boolean success = false;
                try {
                    // 导出视频
                    Uri exportedUri = MediaUtils.exportVideoToGallery(context, video.getFilePath());
                    success = (exportedUri != null);

                    if (success) {
                        succeeded.incrementAndGet();
                    } else {
                        synchronized (failedVideos) {
                            failedVideos.add(video.getFilePath());
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error exporting video: " + video.getFilePath(), e);
                    synchronized (failedVideos) {
                        failedVideos.add(video.getFilePath());
                    }
                } finally {
                    // 更新进度
                    final int current = processed.incrementAndGet();
                    handler.post(() -> {
                        progressDialog.setProgress(current);
                        if (callback != null) {
                            callback.onExportProgress(current, videos.size());
                        }

                        // 检查是否全部完成
                        if (current >= videos.size()) {
                            progressDialog.dismiss();
                            if (callback != null) {
                                callback.onExportComplete(succeeded.get(), failedVideos.size());
                            }
                        }
                    });
                }
            });
        }

        // 关闭线程池（不接受新任务，但会完成已提交的任务）
        executor.shutdown();
    }
}