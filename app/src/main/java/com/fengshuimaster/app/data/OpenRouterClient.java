package com.fengshuimaster.app.data;

import androidx.annotation.NonNull;

import com.fengshuimaster.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpenRouterClient {

    public interface PalmCallback {
        void onSuccess(String content);
        void onFailure(String message);
    }

    private static final String API_URL = BuildConfig.BACKEND_API_BASE_URL + "/api/palm/analyze";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    public OpenRouterClient() {}

    private static String formatApiError(int code, String rawBody) {
        String detail = "";
        try {
            JSONObject json = new JSONObject(rawBody);
            detail = json.optString("error", "").trim();
            if (detail.isEmpty()) {
                JSONObject nested = json.optJSONObject("error");
                if (nested != null) {
                    detail = nested.optString("message", "").trim();
                }
            }
            if (detail.isEmpty()) {
                detail = json.optString("detail", "").trim();
            }
            if (detail.isEmpty()) {
                detail = json.optString("message", "").trim();
            }
        } catch (Exception ignored) {
            if (rawBody != null && !rawBody.trim().isEmpty()) {
                detail = rawBody.trim();
            }
        }
        if (detail.length() > 180) {
            detail = detail.substring(0, 180) + "…";
        }
        if (detail.isEmpty()) {
            return "API 錯誤：" + code;
        }
        return "API 錯誤：" + code + "（" + detail + "）";
    }

    public void analyzePalmImage(
            @NonNull String base64Jpeg,
            @NonNull String handSide,
            @NonNull PalmCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("image_base64", base64Jpeg);
            payload.put("hand_side", handSide);
            payload.put("locale", "zh-Hant");
            payload.put("feature", "palm-reading");

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onFailure("連線失敗：" + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody body = response.body();
                    if (body == null) {
                        callback.onFailure("API 回傳為空。");
                        return;
                    }

                    String rawBody = body.string();
                    if (!response.isSuccessful()) {
                        callback.onFailure(formatApiError(response.code(), rawBody));
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(rawBody);
                        String content = json.optString("result", "").trim();
                        if (content.isEmpty()) {
                            JSONArray choices = json.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                                if (message != null) {
                                    content = message.optString("content", "").trim();
                                }
                            }
                        }
                        if (content.isEmpty()) {
                            callback.onFailure("AI 回傳內容為空。");
                            return;
                        }
                        callback.onSuccess(content);
                    } catch (Exception parseEx) {
                        callback.onFailure("解析回應失敗：" + parseEx.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onFailure("建立請求失敗：" + e.getMessage());
        }
    }
}
