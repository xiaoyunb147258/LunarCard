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
 * 卡牌绘制器 —— 纯 Canvas 程序化绘制，零素材依赖。
 *
 * 设计要点：
 *  - 白色渐变牌面 + 细边框 + 柔和投影
 *  - 左上角竖排「点数 + 花色」，右下角 180° 镜像
 *  - 中央大花色，用贝塞尔路径绘制，视觉干净
 *  - 大小王有专属绘制
 */
public final class CardPainter {

    /** 宽高比 */
    public static final float ASPECT = 0.70f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();

    private static final Typeface BOLD =
            Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface MED =
            Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Theme theme;

    public CardPainter(Theme t) {
        this.theme = t;
        text.setTypeface(BOLD);
    }

    public void setTheme(Theme t) {
        this.theme = t;
    }

    // ============================================================
    //                      正面
    // ============================================================

    /**
     * 画一张正面牌。
     * @param shadow 阴影偏移（0 = 不画）
     * @param lift   选中高亮
     */
    public void drawFace(Canvas c, RectF r, Card card, float shadow, boolean lift) {
        float radius = r.width() * 0.09f;

        // 投影
        if (shadow > 0.5f) {
            fill.setShader(null);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x33000000);
            tmp.set(r);
            tmp.offset(0f, shadow * 0.55f);
            tmp.inset(-shadow * 0.12f, -shadow * 0.06f);
            c.drawRoundRect(tmp, radius, radius, fill);
        }

        // 牌面
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(
                r.left, r.top, r.left, r.bottom,
                0xFFFFFFFF, 0xFFF2F4F7, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, fill);
        fill.setShader(null);

        // 边框
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, r.width() * 0.014f));
        stroke.setColor(0xFFD8DCE3);
        c.drawRoundRect(r, radius, radius, stroke);

        // 选中高亮
        if (lift) {
            stroke.setColor(theme.accent);
            stroke.setStrokeWidth(Math.max(2f, r.width() * 0.045f));
            RectF g = new RectF(r);
            g.inset(-r.width() * 0.02f, -r.width() * 0.02f);
            c.drawRoundRect(g, radius * 1.2f, radius * 1.2f, stroke);
        }

        int ink = card.isRed() ? theme.cardRed : theme.cardBlack;

        if (card.isJoker()) {
            drawJoker(c, r, card);
            return;
        }

        // 左上角
        drawCorner(c, r, card, ink, false);
        // 右下角（旋转 180°）
        c.save();
        c.rotate(180f, r.centerX(), r.centerY());
        drawCorner(c, r, card, ink, true);
        c.restore();

        // 中央花色
        float size = r.width() * 0.54f;
        drawSuit(c, card.suit,
                r.centerX(), r.centerY() + r.height() * 0.03f,
                size, alpha(ink, 0.90f));
    }

    private void drawCorner(Canvas c, RectF r, Card card, int ink, boolean flipped) {
        float padX = r.width() * 0.10f;
        float padY = r.height() * 0.070f;
        float rankSize = r.width() * 0.46f;
        float suitSize = r.width() * 0.235f;

        text.setTypeface(BOLD);
        text.setColor(ink);
        text.setTextAlign(flipped ? Paint.Align.RIGHT : Paint.Align.LEFT);

        String label = card.rankText();
        boolean wide = label.length() > 1;
        if (wide) rankSize *= 0.76f;
        text.setTextSize(rankSize);

        float x = flipped ? r.right - padX : r.left + padX;
        float baseline = r.top + padY + rankSize * 0.80f;
        c.drawText(label, x, baseline, text);

        float sx = flipped
                ? r.right - padX - suitSize * 0.50f
                : r.left + padX + suitSize * 0.50f;
        float sy = baseline + suitSize * 0.62f;
        drawSuit(c, card.suit, sx, sy, suitSize, ink);
    }

    private void drawJoker(Canvas c, RectF r, Card card) {
        boolean big = card.rank == Card.RANK_BIG_JOKER;
        int ink = big ? theme.cardRed : theme.cardBlack;

        // 中央竖排两字
        text.setTypeface(BOLD);
        text.setColor(ink);
        text.setTextAlign(Paint.Align.CENTER);

        float w = r.width();
        text.setTextSize(w * 0.44f);
        c.drawText(big ? "大" : "小", r.centerX(), r.centerY() - r.height() * 0.02f, text);

        text.setTextSize(w * 0.36f);
        c.drawText("王", r.centerX(), r.centerY() + r.height() * 0.30f, text);

        // 左上角小标
        text.setTextSize(w * 0.24f);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(big ? "大" : "小", r.left + w * 0.09f, r.top + r.height() * 0.15f, text);
        c.drawText("王", r.left + w * 0.09f, r.top + r.height() * 0.27f, text);

        // 装饰星形
        drawStar(c, r.centerX(), r.centerY() - r.height() * 0.30f,
                w * 0.13f, alpha(ink, 0.30f));
    }

    private void drawStar(Canvas c, float cx, float cy, float rad, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        path.reset();
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 5 * i - Math.PI / 2;
            float rr = (i % 2 == 0) ? rad : rad * 0.42f;
            float x = cx + (float) (Math.cos(ang) * rr);
            float y = cy + (float) (Math.sin(ang) * rr);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, fill);
    }

    // ============================================================
    //                      背面
    // ============================================================

    public void drawBack(Canvas c, RectF r, float shadow) {
        float radius = r.width() * 0.09f;

        if (shadow > 0.5f) {
            fill.setShader(null);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x33000000);
            tmp.set(r);
            tmp.offset(0f, shadow * 0.55f);
            c.drawRoundRect(tmp, radius, radius, fill);
        }

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(
                r.left, r.top, r.right, r.bottom,
                theme.backColor1, theme.backColor2, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, fill);
        fill.setShader(null);

        // 斜纹
        c.save();
        c.clipRect(r);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, r.width() * 0.018f));
        stroke.setColor(alpha(theme.backLine, 0.32f));
        float step = r.width() * 0.17f;
        for (float x = r.left - r.height(); x < r.right + r.height(); x += step) {
            c.drawLine(x, r.bottom, x + r.height(), r.top, stroke);
        }
        c.restore();

        // 内框
        stroke.setColor(alpha(theme.backLine, 0.75f));
        stroke.setStrokeWidth(Math.max(1.5f, r.width() * 0.030f));
        RectF inner = new RectF(r);
        inner.inset(r.width() * 0.09f, r.width() * 0.09f);
        c.drawRoundRect(inner, radius * 0.6f, radius * 0.6f, stroke);

        // 中心菱形
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(alpha(theme.backLine, 0.72f));
        float cx = r.centerX(), cy = r.centerY();
        float s = r.width() * 0.19f;
        path.reset();
        path.moveTo(cx, cy - s);
        path.lineTo(cx + s * 0.72f, cy);
        path.lineTo(cx, cy + s);
        path.lineTo(cx - s * 0.72f, cy);
        path.close();
        c.drawPath(path, fill);
    }

    /** 空位占位 */
    public void drawSlot(Canvas c, RectF r, float a) {
        float radius = r.width() * 0.09f;
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(alpha(theme.slotColor, 0.28f * a));
        c.drawRoundRect(r, radius, radius, fill);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, r.width() * 0.02f));
        stroke.setColor(alpha(theme.slotColor, 0.55f * a));
        c.drawRoundRect(r, radius, radius, stroke);
    }

    // ============================================================
    //                      花色
    // ============================================================

    public void drawSuit(Canvas c, int suit, float cx, float cy, float size, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(color);

        float h = size;
        float w = size * 0.86f;

        switch (suit) {
            case Card.SUIT_HEART: {
                path.reset();
                float top = cy - h * 0.40f;
                path.moveTo(cx, cy + h * 0.44f);
                path.cubicTo(cx - w * 0.96f, cy - h * 0.02f,
                        cx - w * 0.50f, top - h * 0.04f,
                        cx, top + h * 0.18f);
                path.cubicTo(cx + w * 0.50f, top - h * 0.04f,
                        cx + w * 0.96f, cy - h * 0.02f,
                        cx, cy + h * 0.44f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case Card.SUIT_DIAMOND: {
                path.reset();
                path.moveTo(cx, cy - h * 0.50f);
                path.lineTo(cx + w * 0.44f, cy);
                path.lineTo(cx, cy + h * 0.50f);
                path.lineTo(cx - w * 0.44f, cy);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case Card.SUIT_SPADE: {
                path.reset();
                float bot = cy + h * 0.46f;
                path.moveTo(cx, cy - h * 0.52f);
                path.cubicTo(cx + w * 0.96f, cy + h * 0.08f,
                        cx + w * 0.38f, bot - h * 0.08f,
                        cx + w * 0.13f, bot - h * 0.12f);
                path.lineTo(cx + w * 0.21f, bot);
                path.lineTo(cx - w * 0.21f, bot);
                path.lineTo(cx - w * 0.13f, bot - h * 0.12f);
                path.cubicTo(cx - w * 0.38f, bot - h * 0.08f,
                        cx - w * 0.96f, cy + h * 0.08f,
                        cx, cy - h * 0.52f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case Card.SUIT_CLUB: {
                float rr = w * 0.255f;
                c.drawCircle(cx, cy - h * 0.25f, rr, fill);
                c.drawCircle(cx - w * 0.26f, cy + h * 0.05f, rr, fill);
                c.drawCircle(cx + w * 0.26f, cy + h * 0.05f, rr, fill);
                path.reset();
                path.moveTo(cx, cy + h * 0.04f);
                path.lineTo(cx + w * 0.15f, cy + h * 0.50f);
                path.lineTo(cx - w * 0.15f, cy + h * 0.50f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            default:
                break;
        }
    }

    // ============================================================
    //                      工具
    // ============================================================

    public static int alpha(int color, float a) {
        int aa = (int) (255 * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (aa << 24);
    }
}