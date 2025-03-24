package com.example.spj.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 相机功能工具类
 */
public class CameraUtils {

    private static final String TAG = "CameraUtils";

    /**
     * 启动视频录制（使用完整系统相机）
     *
     * @param activity      活动
     * @param videoFile     视频文件
     * @param requestCode   请求码
     */
    public static void startVideoCapture(Activity activity, File videoFile, int requestCode) {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        // 确保有相机应用处理该Intent
        if (takeVideoIntent.resolveActivity(activity.getPackageManager()) != null) {
            // 创建视频文件的URI
            Uri videoUri = FileUtils.getUriForFile(activity, videoFile);

            Log.d(TAG, "Starting system camera with URI: " + videoUri);

            // 将文件URI设置为Extra
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);

            // 不设置视频质量限制，让系统相机应用使用默认设置
            // 这样可以使用所有系统相机功能，包括高倍变焦

            // 授予URI写入权限
            takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // 启动系统相机应用
            activity.startActivityForResult(takeVideoIntent, requestCode);
        } else {
            Log.e(TAG, "No camera app available to handle ACTION_VIDEO_CAPTURE intent");
        }
    }

    /**
     * 从视频文件生成缩略图
     *
     * @param videoPath       视频路径
     * @param thumbnailPath   缩略图保存路径
     * @return 是否成功
     */
    public static boolean generateThumbnail(String videoPath, String thumbnailPath) {
        Log.d(TAG, "Generating thumbnail from video: " + videoPath);
        Log.d(TAG, "Thumbnail will be saved to: " + thumbnailPath);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        FileOutputStream out = null;
        boolean success = false;

        try {
            retriever.setDataSource(videoPath);

            // 从第一帧获取缩略图
            Bitmap bitmap = retriever.getFrameAtTime(0);

            if (bitmap != null) {
                Log.d(TAG, "Retrieved frame from video, bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // 保存缩略图到文件
                out = new FileOutputStream(thumbnailPath);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                success = true;

                Log.d(TAG, "Thumbnail saved successfully");
            } else {
                Log.e(TAG, "Failed to retrieve frame from video");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate thumbnail: ", e);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                retriever.release();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: ", e);
            }
        }

        return success;
    }
}