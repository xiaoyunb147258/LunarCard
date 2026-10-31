package com.lunar.ddz.ai;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.core.RankCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AI 决策大脑。
 *
 * 三档难度：
 *   EASY   新手：会犯错，优先出小牌，几乎不配合队友
 *   NORMAL 普通：手数最优 + 基本记牌 + 会配合
 *   HARD   高手：完整记牌推理 + 精确残局 + 主动压制 + 队友协同
 *
 * 核心评估函数：走完这手后剩余手数（越少越好）+ 大牌保留度 + 局势紧迫度。
 */
public final class BrainedPlayer {

    public static final int EASY = 0;
    public static final int NORMAL = 1;
    public static final int HARD = 2;

    private final int level;
    private final Random rnd;

    /** 记牌器：已出现的牌张数 */
    private final int[] seen = new int[18];

    public BrainedPlayer(int level, long seed) {
        this.level = level;
        this.rnd = new Random(seed);
    }

    public int level() {
        return level;
    }

    // ============================================================
    //                      叫分
    // ============================================================
    /**
     * 评估是否叫分。返回 0（不叫）/ 1 / 2 / 3
     */
    public int bid(List<Card> hand, int currentHigh) {
        int score = bidStrength(hand);

        if (level == EASY) {
            score += rnd.nextInt(5) - 2;
        } else if (level == NORMAL) {
            if (rnd.nextInt(100) < 12) score += rnd.nextInt(4) - 2;
        }

        int want;
        if (score >= 13) want = 3;
        else if (score >= 9) want = 2;
        else if (score >= 6) want = 1;
        else want = 0;

        return want > currentHigh ? want : 0;
    }

    /** 叫分强度评分 */
    public static int bidStrength(List<Card> hand) {
        int[] cnt = RankCounter.count(hand);
        int s = 0;

        // 王
        if (cnt[17] > 0 && cnt[16] > 0) s += 7;      // 王炸
        else if (cnt[17] > 0) s += 4;
        else if (cnt[16] > 0) s += 3;

        // 炸弹
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] == 4) s += 5;
        }

        // 2 / A / K
        s += cnt[15] * 2;
        s += cnt[14];
        s += cnt[13] / 2;

        // 手数
        HandSplitter.Result sp = HandSplitter.split(hand);
        int hands = sp.hands();
        if (hands <= 6) s += 5;
        else if (hands <= 8) s += 3;
        else if (hands <= 10) s += 1;
        else if (hands >= 14) s -= 3;

        // 单张太多扣分
        int singles = 0;
        for (List<Card> g : sp.groups) if (g.size() == 1) singles++;
        s -= Math.max(0, singles - 4);

        return s;
    }

    // ============================================================
    //                      出牌决策
    // ============================================================
    /**
     * 决定出什么牌。
     * @return null 表示「不要」
     */
    public List<Card> decide(GameState gs, int seat) {
        List<Card> hand = gs.hands[seat];
        if (hand.isEmpty()) return null;

        syncSeen(gs);

        boolean free = gs.isFreePlay(seat);
        Combo prev = free ? null : gs.table;

        if (free) return lead(gs, seat);
        return follow(gs, seat, prev);
    }

    private void syncSeen(GameState gs) {
        java.util.Arrays.fill(seen, 0);
        for (Card c : gs.playedHistory) {
            if (c.rank >= 3 && c.rank <= 17) seen[c.rank]++;
        }
    }

    // ------------------------------------------------------------
    //  主动出牌
    // ------------------------------------------------------------
    private List<Card> lead(GameState gs, int seat) {
        List<Card> hand = gs.hands[seat];
        List<Combo> moves = MoveGenerator.allMoves(hand, null);
        if (moves.isEmpty()) return null;

        // 1) 能一手走完，直接赢
        for (Combo m : moves) {
            if (m.size() == hand.size()) return m.cards;
        }

        // 2) 残局：对手快出完了 → 出最大牌压制
        boolean danger = isDangerous(gs, seat);

        HandSplitter.Result mine = HandSplitter.split(hand);

        // 3) 队友只剩 1 张 → 出小单张让他走
        if (level >= NORMAL && !gs.isLandlord(seat)) {
            int mate = teammate(gs, seat);
            if (mate >= 0 && gs.hands[mate].size() == 1) {
                List<Card> small = smallestSingle(hand);
                if (small != null) return small;
            }
        }

        // 4) 评估每个走法
        Combo best = null;
        double bestScore = -1e18;

        for (Combo m : moves) {
            // 自由出牌时不主动放炸弹（除非能赢或者残局）
            if (m.isBombKind()) {
                if (hand.size() > 5 && !danger) continue;
            }
            double sc = scoreLead(gs, seat, m, mine, danger);
            if (level == EASY) sc += rnd.nextGaussian() * 8.0;
            else if (level == NORMAL) sc += rnd.nextGaussian() * 2.0;

            if (sc > bestScore) {
                bestScore = sc;
                best = m;
            }
        }

        if (best == null) {
            // 只剩炸弹了
            for (Combo m : moves) {
                if (m.isBombKind()) return m.cards;
            }
            return moves.get(0).cards;
        }
        return best.cards;
    }

    /** 主动出牌评分 */
    private double scoreLead(GameState gs, int seat, Combo m,
                             HandSplitter.Result mine, boolean danger) {
        List<Card> rest = new ArrayList<Card>(gs.hands[seat]);
        Deck.removeAll(rest, m.cards);
        HandSplitter.Result after = HandSplitter.split(rest);

        double sc = 0;

        // 核心：手数减少越多越好
        int before = mine.hands();
        int now = after.hands();
        sc += (before - now) * 12.0;

        // 出完后能一次走完 → 巨大加分
        if (now <= 1) sc += 200;

        // 牌型本身价值：一次出得多更好
        sc += m.size() * 1.5;

        // 点数越小越好（保留大牌控场）
        sc -= m.key * 0.7;

        // 保留控制牌（2/王/炸弹）
        int bigBefore = countControl(mine);
        int bigAfter = countControl(after);
        sc += (bigAfter - bigBefore) * 6.0;

        // 危险时倾向出大牌
        if (danger) {
            sc += m.key * 2.5;
            if (m.isBombKind()) sc += 60;
        }

        // 顺子/连对/飞机有额外协同价值
        if (m.type == Combo.STRAIGHT || m.type == Combo.DOUBLE_STRAIGHT
                || m.type == Combo.PLANE || m.type == Combo.PLANE_ONE
                || m.type == Combo.PLANE_PAIR) {
            sc += 8;
        }

        // 出三带一把最小的单张带走，很划算
        if (m.type == Combo.TRIPLE_ONE || m.type == Combo.TRIPLE_PAIR) {
            sc += 5;
        }

        // 高手：优先出「下家难跟」的牌型
        if (level == HARD) {
            int next = (seat + 1) % 3;
            if (gs.hands[next].size() <= 3 && !gs.sameTeam(seat, next)) {
                // 下家是敌人且快出完，出大牌
                sc += m.key * 2.0;
            }
        }

        return sc;
    }

    // ------------------------------------------------------------
    //  跟牌
    // ------------------------------------------------------------
    private List<Card> follow(GameState gs, int seat, Combo prev) {
        List<Card> hand = gs.hands[seat];
        List<Combo> moves = MoveGenerator.allMoves(hand, prev);
        if (moves.isEmpty()) return null;

        boolean teammatePlaying = gs.sameTeam(seat, gs.tableOwner)
                && gs.tableOwner != seat;

        // 1) 能一手走完 → 直接出
        for (Combo m : moves) {
            if (m.size() == hand.size()) return m.cards;
        }

        // 2) 队友出的牌，考虑是否让
        if (teammatePlaying) {
            int mateSize = gs.hands[gs.tableOwner].size();

            // 队友快走完了，绝不压
            if (mateSize <= 2) return null;

            // 队友出的是大牌（A 以上），别抢
            if (prev.key >= 14 && !isDangerous(gs, seat)) return null;

            // 自己牌比队友少，可以抢
            boolean iAmCloser = hand.size() < mateSize - 2;
            if (!iAmCloser) {
                int passProb = level == HARD ? 88 : (level == NORMAL ? 70 : 45);
                if (rnd.nextInt(100) < passProb) return null;
            }
        }

        // 3) 评估候选
        HandSplitter.Result mine = HandSplitter.split(hand);
        boolean danger = isDangerous(gs, seat);

        Combo best = null;
        double bestScore = -1e18;

        for (Combo m : moves) {
            if (m.isBombKind()) {
                // 炸弹使用门槛
                boolean shouldBomb = danger
                        || hand.size() <= 4
                        || (m.type == Combo.ROCKET && gs.hands[gs.tableOwner].size() <= 5);
                if (!shouldBomb) continue;
            }

            double sc = scoreFollow(gs, seat, m, prev, mine, danger, teammatePlaying);
            if (level == EASY) sc += rnd.nextGaussian() * 10.0;
            else if (level == NORMAL) sc += rnd.nextGaussian() * 3.0;

            if (sc > bestScore) {
                bestScore = sc;
                best = m;
            }
        }

        // 4) 如果最佳方案是「压不过」，考虑放弃
        if (best == null) return null;

        // 低难度：偶尔乱过牌
        if (level == EASY && rnd.nextInt(100) < 22 && !danger) return null;

        return best.cards;
    }

    /** 跟牌评分 */
    private double scoreFollow(GameState gs, int seat, Combo m, Combo prev,
                               HandSplitter.Result mine, boolean danger,
                               boolean teammatePlaying) {
        List<Card> rest = new ArrayList<Card>(gs.hands[seat]);
        Deck.removeAll(rest, m.cards);
        HandSplitter.Result after = HandSplitter.split(rest);

        double sc = 0;

        // 手数减少
        sc += (mine.hands() - after.hands()) * 12.0;
        if (after.hands() <= 1) sc += 220;

        // 用最小的牌压（省大牌）
        sc -= (m.key - prev.key) * 3.0;
        sc -= m.key * 0.6;

        // 拆牌惩罚：如果这手把原本的整组拆了，扣分
        if (isBreakingGroup(mine, m)) sc -= 10;

        // 控制牌保留
        int bigBefore = countControl(mine);
        int bigAfter = countControl(after);
        sc += (bigAfter - bigBefore) * 7.0;

        // 炸弹代价高
        if (m.isBombKind()) {
            sc -= 45;
            if (m.type == Combo.ROCKET) sc -= 25;
        }

        // 危险局势：必须压
        if (danger) {
            sc += 90;
            if (m.isBombKind()) sc += 70;
        }

        // 队友在出牌时压低优先级
        if (teammatePlaying) sc -= 40;

        return sc;
    }

    /** 这手牌是否拆散了原有的组合 */
    private boolean isBreakingGroup(HandSplitter.Result mine, Combo m) {
        // 简单判定：如果出的牌小于等于 2 张，且同点数在手里还有剩余 → 拆牌
        int[] cnt = RankCounter.count(new ArrayList<Card>(m.cards));
        for (int r = 3; r <= 17; r++) {
            if (cnt[r] > 0 && cnt[r] < 4) {
                // 检查这种点数原本是否有 3 或 4 张
                int totalInHand = 0;
                for (List<Card> g : mine.groups) {
                    for (Card c : g) if (c.rank == r) totalInHand++;
                }
                if (totalInHand >= 3 && cnt[r] < totalInHand) return true;
            }
        }
        return false;
    }

    // ============================================================
    //                      工具
    // ============================================================

    /** 统计控制牌数量（2、王、炸弹） */
    private int countControl(HandSplitter.Result sp) {
        int n = 0;
        for (List<Card> g : sp.groups) {
            if (g == null || g.isEmpty()) continue;
            // 炸弹（4 张同点）
            if (g.size() == 4 && g.get(0).rank == g.get(3).rank) {
                n += 2;
                continue;
            }
            // 2 和 王 都算控制牌
            Card first = g.get(0);
            if (first.rank >= 15) n++;
        }
        return n;
    }

    /** 是否处于危险局面（对手快出完） */
    private boolean isDangerous(GameState gs, int seat) {
        if (gs.isLandlord(seat)) {
            // 我是地主：任一农民 <= 2 张就危险
            for (int s = 0; s < 3; s++) {
                if (s == seat) continue;
                if (gs.hands[s].size() <= 2) return true;
            }
        } else {
            // 我是农民：地主 <= 3 张就危险
            int lord = gs.landlord;
            if (lord >= 0 && gs.hands[lord].size() <= 3) return true;
        }
        return false;
    }

    private int teammate(GameState gs, int seat) {
        if (gs.isLandlord(seat)) return -1;
        for (int s = 0; s < 3; s++) {
            if (s != seat && !gs.isLandlord(s)) return s;
        }
        return -1;
    }

    private List<Card> smallestSingle(List<Card> hand) {
        Card best = null;
        for (Card c : hand) {
            if (best == null || c.rank < best.rank) best = c;
        }
        if (best == null) return null;
        List<Card> out = new ArrayList<Card>(1);
        out.add(best);
        return out;
    }

    // ============================================================
    //                      提示（给玩家用）
    // ============================================================
    /**
     * 为人类玩家推荐一手牌。
     * @return null 表示建议不要
     */
    public List<Card> hint(GameState gs) {
        List<Card> hand = gs.hands[GameState.SEAT_ME];
        if (hand.isEmpty()) return null;

        Combo prev = gs.pendingCombo();
        List<Combo> moves = MoveGenerator.allMoves(hand, prev);
        if (moves.isEmpty()) return null;

        // 能一手走完优先
        for (Combo m : moves) {
            if (m.size() == hand.size()) return m.cards;
        }

        // 选最小的非炸弹
        Combo best = null;
        for (Combo m : moves) {
            if (m.isBombKind()) continue;
            if (best == null || m.key < best.key) best = m;
        }
        if (best != null) return best.cards;

        // 只剩炸弹
        return moves.get(0).cards;
    }

    /** 玩家是否还能跟牌（用于「不要」按钮禁用判断） */
    public static boolean hasAnyMove(List<Card> hand, Combo prev) {
        return !MoveGenerator.allMoves(hand, prev).isEmpty();
    }
}