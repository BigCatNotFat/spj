package com.example.spj.util;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PermissionUtils {

    private static final String TAG = "PermissionUtils";

    /**
     * 获取应用所需的权限
     */
    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10-12
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else { // Android 9及以下
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }
    public static void diagnosePermissionIssues(Activity activity) {
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "缺少相机权限", Toast.LENGTH_SHORT).show();
        }

        // 检查录音权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "缺少录音权限", Toast.LENGTH_SHORT).show();
        }

        // 检查媒体权限(Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "缺少读取媒体文件权限", Toast.LENGTH_SHORT).show();
            }
        }
        // 检查存储权限(Android 12及以下)
        else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "缺少读取存储权限", Toast.LENGTH_SHORT).show();
            }
        }

        // 检查存储可用性
        if (!FileUtils.checkStorageAvailability(activity)) {
            Toast.makeText(activity, "存储空间不可用", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 检查并请求权限
     */
    public static boolean checkAndRequestPermissions(Activity activity, int requestCode) {
        String[] permissions = getRequiredPermissions();
        List<String> permissionsNeeded = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsNeeded.toArray(new String[0]),
                    requestCode
            );
            return false;
        }

        // 关键修改：即使旧存储权限被拒绝，只要测试文件可写入，就认为有权限
        if (FileUtils.checkStorageAvailability(activity)) {
            return true;
        }

        return true;
    }

    /**
     * 检查权限是否足够使用（不考虑旧版存储权限）
     */
    public static boolean hasEnoughPermissions(Activity activity) {
        // 在Android 13+上，只需检查相机、麦克风和媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                            FileUtils.checkStorageAvailability(activity);
        }
        // 其他版本使用标准检查
        else {
            String[] permissions = getRequiredPermissions();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 显示权限说明对话框
     */
    public static void showPermissionExplanationDialog(Activity activity, int requestCode) {
        new AlertDialog.Builder(activity)
                .setTitle("需要权限")
                .setMessage("录制视频需要相机、麦克风和媒体权限。请授予这些权限以继续。")
                .setPositiveButton("重试", (dialog, which) ->
                        checkAndRequestPermissions(activity, requestCode))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示引导用户去设置页面的对话框
     */
    public static void showSettingsDialog(Activity activity, int settingsRequestCode) {
        new AlertDialog.Builder(activity)
                .setTitle("需要权限")
                .setMessage("您已拒绝所需权限。请前往设置页面手动启用权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivityForResult(intent, settingsRequestCode);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}