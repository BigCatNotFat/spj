package com.example.spj.util;

import android.content.Context;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 预览显示辅助类，用于处理相机预览和屏幕的适配
 */
public class PreviewHelper {
    private static final String TAG = "PreviewHelper";

    /**
     * 获取设备屏幕尺寸
     * @param context 上下文
     * @return 屏幕尺寸
     */
    public static Point getScreenSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        return size;
    }

    /**
     * 获取设备屏幕的纵横比
     * @param context 上下文
     * @return 宽高比值（宽/高）
     */
    public static float getScreenAspectRatio(Context context) {
        Point screenSize = getScreenSize(context);
        return (float) screenSize.x / screenSize.y;
    }

    /**
     * 获取相机传感器方向
     * @param context 上下文
     * @param cameraId 相机ID
     * @return 相机传感器方向（0, 90, 180, 270度）
     */
    public static int getCameraSensorOrientation(Context context, String cameraId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (Exception e) {
            Log.e(TAG, "获取相机传感器方向失败", e);
            return 0; // 默认0度
        }
    }

    /**
     * 获取最适合屏幕的预览尺寸
     * @param context 上下文
     * @param cameraId 相机ID
     * @return 最佳预览尺寸
     */
    public static Size getBestPreviewSize(Context context, String cameraId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                return null;
            }

            // 获取所有支持的预览尺寸
            Size[] sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);

            // 获取屏幕纵横比
            float screenRatio = getScreenAspectRatio(context);

            // 获取相机传感器方向
            int sensorOrientation = getCameraSensorOrientation(context, cameraId);
            boolean swapDimensions = sensorOrientation == 90 || sensorOrientation == 270;

            // 调整目标比例
            float targetRatio = screenRatio;
            if (swapDimensions) {
                targetRatio = 1.0f / screenRatio;
            }

            Size bestSize = null;
            float bestAspectRatioDiff = Float.MAX_VALUE;
            int bestArea = 0;

            for (Size size : sizes) {
                float ratio = (float) size.getWidth() / size.getHeight();
                float ratioDiff = Math.abs(ratio - targetRatio);
                int area = size.getWidth() * size.getHeight();

                // 如果这个尺寸的比例更接近目标比例，或者比例相同但面积更大
                if (ratioDiff < bestAspectRatioDiff ||
                        (Math.abs(ratioDiff - bestAspectRatioDiff) < 0.01f && area > bestArea)) {
                    bestAspectRatioDiff = ratioDiff;
                    bestSize = size;
                    bestArea = area;
                }
            }

            if (bestSize != null) {
                Log.d(TAG, "最佳预览尺寸: " + bestSize.getWidth() + "x" + bestSize.getHeight() +
                        " 比例: " + ((float) bestSize.getWidth() / bestSize.getHeight()) +
                        " (目标比例: " + targetRatio + ")");
                return bestSize;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取最佳预览尺寸失败", e);
        }

        return null;
    }

    /**
     * 计算最佳填充模式值
     * @param cameraAspectRatio 相机纵横比
     * @param displayAspectRatio 显示区域纵横比
     * @return 填充系数 (1.0表示完全匹配，>1.0表示需要裁剪以填充，<1.0表示会有黑边)
     */
    public static float calculateFillFactor(float cameraAspectRatio, float displayAspectRatio) {
        // 如果比例接近，不需要调整
        if (Math.abs(cameraAspectRatio - displayAspectRatio) < 0.01f) {
            return 1.0f;
        }

        // 计算填充因子
        if (cameraAspectRatio > displayAspectRatio) {
            // 相机输出更宽，高度需要拉伸或裁剪宽度
            return displayAspectRatio / cameraAspectRatio;
        } else {
            // 相机输出更高，宽度需要拉伸或裁剪高度
            return cameraAspectRatio / displayAspectRatio;
        }
    }

    /**
     * 计算渲染顶点坐标，用于调整预览填充模式
     * @param cameraAspectRatio 相机纵横比
     * @param displayAspectRatio 显示区域纵横比
     * @param fillMode 填充模式 (0=保持宽高比有黑边, 1=裁剪以填满屏幕)
     * @return 顶点坐标数组 [x1,y1, x2,y2, x3,y3, x4,y4]
     */
    public static float[] calculateVertices(float cameraAspectRatio, float displayAspectRatio, int fillMode) {
        // 默认顶点（单位正方形）
        float[] vertices = {
                -1.0f,  1.0f,   // 左上
                1.0f,  1.0f,   // 右上
                -1.0f, -1.0f,   // 左下
                1.0f, -1.0f    // 右下
        };

        // 比例几乎相同，不需要调整
        if (Math.abs(cameraAspectRatio - displayAspectRatio) < 0.01f) {
            return vertices;
        }

        // 根据填充模式调整顶点
        if (fillMode == 0) {
            // 保持宽高比，可能有黑边
            if (cameraAspectRatio > displayAspectRatio) {
                // 相机比例更宽，需要垂直收缩（信箱效果）
                float adjust = displayAspectRatio / cameraAspectRatio;
                vertices[1] *= adjust;
                vertices[3] *= adjust;
                vertices[5] *= adjust;
                vertices[7] *= adjust;
            } else {
                // 相机比例更高，需要水平收缩（柱状效果）
                float adjust = cameraAspectRatio / displayAspectRatio;
                vertices[0] *= adjust;
                vertices[2] *= adjust;
                vertices[4] *= adjust;
                vertices[6] *= adjust;
            }
        } else if (fillMode == 1) {
            // 填满屏幕，可能裁剪内容
            if (cameraAspectRatio > displayAspectRatio) {
                // 相机比例更宽，需要水平裁剪
                float adjust = displayAspectRatio / cameraAspectRatio;
                float scale = 1.0f / adjust;
                vertices[0] *= scale;
                vertices[2] *= scale;
                vertices[4] *= scale;
                vertices[6] *= scale;
            } else {
                // 相机比例更高，需要垂直裁剪
                float adjust = cameraAspectRatio / displayAspectRatio;
                float scale = 1.0f / adjust;
                vertices[1] *= scale;
                vertices[3] *= scale;
                vertices[5] *= scale;
                vertices[7] *= scale;
            }
        }

        return vertices;
    }
}