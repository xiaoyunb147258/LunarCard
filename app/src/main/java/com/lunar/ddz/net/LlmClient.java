package com.lunar.ddz.net;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenAI 兼容的聊天补全客户端。
 *
 * 设计要点：
 *  - 单线程串行（避免并发打爆接口）
 *  - 结果回调回主线程
 *  - 失败/超时自动回调 onError，由上层降级到本地 AI
 *  - 不依赖任何第三方库，纯 HttpURLConnection
 */
public final class LlmClient {

    public interface Callback {
        void onReply(String text);
        void onError(String message);
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile LlmConfig config;

    public LlmClient(LlmConfig config) {
        this.config = config;
    }

    public void setConfig(LlmConfig cfg) {
        this.config = cfg;
    }

    /**
     * 发送一轮对话。
     * @param system  系统提示词
     * @param user    用户提示词
     */
    public void ask(final String system, final String user, final Callback cb) {
        final LlmConfig cfg = config;
        if (cfg == null || !cfg.isUsable()) {
            main.post(new Runnable() {
                @Override public void run() {
                    cb.onError("未配置 AI 接口");
                }
            });
            return;
        }

        pool.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(cfg.completionsUrl());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(cfg.timeoutSec * 1000);
                    conn.setReadTimeout(cfg.timeoutSec * 1000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
                    conn.setRequestProperty("Accept", "application/json");

                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", system);
                    messages.put(sys);

                    JSONObject usr = new JSONObject();
                    usr.put("role", "user");
                    usr.put("content", user);
                    messages.put(usr);

                    JSONObject body = new JSONObject();
                    body.put("model", cfg.model);
                    body.put("messages", messages);
                    body.put("temperature", cfg.temperature);
                    body.put("max_tokens", 512);
                    body.put("stream", false);

                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300)
                            ? conn.getInputStream() : conn.getErrorStream();
                    final String raw = readAll(is);

                    if (code < 200 || code >= 300) {
                        postError(cb, "HTTP " + code + ": " + shortText(raw));
                        return;
                    }

                    JSONObject resp = new JSONObject(raw);
                    JSONArray choices = resp.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        postError(cb, "返回为空");
                        return;
                    }
                    JSONObject first = choices.getJSONObject(0);
                    JSONObject msg = first.optJSONObject("message");
                    String content = msg != null ? msg.optString("content", "") : "";
                    if (content.trim().length() == 0) {
                        postError(cb, "内容为空");
                        return;
                    }
                    postReply(cb, content);

                } catch (final Exception e) {
                    postError(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    /** 轻量连通性测试 */
    public void test(final Callback cb) {
        ask("你是测试助手。", "只回复两个字：可用", cb);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private void postReply(final Callback cb, final String s) {
        main.post(new Runnable() {
            @Override public void run() { cb.onReply(s); }
        });
    }

    private void postError(final Callback cb, final String s) {
        main.post(new Runnable() {
            @Override public void run() { cb.onError(s); }
        });
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
        } catch (Exception ignored) {}
        return sb.toString();
    }

    private static String shortText(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }
}