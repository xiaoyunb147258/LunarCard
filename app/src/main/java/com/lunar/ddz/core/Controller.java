package com.lunar.ddz.core;

import com.lunar.ddz.ai.BrainedPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 对局驱动。纯逻辑层，不持有 Handler，由外部每帧/定时驱动。
 *
 * 使用方式：
 *   Controller c = new Controller(level, seed);
 *   c.start();                      // 发牌
 *   while (c.tick(now)) {}          // 推进 AI
 *   c.humanBid(2);                  // 玩家叫分
 *   c.humanPlay(cards);             // 玩家出牌
 */
public final class Controller {

    /** 事件回调 */
    public interface Events {
        void onDealt();
        void onBidTurn(int seat);
        void onBidDone(int seat, int score);
        void onLandlord(int seat);
        void onTurn(int seat);
        void onPlayed(int seat, List<Card> cards);
        void onPassed(int seat);
        void onTableClear();
        void onPlayerCountsChanged();
        void onOver();
        void onRedeal();
        void onMessage(String msg);
    }

    public static final int AI_THINK_MS = 700;

    private final GameState gs;
    private final BrainedPlayer[] brains = new BrainedPlayer[3];
    private Events events;

    /**
     * 外部大脑（大模型）。非 null 时优先使用，决策完成后再走 apply。
     * 由 MainActivity 注入，避免 core 层依赖网络层。
     */
    public interface ExternalBrain {
        /** 请求决策；实现方负责在适当时机回调 */
        void decide(int seat, GameState gs, DecisionCallback cb);
        /** 叫分（仍是本地逻辑） */
        int bid(int seat, List<Card> hand, int highest);
    }

    public interface DecisionCallback {
        void onDecided(List<Card> cards);
    }

    private ExternalBrain external;

    public void setExternalBrain(ExternalBrain b) {
        this.external = b;
    }

    /** AI 下一次行动的时间戳，0 表示无待办 */
    private long aiActionAt = 0;
    private boolean aiPending = false;

    public Controller(int difficulty, long seed) {
        this.gs = new GameState(seed);
        for (int i = 0; i < 3; i++) {
            brains[i] = new BrainedPlayer(difficulty, seed * 31 + i * 7 + 1);
        }
    }

    public GameState state() {
        return gs;
    }

    public void setEvents(Events e) {
        this.events = e;
    }

    public void autoPlayHint() {
        // 预留
    }

    // ============================================================
    //                       开局
    // ============================================================
    public void start() {
        gs.deal();
        aiPending = false;
        aiActionAt = 0;
        if (events != null) {
            events.onDealt();
            events.onPlayerCountsChanged();
        }
        afterDeal();
    }

    private void afterDeal() {
        if (gs.phase == GameState.PHASE_BID) {
            if (events != null) events.onBidTurn(gs.bidTurn);
            scheduleAiIfNeeded();
        }
    }

    // ============================================================
    //                       叫分
    // ============================================================
    public void humanBid(int score) {
        if (gs.phase != GameState.PHASE_BID) return;
        if (gs.bidTurn != GameState.SEAT_ME) return;
        doBid(GameState.SEAT_ME, score);
    }

    private void doBid(int seat, int score) {
        int actual = score;
        if (actual <= gs.highestBid) actual = 0;

        boolean done = gs.bid(seat, score);

        if (events != null) events.onBidDone(seat, actual);

        if (done) {
            if (gs.landlord < 0) {
                // 流局重发
                if (events != null) events.onRedeal();
                aiPending = true;
                aiActionAt = 0;
                // 交给外部短暂延迟后调用 redeal()
                return;
            }
            if (events != null) {
                events.onLandlord(gs.landlord);
                events.onPlayerCountsChanged();
                events.onTurn(gs.turn);
            }
            scheduleAiIfNeeded();
        } else {
            if (events != null) events.onBidTurn(gs.bidTurn);
            scheduleAiIfNeeded();
        }
    }

    /** 流局后重新发牌，由外部在延迟后调用 */
    public void redeal() {
        gs.deal();
        aiPending = false;
        aiActionAt = 0;
        if (events != null) {
            events.onDealt();
            events.onPlayerCountsChanged();
        }
        afterDeal();
    }

    // ============================================================
    //                       出牌
    // ============================================================
    public int humanPlay(List<Card> cards) {
        if (gs.phase != GameState.PHASE_PLAY) return GameState.PLAY_NOT_YOUR_TURN;
        if (gs.turn != GameState.SEAT_ME) return GameState.PLAY_NOT_YOUR_TURN;
        return applyPlay(GameState.SEAT_ME, cards);
    }

    public int humanPass() {
        if (gs.phase != GameState.PHASE_PLAY) return GameState.PLAY_NOT_YOUR_TURN;
        if (gs.turn != GameState.SEAT_ME) return GameState.PLAY_NOT_YOUR_TURN;
        return applyPass(GameState.SEAT_ME);
    }

    private int applyPlay(int seat, List<Card> cards) {
        int r = gs.play(seat, cards);
        if (r != GameState.PLAY_OK) return r;

        if (events != null) {
            events.onPlayed(seat, cards);
            events.onPlayerCountsChanged();
        }

        if (gs.phase == GameState.PHASE_OVER) {
            if (events != null) events.onOver();
            return GameState.PLAY_OK;
        }

        boolean cleared = (gs.table == null);
        if (cleared && events != null) events.onTableClear();

        if (events != null) events.onTurn(gs.turn);
        scheduleAiIfNeeded();
        return GameState.PLAY_OK;
    }

    private int applyPass(int seat) {
        int r = gs.pass(seat);
        if (r != GameState.PLAY_OK) return r;

        if (events != null) events.onPassed(seat);

        boolean cleared = (gs.table == null);
        if (cleared && events != null) events.onTableClear();

        if (events != null) events.onTurn(gs.turn);
        scheduleAiIfNeeded();
        return GameState.PLAY_OK;
    }

    // ============================================================
    //                     AI 调度
    // ============================================================
    private void scheduleAiIfNeeded() {
        if (gs.phase == GameState.PHASE_BID && gs.bidTurn != GameState.SEAT_ME) {
            aiPending = true;
            aiActionAt = System.currentTimeMillis() + AI_THINK_MS;
        } else if (gs.phase == GameState.PHASE_PLAY && gs.turn != GameState.SEAT_ME) {
            aiPending = true;
            aiActionAt = System.currentTimeMillis() + AI_THINK_MS;
        } else {
            aiPending = false;
            aiActionAt = 0;
        }
    }

    /**
     * 每帧调用。返回 true 表示本帧执行了一个 AI 动作。
     */
    public boolean tick(long now) {
        if (!aiPending) return false;
        if (now < aiActionAt) return false;

        aiPending = false;
        aiActionAt = 0;

        if (gs.phase == GameState.PHASE_BID) {
            int seat = gs.bidTurn;
            if (seat == GameState.SEAT_ME) return false;
            int score = (external != null)
                    ? external.bid(seat, gs.hands[seat], gs.highestBid)
                    : brains[seat].bid(gs.hands[seat], gs.highestBid);
            doBid(seat, score);
            return true;
        }

        if (gs.phase == GameState.PHASE_PLAY) {
            final int seat = gs.turn;
            if (seat == GameState.SEAT_ME) return false;

            if (external != null) {
                // 异步路径：请求外部大脑，回调后再生效
                // 期间把 aiPending 重新挂起，防止重复触发
                aiPending = true;
                aiActionAt = now + 60000;   // 最长等 60 秒

                external.decide(seat, gs, new DecisionCallback() {
                    @Override
                    public void onDecided(List<Card> cards) {
                        aiPending = false;
                        aiActionAt = 0;

                        if (gs.phase != GameState.PHASE_PLAY || gs.turn != seat) {
                            return;   // 局面已变，丢弃
                        }

                        if (cards == null || cards.isEmpty()) {
                            if (gs.isFreePlay(seat)) {
                                List<Card> fb = fallbackMove(seat);
                                if (fb != null) applyPlay(seat, fb);
                            } else {
                                applyPass(seat);
                            }
                            return;
                        }
                        applyPlay(seat, cards);
                    }
                });
                return true;
            }

            List<Card> move = brains[seat].decide(gs, seat);
            if (move == null) {
                if (gs.isFreePlay(seat)) {
                    move = fallbackMove(seat);
                } else {
                    applyPass(seat);
                    return true;
                }
            }
            if (move == null || move.isEmpty()) return false;
            applyPlay(seat, move);
            return true;
        }

        return false;
    }

    private List<Card> fallbackMove(int seat) {
        List<Card> hand = gs.hands[seat];
        List<Combo> moves = com.lunar.ddz.ai.MoveGenerator.allMoves(hand, null);
        if (moves.isEmpty()) return null;
        return moves.get(0).cards;
    }

    public boolean isWaitingAi() {
        return aiPending;
    }

    // ============================================================
    //                     便利方法
    // ============================================================
    public List<Card> hintForMe() {
        return brains[GameState.SEAT_ME].hint(gs);
    }

    public boolean canPass() {
        return gs.phase == GameState.PHASE_PLAY
                && gs.turn == GameState.SEAT_ME
                && !gs.isFreePlay(GameState.SEAT_ME);
    }

    public String describeCombo(List<Card> cards) {
        Combo c = Combo.analyze(cards);
        return c == null ? "牌型错误" : c.typeName();
    }

    /** 我方手牌（供 UI 用，副本） */
    public List<Card> myHand() {
        return new ArrayList<Card>(gs.hands[GameState.SEAT_ME]);
    }
}