package com.lunar.ddz.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * 程序化绘制的圆角按钮。
 *
 * 三种样式：PRIMARY（实心强调色）/ SECONDARY（描边）/ DANGER（红色）
 * 带按下缩放与暗化反馈，触感接近原生 Material 按钮。
 */
public class PillButton extends View {

    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int DANGER = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Theme theme;
    private String label = "";
    private int style = SECONDARY;
    private boolean enabled = true;
    private float press = 0f;   // 0..1，按下动画

    private static final Typeface TF =
            Typeface.create("sans-serif-medium", Typeface.BOLD);

    public PillButton(Context c) {
        super(c);
        theme = Theme.EMERALD;
        text.setTypeface(TF);
        text.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    public void setTheme(Theme t) {
        theme = t;
        invalidate();
    }

    public void setLabel(String s) {
        label = s == null ? "" : s;
        invalidate();
    }

    public void setStyle(int s) {
        style = s;
        invalidate();
    }

    @Override
    public void setEnabled(boolean e) {
        enabled = e;
        setAlpha(e ? 1f : 0.42f);
        invalidate();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setTextSizePx(float px) {
        text.setTextSize(px);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float shrink = press * 0.035f;
        float inset = Math.min(w, h) * shrink;
        rect.set(inset, inset, w - inset, h - inset);

        float radius = rect.height() * 0.5f;

        int base;
        switch (style) {
            case PRIMARY: base = theme.accent; break;
            case DANGER: base = theme.danger; break;
            default: base = theme.panelBg; break;
        }

        // 底色
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Theme.alpha(base, style == SECONDARY ? 0.85f : 1f),
                Theme.alpha(base, style == SECONDARY ? 0.62f : 0.86f),
                Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        // 按下暗化
        if (press > 0.01f) {
            fill.setColor(0x33000000);
            c.drawRoundRect(rect, radius, radius, fill);
        }

        // 描边
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, rect.height() * 0.035f));
        stroke.setColor(style == SECONDARY
                ? Theme.alpha(theme.panelBorder, 1f)
                : Theme.alpha(base, 0.9f));
        c.drawRoundRect(rect, radius, radius, stroke);

        // 文字
        int ink;
        if (style == SECONDARY) ink = theme.textPrimary;
        else ink = theme.textOnAccent;

        float size = text.getTextSize();
        if (size <= 0f) size = rect.height() * 0.42f;
        text.setTextSize(size);
        text.setColor(ink);
        c.drawText(label, rect.centerX(),
                rect.centerY() + size * 0.355f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                press = 1f;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                press = 0f;
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                press = 0f;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}