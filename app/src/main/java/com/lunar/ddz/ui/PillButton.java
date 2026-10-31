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
 * 圆角按钮 —— 带立体感的实体按键。
 *
 * 三档样式：
 *   PRIMARY   金色实心，用于「出牌」这类主操作
 *   SECONDARY 半透明深色玻璃 + 金色细边，用于次级操作
 *   DANGER    红色，用于危险操作
 *
 * 质感要点：
 *   - 顶部高光渐变 + 底部暗角，形成凸起感
 *   - 外圈描边 + 内圈白色高光，模拟材质厚度
 *   - 按下时整体缩放 + 压暗
 */
public class PillButton extends View {

    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int DANGER = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();

    private Theme theme;
    private String label = "";
    private int style = SECONDARY;
    private boolean enabled = true;
    private float press = 0f;

    private static final Typeface TF =
            Typeface.create("sans-serif-medium", Typeface.BOLD);

    public PillButton(Context c) {
        super(c);
        theme = Theme.GREEN;
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
        setAlpha(e ? 1f : 0.40f);
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
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float shrink = press * 0.030f;
        float inset = Math.min(w, h) * shrink;
        rect.set(inset, inset, w - inset, h - inset);
        float radius = rect.height() * 0.5f;

        // ── 投影 ──
        if (style != SECONDARY) {
            fill.setStyle(Paint.Style.FILL);
            fill.setShader(null);
            fill.setColor(0x40000000);
            RectF sh = new RectF(rect);
            sh.offset(0, rect.height() * 0.09f);
            c.drawRoundRect(sh, radius, radius, fill);
        }

        // ── 底色 ──
        int top, bottom, edge;
        switch (style) {
            case PRIMARY:
                top = lighten(theme.accent, 0.22f);
                bottom = darken(theme.accent, 0.18f);
                edge = theme.accentDeep;
                break;
            case DANGER:
                top = lighten(theme.danger, 0.16f);
                bottom = darken(theme.danger, 0.20f);
                edge = darken(theme.danger, 0.42f);
                break;
            default: {
                top = 0xD92A3846;
                bottom = 0xD016212C;
                edge = Theme.alpha(theme.gold, 0.55f);
                break;
            }
        }

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                top, bottom, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        // ── 顶部高光 ──
        inner.set(rect);
        float hi = rect.height() * 0.42f;
        inner.bottom = inner.top + hi;
        fill.setShader(new LinearGradient(
                inner.left, inner.top, inner.left, inner.bottom,
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(inner, radius, radius * 0.8f, fill);
        fill.setShader(null);

        // ── 按下压暗 ──
        if (press > 0.01f) {
            fill.setColor(0x33000000);
            c.drawRoundRect(rect, radius, radius, fill);
        }

        // ── 描边 ──
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.2f, rect.height() * 0.030f));
        stroke.setColor(edge);
        c.drawRoundRect(rect, radius, radius, stroke);

        // ── 文字 ──
        int ink = (style == SECONDARY) ? 0xFFF0F4F8 : theme.textOnAccent;
        float size = text.getTextSize();
        if (size <= 0f) size = rect.height() * 0.40f;
        text.setTextSize(size);
        text.setColor(ink);

        if (style != SECONDARY) {
            text.setShadowLayer(size * 0.10f, 0f, size * 0.035f, 0x66000000);
        } else {
            text.setShadowLayer(size * 0.10f, 0f, size * 0.030f, 0x99000000);
        }

        c.drawText(label, rect.centerX(),
                rect.centerY() + size * 0.355f, text);
        text.clearShadowLayer();
        fill.setStyle(Paint.Style.FILL);
    }

    private static int lighten(int color, float f) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * (1 - f));
        int g = (int) (((color >> 8) & 0xFF) * (1 - f));
        int b = (int) ((color & 0xFF) * (1 - f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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