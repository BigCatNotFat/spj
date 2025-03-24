package com.example.spj.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.Manifest;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件处理工具类
 */
public class FileUtils {

    private static final String TAG = "FileUtils";
    private static final String FILE_PROVIDER_AUTHORITY = "com.example.spj.fileprovider";

    /**
     * 创建视频文件
     */
    public static File createVideoFile(Context context, int entryId) throws IOException {
        // 创建文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "VIDEO_" + entryId + "_" + timeStamp;

        // 适配Android不同版本的存储目录
        File storageDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android 10及以上，使用应用专属目录
            storageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "SPJ");
        } else {
            // 对于老版本Android，使用标准目录
            storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        }

        // 确保目录存在
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            if (!created) {
                Log.e(TAG, "创建目录失败: " + storageDir.getAbsolutePath());
                throw new IOException("无法创建存储目录");
            }
        }

        // 记录目录状态
        Log.d(TAG, "视频存储目录: " + storageDir.getAbsolutePath());
        Log.d(TAG, "目录存在: " + storageDir.exists() + ", 可写: " + storageDir.canWrite());

        // 创建视频文件
        File videoFile = new File(storageDir, videoFileName + ".mp4");
        boolean created = videoFile.createNewFile();
        Log.d(TAG, "视频文件创建" + (created ? "成功" : "失败") + ": " + videoFile.getAbsolutePath());

        if (!created) {
            // 如果创建失败，尝试使用createTempFile方法
            Log.d(TAG, "尝试使用createTempFile创建文件");
            videoFile = File.createTempFile(
                    videoFileName,  // 前缀
                    ".mp4",         // 后缀
                    storageDir      // 目录
            );
        }

        return videoFile;
    }
    /**
     * 创建图片文件
     */
    public static File createImageFile(Context context, int entryId) throws IOException {
        // 创建文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + entryId + "_" + timeStamp;

        // 适配Android不同版本的存储目录
        File storageDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android 10及以上，使用应用专属目录
            storageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SPJ");
        } else {
            // 对于老版本Android，使用标准目录
            storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }

        // 确保目录存在
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            if (!created) {
                Log.e(TAG, "创建目录失败: " + storageDir.getAbsolutePath());
                throw new IOException("无法创建存储目录");
            }
        }

        // 记录目录状态
        Log.d(TAG, "图片存储目录: " + storageDir.getAbsolutePath());
        Log.d(TAG, "目录存在: " + storageDir.exists() + ", 可写: " + storageDir.canWrite());

        // 创建图片文件
        File imageFile = new File(storageDir, imageFileName + ".jpg");
        boolean created = imageFile.createNewFile();
        Log.d(TAG, "图片文件创建" + (created ? "成功" : "失败") + ": " + imageFile.getAbsolutePath());

        if (!created) {
            // 如果创建失败，尝试使用createTempFile方法
            Log.d(TAG, "尝试使用createTempFile创建文件");
            imageFile = File.createTempFile(
                    imageFileName,  // 前缀
                    ".jpg",         // 后缀
                    storageDir      // 目录
            );
        }

        return imageFile;
    }
    /**
     * 创建视频缩略图文件
     */
    public static File createThumbnailFile(Context context, int entryId) throws IOException {
        // 创建文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String thumbnailFileName = "THUMB_" + entryId + "_" + timeStamp;

        // 适配Android不同版本的存储目录
        File storageDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android 10及以上，使用应用专属目录
            storageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SPJ");
        } else {
            // 对于老版本Android，使用标准目录
            storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }

        // 确保目录存在
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            if (!created) {
                Log.e(TAG, "创建目录失败: " + storageDir.getAbsolutePath());
                throw new IOException("无法创建存储目录");
            }
        }

        // 记录目录状态
        Log.d(TAG, "缩略图存储目录: " + storageDir.getAbsolutePath());
        Log.d(TAG, "目录存在: " + storageDir.exists() + ", 可写: " + storageDir.canWrite());

        // 创建缩略图文件
        File thumbnailFile = new File(storageDir, thumbnailFileName + ".jpg");
        boolean created = thumbnailFile.createNewFile();
        Log.d(TAG, "缩略图文件创建" + (created ? "成功" : "失败") + ": " + thumbnailFile.getAbsolutePath());

        if (!created) {
            // 如果创建失败，尝试使用createTempFile方法
            Log.d(TAG, "尝试使用createTempFile创建文件");
            thumbnailFile = File.createTempFile(
                    thumbnailFileName,  // 前缀
                    ".jpg",             // 后缀
                    storageDir          // 目录
            );
        }

        return thumbnailFile;
    }

    /**
     * 获取视频时长
     */
    public static long getVideoDuration(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(time);
        } catch (Exception e) {
            Log.e(TAG, "获取视频时长失败: ", e);
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                Log.e(TAG, "释放MediaMetadataRetriever异常: ", e);
            }
        }
    }

    /**
     * 获取文件大小
     */
    public static long getFileSize(String filePath) {
        File file = new File(filePath);
        return file.exists() ? file.length() : 0;
    }

    /**
     * 获取文件Uri
     */
    public static Uri getUriForFile(Context context, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file);
            Log.d(TAG, "文件URI: " + uri);
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "获取文件URI失败: ", e);
            throw new IllegalArgumentException("无法获取" + file.getAbsolutePath() + "的URI", e);
        }
    }

    /**
     * 删除文件
     */
    public static boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(filePath);
        boolean deleted = file.exists() && file.delete();
        Log.d(TAG, "删除文件" + (deleted ? "成功" : "失败") + ": " + filePath);
        return deleted;
    }

    /**
     * 检查存储权限和可用性
     */
    public static boolean checkStorageAvailability(Context context) {
        try {
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (externalFilesDir == null) {
                Log.e(TAG, "外部存储不可用");
                return false;
            }

            boolean canWrite = externalFilesDir.canWrite();
            Log.d(TAG, "外部存储可写: " + canWrite);

            if (!canWrite) {
                return false;
            }

            // 尝试创建测试文件
            File testFile = new File(externalFilesDir, "storage_test.tmp");
            boolean testCreated = testFile.createNewFile();
            if (testCreated) {
                testFile.delete();
                Log.d(TAG, "成功创建测试文件");
                return true;
            } else {
                // 如果文件已存在，也认为存储可用
                if (testFile.exists()) {
                    Log.d(TAG, "测试文件已存在");
                    return true;
                }
                Log.e(TAG, "无法创建测试文件");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "检查存储可用性异常: ", e);
            return false;
        }
    }

    /**
     * 从Uri复制内容到文件
     *
     * @param context 上下文
     * @param sourceUri 源Uri
     * @param destFile 目标文件
     * @return 是否复制成功
     */
    public static boolean copyUriToFile(Context context, Uri sourceUri, File destFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI: " + sourceUri);
                return false;
            }

            OutputStream outputStream = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            Log.d(TAG, "Successfully copied content from URI to file: " + destFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying from URI to file: ", e);
            return false;
        }
    }

    /**
     * 打开文件输入流
     *
     * @param filePath 文件路径
     * @return 输入流，失败返回null
     */
    public static InputStream openFileInputStream(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                return new FileInputStream(file);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening file input stream: ", e);
        }
        return null;
    }

    /**
     * 检查并请求存储权限
     *
     * @param activity 活动
     * @param requestCode 请求码
     * @return 是否已有权限
     */
    public static boolean checkStoragePermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 检查READ_MEDIA_VIDEO权限
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO},
                        requestCode);
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12: 检查READ_EXTERNAL_STORAGE权限
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        requestCode);
                return false;
            }
        } else {
            // Android 9及以下: 检查READ_EXTERNAL_STORAGE和WRITE_EXTERNAL_STORAGE权限
            boolean readPermission = ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean writePermission = ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            if (!readPermission || !writePermission) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        requestCode);
                return false;
            }
        }

        return true;
    }
    // 需要在 FileUtils 类中添加以下方法

    /**
     * 获取合集目录
     */
    public static File getEntryDirectory(Context context, int entryId) {
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(storageDir, "entry_" + entryId);
    }

    /**
     * 递归删除目录
     */
    public static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
        return false;
    }
}