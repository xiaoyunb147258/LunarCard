package com.lunar.ddz.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 一局斗地主的完整状态机。纯逻辑，无 Android 依赖，可单元测试。
 *
 * 座位：0 = 我（屏幕下方），1 = 右侧 AI，2 = 左侧 AI
 * 出牌顺序：0 -> 1 -> 2 -> 0
 */
public final class GameState {

    public static final int SEAT_ME = 0;
    public static final int SEAT_RIGHT = 1;
    public static final int SEAT_LEFT = 2;

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_DEAL = 1;
    public static final int PHASE_BID = 2;
    public static final int PHASE_PLAY = 3;
    public static final int PHASE_OVER = 4;

    /** 出牌结果码 */
    public static final int PLAY_OK = 0;
    public static final int PLAY_NOT_YOUR_TURN = 1;
    public static final int PLAY_CARD_NOT_IN_HAND = 2;
    public static final int PLAY_INVALID_COMBO = 3;
    public static final int PLAY_CANNOT_BEAT = 4;
    public static final int PLAY_CANNOT_PASS = 5;

    public final List<Card>[] hands;
    public final List<Card> bottom = new ArrayList<Card>(3);

    public int phase = PHASE_IDLE;
    public int turn = 0;
    public int landlord = -1;
    public int bidTurn = 0;

    public final int[] bidScore = new int[3];
    public final boolean[] bidDone = new boolean[3];
    public int highestBid = 0;
    public int highestBidSeat = -1;

    /** 桌面当前待压的牌型；null 表示自由出牌 */
    public Combo table = null;
    /** 桌面当前牌型的出牌者 */
    public int tableOwner = -1;

    public int baseScore = 1;
    public int bombCount = 0;
    public int springMultiplier = 1;
    public int winner = -1;

    /** 本局所有已出的牌（累计），用于记牌器 */
    public final List<Card> playedHistory = new ArrayList<Card>(54);

    /** 每位玩家最近一次动作，用于 UI 气泡 */
    public static final class Action {
        public final int seat;
        public final List<Card> cards;
        public final boolean pass;
        public Action(int seat, List<Card> cards, boolean pass) {
            this.seat = seat;
            this.cards = cards;
            this.pass = pass;
        }
    }
    public final Action[] lastAction = new Action[3];

    private final Random rnd;

    @SuppressWarnings("unchecked")
    public GameState(long seed) {
        this.rnd = new Random(seed);
        hands = new List[3];
        for (int i = 0; i < 3; i++) hands[i] = new ArrayList<Card>(20);
    }

    public Random random() {
        return rnd;
    }

    // ============================================================
    //                        发牌
    // ============================================================
    public void deal() {
        List<Card> deck = Deck.newDeck();
        Deck.shuffle(deck, rnd);

        for (int i = 0; i < 3; i++) hands[i].clear();
        bottom.clear();
        playedHistory.clear();

        // 每人 17 张，剩 3 张底牌
        for (int i = 0; i < 51; i++) {
            hands[i % 3].add(deck.get(i));
        }
        for (int i = 51; i < 54; i++) bottom.add(deck.get(i));

        for (int i = 0; i < 3; i++) {
            Deck.sortDesc(hands[i]);
            bidScore[i] = 0;
            bidDone[i] = false;
            lastAction[i] = null;
            seatPlayCount[i] = 0;
        }

        phase = PHASE_BID;
        bidTurn = rnd.nextInt(3);
        highestBid = 0;
        highestBidSeat = -1;
        landlord = -1;
        table = null;
        tableOwner = -1;
        bombCount = 0;
        springMultiplier = 1;
        winner = -1;
        baseScore = 1;
    }

    // ============================================================
    //                        叫分
    // ============================================================
    /**
     * 叫分。score = 0 表示不叫，1/2/3 为叫分。
     * @return true 表示叫分阶段结束（已定地主或流局）
     */
    public boolean bid(int seat, int score) {
        if (phase != PHASE_BID || seat != bidTurn) return false;

        // 叫分必须高于当前最高分
        if (score <= highestBid) score = 0;
        if (score > 3) score = 3;

        bidScore[seat] = score;
        bidDone[seat] = true;

        if (score > highestBid) {
            highestBid = score;
            highestBidSeat = seat;
        }

        // 叫到 3 分直接结束
        if (highestBid == 3) {
            finishBidding();
            return true;
        }

        // 找下一个未叫的人
        int next = -1;
        for (int i = 1; i <= 3; i++) {
            int s = (seat + i) % 3;
            if (!bidDone[s]) { next = s; break; }
        }

        if (next < 0) {
            finishBidding();
            return true;
        }
        bidTurn = next;
        return false;
    }

    private void finishBidding() {
        if (highestBidSeat < 0) {
            // 三家都不叫，流局
            phase = PHASE_IDLE;
            return;
        }
        setLandlord(highestBidSeat);
    }

    /** 指定地主，并把底牌并入其手牌 */
    public void setLandlord(int seat) {
        landlord = seat;
        baseScore = Math.max(1, highestBid);
        hands[seat].addAll(bottom);
        Deck.sortDesc(hands[seat]);
        phase = PHASE_PLAY;
        turn = seat;
        table = null;
        tableOwner = -1;
    }

    // ============================================================
    //                        出牌
    // ============================================================
    public boolean isLandlord(int seat) {
        return seat == landlord;
    }

    /** 两人是否同阵营 */
    public boolean sameTeam(int a, int b) {
        return isLandlord(a) == isLandlord(b);
    }

    /** 是否轮到此人自由出牌（无需压牌） */
    public boolean isFreePlay(int seat) {
        return table == null || tableOwner == seat;
    }

    /**
     * 出牌。
     * @return PLAY_OK 或错误码
     */
    public int play(int seat, List<Card> cards) {
        if (phase != PHASE_PLAY || seat != turn) return PLAY_NOT_YOUR_TURN;
        if (cards == null || cards.isEmpty()) return PLAY_INVALID_COMBO;
        if (!Deck.containsAll(hands[seat], cards)) return PLAY_CARD_NOT_IN_HAND;

        Combo combo = Combo.analyze(cards);
        if (combo == null) return PLAY_INVALID_COMBO;

        if (!isFreePlay(seat)) {
            if (!Combo.canBeat(table, combo)) return PLAY_CANNOT_BEAT;
        }

        // 生效
        Deck.removeAll(hands[seat], cards);
        Deck.sortDesc(hands[seat]);
        playedHistory.addAll(cards);
        lastAction[seat] = new Action(seat, new ArrayList<Card>(cards), false);
        seatPlayCount[seat]++;

        if (combo.isBombKind()) bombCount++;

        table = combo;
        tableOwner = seat;

        if (hands[seat].isEmpty()) {
            endGame(seat);
            return PLAY_OK;
        }
        nextTurn();
        return PLAY_OK;
    }

    /**
     * 不要（过牌）。
     * @return PLAY_OK 或错误码
     */
    public int pass(int seat) {
        if (phase != PHASE_PLAY || seat != turn) return PLAY_NOT_YOUR_TURN;
        if (isFreePlay(seat)) return PLAY_CANNOT_PASS;

        lastAction[seat] = new Action(seat, new ArrayList<Card>(), true);
        nextTurn();
        return PLAY_OK;
    }

    private void nextTurn() {
        int next = (turn + 1) % 3;
        // 转回到桌主，说明其他两人都过了 → 重置桌面
        if (tableOwner >= 0 && next == tableOwner) {
            table = null;
            tableOwner = -1;
            for (int i = 0; i < 3; i++) lastAction[i] = null;
        }
        turn = next;
    }

    // ============================================================
    //                        结算
    // ============================================================
    private void endGame(int winSeat) {
        phase = PHASE_OVER;
        winner = winSeat;
        springMultiplier = computeSpring();
    }

    /**
     * 春天：
     *  - 地主赢 且 两个农民一张牌都没出 → ×2
     *  - 农民赢 且 地主只出过一手牌 → ×2
     */
    private int computeSpring() {
        if (landlord < 0) return 1;
        boolean landlordWin = (winner == landlord);

        if (landlordWin) {
            for (int s = 0; s < 3; s++) {
                if (s == landlord) continue;
                // 该农民出过牌？
                if (hasSeatPlayed(s)) return 1;
            }
            return 2;
        } else {
            if (countSeatPlays(landlord) <= 1) return 2;
            return 1;
        }
    }

    /** 该座位是否出过牌 */
    private boolean hasSeatPlayed(int seat) {
        return seatPlayCount[seat] > 0;
    }

    private int countSeatPlays(int seat) {
        return seatPlayCount[seat];
    }

    /** 每位玩家出牌次数（不包含过牌），由 play() 维护 */
    public final int[] seatPlayCount = new int[3];

    public int totalMultiplier() {
        int m = Math.max(1, baseScore);
        for (int i = 0; i < bombCount; i++) m *= 2;
        m *= springMultiplier;
        return m;
    }

    /** 我的得分：地主 ±2 倍，农民 ±1 倍 */
    public int scoreForMe() {
        if (winner < 0) return 0;
        int base = totalMultiplier();
        boolean iAmLandlord = isLandlord(SEAT_ME);
        boolean iWin = sameTeam(SEAT_ME, winner);
        if (iAmLandlord) return iWin ? base * 2 : -base * 2;
        return iWin ? base : -base;
    }

    // ============================================================
    //                        辅助
    // ============================================================
    public int handSize(int seat) {
        return hands[seat].size();
    }

    /** 我当前需要压的牌型；自由出牌返回 null */
    public Combo pendingCombo() {
        if (table == null || tableOwner == SEAT_ME) return null;
        return table;
    }

    /** 座位名 */
    public static String seatName(int seat) {
        switch (seat) {
            case SEAT_ME: return "我";
            case SEAT_RIGHT: return "右家";
            case SEAT_LEFT: return "左家";
            default: return "?";
        }
    }

    /** 甲方是否地主 */
    public boolean landlordWin() {
        return winner >= 0 && winner == landlord;
    }
}