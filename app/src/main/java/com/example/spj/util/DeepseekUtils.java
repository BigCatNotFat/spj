package com.example.spj.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 与DeepSeek API交互的工具类
 */
public class DeepseekUtils {
    private static final String TAG = "DeepseekUtils";
    private static final String API_KEY = "sk-ab5927c3a14543af94bdca454ba541aa";
    private static final String BASE_URL = "https://api.deepseek.com/v1/chat/completions";

    public interface DeepseekCallback {
        void onSuccess(String summary);
        void onFailure(String errorMsg);
    }

    /**
     * 根据文本生成简短摘要
     *
     * @param text 需要总结的文本内容
     * @param callback 回调接口，返回生成的摘要或错误信息
     */
    public static void generateSummary(String text, DeepseekCallback callback) {
        new Thread(() -> {
            try {
                // 创建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "deepseek-chat");

                JSONArray messages = new JSONArray();

                // 添加系统消息
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "你是一个优秀的文本摘要助手，请提供简洁准确的摘要。");
                messages.put(systemMessage);

                // 添加用户消息
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", "请用10个字以内总结以下内容：" + text);
                messages.put(userMessage);

                requestBody.put("messages", messages);
                requestBody.put("stream", false);

                // 创建OkHttpClient，设置较长的超时时间
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)  // 给API足够的响应时间
                        .build();

                // 构建请求
                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"),
                                requestBody.toString()))
                        .build();

                // 发送请求并处理响应
                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    // 解析响应，获取生成的文本
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String summary = message.getString("content").trim();

                    // 返回成功结果
                    callback.onSuccess(summary);
                } else {
                    // 请求失败
                    Log.e(TAG, "API请求失败: " + responseBody);
                    callback.onFailure("生成摘要失败: " + response.code());
                }

            } catch (JSONException | IOException e) {
                Log.e(TAG, "生成摘要异常", e);
                callback.onFailure("处理失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 生成合集总结
     * 根据按日期组织的注释内容，生成全面的总结
     *
     * @param organizedNotes 已按日期组织的笔记内容
     * @param entryName 合集名称
     * @param callback 回调接口，返回生成的总结或错误信息
     */
    public static void generateEntrySummary(String organizedNotes, String entryName, DeepseekCallback callback) {
        new Thread(() -> {
            try {
                // 创建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "deepseek-chat");

                // 增加最大令牌数，以支持更长的输入和输出
                requestBody.put("max_tokens", 4000);

                JSONArray messages = new JSONArray();

                // 添加系统消息
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content",
                        "你是一个专业的日记分析和总结专家。你擅长对按日期组织的笔记内容进行分析和总结，捕捉重要事件、" +
                                "情感变化和关键主题。你的总结清晰、全面且有洞察力，会考虑时间顺序和事件发展。");
                messages.put(systemMessage);

                // 添加用户消息
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                String prompt = String.format(
                        "以下是我在「%s」合集中按日期记录的笔记内容。请帮我简单总结这些内容的主要事件、和关键主题。最后再针对内容给一些合理的建议和提醒" +
                                "请确保总结:\n" +
                                "1. 保留时间脉络，提炼每个时间段的要点\n" +
                                "2. 总结篇幅50字左右\n" +
                                "3. 针对某些事情进行分析推理\n\n" +
                                "4. 给一些合理的建议和提醒\n\n"+
                                "笔记内容如下:\n\n%s",
                        entryName, organizedNotes);

                userMessage.put("content", prompt);
                messages.put(userMessage);

                requestBody.put("messages", messages);
                requestBody.put("stream", false);
                // 增加温度参数，使总结更具创造性
                requestBody.put("temperature", 0.7);

                // 创建OkHttpClient，设置较长的超时时间
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)  // 更长的响应时间
                        .build();

                // 构建请求
                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"),
                                requestBody.toString()))
                        .build();

                // 发送请求并处理响应
                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    // 解析响应，获取生成的文本
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String summary = message.getString("content").trim();

                    // 返回成功结果
                    callback.onSuccess(summary);
                } else {
                    // 请求失败
                    Log.e(TAG, "API请求失败: " + responseBody);
                    callback.onFailure("生成总结失败: " + response.code());
                }

            } catch (JSONException | IOException e) {
                Log.e(TAG, "生成总结异常", e);
                callback.onFailure("处理失败: " + e.getMessage());
            }
        }).start();
    }
}