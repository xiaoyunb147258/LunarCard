package com.lunar.ddz.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 点数计数器工具。所有方法都基于「下标 = rank」的 int[18] 数组，
 * 下标 0/1/2 恒为 0（不使用），3..17 为有效 rank。
 */
public final class RankCounter {

    private RankCounter() {}

    /** 统计每张点数的张数，返回 length=18 的数组 */
    public static int[] count(List<Card> cards) {
        int[] c = new int[18];
        if (cards == null) return c;
        for (Card card : cards) {
            if (card.rank >= 3 && card.rank <= 17) c[card.rank]++;
        }
        return c;
    }

    /** 从大到小返回有牌的 rank */
    public static int[] descRanks(int[] cnt) {
        List<Integer> tmp = new ArrayList<Integer>(8);
        for (int r = 17; r >= 3; r--) {
            if (cnt[r] > 0) tmp.add(r);
        }
        int[] out = new int[tmp.size()];
        for (int i = 0; i < out.length; i++) out[i] = tmp.get(i);
        return out;
    }

    /** 从小到大返回有牌的 rank */
    public static int[] ascRanks(int[] cnt) {
        List<Integer> tmp = new ArrayList<Integer>(8);
        for (int r = 3; r <= 17; r++) {
            if (cnt[r] > 0) tmp.add(r);
        }
        int[] out = new int[tmp.size()];
        for (int i = 0; i < out.length; i++) out[i] = tmp.get(i);
        return out;
    }

    /** 手牌总张数 */
    public static int total(int[] cnt) {
        int s = 0;
        for (int r = 3; r <= 17; r++) s += cnt[r];
        return s;
    }

    /** 是否含王 */
    public static boolean hasJoker(int[] cnt) {
        return cnt[16] > 0 || cnt[17] > 0;
    }

    /** 炸弹数量（含王炸计 1） */
    public static int bombCount(int[] cnt) {
        int n = 0;
        for (int r = 3; r <= 15; r++) if (cnt[r] == 4) n++;
        if (cnt[16] > 0 && cnt[17] > 0) n++;
        return n;
    }

    /** 手牌强度评估：大牌加权 */
    public static int handStrength(List<Card> hand) {
        int v = 0;
        for (Card c : hand) {
            switch (c.rank) {
                case 17: v += 8; break;
                case 16: v += 6; break;
                case 15: v += 4; break;
                case 14: v += 3; break;
                case 13: v += 2; break;
                default:  v += 1; break;
            }
        }
        return v;
    }
}