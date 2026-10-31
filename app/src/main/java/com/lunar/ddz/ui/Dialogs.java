package com.lunar.ddz.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 通用弹窗构建器。纯代码构建，无 XML 布局依赖。
 * 所有弹窗自动套用主题，圆角 + 遮罩。
 */
public final class Dialogs {

    private Dialogs() {}

    /** 弹窗按钮定义 */
    public static final class Btn {
        public final String text;
        public final int style;
        public final Runnable action;
        public Btn(String text, int style, Runnable action) {
            this.text = text;
            this.style = style;
            this.action = action;
        }
    }

    /**
     * 显示一个标准弹窗。
     * @param title   标题（可空）
     * @param body    正文（可空）
     * @param buttons 按钮列表（最多 2 个会横排，3 个以上竖排）
     */
    public static Dialog show(Context ctx, Theme theme, String title, String body,
                              Btn... buttons) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (22 * density);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, (int) (18 * density));

        // 圆角背景（用 GradientDrawable 替代）
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(theme.panelBg);
        bg.setCornerRadius(20 * density);
        bg.setStroke((int) (1.2f * density), theme.panelBorder);
        root.setBackground(bg);

        if (title != null && title.length() > 0) {
            TextView tv = new TextView(ctx);
            tv.setText(title);
            tv.setTextSize(19f);
            tv.setTextColor(theme.textPrimary);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            tv.setPadding(0, 0, 0, (int) (14 * density));
            root.addView(tv);
        }

        if (body != null && body.length() > 0) {
            ScrollView sv = new ScrollView(ctx);
            TextView tv = new TextView(ctx);
            tv.setText(body);
            tv.setTextSize(14.5f);
            tv.setTextColor(theme.textSecondary);
            tv.setLineSpacing(6 * density, 1f);
            sv.addView(tv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (16 * density);
            root.addView(sv, lp);
        }

        // 按钮区
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(buttons.length <= 2
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        for (int i = 0; i < buttons.length; i++) {
            final Btn b = buttons[i];
            final Dialog dd = d;

            PillButton btn = new PillButton(ctx);
            btn.setTheme(theme);
            btn.setLabel(b.text);
            btn.setStyle(b.style);
            btn.setTextSizePx(15.5f * density);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dd.dismiss();
                    if (b.action != null) b.action.run();
                }
            });

            LinearLayout.LayoutParams lp;
            if (buttons.length <= 2) {
                lp = new LinearLayout.LayoutParams(0,
                        (int) (46 * density), 1f);
                lp.setMargins((int) (5 * density), 0,
                        (int) (5 * density), 0);
            } else {
                lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (46 * density));
                lp.setMargins(0, (int) (5 * density), 0, (int) (5 * density));
            }
            btnRow.addView(btn, lp);
        }
        root.addView(btnRow);

        d.setContentView(root);
        d.setCanceledOnTouchOutside(true);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.62f;
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f);
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
        }

        d.show();
        return d;
    }

    /** 简易提示 */
    public static void alert(Context ctx, Theme theme, String title, String body) {
        show(ctx, theme, title, body, new Btn("好", PillButton.PRIMARY, null));
    }
}