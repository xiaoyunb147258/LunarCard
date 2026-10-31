package com.lunar.ddz;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lunar.ddz.ai.LlmBrain;
import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Controller;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.data.Prefs;
import com.lunar.ddz.net.LlmClient;
import com.lunar.ddz.net.LlmConfig;
import com.lunar.ddz.ui.Dialogs;
import com.lunar.ddz.ui.PillButton;
import com.lunar.ddz.ui.SettingsDialog;
import com.lunar.ddz.ui.Theme;
import com.lunar.ddz.view.TableView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 —— 横屏斗地主。
 *
 * 布局（横屏）：
 *   顶部左：积分 / 胜率          顶部右：AI 设置 / 菜单
 *   中间：牌桌
 *   底部靠手牌上方：操作按钮（出牌/不要/提示/叫分）
 */
public class MainActivity extends Activity
        implements Controller.Events, TableView.Listener {

    private Prefs prefs;
    private Theme theme;
    private LlmConfig llmConfig;
    private LlmClient llmClient;

    private Controller controller;

    private FrameLayout root;
    private TableView table;

    private TextView statsText;
    private TextView aiBadge;
    private LinearLayout actionBar;
    private LinearLayout bidBar;

    private PillButton btnHint, btnPass, btnPlay;
    private PillButton btnBid0, btnBid1, btnBid2, btnBid3;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (controller != null) {
                controller.tick(System.currentTimeMillis());
            }
            ui.postDelayed(this, 100);
        }
    };

    // ============================================================
    //                      生命周期
    // ============================================================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = new Prefs(this);
        theme = Theme.of(prefs.skin());
        llmConfig = new LlmConfig(this);
        llmClient = new LlmClient(llmConfig);

        setupWindow();
        buildUi();
        startNewGame();
        ui.post(ticker);

        if (!prefs.tutorialShown()) {
            prefs.setTutorialShown();
            ui.postDelayed(new Runnable() {
                @Override public void run() { showTutorial(); }
            }, 500);
        }
    }

    private void setupWindow() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (llmClient != null) llmClient.shutdown();
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (table != null) table.refreshLayout();
    }

    // ============================================================
    //                      UI 构建（横屏）
    // ============================================================

    private float dp() {
        return getResources().getDisplayMetrics().density;
    }

    private void buildUi() {
        float d = dp();

        root = new FrameLayout(this);
        root.setBackgroundColor(theme.feltBottom);
        setContentView(root);

        // 牌桌铺满
        table = new TableView(this);
        table.setTheme(theme);
        table.setListener(this);
        root.addView(table, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ── 顶部左：统计 ──
        statsText = new TextView(this);
        statsText.setTextColor(0xCCFFFFFF);
        statsText.setTextSize(12.5f);
        statsText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statsText.setShadowLayer(3f, 0f, 1.5f, 0xCC000000);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.TOP | Gravity.START;
        slp.leftMargin = (int) (20 * d);
        slp.topMargin = (int) (14 * d);
        root.addView(statsText, slp);

        // ── 顶部右：按钮组 ──
        LinearLayout topRight = new LinearLayout(this);
        topRight.setOrientation(LinearLayout.HORIZONTAL);
        topRight.setGravity(Gravity.CENTER_VERTICAL);

        aiBadge = new TextView(this);
        aiBadge.setTextSize(11.5f);
        aiBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        aiBadge.setPadding((int) (10 * d), (int) (5 * d), (int) (10 * d), (int) (5 * d));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(20 * d);
        badgeBg.setStroke((int) (1 * d), 0x66FFFFFF);
        badgeBg.setColor(0x55000000);
        aiBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = (int) (8 * d);
        topRight.addView(aiBadge, blp);

        PillButton btnAi = topBtn("AI", new View.OnClickListener() {
            @Override public void onClick(View v) { openAiSettings(); }
        });
        PillButton btnMenu = topBtn("菜单", new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        topRight.addView(btnAi);
        topRight.addView(btnMenu);

        FrameLayout.LayoutParams trp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        trp.gravity = Gravity.TOP | Gravity.END;
        trp.rightMargin = (int) (16 * d);
        trp.topMargin = (int) (10 * d);
        root.addView(topRight, trp);

        // ── 操作按钮条（手牌上方）──
        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);

        btnHint = actionBtn("提示", PillButton.SECONDARY, 1.00f);
        btnPass = actionBtn("不要", PillButton.SECONDARY, 1.10f);
        btnPlay = actionBtn("出牌", PillButton.PRIMARY, 1.35f);

        actionBar.addView(btnHint);
        actionBar.addView(btnPass);
        actionBar.addView(btnPlay);

        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doHint(); }
        });
        btnPass.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doPass(); }
        });
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doPlay(); }
        });

        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        alp.bottomMargin = (int) (96 * d);
        root.addView(actionBar, alp);

        // ── 叫分条 ──
        bidBar = new LinearLayout(this);
        bidBar.setOrientation(LinearLayout.HORIZONTAL);
        bidBar.setGravity(Gravity.CENTER);

        btnBid0 = actionBtn("不叫", PillButton.SECONDARY, 1.05f);
        btnBid1 = actionBtn("1 分", PillButton.SECONDARY, 0.95f);
        btnBid2 = actionBtn("2 分", PillButton.SECONDARY, 0.95f);
        btnBid3 = actionBtn("3 分", PillButton.PRIMARY, 1.05f);

        bidBar.addView(btnBid0);
        bidBar.addView(btnBid1);
        bidBar.addView(btnBid2);
        bidBar.addView(btnBid3);

        btnBid0.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { humanBid(0); }
        });
        btnBid1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { humanBid(1); }
        });
        btnBid2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { humanBid(2); }
        });
        btnBid3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { humanBid(3); }
        });

        FrameLayout.LayoutParams blp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        blp2.bottomMargin = (int) (96 * d);
        root.addView(bidBar, blp2);

        actionBar.setVisibility(View.GONE);
        bidBar.setVisibility(View.GONE);

        refreshStats();
        refreshAiBadge();
    }

    private PillButton topBtn(String label, View.OnClickListener l) {
        float d = dp();
        PillButton b = new PillButton(this);
        b.setTheme(theme);
        b.setLabel(label);
        b.setStyle(PillButton.SECONDARY);
        b.setTextSizePx(12.5f * d);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (58 * d), (int) (32 * d));
        lp.leftMargin = (int) (6 * d);
        b.setLayoutParams(lp);
        return b;
    }

    private PillButton actionBtn(String label, int style, float scale) {
        float d = dp();
        PillButton b = new PillButton(this);
        b.setTheme(theme);
        b.setLabel(label);
        b.setStyle(style);
        b.setTextSizePx(15f * d);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (92 * d * scale), (int) (48 * d));
        lp.setMargins((int) (6 * d), 0, (int) (6 * d), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStats() {
        statsText.setText("积分 " + prefs.score()
                + "  ·  胜率 " + prefs.winRate() + "%"
                + "  ·  连胜 " + prefs.curStreak());
    }

    private void refreshAiBadge() {
        if (llmConfig.isUsable()) {
            aiBadge.setText("大模型");
            aiBadge.setTextColor(theme.gold);
        } else {
            aiBadge.setText("本地AI");
            aiBadge.setTextColor(0xFFB9D4C5);
        }
    }

    // ============================================================
    //                      开局
    // ============================================================

    private void startNewGame() {
        long seed = System.nanoTime() ^ (System.currentTimeMillis() << 13);
        controller = new Controller(prefs.difficulty(), seed);
        controller.setEvents(this);

        // 如果配置了 AI，接入外部大脑
        if (llmConfig.isUsable()) {
            final LlmBrain[] brains = new LlmBrain[3];
            for (int s = 1; s <= 2; s++) {
                brains[s] = new LlmBrain(s, llmClient, prefs.difficulty(),
                        personaFor(s));
            }
            controller.setExternalBrain(new Controller.ExternalBrain() {
                @Override
                public void decide(int seat, GameState gs,
                                   Controller.DecisionCallback cb) {
                    if (seat < 1 || seat > 2 || brains[seat] == null) {
                        cb.onDecided(null);
                        return;
                    }
                    final Controller.DecisionCallback fcb = cb;
                    brains[seat].decide(gs, new LlmBrain.OnDecided() {
                        @Override
                        public void onDecided(List<Card> cards, boolean fromLlm) {
                            fcb.onDecided(cards);
                        }
                    });
                }

                @Override
                public int bid(int seat, List<Card> hand, int highest) {
                    if (seat < 1 || seat > 2 || brains[seat] == null) return 0;
                    return brains[seat].decideBid(hand, highest);
                }
            });
        } else {
            controller.setExternalBrain(null);
        }

        table.bindState(controller.state());
        table.clearTableCards();
        table.setLandlord(-1);
        table.setActiveSeat(-1);
        table.setBottom(new ArrayList<Card>(), false);
        table.setHand(new ArrayList<Card>());
        for (int i = 0; i < 3; i++) table.setRemain(i, 17);

        actionBar.setVisibility(View.GONE);
        bidBar.setVisibility(View.GONE);

        controller.start();
    }

    private String personaFor(int seat) {
        switch (seat) {
            case GameState.SEAT_RIGHT: return "稳健老练，善于控场";
            default: return "思路灵活，喜欢抢先";
        }
    }

    // ============================================================
    //                  Controller.Events
    // ============================================================

    @Override
    public void onDealt() {
        GameState gs = controller.state();
        table.setHand(gs.hands[GameState.SEAT_ME]);
        table.setBottom(gs.bottom, false);
        table.clearTableCards();
        table.setLandlord(-1);
        for (int i = 0; i < 3; i++) table.setRemain(i, gs.hands[i].size());
        actionBar.setVisibility(View.GONE);
        bidBar.setVisibility(View.GONE);
    }

    @Override
    public void onBidTurn(int seat) {
        table.setActiveSeat(seat);
        if (seat == GameState.SEAT_ME) {
            bidBar.setVisibility(View.VISIBLE);
            actionBar.setVisibility(View.GONE);
            GameState gs = controller.state();
            btnBid1.setEnabled(gs.highestBid < 1);
            btnBid2.setEnabled(gs.highestBid < 2);
            btnBid3.setEnabled(gs.highestBid < 3);
            table.setHand(gs.hands[GameState.SEAT_ME]);
        } else {
            bidBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBidDone(int seat, int score) {
        table.showBubble(seat, score == 0 ? "不叫" : score + " 分");
        if (seat == GameState.SEAT_ME) bidBar.setVisibility(View.GONE);
    }

    @Override
    public void onLandlord(int seat) {
        GameState gs = controller.state();
        table.setLandlord(seat);
        table.setBottom(gs.bottom, true);
        table.setHand(gs.hands[GameState.SEAT_ME]);
        for (int i = 0; i < 3; i++) table.setRemain(i, gs.hands[i].size());

        table.showBubble(seat, seat == GameState.SEAT_ME ? "我是地主" : "地主");
    }

    @Override
    public void onTurn(int seat) {
        table.setActiveSeat(seat);
        boolean mine = (seat == GameState.SEAT_ME);
        if (mine) {
            table.setHand(controller.state().hands[GameState.SEAT_ME]);
            table.clearSelection();
            actionBar.setVisibility(View.VISIBLE);
            bidBar.setVisibility(View.GONE);
            updateActionButtons();
        } else {
            actionBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPlayed(int seat, List<Card> cards) {
        table.showTableCards(seat, cards);
        table.setRemain(seat, controller.state().hands[seat].size());
        if (seat == GameState.SEAT_ME) {
            table.setHand(controller.state().hands[GameState.SEAT_ME]);
            table.clearSelection();
            actionBar.setVisibility(View.GONE);
        }
        Combo c = Combo.analyze(cards);
        if (c != null && c.isBombKind()) {
            table.showBubble(seat, c.typeName() + "!");
        }
    }

    @Override
    public void onPassed(int seat) {
        table.showBubble(seat, "不要");
    }

    @Override
    public void onTableClear() {
        ui.postDelayed(new Runnable() {
            @Override public void run() { table.clearTableCards(); }
        }, 480);
    }

    @Override
    public void onPlayerCountsChanged() {
        GameState gs = controller.state();
        for (int i = 0; i < 3; i++) table.setRemain(i, gs.hands[i].size());
    }

    @Override
    public void onOver() {
        GameState gs = controller.state();
        actionBar.setVisibility(View.GONE);
        bidBar.setVisibility(View.GONE);

        final boolean win = gs.sameTeam(GameState.SEAT_ME, gs.winner);
        final int delta = gs.scoreForMe();
        final int mult = gs.totalMultiplier();
        final boolean wasLord = gs.isLandlord(GameState.SEAT_ME);
        final int bombs = gs.bombCount;
        final boolean spring = gs.springMultiplier > 1;

        prefs.recordGame(win, delta, mult, wasLord, bombs, spring);
        refreshStats();

        ui.postDelayed(new Runnable() {
            @Override public void run() {
                showResult(win, delta, mult, bombs, spring);
            }
        }, 650);
    }

    @Override
    public void onRedeal() {
        table.showBubble(GameState.SEAT_ME, "三家都不叫，重新发牌");
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if (controller != null) controller.redeal();
            }
        }, 900);
    }

    @Override
    public void onMessage(String msg) { toast(msg); }

    // ============================================================
    //                  TableView.Listener
    // ============================================================

    @Override
    public void onCardTapped(int index) { updateActionButtons(); }

    @Override
    public void onEmptyTapped() {
        table.clearSelection();
        updateActionButtons();
    }

    @Override
    public void onAnimationSettled() {}

    // ============================================================
    //                      玩家操作
    // ============================================================

    private void humanBid(int score) {
        if (controller == null) return;
        bidBar.setVisibility(View.GONE);
        controller.humanBid(score);
    }

    private void doPlay() {
        if (controller == null) return;
        List<Card> sel = table.getSelectedCards();
        if (sel.isEmpty()) { toast("请先选牌"); return; }

        int r = controller.humanPlay(sel);
        switch (r) {
            case GameState.PLAY_OK:
                table.clearSelection();
                actionBar.setVisibility(View.GONE);
                break;
            case GameState.PLAY_INVALID_COMBO: toast("牌型不合法"); break;
            case GameState.PLAY_CANNOT_BEAT: toast("压不过上家"); break;
            case GameState.PLAY_CARD_NOT_IN_HAND: toast("手牌状态异常"); break;
            case GameState.PLAY_NOT_YOUR_TURN: toast("还没轮到你"); break;
            default: break;
        }
    }

    private void doPass() {
        if (controller == null) return;
        int r = controller.humanPass();
        if (r == GameState.PLAY_CANNOT_PASS) { toast("自由出牌，必须出一手"); return; }
        if (r == GameState.PLAY_OK) {
            table.clearSelection();
            actionBar.setVisibility(View.GONE);
        }
    }

    private void doHint() {
        if (controller == null) return;
        List<Card> h = controller.hintForMe();
        if (h == null || h.isEmpty()) { toast("没有能压过的牌，建议「不要」"); return; }
        table.selectCards(h);
        updateActionButtons();
    }

    private void updateActionButtons() {
        List<Card> sel = table.getSelectedCards();
        GameState gs = controller.state();

        boolean myTurn = gs.phase == GameState.PHASE_PLAY
                && gs.turn == GameState.SEAT_ME;

        btnPass.setEnabled(myTurn && controller.canPass());
        btnHint.setEnabled(myTurn);

        if (!myTurn) { btnPlay.setEnabled(false); btnPlay.setLabel("出牌"); return; }
        if (sel.isEmpty()) { btnPlay.setLabel("出牌"); btnPlay.setEnabled(false); return; }

        Combo c = Combo.analyze(sel);
        if (c == null) { btnPlay.setLabel("牌型错误"); btnPlay.setEnabled(false); return; }

        Combo prev = gs.pendingCombo();
        boolean ok = (prev == null) || Combo.canBeat(prev, c);

        if (!ok) { btnPlay.setLabel("压不过"); btnPlay.setEnabled(false); }
        else { btnPlay.setLabel(c.typeName()); btnPlay.setEnabled(true); }
    }

    // ============================================================
    //                       弹窗
    // ============================================================

    private void openAiSettings() {
        SettingsDialog.show(this, theme, llmConfig, llmClient, new Runnable() {
            @Override
            public void run() {
                refreshAiBadge();
                toast(llmConfig.isUsable()
                        ? "AI 已切换为大模型（下局生效）"
                        : "AI 已切换为本地算法（下局生效）");
            }
        });
    }

    private void showResult(boolean win, int delta, int mult, int bombs, boolean spring) {
        GameState gs = controller.state();
        StringBuilder sb = new StringBuilder();
        sb.append(win ? "你赢了这局" : "这局输了").append("\n");
        sb.append("本局得分：").append(delta > 0 ? "+" : "").append(delta).append("\n");
        sb.append("底分 ").append(gs.baseScore).append("　总倍数 ×").append(mult);
        if (bombs > 0) sb.append("\n炸弹 ").append(bombs).append(" 个");
        if (spring) sb.append("\n春天 额外 ×2");
        sb.append("\n累计积分 ").append(prefs.score())
                .append("　胜率 ").append(prefs.winRate()).append("%");

        Dialogs.show(this, theme,
                win ? "胜　利" : "失　败",
                sb.toString(),
                new Dialogs.Btn("再来一局", PillButton.PRIMARY, new Runnable() {
                    @Override public void run() { startNewGame(); }
                }),
                new Dialogs.Btn("看看牌桌", PillButton.SECONDARY, null));
    }

    private void showMenu() {
        String[] items = {
                "AI 接口设置",
                "切换皮肤：" + theme.name,
                "切换难度：" + diffName(prefs.difficulty()),
                "重新开局",
                "游戏规则",
                "我的战绩",
                "重置统计"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("菜单")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        switch (which) {
                            case 0: openAiSettings(); break;
                            case 1: cycleSkin(); break;
                            case 2: cycleDifficulty(); break;
                            case 3: startNewGame(); break;
                            case 4: showRules(); break;
                            case 5: showStats(); break;
                            case 6: confirmReset(); break;
                            default: break;
                        }
                    }
                })
                .show();
    }

    private String diffName(int d) {
        switch (d) {
            case 0: return "新手";
            case 1: return "普通";
            default: return "高手";
        }
    }

    private void cycleSkin() {
        theme = Theme.next(theme);
        prefs.setSkin(theme.id);
        table.setTheme(theme);
        root.setBackgroundColor(theme.feltBottom);
        refreshAiBadge();
        toast("皮肤：" + theme.name);
    }

    private void cycleDifficulty() {
        int d = (prefs.difficulty() + 1) % 3;
        prefs.setDifficulty(d);
        toast("难度：" + diffName(d) + "（重新开局生效）");
    }

    private void showRules() {
        String s =
                "【牌型】\n" +
                "单张 / 对子 / 三张 / 三带一 / 三带二\n" +
                "顺子：5 张起连续，不含 2 和王\n" +
                "连对：3 对起连续\n" +
                "飞机：连续三张 2 组起，可带单或带对\n" +
                "四带二 / 四带两对\n" +
                "炸弹 / 王炸\n\n" +
                "【大小】\n" +
                "3 < 4 < ... < K < A < 2 < 小王 < 大王\n" +
                "炸弹压任意非炸弹，王炸最大\n\n" +
                "【计分】\n" +
                "叫分作底分，每炸弹 ×2\n" +
                "春天 / 反春天 各 ×2\n" +
                "地主输赢 ×2，农民 ×1\n\n" +
                "【操作】\n" +
                "点牌选中，横向滑动连选\n" +
                "「提示」自动选一手合法牌";
        Dialogs.alert(this, theme, "游戏规则", s);
    }

    private void showStats() {
        String s =
                "总局数：" + prefs.games() + "\n" +
                "胜：" + prefs.wins() + "　负：" + prefs.loses() + "\n" +
                "胜率：" + prefs.winRate() + "%\n" +
                "当前连胜：" + prefs.curStreak() + "\n" +
                "最高连胜：" + prefs.maxStreak() + "\n\n" +
                "地主胜场：" + prefs.landlordWins() + "\n" +
                "农民胜场：" + prefs.farmerWins() + "\n" +
                "累计炸弹：" + prefs.totalBombs() + "\n" +
                "春天次数：" + prefs.totalSprings() + "\n" +
                "最高倍数：×" + prefs.bestMultiplier() + "\n\n" +
                "累计积分：" + prefs.score();
        Dialogs.alert(this, theme, "我的战绩", s);
    }

    private void confirmReset() {
        Dialogs.show(this, theme, "重置统计", "所有战绩将清零，确定吗？",
                new Dialogs.Btn("取消", PillButton.SECONDARY, null),
                new Dialogs.Btn("确定", PillButton.DANGER, new Runnable() {
                    @Override public void run() {
                        prefs.resetStats();
                        refreshStats();
                        toast("已重置");
                    }
                }));
    }

    private void showTutorial() {
        String s =
                "这是一款单机斗地主。\n\n" +
                "两个对手由 AI 控制，可以在「AI」按钮里接上大模型，\n" +
                "让它们打得更像真人。不接也能玩，用的是本地算法。\n\n" +
                "开局先叫分，叫得越高底分越高。\n" +
                "点牌选中，横向滑动可连选。\n" +
                "不确定出什么就点「提示」。";
        Dialogs.show(this, theme, "开始游戏", s,
                new Dialogs.Btn("开始", PillButton.PRIMARY, null));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        Dialogs.show(this, theme, "退出游戏", "确定要离开牌桌吗？",
                new Dialogs.Btn("继续游戏", PillButton.SECONDARY, null),
                new Dialogs.Btn("退出", PillButton.DANGER, new Runnable() {
                    @Override public void run() { finish(); }
                }));
    }
}