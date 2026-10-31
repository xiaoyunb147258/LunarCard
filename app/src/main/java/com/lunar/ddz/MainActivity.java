package com.lunar.ddz;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
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

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Controller;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.data.Prefs;
import com.lunar.ddz.ui.Dialogs;
import com.lunar.ddz.ui.PillButton;
import com.lunar.ddz.ui.Theme;
import com.lunar.ddz.view.TableView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面。负责把 Controller（逻辑）和 TableView（画面）粘起来。
 */
public class MainActivity extends Activity
        implements Controller.Events, TableView.Listener {

    private Prefs prefs;
    private Theme theme;
    private Controller controller;

    private FrameLayout root;
    private TableView table;

    private TextView statsText;
    private LinearLayout actionBar;
    private LinearLayout bidBar;

    private PillButton btnHint, btnPass, btnPlay;
    private PillButton btnBid0, btnBid1, btnBid2, btnBid3;
    private PillButton btnMenu;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (controller != null) {
                long now = System.currentTimeMillis();
                // 一次 tick 最多执行 1 个 AI 动作，保证节奏自然
                controller.tick(now);
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

        setupWindow();
        buildUi();

        if (!prefs.tutorialShown()) {
            prefs.setTutorialShown();
            ui.postDelayed(new Runnable() {
                @Override public void run() { showTutorial(); }
            }, 600);
        }

        startNewGame();
        ui.post(ticker);
    }

    private void setupWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(theme.tableBottom);
        }
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
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
    }

    // ============================================================
    //                      UI 构建
    // ============================================================

    private void buildUi() {
        float d = getResources().getDisplayMetrics().density;

        root = new FrameLayout(this);
        root.setBackgroundColor(theme.tableBottom);
        setContentView(root);

        table = new TableView(this);
        table.setTheme(theme);
        table.setListener(this);
        root.addView(table, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 顶部信息条 ----
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding((int) (14 * d), (int) (10 * d), (int) (10 * d), 0);

        statsText = new TextView(this);
        statsText.setTextColor(theme.textSecondary);
        statsText.setTextSize(12.5f);
        statsText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(statsText, slp);

        btnMenu = new PillButton(this);
        btnMenu.setTheme(theme);
        btnMenu.setLabel("菜单");
        btnMenu.setStyle(PillButton.SECONDARY);
        btnMenu.setTextSizePx(13f * d);
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        topBar.addView(btnMenu, new LinearLayout.LayoutParams(
                (int) (64 * d), (int) (34 * d)));

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        root.addView(topBar, tlp);

        // ---- 操作按钮条 ----
        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setPadding(0, 0, 0, (int) (6 * d));

        btnHint = makeActionBtn("提示", PillButton.SECONDARY, 0.92f);
        btnPass = makeActionBtn("不要", PillButton.SECONDARY, 1.02f);
        btnPlay = makeActionBtn("出牌", PillButton.PRIMARY, 1.20f);

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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.gravity = Gravity.BOTTOM;
        alp.bottomMargin = (int) (getResources().getDisplayMetrics().heightPixels * 0.295f);
        root.addView(actionBar, alp);

        // ---- 叫分条 ----
        bidBar = new LinearLayout(this);
        bidBar.setOrientation(LinearLayout.HORIZONTAL);
        bidBar.setGravity(Gravity.CENTER);
        bidBar.setPadding(0, 0, 0, (int) (6 * d));

        btnBid0 = makeActionBtn("不叫", PillButton.SECONDARY, 1.0f);
        btnBid1 = makeActionBtn("1 分", PillButton.SECONDARY, 0.88f);
        btnBid2 = makeActionBtn("2 分", PillButton.SECONDARY, 0.88f);
        btnBid3 = makeActionBtn("3 分", PillButton.PRIMARY, 0.96f);

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

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        blp.bottomMargin = (int) (getResources().getDisplayMetrics().heightPixels * 0.295f);
        root.addView(bidBar, blp);

        actionBar.setVisibility(View.GONE);
        bidBar.setVisibility(View.GONE);

        refreshStats();
    }

    private PillButton makeActionBtn(String label, int style, float scale) {
        float d = getResources().getDisplayMetrics().density;
        PillButton b = new PillButton(this);
        b.setTheme(theme);
        b.setLabel(label);
        b.setStyle(style);
        b.setTextSizePx(15f * d);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (86 * d * scale), (int) (46 * d));
        lp.setMargins((int) (5 * d), 0, (int) (5 * d), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void applyTheme() {
        table.setTheme(theme);
        root.setBackgroundColor(theme.tableBottom);
        statsText.setTextColor(theme.textSecondary);

        btnMenu.setTheme(theme);
        btnHint.setTheme(theme);
        btnPass.setTheme(theme);
        btnPlay.setTheme(theme);
        btnBid0.setTheme(theme);
        btnBid1.setTheme(theme);
        btnBid2.setTheme(theme);
        btnBid3.setTheme(theme);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setNavigationBarColor(theme.tableBottom);
        }
    }

    private void refreshStats() {
        statsText.setText("积分 " + prefs.score()
                + "  胜率 " + prefs.winRate() + "%"
                + "  连胜 " + prefs.curStreak());
    }

    // ============================================================
    //                     开局
    // ============================================================

    private void startNewGame() {
        if (controller != null) {
            // 丢弃旧控制器（无异步资源需要释放）
        }
        long seed = System.nanoTime() ^ (System.currentTimeMillis() << 13);
        controller = new Controller(prefs.difficulty(), seed);
        controller.setEvents(this);

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

    // ============================================================
    //                   Controller.Events
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
    public void onBidTurn(final int seat) {
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
        String txt;
        if (score == 0) txt = "不叫";
        else txt = score + " 分";
        table.showBubble(seat, txt);
        if (seat == GameState.SEAT_ME) bidBar.setVisibility(View.GONE);
    }

    @Override
    public void onLandlord(int seat) {
        GameState gs = controller.state();
        table.setLandlord(seat);
        table.setBottom(gs.bottom, true);
        table.setHand(gs.hands[GameState.SEAT_ME]);
        for (int i = 0; i < 3; i++) table.setRemain(i, gs.hands[i].size());

        if (seat == GameState.SEAT_ME) {
            table.showBubble(seat, "我是地主");
        } else {
            table.showBubble(seat, "地主");
        }
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
            table.showBubble(seat, c.typeName() + "！");
        }
    }

    @Override
    public void onPassed(int seat) {
        table.showBubble(seat, "不要");
    }

    @Override
    public void onTableClear() {
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                table.clearTableCards();
            }
        }, 480);
    }

    @Override
    public void onPlayerCountsChanged() {
        GameState gs = controller.state();
        for (int i = 0; i < 3; i++) {
            table.setRemain(i, gs.hands[i].size());
        }
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
        }, 700);
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
    public void onMessage(String msg) {
        toast(msg);
    }

    // ============================================================
    //                   TableView.Listener
    // ============================================================

    @Override
    public void onCardTapped(int index) {
        updateActionButtons();
    }

    @Override
    public void onHandScrolled(float dx) {
        // 预留
    }

    @Override
    public void onEmptyTapped() {
        table.clearSelection();
        table.refreshLayout();
        updateActionButtons();
    }

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
        if (sel.isEmpty()) {
            toast("请先选牌");
            return;
        }
        int r = controller.humanPlay(sel);
        switch (r) {
            case GameState.PLAY_OK:
                table.clearSelection();
                table.refreshLayout();
                actionBar.setVisibility(View.GONE);
                break;
            case GameState.PLAY_INVALID_COMBO:
                toast("牌型不合法");
                break;
            case GameState.PLAY_CANNOT_BEAT:
                toast("压不过上家");
                break;
            case GameState.PLAY_CARD_NOT_IN_HAND:
                toast("手牌状态异常");
                break;
            case GameState.PLAY_NOT_YOUR_TURN:
                toast("还没轮到你");
                break;
            default:
                break;
        }
    }

    private void doPass() {
        if (controller == null) return;
        int r = controller.humanPass();
        if (r == GameState.PLAY_CANNOT_PASS) {
            toast("自由出牌，必须出一手");
            return;
        }
        if (r == GameState.PLAY_OK) {
            table.clearSelection();
            table.refreshLayout();
            actionBar.setVisibility(View.GONE);
        }
    }

    private void doHint() {
        if (controller == null) return;
        List<Card> h = controller.hintForMe();
        if (h == null || h.isEmpty()) {
            toast("没有能压过的牌，建议「不要」");
            return;
        }
        table.selectCards(h);
        updateActionButtons();
    }

    /** 根据当前选中更新按钮文字与可用性 */
    private void updateActionButtons() {
        List<Card> sel = table.getSelectedCards();
        GameState gs = controller.state();

        boolean myTurn = gs.phase == GameState.PHASE_PLAY
                && gs.turn == GameState.SEAT_ME;

        btnPass.setEnabled(myTurn && controller.canPass());
        btnHint.setEnabled(myTurn);

        if (!myTurn) {
            btnPlay.setEnabled(false);
            btnPlay.setLabel("出牌");
            return;
        }

        if (sel.isEmpty()) {
            btnPlay.setLabel("出牌");
            btnPlay.setEnabled(false);
            return;
        }

        Combo c = Combo.analyze(sel);
        if (c == null) {
            btnPlay.setLabel("牌型错误");
            btnPlay.setEnabled(false);
            return;
        }

        Combo prev = gs.pendingCombo();
        boolean ok = (prev == null) || Combo.canBeat(prev, c);

        if (!ok) {
            btnPlay.setLabel("压不过");
            btnPlay.setEnabled(false);
        } else {
            btnPlay.setLabel(c.typeName());
            btnPlay.setEnabled(true);
        }
    }

    // ============================================================
    //                       弹窗
    // ============================================================

    private void showResult(boolean win, int delta, int mult, int bombs, boolean spring) {
        GameState gs = controller.state();

        StringBuilder sb = new StringBuilder();
        sb.append(win ? "你赢了这局" : "这局输了").append("\n\n");
        sb.append("本局得分：").append(delta > 0 ? "+" : "").append(delta).append("\n");
        sb.append("底分 ").append(gs.baseScore)
                .append("　总倍数 ×").append(mult).append("\n");
        if (bombs > 0) sb.append("炸弹 ").append(bombs).append(" 个\n");
        if (spring) sb.append("春天 额外 ×2\n");
        sb.append("地主：").append(seatName(gs.landlord)).append("\n");
        sb.append("赢家：").append(seatName(gs.winner)).append("\n\n");
        sb.append("累计积分 ").append(prefs.score())
                .append("　胜率 ").append(prefs.winRate()).append("%");

        Dialogs.show(this, theme,
                win ? "胜　利" : "失　败",
                sb.toString(),
                new Dialogs.Btn("再来一局", PillButton.PRIMARY, new Runnable() {
                    @Override public void run() { startNewGame(); }
                }),
                new Dialogs.Btn("查看牌桌", PillButton.SECONDARY, null));
    }

    private void showMenu() {
        String[] items = new String[]{
                "切换皮肤：" + theme.name,
                "切换难度：" + diffName(prefs.difficulty()),
                "重新开局",
                "游戏规则",
                "我的战绩",
                "重置统计"
        };

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("菜单");
        b.setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                d.dismiss();
                switch (which) {
                    case 0: cycleSkin(); break;
                    case 1: cycleDifficulty(); break;
                    case 2: startNewGame(); break;
                    case 3: showRules(); break;
                    case 4: showStats(); break;
                    case 5: confirmReset(); break;
                    default: break;
                }
            }
        });
        b.show();
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
        applyTheme();
        table.refreshLayout();
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
                "连对：3 对起连续，不含 2 和王\n" +
                "飞机：连续三张 2 组起，可带单或带对\n" +
                "四带二 / 四带两对\n" +
                "炸弹：四张相同；王炸：双王\n\n" +
                "【大小顺序】\n" +
                "3 < 4 < 5 < 6 < 7 < 8 < 9 < 10 < J < Q < K < A < 2 < 小王 < 大王\n" +
                "炸弹可压任意非炸弹，王炸最大\n\n" +
                "【计分】\n" +
                "叫分作为底分，每出一个炸弹 ×2\n" +
                "春天（农民一张没出）×2\n" +
                "反春天（地主只出一手）×2\n" +
                "地主输赢按 2 倍结算，农民按 1 倍\n\n" +
                "【操作】\n" +
                "点牌选中 / 再点取消\n" +
                "横向滑动可连续多选\n" +
                "点「提示」让系统帮你选一手\n" +
                "选好后点「出牌」；压不过可点「不要」";

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
        Dialogs.show(this, theme, "重置统计", "所有战绩数据将被清零，确定吗？",
                new Dialogs.Btn("取消", PillButton.SECONDARY, null),
                new Dialogs.Btn("确定重置", PillButton.DANGER, new Runnable() {
                    @Override public void run() {
                        prefs.resetStats();
                        refreshStats();
                        toast("统计已重置");
                    }
                }));
    }

    private void showTutorial() {
        String s =
                "欢迎来到月斗地主。\n\n" +
                "这是一款纯单机斗地主，三名 AI 各有性格：\n" +
                "新手会犯错，普通稳健，高手会算牌。\n\n" +
                "开局先叫分，叫得越高底分越高。\n" +
                "点牌选中，横向滑动可连选。\n" +
                "不确定出什么就点「提示」。\n\n" +
                "祝你好运。";

        Dialogs.show(this, theme, "开始游戏", s,
                new Dialogs.Btn("开始", PillButton.PRIMARY, null));
    }

    private String seatName(int seat) {
        if (seat == GameState.SEAT_ME) return "你";
        return GameState.seatName(seat);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        Dialogs.show(this, theme, "退出游戏", "确定要离开牌桌吗？",
                new Dialogs.Btn("继续游戏", PillButton.SECONDARY, null),
                new Dialogs.Btn("退出", PillButton.DANGER, new Runnable() {
                    @Override public void run() {
                        finish();
                    }
                }));
    }
}