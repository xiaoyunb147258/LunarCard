package com.lunar.ddz.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌型识别与比较引擎。
 *
 * 支持全部标准斗地主牌型：
 *   单张 / 对子 / 三张 / 三带一 / 三带二
 *   顺子（>=5张，不含2和王）
 *   连对（>=3对，不含2和王）
 *   飞机（连续三张 >=2组，不带/带单/带对）
 *   四带二单 / 四带两对
 *   炸弹 / 王炸
 */
public final class Combo {

    public static final int INVALID = 0;
    public static final int SINGLE = 1;
    public static final int PAIR = 2;
    public static final int TRIPLE = 3;
    public static final int TRIPLE_ONE = 4;
    public static final int TRIPLE_PAIR = 5;
    public static final int STRAIGHT = 6;
    public static final int DOUBLE_STRAIGHT = 7;
    public static final int PLANE = 8;
    public static final int PLANE_ONE = 9;
    public static final int PLANE_PAIR = 10;
    public static final int FOUR_TWO_SINGLE = 11;
    public static final int FOUR_TWO_PAIR = 12;
    public static final int BOMB = 13;
    public static final int ROCKET = 14;

    /** 牌型 */
    public final int type;
    /** 主比较值：单/对/三取 rank；顺子/连对/飞机取最大 rank；炸弹取 rank；王炸固定 100 */
    public final int key;
    /** 序列长度：顺子=张数，连对=对数，飞机=组数；其它=0 */
    public final int len;
    /** 组成这手牌的牌（已排序，可被引用，不要就地修改） */
    public final List<Card> cards;

    private Combo(int type, int key, int len, List<Card> cards) {
        this.type = type;
        this.key = key;
        this.len = len;
        this.cards = cards;
    }

    public static Combo of(int type, int key, int len, List<Card> cards) {
        return new Combo(type, key, len, cards);
    }

    public boolean isValid() {
        return type != INVALID;
    }

    public boolean isBombKind() {
        return type == BOMB || type == ROCKET;
    }

    public boolean isRocket() {
        return type == ROCKET;
    }

    public int size() {
        return cards == null ? 0 : cards.size();
    }

    public String typeName() {
        switch (type) {
            case SINGLE: return "单张";
            case PAIR: return "对子";
            case TRIPLE: return "三张";
            case TRIPLE_ONE: return "三带一";
            case TRIPLE_PAIR: return "三带二";
            case STRAIGHT: return "顺子";
            case DOUBLE_STRAIGHT: return "连对";
            case PLANE: return "飞机";
            case PLANE_ONE: return "飞机带单";
            case PLANE_PAIR: return "飞机带对";
            case FOUR_TWO_SINGLE: return "四带二";
            case FOUR_TWO_PAIR: return "四带两对";
            case BOMB: return "炸弹";
            case ROCKET: return "王炸";
            default: return "无效";
        }
    }

    // ============================================================
    //                        识别
    // ============================================================

    /** 识别一手牌。无法识别返回 null */
    public static Combo analyze(List<Card> src) {
        if (src == null || src.isEmpty()) return null;
        List<Card> cards = new ArrayList<Card>(src);
        Collections.sort(cards);

        int n = cards.size();
        int[] cnt = RankCounter.count(cards);      // 下标 3..17
        int[] ranks = RankCounter.descRanks(cnt);  // 有点数的 rank（从大到小）

        // ---- 王炸 ----
        if (n == 2 && cnt[16] == 1 && cnt[17] == 1) {
            return new Combo(ROCKET, 100, 0, cards);
        }
        // ---- 炸弹 ----
        if (n == 4 && ranks.length == 1) {
            return new Combo(BOMB, ranks[0], 0, cards);
        }
        // ---- 单张 ----
        if (n == 1) {
            return new Combo(SINGLE, cards.get(0).rank, 0, cards);
        }
        // ---- 对子 ----
        if (n == 2) {
            if (ranks.length == 1) return new Combo(PAIR, ranks[0], 0, cards);
            return null;
        }
        // ---- 三张 ----
        if (n == 3) {
            if (ranks.length == 1) return new Combo(TRIPLE, ranks[0], 0, cards);
            return null;
        }

        // ---- 三带一 ----
        if (n == 4) {
            int t = findRankWithCnt(cnt, 3);
            if (t > 0) return new Combo(TRIPLE_ONE, t, 0, cards);
            return null;
        }

        // ---- 三带二 ----
        if (n == 5) {
            int t = findRankWithCnt(cnt, 3);
            int p = findRankWithCnt(cnt, 2);
            if (t > 0 && p > 0) return new Combo(TRIPLE_PAIR, t, 0, cards);
        }

        // ---- 顺子（>=5 张，全部单张，连续，不含 2 和王）----
        if (n >= 5) {
            int rs = straightTop(cnt, 1, n);
            if (rs > 0) return new Combo(STRAIGHT, rs, n, cards);
        }

        // ---- 连对（>=3 对，全部恰好 2 张，连续，不含 2 和王）----
        if (n >= 6 && n % 2 == 0) {
            int pairs = n / 2;
            if (pairs >= 3) {
                int rs = straightTop(cnt, 2, pairs);
                if (rs > 0) return new Combo(DOUBLE_STRAIGHT, rs, pairs, cards);
            }
        }

        // ---- 飞机系列 ----
        Combo plane = tryPlane(cnt, cards, n);
        if (plane != null) return plane;

        // ---- 四带二 ----
        if (n == 6) {
            int f = findRankWithCnt(cnt, 4);
            // 注意：4+2 单张，且不能是两组三张（那是飞机，已在上方处理）
            if (f > 0) {
                // 剩余 2 张可以是任意牌（含一对，也可以是一张单+另一张单）
                return new Combo(FOUR_TWO_SINGLE, f, 0, cards);
            }
        }
        if (n == 8) {
            int f = findRankWithCnt(cnt, 4);
            if (f > 0) {
                // 剩余 4 张必须是 2 个对子
                int pairs = 0;
                for (int r = 3; r <= 17; r++) {
                    if (r == f) continue;
                    if (cnt[r] == 2) pairs++;
                }
                if (pairs == 2) return new Combo(FOUR_TWO_PAIR, f, 0, cards);
            }
        }

        return null;
    }

    /** 飞机识别：连续三张 >= 2 组，+0 / +k / +2k 张 */
    private static Combo tryPlane(int[] cnt, List<Card> cards, int n) {
        List<int[]> runs = tripleRuns(cnt);
        for (int[] run : runs) {
            int start = run[0];
            int k = run[1];
            if (k < 2) continue;
            int top = start + k - 1;

            // 纯飞机
            if (n == k * 3) {
                if (allExactly(cnt, start, top, 3)) {
                    return new Combo(PLANE, top, k, cards);
                }
            }
            // 飞机带单：主体 k*3 + k 张单牌
            if (n == k * 4) {
                if (allExactly(cnt, start, top, 3)
                        && outsideTotal(cnt, start, top) == k) {
                    // 带牌不能是王（王不能拆开当单牌跟飞机），实际规则允许，但通常禁止四张同点作带牌
                    if (!hasQuadOutside(cnt, start, top)) {
                        return new Combo(PLANE_ONE, top, k, cards);
                    }
                }
            }
            // 飞机带对：主体 k*3 + k 对
            if (n == k * 5) {
                if (allExactly(cnt, start, top, 3)
                        && outsidePairCount(cnt, start, top) == k) {
                    return new Combo(PLANE_PAIR, top, k, cards);
                }
            }
        }
        return null;
    }

    /** 区间内每个点数的张数是否都恰好等于 each */
    private static boolean allExactly(int[] cnt, int from, int to, int each) {
        for (int r = from; r <= to; r++) {
            if (cnt[r] != each) return false;
        }
        return true;
    }

    /** 区间外总张数 */
    private static int outsideTotal(int[] cnt, int from, int to) {
        int t = 0;
        for (int r = 3; r <= 17; r++) {
            if (r >= from && r <= to) continue;
            t += cnt[r];
        }
        return t;
    }

    /** 区间外是否含四张同点（不允许作为飞机带牌） */
    private static boolean hasQuadOutside(int[] cnt, int from, int to) {
        for (int r = 3; r <= 17; r++) {
            if (r >= from && r <= to) continue;
            if (cnt[r] == 4) return true;
        }
        return false;
    }

    /** 区间外对子数量；若存在非对子牌返回 -1 */
    private static int outsidePairCount(int[] cnt, int from, int to) {
        int pairs = 0;
        for (int r = 3; r <= 17; r++) {
            if (r >= from && r <= to) continue;
            if (cnt[r] == 0) continue;
            if (cnt[r] == 2) pairs++;
            else return -1;
        }
        return pairs;
    }

    /**
     * 找出所有「连续 >=3 张」的段，每个段尝试所有子长度。
     * 返回 {startRank, length} 列表，按长度降序。
     */
    private static List<int[]> tripleRuns(int[] cnt) {
        // 先找最大连续段
        List<int[]> segs = new ArrayList<int[]>();
        int s = -1;
        for (int r = 3; r <= 17; r++) {
            boolean has = cnt[r] >= 3;
            if (has && s < 0) s = r;
            if (!has && s >= 0) {
                segs.add(new int[]{s, r - s});
                s = -1;
            }
        }
        if (s >= 0) segs.add(new int[]{s, 18 - s});

        // 对每个段展开所有子区间
        List<int[]> out = new ArrayList<int[]>();
        for (int[] seg : segs) {
            int st = seg[0], len = seg[1];
            if (len < 2) continue;
            for (int sub = 2; sub <= len; sub++) {
                for (int off = 0; off + sub <= len; off++) {
                    out.add(new int[]{st + off, sub});
                }
            }
        }
        // 长度降序
        Collections.sort(out, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[1] - a[1];
            }
        });
        return out;
    }

    /**
     * 检查是否构成「顺子/连对」形态。
     * @param cnt      点数计数数组
     * @param each     每个点数需要几张（顺子=1，连对=2）
     * @param count    序列长度（顺子=张数，连对=对数）
     * @return 序列最高 rank；不成立返回 -1
     *
     * 规则：序列必须完全落在 3..14 区间内（不含 2 和王），
     * 且序列外不能有任何牌。
     */
    private static int straightTop(int[] cnt, int each, int count) {
        // 先确认总张数确实吻合
        if (RankCounter.total(cnt) != each * count) return -1;
        // 2 和王绝对不能在顺子里
        if (cnt[15] != 0 || cnt[16] != 0 || cnt[17] != 0) return -1;
        // 找连续段
        for (int top = 14; top - count + 1 >= 3; top--) {
            int start = top - count + 1;
            boolean ok = true;
            for (int r = start; r <= top; r++) {
                if (cnt[r] != each) { ok = false; break; }
            }
            if (!ok) continue;
            // 段外必须全空
            boolean clean = true;
            for (int r = 3; r < start; r++) if (cnt[r] != 0) { clean = false; break; }
            if (!clean) continue;
            for (int r = top + 1; r <= 14; r++) if (cnt[r] != 0) { clean = false; break; }
            if (!clean) continue;
            return top;
        }
        return -1;
    }

    private static int findRankWithCnt(int[] cnt, int c) {
        for (int r = 15; r >= 3; r--) {
            if (cnt[r] == c) return r;
        }
        return -1;
    }

    // (辅助方法见上方 allExactly / outsideTotal / outsidePairCount)

    // ============================================================
    //                        比较
    // ============================================================

    /**
     * next 能否压过 prev。
     * prev 为 null 表示自由出牌，任何合法牌型都能出。
     */
    public static boolean canBeat(Combo prev, Combo next) {
        if (next == null || !next.isValid()) return false;
        if (prev == null || !prev.isValid()) return true;

        if (next.type == ROCKET) return prev.type != ROCKET;
        if (prev.type == ROCKET) return false;

        if (next.type == BOMB) {
            if (prev.type != BOMB) return true;
            return next.key > prev.key;
        }
        if (prev.type == BOMB) return false;

        if (next.type != prev.type) return false;
        if (next.len != prev.len) return false;
        return next.key > prev.key;
    }

    @Override
    public String toString() {
        return typeName() + "[key=" + key + ",len=" + len + ",n=" + size() + "]";
    }
}