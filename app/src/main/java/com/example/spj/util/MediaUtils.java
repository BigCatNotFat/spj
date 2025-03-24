package com.example.spj.util;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.system.Os;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 多媒体导入导出工具类
 */
public class MediaUtils {
    private static final String TAG = "MediaUtils";

    /**
     * 从URI导入视频到应用私有存储
     *
     * @param context  上下文
     * @param sourceUri 源视频URI
     * @param entryId  条目ID
     * @return 返回导入后的视频文件，失败则返回null
     */
    public static File importVideoFromUri(Context context, Uri sourceUri, int entryId) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        String fileName = getFileNameFromUri(contentResolver, sourceUri);
        String extension = getFileExtension(fileName);

        // 确保扩展名为视频格式
        if (extension == null || extension.isEmpty()) {
            extension = ".mp4"; // 默认视频扩展名
        }

        // 创建目标文件
        File destinationFile = FileUtils.createVideoFile(context, entryId);

        // 复制文件内容
        try (InputStream in = contentResolver.openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destinationFile)) {

            if (in == null) {
                throw new IOException("Cannot open input stream from URI: " + sourceUri);
            }

            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();

            Log.d(TAG, "Video imported successfully to: " + destinationFile.getAbsolutePath());
            return destinationFile;
        } catch (IOException e) {
            Log.e(TAG, "Error importing video: ", e);
            // 如果失败，删除部分创建的文件
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            throw e;
        }
    }
    /**
     * 从应用目录导出图片到公共媒体库
     *
     * @param context 上下文
     * @param sourceFilePath 源图片文件路径
     * @return 导出后的URI，失败则返回null
     */
    public static Uri exportImageToGallery(Context context, String sourceFilePath) throws IOException {
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            throw new IOException("源文件不存在或无法读取: " + sourceFilePath);
        }

        // 创建文件名
        String fileName = getExportFileName(sourceFile.getName(), ".jpg");

        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();

        // 设置基本属性
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        contentValues.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);

        // 根据Android版本设置存储路径
        Uri contentUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SPJ");
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
            contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            // Android 9及以下直接存到外部存储
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "SPJ");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory);
            }

            File destFile = new File(directory, fileName);
            contentValues.put(MediaStore.MediaColumns.DATA, destFile.getAbsolutePath());
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        // 执行插入操作获取目标URI
        Uri insertUri = resolver.insert(contentUri, contentValues);
        if (insertUri == null) {
            throw new IOException("Failed to create new MediaStore record.");
        }

        // 复制文件内容
        try (InputStream in = FileUtils.openFileInputStream(sourceFilePath);
             OutputStream out = resolver.openOutputStream(insertUri)) {

            if (in == null || out == null) {
                throw new IOException("Cannot open streams for copying");
            }

            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();

            // 对于Android 10及以上，完成后更新IS_PENDING状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(insertUri, contentValues, null, null);
            }

            Log.d(TAG, "Image exported successfully to gallery: " + insertUri);
            return insertUri;
        } catch (IOException e) {
            // 发生错误时删除创建的条目
            resolver.delete(insertUri, null, null);
            Log.e(TAG, "Error exporting image: ", e);
            throw e;
        }
    }

    /**
     * 根据文件类型生成导出文件名
     * @param originalName 原始文件名
     * @param defaultExtension 默认扩展名（如 .jpg, .mp4）
     * @return 导出文件名
     */
    private static String getExportFileName(String originalName, String defaultExtension) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String baseName = "SPJ_";

        // 如果原文件名格式合适，保留其部分信息
        if (originalName != null && !originalName.isEmpty()) {
            // 去除路径信息
            int lastSlash = originalName.lastIndexOf('/');
            if (lastSlash >= 0) {
                originalName = originalName.substring(lastSlash + 1);
            }

            // 去除扩展名
            int lastDot = originalName.lastIndexOf('.');
            if (lastDot >= 0) {
                originalName = originalName.substring(0, lastDot);
            }

            // 使用原文件名前缀
            if (originalName.length() > 0) {
                baseName = originalName + "_";
            }
        }

        return baseName + timeStamp + defaultExtension;
    }
    /**
     * 从应用目录导出视频到公共媒体库
     *
     * @param context  上下文
     * @param sourceFilePath 源视频文件路径
     * @return 导出后的URI，失败则返回null
     */
    public static Uri exportVideoToGallery(Context context, String sourceFilePath) throws IOException {
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            throw new IOException("Source file does not exist or cannot be read: " + sourceFilePath);
        }

        // 获取视频元数据
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(sourceFilePath);
        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        retriever.release();

        // 创建文件名
        String fileName = getExportFileName(sourceFile.getName());

        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();

        // 设置基本属性
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        contentValues.put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);

        if (duration != null) {
            contentValues.put(MediaStore.Video.Media.DURATION, Long.parseLong(duration));
        }

        if (width != null && height != null) {
            contentValues.put(MediaStore.Video.Media.WIDTH, Integer.parseInt(width));
            contentValues.put(MediaStore.Video.Media.HEIGHT, Integer.parseInt(height));
        }

        // 根据Android版本设置存储路径
        Uri contentUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SPJ");
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 1);
            contentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            // Android 9及以下直接存到外部存储
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), "SPJ");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory);
            }

            File destFile = new File(directory, fileName);
            contentValues.put(MediaStore.MediaColumns.DATA, destFile.getAbsolutePath());
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        // 执行插入操作获取目标URI
        Uri insertUri = resolver.insert(contentUri, contentValues);
        if (insertUri == null) {
            throw new IOException("Failed to create new MediaStore record.");
        }

        // 复制文件内容
        try (InputStream in = FileUtils.openFileInputStream(sourceFilePath);
             OutputStream out = resolver.openOutputStream(insertUri)) {

            if (in == null || out == null) {
                throw new IOException("Cannot open streams for copying");
            }

            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();

            // 对于Android 10及以上，完成后更新IS_PENDING状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                resolver.update(insertUri, contentValues, null, null);
            }

            Log.d(TAG, "Video exported successfully to gallery: " + insertUri);
            return insertUri;
        } catch (IOException e) {
            // 发生错误时删除创建的条目
            resolver.delete(insertUri, null, null);
            Log.e(TAG, "Error exporting video: ", e);
            throw e;
        }
    }

    /**
     * 从URI获取文件名
     */
    @SuppressLint("Range")
    private static String getFileNameFromUri(ContentResolver contentResolver, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from URI: ", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    /**
     * 生成导出文件名
     */
    private static String getExportFileName(String originalName) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String baseName = "SPJ_";

        // 如果原文件名格式合适，保留其部分信息
        if (originalName != null && !originalName.isEmpty()) {
            // 去除路径信息
            int lastSlash = originalName.lastIndexOf('/');
            if (lastSlash >= 0) {
                originalName = originalName.substring(lastSlash + 1);
            }

            // 去除扩展名
            int lastDot = originalName.lastIndexOf('.');
            if (lastDot >= 0) {
                originalName = originalName.substring(0, lastDot);
            }

            // 使用原文件名前缀
            if (originalName.length() > 0) {
                baseName = originalName + "_";
            }
        }

        return baseName + timeStamp + ".mp4";
    }

    /**
     * 获取视频的创建日期
     * @param context 上下文
     * @param videoUri 视频URI
     * @return 创建日期，如果无法获取则返回null
     */
    /**
     * 获取视频的创建日期
     * @param context 上下文
     * @param videoUri 视频URI
     * @return 创建日期，如果无法获取则返回null
     */
    public static Date getVideoCreationDate(Context context, Uri videoUri) {
        try {
            // 尝试从媒体数据库获取
            String[] projection = { MediaStore.Video.Media.DATE_TAKEN };
            Cursor cursor = context.getContentResolver().query(videoUri, projection, null, null, null);

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN);
                        long dateTaken = cursor.getLong(columnIndex);

                        if (dateTaken > 0) {
                            return new Date(dateTaken);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error getting date from media store: " + e.getMessage());
                } finally {
                    cursor.close();
                }
            }

            // 尝试从文件元数据获取
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);

            String date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            if (date != null && !date.isEmpty()) {
                try {
                    // 尝试解析元数据日期格式
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US);
                    return sdf.parse(date);
                } catch (ParseException e) {
                    Log.w(TAG, "Error parsing date from metadata: " + e.getMessage());
                }
            }

            // 如果前两种方法都失败，尝试获取文件上次修改时间
            if ("file".equals(videoUri.getScheme())) {
                File file = new File(videoUri.getPath());
                if (file.exists()) {
                    return new Date(file.lastModified());
                }
            } else {
                // 尝试获取文件描述符
                try {
                    ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(videoUri, "r");
                    if (pfd != null) {
                        try {
                            // 使用getFileDescriptor()获取FileDescriptor对象，而不是getFd()获取整数描述符
                            FileDescriptor fd = pfd.getFileDescriptor();
                            if (fd != null) {
                                StructStat stat = Os.fstat(fd);
                                if (stat.st_mtime > 0) {
                                    return new Date(stat.st_mtime * 1000); // 转换为毫秒
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error getting file stats: " + e.getMessage());
                        } finally {
                            pfd.close();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error opening file descriptor: " + e.getMessage());
                }
            }

            // 如果所有方法都失败，返回null
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting video creation date: ", e);
            return null;
        }
    }
    /**
     * 从URI导入图片到应用私有存储
     *
     * @param context  上下文
     * @param sourceUri 源图片URI
     * @param entryId  条目ID
     * @return 返回导入后的图片文件，失败则返回null
     */
    public static File importImageFromUri(Context context, Uri sourceUri, int entryId) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        String fileName = getFileNameFromUri(contentResolver, sourceUri);
        String extension = getFileExtension(fileName);

        // 确保扩展名为图片格式
        if (extension == null || extension.isEmpty()) {
            extension = ".jpg"; // 默认图片扩展名
        }

        // 创建目标文件
        File destinationFile = FileUtils.createImageFile(context, entryId);

        // 复制文件内容
        try (InputStream in = contentResolver.openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destinationFile)) {

            if (in == null) {
                throw new IOException("Cannot open input stream from URI: " + sourceUri);
            }

            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();

            Log.d(TAG, "Image imported successfully to: " + destinationFile.getAbsolutePath());
            return destinationFile;
        } catch (IOException e) {
            Log.e(TAG, "Error importing image: ", e);
            // 如果失败，删除部分创建的文件
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            throw e;
        }
    }

    /**
     * 获取图片的创建日期
     * @param context 上下文
     * @param imageUri 图片URI
     * @return 创建日期，如果无法获取则返回null
     */
    public static Date getImageCreationDate(Context context, Uri imageUri) {
        try {
            // 尝试从媒体数据库获取
            String[] projection = { MediaStore.Images.Media.DATE_TAKEN };
            Cursor cursor = context.getContentResolver().query(imageUri, projection, null, null, null);

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                        long dateTaken = cursor.getLong(columnIndex);

                        if (dateTaken > 0) {
                            return new Date(dateTaken);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error getting date from media store: " + e.getMessage());
                } finally {
                    cursor.close();
                }
            }

            // 尝试从EXIF数据获取
            try {
                InputStream in = context.getContentResolver().openInputStream(imageUri);
                if (in != null) {
                    ExifInterface exif = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        exif = new ExifInterface(in);
                    }
                    String dateString = exif.getAttribute(ExifInterface.TAG_DATETIME);
                    if (dateString != null && !dateString.isEmpty()) {
                        // EXIF日期格式通常为: "yyyy:MM:dd HH:mm:ss"
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                        try {
                            return sdf.parse(dateString);
                        } catch (ParseException e) {
                            Log.w(TAG, "Error parsing EXIF date: " + e.getMessage());
                        }
                    }
                    in.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error reading EXIF data: " + e.getMessage());
            }

            // 如果前两种方法都失败，尝试获取文件上次修改时间
            if ("file".equals(imageUri.getScheme())) {
                File file = new File(imageUri.getPath());
                if (file.exists()) {
                    return new Date(file.lastModified());
                }
            } else {
                // 尝试获取文件描述符
                try {
                    ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(imageUri, "r");
                    if (pfd != null) {
                        try {
                            FileDescriptor fd = pfd.getFileDescriptor();
                            if (fd != null) {
                                StructStat stat = Os.fstat(fd);
                                if (stat.st_mtime > 0) {
                                    return new Date(stat.st_mtime * 1000); // 转换为毫秒
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error getting file stats: " + e.getMessage());
                        } finally {
                            pfd.close();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error opening file descriptor: " + e.getMessage());
                }
            }

            // 如果所有方法都失败，返回null
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting image creation date: ", e);
            return null;
        }
    }
}