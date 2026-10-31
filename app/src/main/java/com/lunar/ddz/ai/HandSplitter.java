package com.lunar.ddz.ai;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.RankCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * 手牌拆解器：把一手牌拆成若干「可出单元」，估算还需几手出完。
 *
 * 核心思想：手数越少，出完越快。AI 评估候选走法时，
 * 用「拆完后的剩余手数」作为主要代价指标。
 *
 * 采用贪心 + 优先级：
 *   1. 王炸、炸弹先固定（不拆）
 *   2. 长顺子优先（一次走更多张）
 *   3. 连对、飞机
 *   4. 三张（尽量带牌）
 *   5. 对子
 *   6. 单张
 */
public final class HandSplitter {

    /** 拆解结果 */
    public static final class Result {
        /** 每个可出单元 */
        public final List<List<Card>> groups = new ArrayList<List<Card>>();
        /** 手数 */
        public int hands() {
            return groups.size();
        }
        public int bombs;
        public int bigCards;
        /** 综合评分，越高越好 */
        public int score;
    }

    private HandSplitter() {}

    public static Result split(List<Card> hand) {
        Result r = new Result();
        if (hand == null || hand.isEmpty()) {
            r.score = 0;
            return r;
        }

        List<Card> pool = Deck.copySortedDesc(hand);
        int[] cnt = RankCounter.count(pool);

        // 1) 王炸
        if (cnt[16] > 0 && cnt[17] > 0) {
            List<Card> rocket = new ArrayList<Card>(2);
            rocket.addAll(Deck.takeRank(pool, 17, 1));
            rocket.addAll(Deck.takeRank(pool, 16, 1));
            pool.removeAll(rocket);
            cnt[17]--;
            cnt[16]--;
            r.groups.add(rocket);
            r.bombs++;
        }

        // 2) 炸弹（四张同点）
        for (int rank = 15; rank >= 3; rank--) {
            while (cnt[rank] == 4) {
                List<Card> bomb = Deck.takeRank(pool, rank, 4);
                pool.removeAll(bomb);
                cnt[rank] -= 4;
                r.groups.add(bomb);
                r.bombs++;
            }
        }

        // 3) 飞机（连续三张，>=2 组，优先带单）
        extractPlanes(pool, cnt, r);

        // 4) 连对（>=3 对连续）
        extractDoubleStraights(pool, cnt, r);

        // 5) 顺子（>=5 张连续）
        extractStraights(pool, cnt, r);

        // 6) 三张 → 尽量带一张小单
        for (int rank = 15; rank >= 3; rank--) {
            while (cnt[rank] >= 3) {
                List<Card> triple = Deck.takeRank(pool, rank, 3);
                pool.removeAll(triple);
                cnt[rank] -= 3;

                List<Card> kicker = pickLowestSingle(pool, cnt, rank);
                if (kicker != null && !kicker.isEmpty()) {
                    triple.addAll(kicker);
                    pool.removeAll(kicker);
                    cnt[kicker.get(0).rank]--;
                }
                r.groups.add(triple);
            }
        }

        // 7) 对子
        for (int rank = 15; rank >= 3; rank--) {
            while (cnt[rank] >= 2) {
                List<Card> pair = Deck.takeRank(pool, rank, 2);
                pool.removeAll(pair);
                cnt[rank] -= 2;
                r.groups.add(pair);
            }
        }

        // 8) 剩余单张
        for (Card c : pool) {
            List<Card> one = new ArrayList<Card>(1);
            one.add(c);
            r.groups.add(one);
        }

        r.bigCards = countBig(hand);
        r.score = evaluate(r);
        return r;
    }

    private static int evaluate(Result r) {
        int s = 100 - r.hands() * 10;
        s += r.bombs * 20;
        s += r.bigCards * 3;
        int singles = 0;
        for (List<Card> g : r.groups) if (g.size() == 1) singles++;
        s -= singles * 4;
        return s;
    }

    public static int countBig(List<Card> hand) {
        int n = 0;
        for (Card c : hand) if (c.rank >= 14) n++;
        return n;
    }

    /** 挑一张最小的独立单张（不拆对子/三张），用于三带一的带牌 */
    private static List<Card> pickLowestSingle(List<Card> pool, int[] cnt, int exclude) {
        Card best = null;
        for (Card c : pool) {
            if (c.rank == exclude) continue;
            if (c.rank >= 16) continue;   // 王不带
            if (cnt[c.rank] != 1) continue;
            if (best == null || c.rank < best.rank) best = c;
        }
        if (best == null) return null;
        List<Card> out = new ArrayList<Card>(1);
        out.add(best);
        return out;
    }

    /** 抽取飞机：从长到短，尽量一次抽多 */
    private static void extractPlanes(List<Card> pool, int[] cnt, Result r) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int len = 6; len >= 2 && !progress; len--) {
                for (int top = 15; top - len + 1 >= 3 && !progress; top--) {
                    int start = top - len + 1;
                    boolean ok = true;
                    for (int rank = start; rank <= top; rank++) {
                        if (cnt[rank] < 3) { ok = false; break; }
                    }
                    if (!ok) continue;

                    List<Card> plane = new ArrayList<Card>(len * 3);
                    for (int rank = start; rank <= top; rank++) {
                        List<Card> t = Deck.takeRank(pool, rank, 3);
                        plane.addAll(t);
                        pool.removeAll(t);
                        cnt[rank] -= 3;
                    }

                    // 尝试带 len 张最小单牌
                    List<Card> kickers = new ArrayList<Card>(len);
                    for (int i = 0; i < len; i++) {
                        Card k = lowestSingleCard(pool, cnt);
                        if (k == null) break;
                        kickers.add(k);
                        pool.remove(k);
                        cnt[k.rank]--;
                    }
                    plane.addAll(kickers);

                    r.groups.add(plane);
                    progress = true;
                }
            }
        }
    }

    private static Card lowestSingleCard(List<Card> pool, int[] cnt) {
        Card best = null;
        for (Card c : pool) {
            if (c.rank >= 16) continue;
            if (cnt[c.rank] != 1) continue;
            if (best == null || c.rank < best.rank) best = c;
        }
        return best;
    }

    private static void extractDoubleStraights(List<Card> pool, int[] cnt, Result r) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int len = 10; len >= 3 && !progress; len--) {
                for (int top = 14; top - len + 1 >= 3 && !progress; top--) {
                    int start = top - len + 1;
                    boolean ok = true;
                    for (int rank = start; rank <= top; rank++) {
                        if (cnt[rank] < 2) { ok = false; break; }
                    }
                    if (!ok) continue;

                    List<Card> ds = new ArrayList<Card>(len * 2);
                    for (int rank = start; rank <= top; rank++) {
                        List<Card> p = Deck.takeRank(pool, rank, 2);
                        ds.addAll(p);
                        pool.removeAll(p);
                        cnt[rank] -= 2;
                    }
                    r.groups.add(ds);
                    progress = true;
                }
            }
        }
    }

    private static void extractStraights(List<Card> pool, int[] cnt, Result r) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int len = 12; len >= 5 && !progress; len--) {
                for (int top = 14; top - len + 1 >= 3 && !progress; top--) {
                    int start = top - len + 1;
                    boolean ok = true;
                    for (int rank = start; rank <= top; rank++) {
                        if (cnt[rank] < 1) { ok = false; break; }
                    }
                    if (!ok) continue;

                    // 不要拆炸弹
                    boolean hasBomb = false;
                    for (int rank = start; rank <= top; rank++) {
                        if (cnt[rank] >= 4) { hasBomb = true; break; }
                    }
                    if (hasBomb) continue;

                    List<Card> straight = new ArrayList<Card>(len);
                    for (int rank = start; rank <= top; rank++) {
                        List<Card> one = Deck.takeRank(pool, rank, 1);
                        straight.addAll(one);
                        pool.removeAll(one);
                        cnt[rank] -= 1;
                    }
                    r.groups.add(straight);
                    progress = true;
                }
            }
        }
    }
}