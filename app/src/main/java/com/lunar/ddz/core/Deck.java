package com.lunar.ddz.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 牌堆工具：建牌、洗牌、排序、增删。 */
public final class Deck {

    public static final int DECK_SIZE = 54;

    private Deck() {}

    /** 标准 54 张牌，未排序 */
    public static List<Card> newDeck() {
        List<Card> deck = new ArrayList<Card>(DECK_SIZE);
        int id = 0;
        for (int rank = 3; rank <= 15; rank++) {
            for (int suit = 0; suit < 4; suit++) {
                deck.add(new Card(id++, rank, suit));
            }
        }
        deck.add(new Card(id++, Card.RANK_SMALL_JOKER, Card.SUIT_JOKER));
        deck.add(new Card(id, Card.RANK_BIG_JOKER, Card.SUIT_JOKER));
        return deck;
    }

    public static void shuffle(List<Card> deck, Random rnd) {
        // Fisher-Yates，保证均匀分布
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Card tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
    }

    /** 升序：小 -> 大 */
    public static void sortAsc(List<Card> cards) {
        Collections.sort(cards);
    }

    /** 降序：大 -> 小（斗地主手牌习惯） */
    public static void sortDesc(List<Card> cards) {
        Collections.sort(cards, Collections.reverseOrder());
    }

    public static List<Card> copy(List<Card> src) {
        return new ArrayList<Card>(src);
    }

    public static List<Card> copySortedDesc(List<Card> src) {
        List<Card> out = new ArrayList<Card>(src);
        sortDesc(out);
        return out;
    }

    /** 从 hand 中移除 remove 里的牌；若有不存在的牌返回 false */
    public static boolean removeAll(List<Card> hand, List<Card> remove) {
        boolean ok = true;
        for (Card c : remove) {
            if (!hand.remove(c)) ok = false;
        }
        return ok;
    }

    /** hand 是否完整包含 cards */
    public static boolean containsAll(List<Card> hand, List<Card> cards) {
        List<Card> tmp = new ArrayList<Card>(hand);
        for (Card c : cards) {
            if (!tmp.remove(c)) return false;
        }
        return true;
    }

    /** 按点数从 hand 里挑出 count 张 rank 的牌 */
    public static List<Card> takeRank(List<Card> sortedDesc, int rank, int count) {
        List<Card> out = new ArrayList<Card>(count);
        for (Card c : sortedDesc) {
            if (c.rank == rank && out.size() < count) out.add(c);
        }
        return out.size() == count ? out : null;
    }

    /** 把牌转成短字符串，如 "♠A ♥2 大王" */
    public static String describe(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(cards.get(i).shortName());
        }
        return sb.toString();
    }
}