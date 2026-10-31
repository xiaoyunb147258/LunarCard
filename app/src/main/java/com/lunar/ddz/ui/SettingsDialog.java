package com.lunar.ddz.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.lunar.ddz.net.LlmClient;
import com.lunar.ddz.net.LlmConfig;

/**
 * AI 接口设置面板。
 *
 * 三个 AI 座位共用一套配置：
 *   - 开关：是否启用大模型（关掉就用本地算法）
 *   - Base URL / API Key / 模型名
 *   - 温度、超时
 *   - 一键测试连通性
 *   - 快捷预设（DeepSeek / OpenAI / Kimi / 通义）
 */
public final class SettingsDialog {

    private SettingsDialog() {}

    public static Dialog show(final Context ctx, final Theme theme,
                              final LlmConfig cfg, final LlmClient client,
                              final Runnable onSaved) {

        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final float dp = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (22 * dp);

        ScrollView sv = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, (int) (18 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141C26);
        bg.setCornerRadius(20 * dp);
        bg.setStroke((int) (1.2f * dp), Theme.alpha(theme.gold, 0.30f));
        root.setBackground(bg);

        // ── 标题 ──
        TextView title = new TextView(ctx);
        title.setText("AI 设置");
        title.setTextSize(19f);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(0, 0, 0, (int) (6 * dp));
        root.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("三个 AI 座位共用这一套配置");
        sub.setTextSize(12.5f);
        sub.setTextColor(0xFF8FA0B4);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, (int) (16 * dp));
        root.addView(sub);

        // ── 启用开关 ──
        LinearLayout swRow = new LinearLayout(ctx);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, 0, 0, (int) (14 * dp));

        TextView swLabel = new TextView(ctx);
        swLabel.setText("启用大模型出牌");
        swLabel.setTextSize(15f);
        swLabel.setTextColor(0xFFE8EEF5);
        swRow.addView(swLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(ctx);
        sw.setChecked(cfg.enabled);
        swRow.addView(sw);
        root.addView(swRow);

        TextView hint = new TextView(ctx);
        hint.setText("关闭时使用本地算法，离线可玩，不消耗任何额度。");
        hint.setTextSize(12f);
        hint.setTextColor(0xFF7B8A9C);
        hint.setPadding(0, 0, 0, (int) (16 * dp));
        root.addView(hint);

        // ── 输入框 ──
        final EditText etBase = field(ctx, theme, root, "Base URL",
                cfg.baseUrl, "https://api.deepseek.com");
        final EditText etKey = field(ctx, theme, root, "API Key",
                cfg.apiKey, "sk-...");
        etKey.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText etModel = field(ctx, theme, root, "模型名",
                cfg.model, "deepseek-chat");

        // ── 快捷预设 ──
        TextView presetLabel = new TextView(ctx);
        presetLabel.setText("快捷预设");
        presetLabel.setTextSize(12.5f);
        presetLabel.setTextColor(0xFF8FA0B4);
        presetLabel.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        root.addView(presetLabel);

        LinearLayout presets = new LinearLayout(ctx);
        presets.setOrientation(LinearLayout.VERTICAL);

        addPreset(ctx, theme, presets, "DeepSeek",
                "https://api.deepseek.com", "deepseek-chat", etBase, etModel);
        addPreset(ctx, theme, presets, "OpenAI",
                "https://api.openai.com", "gpt-4o-mini", etBase, etModel);
        addPreset(ctx, theme, presets, "Kimi (月之暗面)",
                "https://api.moonshot.cn", "moonshot-v1-8k", etBase, etModel);
        addPreset(ctx, theme, presets, "通义千问",
                "https://dashscope.aliyuncs.com/compatible-mode", "qwen-plus", etBase, etModel);

        root.addView(presets);

        // ── 测试按钮 ──
        PillButton btnTest = new PillButton(ctx);
        btnTest.setTheme(Theme.GREEN);
        btnTest.setLabel("测试连接");
        btnTest.setStyle(PillButton.SECONDARY);
        btnTest.setTextSizePx(14 * dp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * dp));
        tlp.setMargins(0, (int) (10 * dp), 0, (int) (14 * dp));
        btnTest.setLayoutParams(tlp);

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 临时写入配置用于测试
                LlmConfig tmp = new LlmConfig(ctx);
                tmp.enabled = true;
                tmp.baseUrl = etBase.getText().toString().trim();
                tmp.apiKey = etKey.getText().toString().trim();
                tmp.model = etModel.getText().toString().trim();
                tmp.temperature = 0.3f;
                tmp.timeoutSec = 20;

                if (!tmp.isUsable()) {
                    Toast.makeText(ctx, "请先填完 Base URL / Key / 模型",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                btnTest.setLabel("测试中…");
                btnTest.setEnabled(false);

                LlmClient c = new LlmClient(tmp);
                c.test(new LlmClient.Callback() {
                    @Override public void onReply(String text) {
                        btnTest.setLabel("测试连接");
                        btnTest.setEnabled(true);
                        Toast.makeText(ctx, "连接成功：" + text.trim(),
                                Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(String msg) {
                        btnTest.setLabel("测试连接");
                        btnTest.setEnabled(true);
                        Toast.makeText(ctx, "失败：" + msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        root.addView(btnTest);

        // ── 底部按钮 ──
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        PillButton cancel = new PillButton(ctx);
        cancel.setTheme(Theme.GREEN);
        cancel.setLabel("取消");
        cancel.setStyle(PillButton.SECONDARY);
        cancel.setTextSizePx(15 * dp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });

        PillButton save = new PillButton(ctx);
        save.setTheme(Theme.GREEN);
        save.setLabel("保存");
        save.setStyle(PillButton.PRIMARY);
        save.setTextSizePx(15 * dp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cfg.enabled = sw.isChecked();
                cfg.baseUrl = etBase.getText().toString().trim();
                cfg.apiKey = etKey.getText().toString().trim();
                cfg.model = etModel.getText().toString().trim();
                cfg.save();
                if (client != null) client.setConfig(cfg);
                d.dismiss();
                if (onSaved != null) onSaved.run();
                Toast.makeText(ctx,
                        cfg.isUsable() ? "已保存，AI 将使用大模型出牌" : "已保存（本地算法模式）",
                        Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, (int) (46 * dp), 1f);
        blp.setMargins((int) (5 * dp), 0, (int) (5 * dp), 0);
        btns.addView(cancel, blp);
        btns.addView(save, blp);
        root.addView(btns);

        sv.addView(root);
        d.setContentView(sv);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.70f;
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.62f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
        }

        d.show();
        return d;
    }

    private static EditText field(Context ctx, Theme theme, LinearLayout parent,
                                  String label, String value, String hintText) {
        float dp = ctx.getResources().getDisplayMetrics().density;

        TextView lb = new TextView(ctx);
        lb.setText(label);
        lb.setTextSize(12.5f);
        lb.setTextColor(0xFF8FA0B4);
        lb.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        parent.addView(lb);

        EditText et = new EditText(ctx);
        et.setText(value);
        et.setHint(hintText);
        et.setTextSize(14f);
        et.setTextColor(0xFFE8EEF5);
        et.setHintTextColor(0xFF566274);
        et.setSingleLine(true);
        et.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0E151D);
        bg.setCornerRadius(9 * dp);
        bg.setStroke((int) (1 * dp), 0xFF2A3644);
        et.setBackground(bg);

        parent.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private static void addPreset(Context ctx, Theme theme, LinearLayout parent,
                                  String name, String base, String model,
                                  final EditText etBase, final EditText etModel) {
        float dp = ctx.getResources().getDisplayMetrics().density;

        PillButton b = new PillButton(ctx);
        b.setTheme(theme);
        b.setLabel(name);
        b.setStyle(PillButton.SECONDARY);
        b.setTextSizePx(13 * dp);

        final String fBase = base;
        final String fModel = model;
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etBase.setText(fBase);
                etModel.setText(fModel);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * dp));
        lp.setMargins(0, (int) (3 * dp), 0, (int) (3 * dp));
        parent.addView(b, lp);
    }
}