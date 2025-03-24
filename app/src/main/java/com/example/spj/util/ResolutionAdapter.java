package com.example.spj.util;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 自适应摄像头分辨率工具类
 * 用于选择最佳的预览和录制分辨率
 */
public class ResolutionAdapter {
    private static final String TAG = "ResolutionAdapter";

    // 最大录制分辨率限制
    private static final int MAX_RECORDING_WIDTH = 1920;
    private static final int MAX_RECORDING_HEIGHT = 1080;

    // 标准目标分辨率（如果没有找到更适合的）
    private static final Size[] TARGET_RESOLUTIONS = {
            new Size(1920, 1080), // 全高清 16:9
            new Size(1280, 720),  // 高清 16:9
            new Size(720, 720),   // 正方形 1:1
            new Size(640, 480)    // VGA 4:3
    };

    /**
     * 获取设备屏幕的纵横比
     * @param context 上下文
     * @return 宽高比值（宽/高）
     */
    public static float getScreenAspectRatio(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();

        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return (float) metrics.widthPixels / metrics.heightPixels;
        } else {
            return (float) metrics.heightPixels / metrics.widthPixels;
        }
    }

    /**
     * 获取摄像头支持的分辨率列表
     * @param context 上下文
     * @param cameraId 摄像头ID
     * @return 支持的分辨率列表，如果获取失败则返回null
     */
    public static Size[] getSupportedResolutions(Context context, String cameraId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map != null) {
                return map.getOutputSizes(android.graphics.SurfaceTexture.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取摄像头支持的分辨率失败", e);
        }

        return null;
    }

    /**
     * 获取建议的录制分辨率选项
     * @param context 上下文
     * @param cameraId 摄像头ID
     * @return 分辨率选项列表
     */
    public static List<Size> getRecommendedResolutions(Context context, String cameraId) {
        Size[] supportedSizes = getSupportedResolutions(context, cameraId);
        if (supportedSizes == null || supportedSizes.length == 0) {
            // 如果无法获取支持的分辨率，返回标准选项
            return Arrays.asList(TARGET_RESOLUTIONS);
        }

        // 按面积从大到小排序
        List<Size> sortedSizes = Arrays.asList(supportedSizes);
        Collections.sort(sortedSizes, new Comparator<Size>() {
            @Override
            public int compare(Size s1, Size s2) {
                // 降序排列
                return Long.signum((long) s2.getWidth() * s2.getHeight() -
                        (long) s1.getWidth() * s1.getHeight());
            }
        });

        // 过滤掉过大的分辨率
        List<Size> filteredSizes = new ArrayList<>();
        for (Size size : sortedSizes) {
            if (size.getWidth() <= MAX_RECORDING_WIDTH &&
                    size.getHeight() <= MAX_RECORDING_HEIGHT) {
                filteredSizes.add(size);
            }
        }

        // 如果过滤后没有分辨率，使用原始列表的前几个选项
        if (filteredSizes.isEmpty()) {
            for (int i = 0; i < Math.min(4, sortedSizes.size()); i++) {
                filteredSizes.add(sortedSizes.get(i));
            }
        }

        // 返回不超过4个选项的列表
        return filteredSizes.subList(0, Math.min(4, filteredSizes.size()));
    }

    /**
     * 选择最适合屏幕比例的录制分辨率
     * @param context 上下文
     * @param cameraId 摄像头ID
     * @return 最佳录制分辨率
     */
    public static Size getBestRecordingResolution(Context context, String cameraId) {
        float screenRatio = getScreenAspectRatio(context);
        Size[] supportedSizes = getSupportedResolutions(context, cameraId);

        if (supportedSizes == null || supportedSizes.length == 0) {
            // 默认返回720p分辨率
            return new Size(1280, 720);
        }

        // 寻找最接近屏幕比例的分辨率，同时不超过最大限制
        Size bestSize = null;
        float minRatioDiff = Float.MAX_VALUE;

        for (Size size : supportedSizes) {
            if (size.getWidth() <= MAX_RECORDING_WIDTH &&
                    size.getHeight() <= MAX_RECORDING_HEIGHT) {
                float ratio = (float) size.getWidth() / size.getHeight();
                float ratioDiff = Math.abs(ratio - screenRatio);

                if (ratioDiff < minRatioDiff) {
                    minRatioDiff = ratioDiff;
                    bestSize = size;
                }
            }
        }

        // 如果没找到合适的，返回默认值
        if (bestSize == null) {
            for (Size target : TARGET_RESOLUTIONS) {
                for (Size supported : supportedSizes) {
                    if (supported.getWidth() == target.getWidth() &&
                            supported.getHeight() == target.getHeight()) {
                        return target;
                    }
                }
            }
            // 最终默认
            return new Size(1920, 1280);
        }

        return bestSize;
    }

    /**
     * 获取相机的原生长宽比
     * @param context 上下文
     * @param cameraId 摄像头ID
     * @return 摄像头原生长宽比，如果无法确定则返回16:9的比例
     */
    public static float getCameraNativeAspectRatio(Context context, String cameraId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            // 尝试获取传感器原生比例
            Size[] sizes = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(android.graphics.SurfaceTexture.class);

            if (sizes != null && sizes.length > 0) {
                // 通常最大分辨率反映了传感器的原生比例
                Size largestSize = Collections.max(
                        Arrays.asList(sizes),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                                        (long) rhs.getWidth() * rhs.getHeight());
                            }
                        });

                return (float) largestSize.getWidth() / largestSize.getHeight();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取摄像头原生比例失败", e);
        }

        // 默认返回16:9比例
        return 16.0f / 9.0f;
    }
}