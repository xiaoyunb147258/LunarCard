package com.lunar.ddz.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.ui.CardPainter;
import com.lunar.ddz.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 牌桌主视图 —— 横屏实体牌桌。
 *
 * 布局（横屏）：
 *   ┌────────────────────────────────────────┐
 *   │                 底牌                   │
 *   │  [左家头像]                [右家头像]  │
 *   │      左家出牌        右家出牌          │
 *   │            我出牌区                    │
 *   │                                        │
 *   │  ══════════ 我的手牌 ══════════         │
 *   └────────────────────────────────────────┘
 */
public class TableView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface Listener {
        void onCardTapped(int index);
        void onEmptyTapped();
        void onAnimationSettled();
    }

    private static final int FPS = 60;
    private static final long FRAME_MS = 1000L / FPS;

    private Thread renderThread;
    private volatile boolean running;

    private final CardPainter painter;
    private Theme theme;

    // ── 复用绘制资源 ──
    private final Paint bgPaint = new Paint();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rc = new RectF();
    private final RectF rc2 = new RectF();
    private final Path path = new Path();

    private static final Typeface F_BOLD =
            Typeface.create("sans-serif-medium", Typeface.BOLD);
    private static final Typeface F_NORMAL =
            Typeface.create("sans-serif", Typeface.NORMAL);

    // ── 状态 ──
    private GameState state;
    private final List<Card> myHand = new ArrayList<Card>(20);
    private final List<Integer> selected = new ArrayList<Integer>();
    private final List<RectF> handRects = new ArrayList<RectF>(20);

    private final List<Card>[] tableCards = new List[3];
    private final String[] bubbles = new String[3];
    private final float[] bubbleA = new float[3];
    private final long[] bubbleAt = new long[3];
    private final float[] thinkA = new float[3];

    private final int[] remain = new int[3];
    private int landlord = -1;
    private int activeSeat = -1;

    private final List<Card> bottom = new ArrayList<Card>(3);
    private boolean bottomRevealed = false;

    // ── 我的手牌布局 ──
    private float cardW, cardH, stepX, liftY, handTopY;

    // ── 毛毡程序纹理（一次性生成，缓存） ──
    private int[] feltNoise;
    private int feltNoiseW, feltNoiseH;

    private Listener listener;
    private final Random rnd = new Random(20240101L);

    public TableView(Context ctx) {
        super(ctx);
        this.theme = Theme.GREEN;
        this.painter = new CardPainter(theme);
        getHolder().addCallback(this);
        setZOrderOnTop(false);

        for (int i = 0; i < 3; i++) {
            tableCards[i] = new ArrayList<Card>();
        }
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setTheme(Theme t) {
        this.theme = t;
        this.painter.setTheme(t);
        feltNoise = null;   // 主题变了，纹理重生成
    }

    // ============================================================
    //                      数据接口
    // ============================================================

    public void bindState(GameState gs) { this.state = gs; }

    public void setHand(List<Card> cards) {
        myHand.clear();
        myHand.addAll(cards);
        Deck.sortDesc(myHand);
        selected.clear();
        layoutHand();
    }

    public void setRemain(int seat, int n) { remain[seat] = n; }
    public void setLandlord(int seat) { this.landlord = seat; }
    public void setActiveSeat(int seat) { this.activeSeat = seat; }

    public void setBottom(List<Card> cards, boolean reveal) {
        bottom.clear();
        bottom.addAll(cards);
        bottomRevealed = reveal;
    }

    public void showTableCards(int seat, List<Card> cards) {
        tableCards[seat].clear();
        tableCards[seat].addAll(cards);
    }

    public void clearTableCards() {
        for (int i = 0; i < 3; i++) tableCards[i].clear();
    }

    public void showBubble(int seat, String s) {
        bubbles[seat] = s;
        bubbleA[seat] = 1f;
        bubbleAt[seat] = System.currentTimeMillis();
    }

    public List<Card> getSelectedCards() {
        List<Card> out = new ArrayList<Card>(selected.size());
        for (int idx : selected) {
            if (idx >= 0 && idx < myHand.size()) out.add(myHand.get(idx));
        }
        return out;
    }

    public void clearSelection() {
        selected.clear();
        layoutHand();
    }

    public void selectCards(List<Card> cards) {
        selected.clear();
        if (cards != null) {
            for (Card t : cards) {
                for (int i = 0; i < myHand.size(); i++) {
                    if (myHand.get(i).id == t.id
                            && !selected.contains(Integer.valueOf(i))) {
                        selected.add(Integer.valueOf(i));
                        break;
                    }
                }
            }
        }
        layoutHand();
    }

    public void refreshLayout() { layoutHand(); }

    // ============================================================
    //                      渲染线程
    // ============================================================

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        running = true;
        renderThread = new Thread(this, "TableView");
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        layoutHand();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        boolean retry = true;
        while (retry) {
            try {
                if (renderThread != null) renderThread.join();
                retry = false;
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void run() {
        while (running) {
            long t0 = System.currentTimeMillis();
            Canvas c = null;
            SurfaceHolder h = getHolder();
            try {
                c = h.lockCanvas();
                if (c != null) drawFrame(c);
            } catch (Throwable ignored) {
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
                }
            }
            long cost = System.currentTimeMillis() - t0;
            long sleep = FRAME_MS - cost;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ============================================================
    //                      主绘制
    // ============================================================

    private void drawFrame(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        drawTable(c, w, h);
        drawBottomCards(c, w, h);
        drawOpponent(c, GameState.SEAT_LEFT, w, h);
        drawOpponent(c, GameState.SEAT_RIGHT, w, h);
        drawCenterPlays(c, w, h);
        drawMyHand(c, w, h);
        drawBubbles(c, w, h);
        tickAnimations();
    }

    // ── 桌面：毛毡 + 木框 + 光晕 ──
    private void drawTable(Canvas c, int w, int h) {
        float rim = Math.min(w, h) * 0.022f;

        // 木框
        p.setShader(new LinearGradient(0, 0, 0, h,
                theme.woodLight, theme.woodDark, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // 毛毡（内嵌）
        rc.set(rim, rim, w - rim, h - rim);
        float r = rim * 0.9f;
        p.setShader(new LinearGradient(0, rim, 0, h - rim,
                new int[]{theme.feltTop, theme.feltMid, theme.feltBottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, r, r, p);
        p.setShader(null);

        // 毛毡纹理噪点
        drawFeltNoise(c, rc);

        // 顶部灯光
        p.setShader(new RadialGradient(
                w * 0.5f, h * 0.10f, w * 0.78f,
                Theme.alpha(0xFFFFFF, 0.09f), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, r, r, p);
        p.setShader(null);

        // 四周暗角
        p.setShader(new RadialGradient(
                w * 0.5f, h * 0.48f, w * 0.80f,
                Color.TRANSPARENT, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, r, r, p);
        p.setShader(null);

        // 木框高光内沿
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, rim * 0.22f));
        p.setColor(theme.rimGlow);
        c.drawRoundRect(rc, r, r, p);

        // 木框外深边
        p.setStrokeWidth(Math.max(2f, rim * 0.30f));
        p.setColor(0x66000000);
        rc2.set(rim * 0.35f, rim * 0.35f, w - rim * 0.35f, h - rim * 0.35f);
        c.drawRoundRect(rc2, r * 1.2f, r * 1.2f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawFeltNoise(Canvas c, RectF area) {
        if (feltNoise == null) buildFeltNoise();
        if (feltNoise == null) return;
        // 用极低透明度的小点模拟织物颗粒（性能友好：少量平铺）
        p.setStyle(Paint.Style.FILL);
        int step = 14;
        int n = 0;
        for (float y = area.top; y < area.bottom; y += step) {
            for (float x = area.left + (n % 2) * (step / 2f); x < area.right; x += step) {
                int idx = ((int) x + (int) y * 7) % feltNoise.length;
                int col = feltNoise[idx];
                if ((col & 0xFF) == 0) continue;
                p.setColor(col);
                c.drawCircle(x, y, 1.1f, p);
            }
            n++;
        }
    }

    private void buildFeltNoise() {
        int count = 4096;
        feltNoise = new int[count];
        for (int i = 0; i < count; i++) {
            int r = rnd.nextInt(100);
            if (r < 62) {
                feltNoise[i] = 0x00000000;   // 多数透明
            } else if (r < 88) {
                feltNoise[i] = Theme.alpha(theme.feltNoise, 0.22f);   // 亮点
            } else {
                feltNoise[i] = 0x12000000;   // 暗点
            }
        }
    }

    // ── 底牌（顶部居中）──
    private void drawBottomCards(Canvas c, int w, int h) {
        if (bottom.isEmpty()) return;

        float cw = Math.min(w, h) * 0.098f;
        float ch = cw / CardPainter.ASPECT;
        float gap = cw * 0.16f;
        float total = cw * 3 + gap * 2;
        float x0 = (w - total) / 2f;
        float y = Math.min(w, h) * 0.030f;

        // 底座阴影
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        rc.set(x0 - cw * 0.10f, y - cw * 0.08f,
                x0 + total + cw * 0.10f, y + ch + cw * 0.10f);
        c.drawRoundRect(rc, cw * 0.16f, cw * 0.16f, p);

        for (int i = 0; i < 3 && i < bottom.size(); i++) {
            rc.set(x0 + i * (cw + gap), y, x0 + i * (cw + gap) + cw, y + ch);
            if (bottomRevealed) {
                painter.drawFace(c, rc, bottom.get(i), cw * 0.10f, false);
            } else {
                painter.drawBack(c, rc, cw * 0.10f);
            }
        }
    }

    // ── 对手（左右）──
    private void drawOpponent(Canvas c, int seat, int w, int h) {
        boolean left = (seat == GameState.SEAT_LEFT);
        float base = Math.min(w, h);

        // 头像
        float ar = base * 0.068f;
        float cx = left ? (base * 0.115f) : (w - base * 0.115f);
        float cy = h * 0.30f;

        // 思考脉冲
        float pulse = thinkA[seat];
        if (pulse > 0.01f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ar * 0.18f);
            p.setColor(Theme.alpha(theme.accent, 0.55f * pulse));
            c.drawCircle(cx, cy, ar * (1.10f + 0.16f * (1f - pulse)), p);
            p.setStyle(Paint.Style.FILL);
        }

        // 头像底
        p.setColor(0xCC000000);
        c.drawCircle(cx, cy, ar * 1.04f, p);
        p.setShader(new RadialGradient(cx, cy - ar * 0.3f, ar * 1.2f,
                0xFF2A3444, 0xFF141A24, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, ar, p);
        p.setShader(null);

        // 头像描边
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(ar * (activeSeat == seat ? 0.15f : 0.075f));
        p.setColor(activeSeat == seat ? theme.gold : 0x66FFFFFF);
        c.drawCircle(cx, cy, ar, p);
        p.setStyle(Paint.Style.FILL);

        // 身份文字
        boolean isLord = (landlord == seat);
        txt.setTypeface(F_BOLD);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(ar * 0.46f);
        txt.setColor(isLord ? theme.gold : 0xFFB9C6D4);
        c.drawText(isLord ? "地主" : "农民", cx, cy + ar * 0.17f, txt);

        // 地主王冠
        if (isLord) {
            drawCrown(c, cx, cy - ar * 0.52f, ar * 0.52f, theme.gold);
        }

        // 剩余张数
        float br = ar * 0.44f;
        float bx = left ? cx + ar * 0.74f : cx - ar * 0.74f;
        float by = cy + ar * 0.74f;
        boolean low = remain[seat] <= 2;

        p.setColor(0xDD000000);
        c.drawCircle(bx, by, br * 1.10f, p);
        p.setColor(low ? theme.danger : 0xFF1E2836);
        c.drawCircle(bx, by, br, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(br * 0.13f);
        p.setColor(low ? 0xFFFFDDDD : 0x55FFFFFF);
        c.drawCircle(bx, by, br, p);
        p.setStyle(Paint.Style.FILL);

        txt.setTypeface(F_BOLD);
        txt.setTextSize(br * 1.06f);
        txt.setColor(low ? 0xFFFFFFFF : 0xFFE8EEF5);
        c.drawText(String.valueOf(remain[seat]), bx, by + br * 0.37f, txt);

        // 场上出牌
        List<Card> tc = tableCards[seat];
        if (!tc.isEmpty()) {
            float cw = base * 0.050f;
            float ch = cw / CardPainter.ASPECT;
            float ov = cw * 0.40f;
            int n = tc.size();
            float tw = cw + ov * (n - 1);
            float x0 = left ? (cx + ar * 1.35f) : (cx - ar * 1.35f - tw);
            float y = cy - ch / 2f;

            for (int i = 0; i < n; i++) {
                rc.set(x0 + i * ov, y, x0 + i * ov + cw, y + ch);
                painter.drawFace(c, rc, tc.get(i), cw * 0.09f, false);
            }
        }
    }

    private void drawCrown(Canvas c, float cx, float cy, float w, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        path.reset();
        float h = w * 0.62f;
        path.moveTo(cx - w * 0.5f, cy + h * 0.5f);
        path.lineTo(cx - w * 0.5f, cy - h * 0.15f);
        path.lineTo(cx - w * 0.22f, cy + h * 0.12f);
        path.lineTo(cx, cy - h * 0.5f);
        path.lineTo(cx + w * 0.22f, cy + h * 0.12f);
        path.lineTo(cx + w * 0.5f, cy - h * 0.15f);
        path.lineTo(cx + w * 0.5f, cy + h * 0.5f);
        path.close();
        c.drawPath(path, p);
    }

    // ── 中央出牌区（我方）──
    private void drawCenterPlays(Canvas c, int w, int h) {
        List<Card> tc = tableCards[GameState.SEAT_ME];
        if (tc.isEmpty()) return;

        float base = Math.min(w, h);
        float cw = base * 0.072f;
        float ch = cw / CardPainter.ASPECT;
        float ov = cw * 0.46f;
        int n = tc.size();
        float tw = cw + ov * (n - 1);
        float x0 = (w - tw) / 2f;
        float y = h * 0.545f;

        for (int i = 0; i < n; i++) {
            rc.set(x0 + i * ov, y, x0 + i * ov + cw, y + ch);
            painter.drawFace(c, rc, tc.get(i), cw * 0.11f, false);
        }
    }

    // ── 我的手牌 ──
    private void drawMyHand(Canvas c, int w, int h) {
        if (myHand.isEmpty()) return;

        for (int i = 0; i < handRects.size() && i < myHand.size(); i++) {
            boolean sel = selected.contains(Integer.valueOf(i));
            painter.drawFace(c, handRects.get(i), myHand.get(i),
                    cardH * 0.045f, sel);
        }

        if (!selected.isEmpty()) {
            txt.setTypeface(F_BOLD);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(cardH * 0.155f);
            txt.setColor(theme.gold);
            c.drawText("已选 " + selected.size() + " 张",
                    w / 2f, handTopY - cardH * 0.10f, txt);
        }
    }

    // ── 气泡 ──
    private void drawBubbles(Canvas c, int w, int h) {
        float base = Math.min(w, h);
        for (int seat = 0; seat < 3; seat++) {
            if (bubbles[seat] == null || bubbleA[seat] <= 0.02f) continue;

            float cx, cy;
            if (seat == GameState.SEAT_LEFT) {
                cx = base * 0.27f;
                cy = h * 0.42f;
            } else if (seat == GameState.SEAT_RIGHT) {
                cx = w - base * 0.27f;
                cy = h * 0.42f;
            } else {
                cx = w * 0.5f;
                cy = h * 0.455f;
            }

            txt.setTypeface(F_BOLD);
            txt.setTextSize(base * 0.046f);
            txt.setTextAlign(Paint.Align.CENTER);

            float tw = txt.measureText(bubbles[seat]);
            float ph = base * 0.030f;
            float pv = base * 0.018f;
            float bw = tw + ph * 2;
            float bh = base * 0.046f + pv * 2;
            float by = cy - bh / 2f;

            rc2.set(cx - bw / 2f, by, cx + bw / 2f, by + bh);

            // 阴影
            rc.set(rc2);
            rc.offset(0, bh * 0.10f);
            p.setColor(0x44000000);
            c.drawRoundRect(rc, bh / 2f, bh / 2f, p);

            // 底
            p.setColor(Theme.alpha(0xF5101A24, bubbleA[seat]));
            c.drawRoundRect(rc2, bh / 2f, bh / 2f, p);

            // 边
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(base * 0.0032f);
            p.setColor(Theme.alpha(theme.gold, 0.62f * bubbleA[seat]));
            c.drawRoundRect(rc2, bh / 2f, bh / 2f, p);
            p.setStyle(Paint.Style.FILL);

            txt.setColor(Theme.alpha(0xFFFFFFFF, bubbleA[seat]));
            c.drawText(bubbles[seat], cx, by + bh / 2f + base * 0.016f, txt);
        }
    }

    private void tickAnimations() {
        long now = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            if (bubbleAt[i] > 0) {
                long el = now - bubbleAt[i];
                final long HOLD = 1000, FADE = 400;
                if (el < HOLD) bubbleA[i] = 1f;
                else if (el < HOLD + FADE) bubbleA[i] = 1f - (el - HOLD) / (float) FADE;
                else { bubbleA[i] = 0f; bubbles[i] = null; bubbleAt[i] = 0; }
            }
            // 思考脉冲
            if (activeSeat == i && i != GameState.SEAT_ME) {
                thinkA[i] += (1f - thinkA[i]) * 0.15f;
                if (thinkA[i] > 0.97f) thinkA[i] = 0.15f;   // 循环
            } else {
                thinkA[i] *= 0.85f;
            }
        }
    }

    // ============================================================
    //                      手牌布局
    // ============================================================

    private void layoutHand() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        handRects.clear();
        int n = myHand.size();
        if (n == 0) return;

        float base = Math.min(w, h);
        float areaH = base * 0.285f;      // 手牌区高度
        handTopY = h - areaH;

        cardH = areaH * 0.80f;
        cardW = cardH * CardPainter.ASPECT;
        liftY = areaH * 0.22f;

        float avail = w - base * 0.10f;
        float ideal = cardW * 0.56f;
        stepX = ideal;

        if (n > 1) {
            float need = ideal * (n - 1) + cardW;
            if (need > avail) {
                stepX = (avail - cardW) / (n - 1);
                float min = cardW * 0.24f;
                if (stepX < min) stepX = min;
            }
        }

        float total = stepX * (n - 1) + cardW;
        if (total > w - base * 0.04f) {
            float s = (w - base * 0.04f) / total;
            cardW *= s;
            cardH *= s;
            stepX *= s;
            total = stepX * (n - 1) + cardW;
        }

        float x0 = (w - total) / 2f;
        float yBase = h - cardH - base * 0.020f;

        for (int i = 0; i < n; i++) {
            boolean sel = selected.contains(Integer.valueOf(i));
            float x = x0 + stepX * i;
            float y = yBase - (sel ? liftY : 0);
            handRects.add(new RectF(x, y, x + cardW, y + cardH));
        }
    }

    // ============================================================
    //                      触摸
    // ============================================================

    private boolean dragging = false;
    private int dragFrom = -1;
    private boolean dragMode = true;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int idx = hitTest(x, y);
                if (idx >= 0) {
                    dragging = true;
                    dragFrom = idx;
                    dragMode = !selected.contains(Integer.valueOf(idx));
                    toggle(idx, dragMode);
                    return true;
                }
                if (listener != null) listener.onEmptyTapped();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                int idx = hitTest(x, y);
                if (idx >= 0 && dragFrom >= 0) {
                    int lo = Math.min(dragFrom, idx);
                    int hi = Math.max(dragFrom, idx);
                    boolean changed = false;
                    for (int i = lo; i <= hi; i++) {
                        boolean has = selected.contains(Integer.valueOf(i));
                        if (has != dragMode) {
                            if (dragMode) selected.add(Integer.valueOf(i));
                            else selected.remove(Integer.valueOf(i));
                            changed = true;
                        }
                    }
                    if (changed) layoutHand();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                dragFrom = -1;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void toggle(int idx, boolean on) {
        Integer k = Integer.valueOf(idx);
        if (on) {
            if (!selected.contains(k)) selected.add(k);
        } else {
            selected.remove(k);
        }
        layoutHand();
        if (listener != null) listener.onCardTapped(idx);
    }

    private int hitTest(float x, float y) {
        for (int i = handRects.size() - 1; i >= 0; i--) {
            RectF r = handRects.get(i);
            if (x >= r.left && x <= r.right
                    && y >= r.top && y <= r.bottom + liftY) {
                return i;
            }
        }
        return -1;
    }
}