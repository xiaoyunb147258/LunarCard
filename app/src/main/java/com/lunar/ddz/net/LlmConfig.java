package com.lunar.ddz.net;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI 接口配置。三个 AI 座位共用同一套配置。
 *
 * 兼容任何 OpenAI 风格的 /v1/chat/completions 接口：
 *   DeepSeek   https://api.deepseek.com        model=deepseek-chat
 *   OpenAI     https://api.openai.com          model=gpt-4o-mini
 *   Kimi       https://api.moonshot.cn         model=moonshot-v1-8k
 *   通义       https://dashscope.aliyuncs.com/compatible-mode  model=qwen-plus
 *   Ollama     http://192.168.1.10:11434       model=qwen2.5
 */
public final class LlmConfig {

    private static final String FILE = "lunar_llm";

    private static final String K_ENABLED = "enabled";
    private static final String K_BASE = "base_url";
    private static final String K_KEY = "api_key";
    private static final String K_MODEL = "model";
    private static final String K_TEMP = "temperature";
    private static final String K_TIMEOUT = "timeout";

    /** 默认值：指向 DeepSeek，用户可改 */
    public static final String DEFAULT_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";
    public static final int DEFAULT_TIMEOUT = 20;

    public boolean enabled;
    public String baseUrl;
    public String apiKey;
    public String model;
    public float temperature;
    public int timeoutSec;

    private final SharedPreferences sp;

    public LlmConfig(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    public void load() {
        enabled = sp.getBoolean(K_ENABLED, false);
        baseUrl = sp.getString(K_BASE, DEFAULT_BASE);
        apiKey = sp.getString(K_KEY, "");
        model = sp.getString(K_MODEL, DEFAULT_MODEL);
        temperature = sp.getFloat(K_TEMP, 0.3f);
        timeoutSec = sp.getInt(K_TIMEOUT, DEFAULT_TIMEOUT);
    }

    public void save() {
        sp.edit()
                .putBoolean(K_ENABLED, enabled)
                .putString(K_BASE, baseUrl)
                .putString(K_KEY, apiKey)
                .putString(K_MODEL, model)
                .putFloat(K_TEMP, temperature)
                .putInt(K_TIMEOUT, timeoutSec)
                .apply();
    }

    /** 配置是否完整可用 */
    public boolean isUsable() {
        return enabled
                && baseUrl != null && baseUrl.trim().length() > 4
                && model != null && model.trim().length() > 0
                && apiKey != null && apiKey.trim().length() > 0;
    }

    /** 拼接完整的 chat completions URL */
    public String completionsUrl() {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        if (b.endsWith("/v1")) return b + "/chat/completions";
        return b + "/v1/chat/completions";
    }

    /** 遮蔽后的 Key，用于界面展示 */
    public String maskedKey() {
        if (apiKey == null || apiKey.length() < 12) return "";
        return apiKey.substring(0, 6) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}