package com.lunar.ddz.ai;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.RankCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * 合法走法生成器。
 *
 * 提供两类能力：
 *  - allMoves(hand, prev)：列出所有合法走法（AI 决策 + 提示功能共用）
 *  - followMoves(hand, prev)：只列出能压过 prev 的走法
 *
 * 生成结果已按「牌型自然顺序」排列，调用方自行排序。
 */
public final class MoveGenerator {

    private MoveGenerator() {}

    /** 生成所有合法走法。prev == null 表示自由出牌 */
    public static List<Combo> allMoves(List<Card> hand, Combo prev) {
        List<Combo> out = new ArrayList<Combo>(48);
        if (hand == null || hand.isEmpty()) return out;

        List<Card> sorted = Deck.copySortedDesc(hand);
        int[] cnt = RankCounter.count(sorted);

        if (prev == null) {
            genSingles(cnt, sorted, out);
            genPairs(cnt, sorted, out);
            genTriples(cnt, sorted, out);
            genTripleWith(cnt, sorted, out);
            genStraights(cnt, sorted, out);
            genDoubleStraights(cnt, sorted, out);
            genPlanes(cnt, sorted, out);
            genFourWith(cnt, sorted, out);
            genBombs(cnt, sorted, out);
        } else {
            genFollow(cnt, sorted, prev, out);
        }
        return out;
    }

    // ============================================================
    //                    自由出牌：全量生成
    // ============================================================

    private static void genSingles(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 17; r++) {
            if (cnt[r] >= 1) {
                out.add(Combo.of(Combo.SINGLE, r, 0, take(sorted, r, 1)));
            }
        }
    }

    private static void genPairs(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] >= 2) {
                out.add(Combo.of(Combo.PAIR, r, 0, take(sorted, r, 2)));
            }
        }
    }

    private static void genTriples(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] >= 3) {
                out.add(Combo.of(Combo.TRIPLE, r, 0, take(sorted, r, 3)));
            }
        }
    }

    private static void genTripleWith(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] < 3) continue;
            List<Card> base = take(sorted, r, 3);

            // 三带一：任意单张（不包含 base 的点数）
            for (int k = 3; k <= 17; k++) {
                if (k == r || cnt[k] < 1) continue;
                List<Card> m = new ArrayList<Card>(base);
                m.addAll(take(sorted, k, 1));
                out.add(Combo.of(Combo.TRIPLE_ONE, r, 0, m));
            }

            // 三带二：任意对子（不含王，王无对）
            for (int k = 3; k <= 15; k++) {
                if (k == r || cnt[k] < 2) continue;
                List<Card> m = new ArrayList<Card>(base);
                m.addAll(take(sorted, k, 2));
                out.add(Combo.of(Combo.TRIPLE_PAIR, r, 0, m));
            }
        }
    }

    private static void genStraights(int[] cnt, List<Card> sorted, List<Combo> out) {
        // 起点 3..14，长度 5..12
        for (int len = 5; len <= 12; len++) {
            for (int start = 3; start + len - 1 <= 14; start++) {
                boolean ok = true;
                for (int r = start; r < start + len; r++) {
                    if (cnt[r] < 1) { ok = false; break; }
                }
                if (!ok) continue;
                List<Card> m = new ArrayList<Card>(len);
                for (int r = start; r < start + len; r++) m.addAll(take(sorted, r, 1));
                out.add(Combo.of(Combo.STRAIGHT, start + len - 1, len, m));
            }
        }
    }

    private static void genDoubleStraights(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int pairs = 3; pairs <= 10; pairs++) {
            for (int start = 3; start + pairs - 1 <= 14; start++) {
                boolean ok = true;
                for (int r = start; r < start + pairs; r++) {
                    if (cnt[r] < 2) { ok = false; break; }
                }
                if (!ok) continue;
                List<Card> m = new ArrayList<Card>(pairs * 2);
                for (int r = start; r < start + pairs; r++) m.addAll(take(sorted, r, 2));
                out.add(Combo.of(Combo.DOUBLE_STRAIGHT, start + pairs - 1, pairs, m));
            }
        }
    }

    private static void genPlanes(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int k = 2; k <= 6; k++) {
            for (int start = 3; start + k - 1 <= 15; start++) {
                int top = start + k - 1;
                boolean ok = true;
                for (int r = start; r <= top; r++) {
                    if (cnt[r] < 3) { ok = false; break; }
                }
                if (!ok) continue;

                List<Card> base = new ArrayList<Card>(k * 3);
                int[] used = new int[18];
                for (int r = start; r <= top; r++) {
                    base.addAll(take(sorted, r, 3));
                    used[r] = 3;
                }

                // 纯飞机
                out.add(Combo.of(Combo.PLANE, top, k, base));

                // 飞机带单
                List<Card> singles = pickKickers(cnt, sorted, used, k, 1);
                if (singles != null) {
                    List<Card> m = new ArrayList<Card>(base);
                    m.addAll(singles);
                    out.add(Combo.of(Combo.PLANE_ONE, top, k, m));
                }

                // 飞机带对
                List<Card> pairs = pickKickers(cnt, sorted, used, k, 2);
                if (pairs != null) {
                    List<Card> m = new ArrayList<Card>(base);
                    m.addAll(pairs);
                    out.add(Combo.of(Combo.PLANE_PAIR, top, k, m));
                }
            }
        }
    }

    private static void genFourWith(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] < 4) continue;
            List<Card> base = take(sorted, r, 4);
            int[] used = new int[18];
            used[r] = 4;

            List<Card> singles = pickKickers(cnt, sorted, used, 2, 1);
            if (singles != null) {
                List<Card> m = new ArrayList<Card>(base);
                m.addAll(singles);
                out.add(Combo.of(Combo.FOUR_TWO_SINGLE, r, 0, m));
            }
            List<Card> pairs = pickKickers(cnt, sorted, used, 2, 2);
            if (pairs != null) {
                List<Card> m = new ArrayList<Card>(base);
                m.addAll(pairs);
                out.add(Combo.of(Combo.FOUR_TWO_PAIR, r, 0, m));
            }
        }
    }

    private static void genBombs(int[] cnt, List<Card> sorted, List<Combo> out) {
        for (int r = 3; r <= 15; r++) {
            if (cnt[r] == 4) {
                out.add(Combo.of(Combo.BOMB, r, 0, take(sorted, r, 4)));
            }
        }
        if (cnt[16] >= 1 && cnt[17] >= 1) {
            List<Card> m = new ArrayList<Card>(2);
            m.addAll(take(sorted, 17, 1));
            m.addAll(take(sorted, 16, 1));
            out.add(Combo.of(Combo.ROCKET, 100, 0, m));
        }
    }

    // ============================================================
    //                    跟牌：只生成能压过的
    // ============================================================

    private static void genFollow(int[] cnt, List<Card> sorted, Combo prev, List<Combo> out) {
        if (prev.isRocket()) return;

        switch (prev.type) {
            case Combo.SINGLE:
                for (int r = prev.key + 1; r <= 17; r++) {
                    if (cnt[r] >= 1) out.add(Combo.of(Combo.SINGLE, r, 0, take(sorted, r, 1)));
                }
                break;
            case Combo.PAIR:
                for (int r = prev.key + 1; r <= 15; r++) {
                    if (cnt[r] >= 2) out.add(Combo.of(Combo.PAIR, r, 0, take(sorted, r, 2)));
                }
                break;
            case Combo.TRIPLE:
                for (int r = prev.key + 1; r <= 15; r++) {
                    if (cnt[r] >= 3) out.add(Combo.of(Combo.TRIPLE, r, 0, take(sorted, r, 3)));
                }
                break;
            case Combo.TRIPLE_ONE:
            case Combo.TRIPLE_PAIR: {
                boolean needPair = prev.type == Combo.TRIPLE_PAIR;
                for (int r = prev.key + 1; r <= 15; r++) {
                    if (cnt[r] < 3) continue;
                    List<Card> base = take(sorted, r, 3);
                    int[] used = new int[18];
                    used[r] = 3;
                    List<Card> extra = pickKickers(cnt, sorted, used, 1, needPair ? 2 : 1);
                    if (extra == null) continue;
                    List<Card> m = new ArrayList<Card>(base);
                    m.addAll(extra);
                    out.add(Combo.of(prev.type, r, 0, m));
                }
                break;
            }
            case Combo.STRAIGHT: {
                int len = prev.len;
                for (int start = 3; start + len - 1 <= 14; start++) {
                    int top = start + len - 1;
                    if (top <= prev.key) continue;
                    boolean ok = true;
                    for (int r = start; r <= top; r++) {
                        if (cnt[r] < 1) { ok = false; break; }
                    }
                    if (!ok) continue;
                    List<Card> m = new ArrayList<Card>(len);
                    for (int r = start; r <= top; r++) m.addAll(take(sorted, r, 1));
                    out.add(Combo.of(Combo.STRAIGHT, top, len, m));
                }
                break;
            }
            case Combo.DOUBLE_STRAIGHT: {
                int pairs = prev.len;
                for (int start = 3; start + pairs - 1 <= 14; start++) {
                    int top = start + pairs - 1;
                    if (top <= prev.key) continue;
                    boolean ok = true;
                    for (int r = start; r <= top; r++) {
                        if (cnt[r] < 2) { ok = false; break; }
                    }
                    if (!ok) continue;
                    List<Card> m = new ArrayList<Card>(pairs * 2);
                    for (int r = start; r <= top; r++) m.addAll(take(sorted, r, 2));
                    out.add(Combo.of(Combo.DOUBLE_STRAIGHT, top, pairs, m));
                }
                break;
            }
            case Combo.PLANE:
            case Combo.PLANE_ONE:
            case Combo.PLANE_PAIR: {
                int k = prev.len;
                int per = prev.type == Combo.PLANE ? 0 : (prev.type == Combo.PLANE_ONE ? 1 : 2);
                for (int start = 3; start + k - 1 <= 15; start++) {
                    int top = start + k - 1;
                    if (top <= prev.key) continue;
                    boolean ok = true;
                    for (int r = start; r <= top; r++) {
                        if (cnt[r] < 3) { ok = false; break; }
                    }
                    if (!ok) continue;

                    List<Card> base = new ArrayList<Card>(k * 3);
                    int[] used = new int[18];
                    for (int r = start; r <= top; r++) {
                        base.addAll(take(sorted, r, 3));
                        used[r] = 3;
                    }
                    if (per == 0) {
                        out.add(Combo.of(Combo.PLANE, top, k, base));
                    } else {
                        List<Card> extra = pickKickers(cnt, sorted, used, k, per);
                        if (extra == null) continue;
                        List<Card> m = new ArrayList<Card>(base);
                        m.addAll(extra);
                        out.add(Combo.of(prev.type, top, k, m));
                    }
                }
                break;
            }
            case Combo.FOUR_TWO_SINGLE:
            case Combo.FOUR_TWO_PAIR: {
                boolean needPair = prev.type == Combo.FOUR_TWO_PAIR;
                for (int r = prev.key + 1; r <= 15; r++) {
                    if (cnt[r] < 4) continue;
                    List<Card> base = take(sorted, r, 4);
                    int[] used = new int[18];
                    used[r] = 4;
                    List<Card> extra = pickKickers(cnt, sorted, used, 2, needPair ? 2 : 1);
                    if (extra == null) continue;
                    List<Card> m = new ArrayList<Card>(base);
                    m.addAll(extra);
                    out.add(Combo.of(prev.type, r, 0, m));
                }
                break;
            }
            default:
                break;
        }

        // 炸弹 / 王炸永远能压非炸弹
        if (prev.type != Combo.BOMB) {
            for (int r = 3; r <= 15; r++) {
                if (cnt[r] == 4) out.add(Combo.of(Combo.BOMB, r, 0, take(sorted, r, 4)));
            }
            if (cnt[16] >= 1 && cnt[17] >= 1) {
                List<Card> m = new ArrayList<Card>(2);
                m.addAll(take(sorted, 17, 1));
                m.addAll(take(sorted, 16, 1));
                out.add(Combo.of(Combo.ROCKET, 100, 0, m));
            }
        } else {
            // 压炸弹：更大的炸弹或王炸
            for (int r = prev.key + 1; r <= 15; r++) {
                if (cnt[r] == 4) out.add(Combo.of(Combo.BOMB, r, 0, take(sorted, r, 4)));
            }
            if (cnt[16] >= 1 && cnt[17] >= 1) {
                List<Card> m = new ArrayList<Card>(2);
                m.addAll(take(sorted, 17, 1));
                m.addAll(take(sorted, 16, 1));
                out.add(Combo.of(Combo.ROCKET, 100, 0, m));
            }
        }
    }

    // ============================================================
    //                    工具
    // ============================================================

    private static List<Card> take(List<Card> sortedDesc, int rank, int count) {
        List<Card> out = new ArrayList<Card>(count);
        for (Card c : sortedDesc) {
            if (c.rank == rank && out.size() < count) out.add(c);
        }
        return out;
    }

    /**
     * 挑带牌：优先最小、且不拆对子/三张/炸弹的单张或对子。
     * @param units 需要几组
     * @param per   每组几张（1=单张，2=对子）
     */
    private static List<Card> pickKickers(int[] cnt, List<Card> sorted, int[] used,
                                          int units, int per) {
        List<Card> out = new ArrayList<Card>(units * per);

        if (per == 1) {
            // 优先独立单张（cnt==1），从小到大
            for (int r = 3; r <= 17 && out.size() < units; r++) {
                if (used[r] > 0) continue;
                if (cnt[r] - used[r] == 1) {
                    out.addAll(take(sorted, r, 1));
                }
            }
            // 不够则拆对子
            for (int r = 3; r <= 17 && out.size() < units; r++) {
                if (used[r] > 0) continue;
                int avail = cnt[r] - used[r];
                if (avail >= 2 && cnt[r] < 4) {
                    out.addAll(take(sorted, r, 1));
                }
            }
            // 还不够，随便拿（但不拆炸弹，除非没得选）
            for (int r = 3; r <= 17 && out.size() < units; r++) {
                if (used[r] > 0) continue;
                if (cnt[r] >= 4) continue;
                int avail = cnt[r] - used[r];
                if (avail >= 1) out.addAll(take(sorted, r, 1));
            }
        } else {
            // 优先独立对子，从小到大
            for (int r = 3; r <= 15 && out.size() < units * 2; r++) {
                if (used[r] > 0) continue;
                if (cnt[r] - used[r] == 2) {
                    out.addAll(take(sorted, r, 2));
                }
            }
            // 其次拆三张
            for (int r = 3; r <= 15 && out.size() < units * 2; r++) {
                if (used[r] > 0) continue;
                if (cnt[r] - used[r] >= 3 && cnt[r] < 4) {
                    out.addAll(take(sorted, r, 2));
                }
            }
        }

        return out.size() == units * per ? out : null;
    }
}