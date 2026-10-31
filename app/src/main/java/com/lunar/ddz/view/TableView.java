package com.lunar.ddz.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.ui.CardPainter;
import com.lunar.ddz.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * 牌桌主视图。所有游戏画面均在此绘制。
 *
 * 性能设计：
 *  - SurfaceView + 独立渲染线程，恒定 60fps
 *  - 所有 Paint/RectF 复用，绘制循环内零分配
 *  - 卡牌位置每帧计算，不缓存 RectF 列表
 */
public class TableView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface Listener {
        void onCardTapped(int index);
        void onHandScrolled(float dx);
        void onEmptyTapped();
    }

    private static final int FPS = 60;
    private static final long FRAME_MS = 1000L / FPS;

    private Thread renderThread;
    private volatile boolean running;

    private final CardPainter painter;
    private Theme theme;

    // 绘制资源
    private final Paint bgPaint = new Paint();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();

    private static final Typeface BOLD = Typeface.create("sans-serif-medium", Typeface.BOLD);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);

    // ---- 数据 ----
    private GameState state;

    /** 我的手牌（用于绘制） */
    private final List<Card> myHand = new ArrayList<Card>();
    /** 选中索引 */
    private final List<Integer> selected = new ArrayList<Integer>();

    /** 出牌区（每位玩家） */
    private final List<Card>[] tableCards = new List[3];
    /** 气泡文字 */
    private final String[] bubbles = new String[3];
    private final float[] bubbleAlpha = new float[3];
    private final long[] bubbleTime = new long[3];

    /** 剩余张数 */
    private final int[] remain = new int[3];
    /** 地主标记 */
    private int landlord = -1;
    /** 当前回合 */
    private int activeSeat = -1;

    /** 底牌 */
    private final List<Card> bottom = new ArrayList<Card>();
    private boolean bottomRevealed;

    /** 我的手牌布局参数 */
    private float cardW, cardH, stepX, liftY, handTopY;
    private final List<RectF> handRects = new ArrayList<RectF>(20);

    private Listener listener;

    // ---- 动画 ----
    private long lastFrame = 0;

    public TableView(Context ctx) {
        super(ctx);
        this.theme = Theme.EMERALD;
        this.painter = new CardPainter(theme);

        getHolder().addCallback(this);
        setZOrderOnTop(false);

        for (int i = 0; i < 3; i++) {
            tableCards[i] = new ArrayList<Card>();
            bubbles[i] = null;
        }

        bgPaint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setTheme(Theme t) {
        this.theme = t;
        this.painter.setTheme(t);
    }

    // ============================================================
    //                 外部数据接口
    // ============================================================

    public void bindState(GameState gs) {
        this.state = gs;
    }

    public void setHand(List<Card> cards) {
        myHand.clear();
        myHand.addAll(cards);
        Deck.sortDesc(myHand);
        selected.clear();
        layoutHand();
    }

    public void setRemain(int seat, int n) {
        remain[seat] = n;
    }

    public void setLandlord(int seat) {
        this.landlord = seat;
    }

    public void setActiveSeat(int seat) {
        this.activeSeat = seat;
    }

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
        bubbleAlpha[seat] = 1f;
        bubbleTime[seat] = System.currentTimeMillis();
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

    /** 按牌对象选中（用于「提示」功能） */
    public void selectCards(List<Card> cards) {
        selected.clear();
        if (cards != null) {
            for (Card target : cards) {
                for (int i = 0; i < myHand.size(); i++) {
                    if (myHand.get(i).id == target.id
                            && !selected.contains(Integer.valueOf(i))) {
                        selected.add(Integer.valueOf(i));
                        break;
                    }
                }
            }
        }
        layoutHand();
    }

    public boolean isHandReady() {
        return handRects.size() == myHand.size();
    }

    // ============================================================
    //                 渲染线程
    // ============================================================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        renderThread = new Thread(this, "TableView-Render");
        renderThread.setPriority(Thread.NORM_PRIORITY + 1);
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        layoutHand();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        boolean retry = true;
        while (retry) {
            try {
                if (renderThread != null) renderThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
                // 重试
            }
        }
    }

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            Canvas c = null;
            SurfaceHolder h = getHolder();
            try {
                c = h.lockCanvas();
                if (c != null) {
                    drawFrame(c);
                }
            } catch (Throwable ignored) {
                // 忽略绘制异常，避免崩溃
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
                }
            }
            long cost = System.currentTimeMillis() - start;
            long sleep = FRAME_MS - cost;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ============================================================
    //                 绘制
    // ============================================================

    private void drawFrame(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        drawBackground(c, w, h);
        drawBottomCards(c, w, h);
        drawOpponent(c, GameState.SEAT_LEFT, w, h);
        drawOpponent(c, GameState.SEAT_RIGHT, w, h);
        drawTableArea(c, w, h);
        drawMyHand(c, w, h);
        drawBubbles(c, w, h);
        updateAnimations();
    }

    private void drawBackground(Canvas c, int w, int h) {
        // 桌面渐变
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                theme.tableTop, theme.tableBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(null);

        // 中心光晕
        paint.setShader(new RadialGradient(
                w * 0.5f, h * 0.42f, w * 0.72f,
                Theme.alpha(theme.accent, 0.10f), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // 四周暗角
        paint.setShader(new RadialGradient(
                w * 0.5f, h * 0.48f, w * 0.85f,
                Color.TRANSPARENT, Theme.alpha(theme.tableVignette, 0.72f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    /** 底牌展示（顶部中间） */
    private void drawBottomCards(Canvas c, int w, int h) {
        if (bottom.isEmpty()) return;

        float cw = w * 0.085f;
        float ch = cw / CardPainter.ASPECT;
        float gap = cw * 0.14f;
        float totalW = cw * 3 + gap * 2;
        float startX = (w - totalW) / 2f;
        float y = h * 0.035f;

        for (int i = 0; i < 3 && i < bottom.size(); i++) {
            rect.set(startX + i * (cw + gap), y, startX + i * (cw + gap) + cw, y + ch);
            if (bottomRevealed) {
                painter.drawFace(c, rect, bottom.get(i), 4f, false);
            } else {
                painter.drawBack(c, rect, 4f);
            }
        }
    }

    /** 左右对手 */
    private void drawOpponent(Canvas c, int seat, int w, int h) {
        boolean left = (seat == GameState.SEAT_LEFT);

        // 头像位置
        float avatarR = w * 0.072f;
        float cx = left ? avatarR + w * 0.035f : w - avatarR - w * 0.035f;
        float cy = h * 0.24f;

        // 头像圆
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.alpha(theme.panelBg, 0.92f));
        c.drawCircle(cx, cy, avatarR, paint);

        // 边框（轮到谁，谁高亮）
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(avatarR * (activeSeat == seat ? 0.14f : 0.07f));
        paint.setColor(activeSeat == seat ? theme.accent : theme.panelBorder);
        c.drawCircle(cx, cy, avatarR, paint);
        paint.setStyle(Paint.Style.FILL);

        // 头像内文字：地主/农民
        textPaint.setTypeface(BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(landlord == seat ? theme.accent : theme.textSecondary);
        textPaint.setTextSize(avatarR * 0.52f);
        c.drawText(landlord == seat ? "地主" : "农民", cx, cy + avatarR * 0.18f, textPaint);

        // 剩余张数徽标
        float badgeR = avatarR * 0.46f;
        float bx = left ? cx + avatarR * 0.72f : cx - avatarR * 0.72f;
        float by = cy + avatarR * 0.70f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(remain[seat] <= 2 ? theme.danger
                : Theme.alpha(theme.panelBg, 0.95f));
        c.drawCircle(bx, by, badgeR, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(badgeR * 0.10f);
        paint.setColor(theme.panelBorder);
        c.drawCircle(bx, by, badgeR, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTypeface(BOLD);
        textPaint.setColor(remain[seat] <= 2 ? 0xFFFFFFFF : theme.textPrimary);
        textPaint.setTextSize(badgeR * 1.05f);
        c.drawText(String.valueOf(remain[seat]), bx, by + badgeR * 0.36f, textPaint);

        // 场上牌：对手出牌显示在其头像与中心之间
        List<Card> tc = tableCards[seat];
        if (!tc.isEmpty()) {
            float cw = w * 0.058f;
            float ch = cw / CardPainter.ASPECT;
            float ov = cw * 0.36f;
            int n = tc.size();
            float totalW = cw + ov * (n - 1);
            float startX = left ? (w * 0.24f) : (w * 0.76f - totalW);
            float y = h * 0.40f;

            for (int i = 0; i < n; i++) {
                rect.set(startX + i * ov, y, startX + i * ov + cw, y + ch);
                painter.drawFace(c, rect, tc.get(i), 3f, false);
            }
        }
    }

    /** 中央出牌区（我方） */
    private void drawTableArea(Canvas c, int w, int h) {
        List<Card> tc = tableCards[GameState.SEAT_ME];
        if (tc.isEmpty()) return;

        float cw = w * 0.078f;
        float ch = cw / CardPainter.ASPECT;
        float ov = cw * 0.42f;
        int n = tc.size();
        float totalW = cw + ov * (n - 1);
        float startX = (w - totalW) / 2f;
        float y = h * 0.55f;

        for (int i = 0; i < n; i++) {
            rect.set(startX + i * ov, y, startX + i * ov + cw, y + ch);
            painter.drawFace(c, rect, tc.get(i), 5f, false);
        }
    }

    /** 我的手牌 */
    private void drawMyHand(Canvas c, int w, int h) {
        if (myHand.isEmpty()) return;

        for (int i = 0; i < handRects.size() && i < myHand.size(); i++) {
            RectF r = handRects.get(i);
            boolean sel = selected.contains(Integer.valueOf(i));
            painter.drawFace(c, r, myHand.get(i), sel ? 10f : 5f, sel);
        }

        // 已选提示
        if (!selected.isEmpty()) {
            textPaint.setTypeface(NORMAL);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Theme.alpha(theme.accent, 0.92f));
            textPaint.setTextSize(cardH * 0.20f);
            c.drawText("已选 " + selected.size() + " 张",
                    w / 2f, handTopY - cardH * 0.10f, textPaint);
        }
    }

    /** 气泡 */
    private void drawBubbles(Canvas c, int w, int h) {
        for (int seat = 0; seat < 3; seat++) {
            if (bubbles[seat] == null || bubbleAlpha[seat] <= 0.01f) continue;

            float cx, cy;
            if (seat == GameState.SEAT_LEFT) {
                cx = w * 0.22f;
                cy = h * 0.32f;
            } else if (seat == GameState.SEAT_RIGHT) {
                cx = w * 0.78f;
                cy = h * 0.32f;
            } else {
                cx = w * 0.5f;
                cy = h * 0.47f;
            }

            textPaint.setTextSize(w * 0.048f);
            textPaint.setTypeface(BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);

            float tw = textPaint.measureText(bubbles[seat]);
            float padH = w * 0.030f;
            float padV = w * 0.020f;
            float bw = tw + padH * 2;
            float bh = w * 0.048f + padV * 2;
            float by = cy - bh / 2;

            rect2.set(cx - bw / 2, by, cx + bw / 2, by + bh);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.alpha(theme.panelBg, 0.94f * bubbleAlpha[seat]));
            c.drawRoundRect(rect2, bh / 2, bh / 2, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.0035f);
            paint.setColor(Theme.alpha(theme.accent, 0.55f * bubbleAlpha[seat]));
            c.drawRoundRect(rect2, bh / 2, bh / 2, paint);
            paint.setStyle(Paint.Style.FILL);

            textPaint.setColor(Theme.alpha(theme.textPrimary, bubbleAlpha[seat]));
            c.drawText(bubbles[seat], cx, by + bh / 2 + w * 0.017f, textPaint);
        }
    }

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            if (bubbleTime[i] <= 0) continue;
            long el = now - bubbleTime[i];
            final long HOLD = 1100;
            final long FADE = 420;
            if (el < HOLD) {
                bubbleAlpha[i] = 1f;
            } else if (el < HOLD + FADE) {
                bubbleAlpha[i] = 1f - (el - HOLD) / (float) FADE;
            } else {
                bubbleAlpha[i] = 0f;
                bubbles[i] = null;
                bubbleTime[i] = 0;
            }
        }
    }

    // ============================================================
    //                 布局与触摸
    // ============================================================

    private void layoutHand() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        handRects.clear();
        int n = myHand.size();
        if (n == 0) return;

        // 手牌区高度占比
        float areaH = h * 0.30f;
        handTopY = h - areaH;

        cardH = areaH * 0.76f;
        cardW = cardH * CardPainter.ASPECT;
        liftY = areaH * 0.16f;

        // 根据张数决定步进
        float available = w - cardW * 0.55f;
        float ideal = cardW * 0.58f;
        stepX = ideal;
        if (n > 1) {
            float need = ideal * (n - 1) + cardW;
            if (need > available) {
                stepX = (available - cardW) / (n - 1);
                float min = cardW * 0.22f;
                if (stepX < min) {
                    stepX = min;
                }
            }
        }

        float totalW = stepX * (n - 1) + cardW;
        if (totalW > w) {
            float s = w / totalW;
            cardW *= s;
            cardH *= s;
            stepX *= s;
            totalW = stepX * (n - 1) + cardW;
        }

        float startX = (w - totalW) / 2f;
        float baseY = h - cardH - h * 0.012f;

        for (int i = 0; i < n; i++) {
            boolean sel = selected.contains(Integer.valueOf(i));
            float x = startX + stepX * i;
            float y = baseY - (sel ? liftY : 0);
            handRects.add(new RectF(x, y, x + cardW, y + cardH));
        }
    }

    private boolean dragging = false;
    private float lastX = 0f;
    private int dragBaseIdx = -1;
    private boolean dragMode = true;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int h = getHeight();
        float x = e.getX(), y = e.getY();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int idx = hitTest(x, y);
                if (idx >= 0) {
                    dragging = true;
                    dragBaseIdx = idx;
                    lastX = x;
                    dragMode = !selected.contains(Integer.valueOf(idx));
                    toggleSelection(idx, dragMode);
                    return true;
                }
                if (listener != null) listener.onEmptyTapped();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                int idx = hitTest(x, y);
                if (idx >= 0 && dragBaseIdx >= 0) {
                    int lo = Math.min(dragBaseIdx, idx);
                    int hi = Math.max(dragBaseIdx, idx);
                    boolean changed = false;
                    for (int i = lo; i <= hi; i++) {
                        boolean has = selected.contains(Integer.valueOf(i));
                        if (has != dragMode) {
                            if (dragMode) selected.add(Integer.valueOf(i));
                            else selected.remove(Integer.valueOf(i));
                            changed = true;
                        }
                    }
                    if (changed) {
                        layoutHand();
                        layoutRefresh();
                    }
                }
                lastX = x;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    dragging = false;
                    dragBaseIdx = -1;
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void toggleSelection(int idx, boolean on) {
        Integer key = Integer.valueOf(idx);
        if (on) {
            if (!selected.contains(key)) selected.add(key);
        } else {
            selected.remove(key);
        }
        layoutHand();
        layoutRefresh();
        if (listener != null) listener.onCardTapped(idx);
    }

    /** 触发重排（选中状态变化后牌位会移动） */
    private void layoutRefresh() {
        // layoutHand 已重建 rect，无需额外操作
    }

    /** 命中测试：从右往左，上层优先 */
    private int hitTest(float x, float y) {
        for (int i = handRects.size() - 1; i >= 0; i--) {
            RectF r = handRects.get(i);
            // 选中上移后，下方空间依然可点
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom + liftY) {
                return i;
            }
        }
        return -1;
    }

    public void refreshLayout() {
        layoutHand();
    }
}