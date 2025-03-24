package com.example.spj.util;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 科大讯飞语音识别API封装类
 */
public class XunfeiASR {
    private static final String TAG = "XunfeiASR";
    // 尝试使用HTTPS代替HTTP，如果不行可以改回HTTP
    private static final String LFASR_HOST = "https://raasr.xfyun.cn/api";

    // API接口名
    private static final String API_PREPARE = "/prepare";
    private static final String API_UPLOAD = "/upload";
    private static final String API_MERGE = "/merge";
    private static final String API_GET_PROGRESS = "/getProgress";
    private static final String API_GET_RESULT = "/getResult";

    // 文件分片大小10M
    private static final int FILE_PIECE_SIZE = 10 * 1024 * 1024;

    private String appId;
    private String secretKey;
    private File audioFile;

    // 回调接口
    public interface XunfeiASRCallback {
        void onSuccess(String result);
        void onProgress(String progress);
        void onFailure(String errorMsg);
    }

    public XunfeiASR(String appId, String secretKey, File audioFile) {
        this.appId = appId;
        this.secretKey = secretKey;
        this.audioFile = audioFile;
    }

    // 执行完整的识别过程
    public void executeRecognition(final XunfeiASRCallback callback) {
        new Thread(() -> {
            try {
                // 1. 预处理
                String taskId = prepareRequest();
                callback.onProgress("准备任务完成，任务ID: " + taskId);

                // 2. 分片上传
                uploadRequest(taskId);
                callback.onProgress("文件上传完成");

                // 3. 文件合并
                mergeRequest(taskId);
                callback.onProgress("文件合并完成");

                // 4. 获取任务进度
                while (true) {
                    String progressData = getProgressRequest(taskId);
                    JSONObject progressObj = new JSONObject(progressData);
                    int errNo = progressObj.getInt("err_no");

                    if (errNo != 0 && errNo != 26605) {
                        callback.onFailure("任务错误: " + progressObj.getString("failed"));
                        return;
                    } else {
                        String data = progressObj.getString("data");
                        JSONObject taskStatus = new JSONObject(data);
                        int status = taskStatus.getInt("status");

                        callback.onProgress("任务状态: " + status);

                        if (status == 9) {
                            callback.onProgress("任务 " + taskId + " 已完成");
                            break;
                        }
                    }

                    // 每次获取进度间隔20秒
                    Thread.sleep(20000);
                }

                // 5. 获取结果
                String result = getResultRequest(taskId);
                callback.onSuccess(result);

            } catch (Exception e) {
                callback.onFailure("识别失败: " + e.getMessage());
                Log.e(TAG, "识别失败", e);
            }
        }).start();
    }

    // 根据不同的API生成参数
    private Map<String, String> generateParams(String apiName, String taskId, String sliceId) {
        Map<String, String> params = new HashMap<>();
        String ts = String.valueOf(System.currentTimeMillis() / 1000);

        try {
            String md5 = MD5(appId + ts);
            String signa = Base64.encodeToString(
                    HmacSHA1Encrypt(md5, secretKey),
                    Base64.NO_WRAP
            );

            long fileLen = audioFile.length();
            String fileName = audioFile.getName();

            params.put("app_id", appId);
            params.put("signa", signa);
            params.put("ts", ts);

            if (API_PREPARE.equals(apiName)) {
                int sliceNum = (int) (fileLen / FILE_PIECE_SIZE) + (fileLen % FILE_PIECE_SIZE == 0 ? 0 : 1);
                params.put("file_len", String.valueOf(fileLen));
                params.put("file_name", fileName);
                params.put("slice_num", String.valueOf(sliceNum));
            } else if (API_UPLOAD.equals(apiName)) {
                params.put("task_id", taskId);
                params.put("slice_id", sliceId);
            } else if (API_MERGE.equals(apiName)) {
                params.put("task_id", taskId);
                params.put("file_name", fileName);
            } else if (API_GET_PROGRESS.equals(apiName) || API_GET_RESULT.equals(apiName)) {
                params.put("task_id", taskId);
            }

        } catch (Exception e) {
            Log.e(TAG, "生成参数错误", e);
        }

        return params;
    }

    // 计算MD5
    private String MD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5计算错误", e);
            return "";
        }
    }

    // 计算HMAC-SHA1
    private byte[] HmacSHA1Encrypt(String encryptText, String encryptKey) throws Exception {
        byte[] data = encryptKey.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(data, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKey);
        byte[] text = encryptText.getBytes(StandardCharsets.UTF_8);
        return mac.doFinal(text);
    }

    // 预处理请求
    private String prepareRequest() throws Exception {
        Map<String, String> params = generateParams(API_PREPARE, null, null);
        JSONObject response = sendRequest(LFASR_HOST + API_PREPARE, params, null);

        if (response.getInt("ok") == 0) {
            return response.getString("data");
        } else {
            throw new Exception("预处理请求失败: " + response.toString());
        }
    }

    // 分片ID生成器
    private static class SliceIdGenerator {
        private char[] ch = "aaaaaaaaa`".toCharArray();

        public String getNextSliceId() {
            int j = ch.length - 1;
            while (j >= 0) {
                if (ch[j] != 'z') {
                    ch[j]++;
                    break;
                } else {
                    ch[j] = 'a';
                    j--;
                }
            }
            return new String(ch);
        }
    }

    // 上传请求
    private void uploadRequest(String taskId) throws Exception {
        FileInputStream fis = new FileInputStream(audioFile);
        byte[] buffer = new byte[FILE_PIECE_SIZE];
        int index = 1;
        SliceIdGenerator sig = new SliceIdGenerator();

        try {
            int len;
            while ((len = fis.read(buffer)) > 0) {
                String sliceId = sig.getNextSliceId();
                Map<String, String> params = generateParams(API_UPLOAD, taskId, sliceId);

                // 创建一个新的byte数组，只包含实际读取的数据
                byte[] actualData = new byte[len];
                System.arraycopy(buffer, 0, actualData, 0, len);

                JSONObject response = sendRequestWithFile(LFASR_HOST + API_UPLOAD, params, sliceId, actualData);

                if (response.getInt("ok") != 0) {
                    throw new Exception("上传分片失败, 响应: " + response.toString());
                }

                Log.d(TAG, "上传分片 " + index + " 成功");
                index++;
            }
        } finally {
            fis.close();
        }
    }

    // 合并请求
    private void mergeRequest(String taskId) throws Exception {
        Map<String, String> params = generateParams(API_MERGE, taskId, null);
        JSONObject response = sendRequest(LFASR_HOST + API_MERGE, params, null);

        if (response.getInt("ok") != 0) {
            throw new Exception("合并请求失败: " + response.toString());
        }
    }

    // 获取进度请求
    private String getProgressRequest(String taskId) throws Exception {
        Map<String, String> params = generateParams(API_GET_PROGRESS, taskId, null);
        JSONObject response = sendRequest(LFASR_HOST + API_GET_PROGRESS, params, null);

        if (response.getInt("ok") == 0) {
            return response.toString();
        } else {
            throw new Exception("获取进度请求失败: " + response.toString());
        }
    }

    // 获取结果请求
    private String getResultRequest(String taskId) throws Exception {
        Map<String, String> params = generateParams(API_GET_RESULT, taskId, null);
        JSONObject response = sendRequest(LFASR_HOST + API_GET_RESULT, params, null);

        if (response.getInt("ok") == 0) {
            return response.getString("data");
        } else {
            throw new Exception("获取结果请求失败: " + response.toString());
        }
    }

    // 发送HTTP请求
    private JSONObject sendRequest(String url, Map<String, String> params, Map<String, String> headers) throws Exception {
        // 创建一个带超时的OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        FormBody.Builder formBuilder = new FormBody.Builder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(formBuilder.build());

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Log.d(TAG, "发送请求: " + url);

        Response response = client.newCall(requestBuilder.build()).execute();
        String responseBody = response.body().string();

        Log.d(TAG, "响应: " + responseBody);

        return new JSONObject(responseBody);
    }

    // 发送带文件的HTTP请求
    private JSONObject sendRequestWithFile(String url, Map<String, String> params, String sliceId, byte[] fileData) throws Exception {
        // 创建一个带超时的OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (Map.Entry<String, String> entry : params.entrySet()) {
            multipartBuilder.addFormDataPart(entry.getKey(), entry.getValue());
        }

        // 使用"content"作为参数名而不是"filename"
        // 注意：根据科大讯飞API文档，文件内容的参数名应该是"content"
        multipartBuilder.addFormDataPart("content", sliceId,
                RequestBody.create(MediaType.parse("application/octet-stream"), fileData));

        Request request = new Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build();

        Log.d(TAG, "发送分片请求，URL: " + url + ", 分片ID: " + sliceId + ", 数据大小: " + fileData.length + " 字节");

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        Log.d(TAG, "请求响应: " + responseBody);
        return new JSONObject(responseBody);
    }
}