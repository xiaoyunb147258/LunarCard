package com.lunar.ddz.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.lunar.ddz.core.Card;

/**
 * 卡牌绘制器 —— 追求真实纸牌质感。
 *
 * 关键手法：
 *  1. 牌面用多层渐变模拟纸张：顶部高光 → 中部纯白 → 底部极淡灰
 *  2. 外圈用 1px 深色描边 + 内圈用 1px 白色高光，形成「卡纸厚度」错觉
 *  3. 投影分两层：远影（大而淡）+ 近影（小而深），比单层投影立体得多
 *  4. 花色用贝塞尔曲线精确绘制，尖角收口
 *  5. 中央花色带极淡的同色描边，视觉更饱满
 */
public final class CardPainter {

    public static final float ASPECT = 0.694f;   // 标准扑克牌 63:88

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();

    private static final Typeface RANK_FONT =
            Typeface.create("sans-serif-condensed", Typeface.BOLD);

    private Theme theme;

    public CardPainter(Theme t) {
        this.theme = t;
    }

    public void setTheme(Theme t) {
        this.theme = t;
    }

    // ============================================================
    //                        正面
    // ============================================================

    public void drawFace(Canvas c, RectF r, Card card, float elevation, boolean lift) {
        final float radius = r.width() * 0.075f;

        drawShadow(c, r, radius, elevation, lift);

        // ── 牌面底色（纸张渐变）──
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(
                r.left, r.top, r.left, r.bottom,
                new int[]{0xFFFFFFFF, 0xFFFEFEFF, 0xFFF7F8FA, 0xFFF0F2F5},
                new float[]{0f, 0.35f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);

        // ── 外描边（模拟卡片边缘）──
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r.width() * 0.011f));
        p.setColor(lift ? theme.accent : 0xFFC8CCD4);
        c.drawRoundRect(r, radius, radius, p);

        // ── 内高光（纸面反光）──
        if (r.width() > 30f) {
            RectF inner = new RectF(r);
            float inset = Math.max(1f, r.width() * 0.014f);
            inner.inset(inset, inset);
            p.setStrokeWidth(Math.max(1f, r.width() * 0.007f));
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(inner, radius * 0.9f, radius * 0.9f, p);
        }

        if (lift) {
            p.setStrokeWidth(Math.max(2.5f, r.width() * 0.048f));
            p.setColor(Theme.alpha(theme.accent, 0.55f));
            RectF g = new RectF(r);
            float o = r.width() * 0.018f;
            g.inset(-o, -o);
            c.drawRoundRect(g, radius * 1.25f, radius * 1.25f, p);
        }
        p.setStyle(Paint.Style.FILL);

        int ink = card.isRed() ? theme.cardRed : theme.cardBlack;

        if (card.isJoker()) {
            drawJoker(c, r, card);
            return;
        }

        drawCorner(c, r, card, ink, false);

        c.save();
        c.rotate(180f, r.centerX(), r.centerY());
        drawCorner(c, r, card, ink, true);
        c.restore();

        // 中央花色：带淡淡同色底衬，更饱满
        float cx = r.centerX();
        float cy = r.centerY() + r.height() * 0.015f;
        float size = r.width() * 0.50f;
        drawSuit(c, card.suit, cx, cy, size * 1.04f, Theme.alpha(ink, 0.13f));
        drawSuit(c, card.suit, cx, cy, size, ink);
    }

    private void drawShadow(Canvas c, RectF r, float radius, float elevation, boolean lift) {
        float e = elevation + (lift ? r.height() * 0.055f : 0f);
        if (e < 0.8f) return;

        p.setStyle(Paint.Style.FILL);
        p.setShader(null);

        // 远影：扩散大、透明度低
        p.setColor(0x18000000);
        tmp.set(r);
        tmp.offset(0f, e * 0.85f);
        tmp.inset(-e * 0.55f, -e * 0.20f);
        c.drawRoundRect(tmp, radius + e * 0.5f, radius + e * 0.5f, p);

        // 近影：紧凑、较深
        p.setColor(0x30000000);
        tmp.set(r);
        tmp.offset(0f, e * 0.34f);
        tmp.inset(-e * 0.10f, -e * 0.02f);
        c.drawRoundRect(tmp, radius, radius, p);
    }

    private void drawCorner(Canvas c, RectF r, Card card, int ink, boolean flipped) {
        float padX = r.width() * 0.088f;
        float padTop = r.height() * 0.055f;

        float rankSize = r.width() * 0.455f;
        String label = card.rankText();
        if (label.length() > 1) rankSize *= 0.74f;

        textPaint.setTypeface(RANK_FONT);
        textPaint.setColor(ink);
        textPaint.setTextSize(rankSize);
        textPaint.setTextAlign(flipped ? Paint.Align.RIGHT : Paint.Align.LEFT);

        float x = flipped ? r.right - padX : r.left + padX;
        float baseY = r.top + padTop + rankSize * 0.78f;
        c.drawText(label, x, baseY, textPaint);

        float suitSize = r.width() * 0.225f;
        float sx = flipped
                ? r.right - padX - suitSize * 0.46f
                : r.left + padX + suitSize * 0.46f;
        float sy = baseY + suitSize * 0.60f;
        drawSuit(c, card.suit, sx, sy, suitSize, ink);
    }

    private void drawJoker(Canvas c, RectF r, Card card) {
        boolean big = card.rank == Card.RANK_BIG_JOKER;
        int ink = big ? theme.cardRed : theme.cardBlack;
        float w = r.width();

        // 中央竖排
        textPaint.setTypeface(RANK_FONT);
        textPaint.setColor(ink);
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint.setTextSize(w * 0.42f);
        c.drawText(big ? "大" : "小", r.centerX(), r.centerY() - r.height() * 0.015f, textPaint);

        textPaint.setTextSize(w * 0.34f);
        c.drawText("王", r.centerX(), r.centerY() + r.height() * 0.295f, textPaint);

        // 顶部小标
        textPaint.setTextSize(w * 0.215f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        c.drawText(big ? "大" : "小", r.left + w * 0.085f, r.top + r.height() * 0.135f, textPaint);
        c.drawText("王", r.left + w * 0.085f, r.top + r.height() * 0.25f, textPaint);

        // 装饰星
        drawStar(c, r.centerX(), r.centerY() - r.height() * 0.285f,
                w * 0.115f, Theme.alpha(ink, 0.22f));
    }

    private void drawStar(Canvas c, float cx, float cy, float rad, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        path.reset();
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 5 * i - Math.PI / 2;
            float rr = (i % 2 == 0) ? rad : rad * 0.40f;
            float x = cx + (float) (Math.cos(ang) * rr);
            float y = cy + (float) (Math.sin(ang) * rr);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, p);
    }

    // ============================================================
    //                        背面
    // ============================================================

    public void drawBack(Canvas c, RectF r, float elevation) {
        final float radius = r.width() * 0.075f;
        drawShadow(c, r, radius, elevation, false);

        // 底
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(
                r.left, r.top, r.right, r.bottom,
                theme.backColor1, theme.backColor2, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);

        // 菱形网格纹
        c.save();
        c.clipRect(r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r.width() * 0.012f));
        p.setColor(Theme.alpha(theme.backLine, 0.20f));
        float step = r.width() * 0.155f;
        for (float x = r.left - r.height(); x < r.right + r.height(); x += step) {
            c.drawLine(x, r.bottom, x + r.height(), r.top, p);
            c.drawLine(x, r.top, x + r.height(), r.bottom, p);
        }
        c.restore();

        // 内框
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.4f, r.width() * 0.026f));
        p.setColor(Theme.alpha(theme.backLine, 0.62f));
        RectF inner = new RectF(r);
        inner.inset(r.width() * 0.082f, r.width() * 0.082f);
        c.drawRoundRect(inner, radius * 0.55f, radius * 0.55f, p);

        // 中心徽记：菱形 + 圆
        float cx = r.centerX(), cy = r.centerY();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(theme.backLine, 0.68f));
        float s = r.width() * 0.175f;
        path.reset();
        path.moveTo(cx, cy - s);
        path.lineTo(cx + s * 0.70f, cy);
        path.lineTo(cx, cy + s);
        path.lineTo(cx - s * 0.70f, cy);
        path.close();
        c.drawPath(path, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r.width() * 0.014f));
        p.setColor(Theme.alpha(theme.backLine, 0.40f));
        c.drawCircle(cx, cy, s * 1.55f, p);

        // 外描边
        p.setStrokeWidth(Math.max(1f, r.width() * 0.011f));
        p.setColor(0xFFB9BFC9);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** 空位占位 */
    public void drawSlot(Canvas c, RectF r, float a) {
        float radius = r.width() * 0.075f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(0xFFFFFF, 0.10f * a));
        c.drawRoundRect(r, radius, radius, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r.width() * 0.022f));
        p.setColor(Theme.alpha(0xFFFFFF, 0.26f * a));
        c.drawRoundRect(r, radius, radius, p);
    }

    // ============================================================
    //                        花色
    // ============================================================

    public void drawSuit(Canvas c, int suit, float cx, float cy, float size, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(color);

        float h = size;
        float w = size * 0.84f;

        switch (suit) {
            case Card.SUIT_HEART: {
                path.reset();
                float top = cy - h * 0.38f;
                path.moveTo(cx, cy + h * 0.45f);
                path.cubicTo(cx - w * 0.98f, cy - h * 0.04f,
                        cx - w * 0.48f, top - h * 0.10f,
                        cx, top + h * 0.16f);
                path.cubicTo(cx + w * 0.48f, top - h * 0.10f,
                        cx + w * 0.98f, cy - h * 0.04f,
                        cx, cy + h * 0.45f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case Card.SUIT_DIAMOND: {
                path.reset();
                path.moveTo(cx, cy - h * 0.50f);
                path.lineTo(cx + w * 0.425f, cy);
                path.lineTo(cx, cy + h * 0.50f);
                path.lineTo(cx - w * 0.425f, cy);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case Card.SUIT_SPADE: {
                path.reset();
                float bot = cy + h * 0.47f;
                path.moveTo(cx, cy - h * 0.52f);
                path.cubicTo(cx + w * 0.98f, cy + h * 0.06f,
                        cx + w * 0.34f, bot - h * 0.10f,
                        cx + w * 0.115f, bot - h * 0.14f);
                path.lineTo(cx + w * 0.195f, bot);
                path.lineTo(cx - w * 0.195f, bot);
                path.lineTo(cx - w * 0.115f, bot - h * 0.14f);
                path.cubicTo(cx - w * 0.34f, bot - h * 0.10f,
                        cx - w * 0.98f, cy + h * 0.06f,
                        cx, cy - h * 0.52f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case Card.SUIT_CLUB: {
                float rr = w * 0.242f;
                c.drawCircle(cx, cy - h * 0.255f, rr, p);
                c.drawCircle(cx - w * 0.245f, cy + h * 0.045f, rr, p);
                c.drawCircle(cx + w * 0.245f, cy + h * 0.045f, rr, p);
                path.reset();
                path.moveTo(cx, cy + h * 0.035f);
                path.lineTo(cx + w * 0.135f, cy + h * 0.51f);
                path.lineTo(cx - w * 0.135f, cy + h * 0.51f);
                path.close();
                c.drawPath(path, p);
                break;
            }
            default:
                break;
        }
    }

    public static int alpha(int color, float a) {
        int aa = (int) (255 * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (aa << 24);
    }
}