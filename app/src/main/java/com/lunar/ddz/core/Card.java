package com.lunar.ddz.core;

/**
 * 一张扑克牌（不可变值对象）。
 *
 * rank 取值：
 *   3..10  -> 3..10
 *   11     -> J
 *   12     -> Q
 *   13     -> K
 *   14     -> A
 *   15     -> 2
 *   16     -> 小王
 *   17     -> 大王
 *
 * suit 取值：0=黑桃 1=红桃 2=梅花 3=方块 4=王（无花色）
 * id  ：0..53 全局唯一，用于判定同一张牌
 */
public final class Card implements Comparable<Card> {

    public static final int SUIT_SPADE = 0;
    public static final int SUIT_HEART = 1;
    public static final int SUIT_CLUB = 2;
    public static final int SUIT_DIAMOND = 3;
    public static final int SUIT_JOKER = 4;

    public static final int RANK_SMALL_JOKER = 16;
    public static final int RANK_BIG_JOKER = 17;

    public static final int MIN_RANK = 3;
    public static final int MAX_RANK = 17;

    public final int id;
    public final int rank;
    public final int suit;

    public Card(int id, int rank, int suit) {
        this.id = id;
        this.rank = rank;
        this.suit = suit;
    }

    public boolean isJoker() {
        return rank >= RANK_SMALL_JOKER;
    }

    public boolean isRed() {
        return suit == SUIT_HEART || suit == SUIT_DIAMOND || rank == RANK_BIG_JOKER;
    }

    /** 点数文字：3..10 J Q K A 2 小 大 */
    public String rankText() {
        switch (rank) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            case 15: return "2";
            case RANK_SMALL_JOKER: return "小";
            case RANK_BIG_JOKER: return "大";
            default: return String.valueOf(rank);
        }
    }

    /** 花色符号 */
    public String suitText() {
        switch (suit) {
            case SUIT_SPADE: return "\u2660";
            case SUIT_HEART: return "\u2665";
            case SUIT_CLUB: return "\u2663";
            case SUIT_DIAMOND: return "\u2666";
            default: return "";
        }
    }

    public String shortName() {
        if (isJoker()) return rank == RANK_BIG_JOKER ? "大王" : "小王";
        return suitText() + rankText();
    }

    @Override
    public int compareTo(Card o) {
        if (rank != o.rank) return rank - o.rank;
        return suit - o.suit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        return ((Card) o).id == id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return shortName();
    }
}
