package com.lunar.ddz.ai;

import com.lunar.ddz.core.Card;
import com.lunar.ddz.core.Combo;
import com.lunar.ddz.core.Deck;
import com.lunar.ddz.core.GameState;
import com.lunar.ddz.net.LlmClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 用大模型做决策的 AI 大脑。
 *
 * 工作方式：
 *  1. 把当前牌局序列化成简洁文本（我的手牌 / 桌面牌 / 各家张数 / 身份）
 *  2. 要求模型只输出 JSON：{"action":"play","cards":["S3","H3"]} 或 {"action":"pass"}
 *  3. 解析结果 → 映射回手牌里的具体牌张
 *  4. 任何一步失败 → 回退到本地算法（LocalBrain），保证永远不会卡死
 */
public final class LlmBrain {

    public interface OnDecided {
        /** 返回要出的牌；null 表示不要 */
        void onDecided(List<Card> cards, boolean fromLlm);
    }

    private final int seat;
    private final LlmClient client;
    private final BrainedPlayer fallback;
    private final String persona;

    public LlmBrain(int seat, LlmClient client, int fallbackLevel, String persona) {
        this.seat = seat;
        this.client = client;
        this.fallback = new BrainedPlayer(fallbackLevel, System.nanoTime() + seat);
        this.persona = persona;
    }

    /** 叫分决策（本地算法，不用模型，省 token 且更快） */
    public int decideBid(List<Card> hand, int highest) {
        return fallback.bid(hand, highest);
    }

    /** 出牌决策：先问模型，失败则本地 */
    public void decide(final GameState gs, final OnDecided cb) {
        final Combo prev = gs.isFreePlay(seat) ? null : gs.table;

        // 能一手走完，不必浪费 token
        List<Card> instant = instantWin(gs);
        if (instant != null) {
            cb.onDecided(instant, false);
            return;
        }

        String system = buildSystem();
        String user = buildUser(gs, prev);

        client.ask(system, user, new LlmClient.Callback() {
            @Override
            public void onReply(String text) {
                List<Card> parsed = parse(text, gs, prev);
                if (parsed != null) {
                    cb.onDecided(parsed.isEmpty() ? null : parsed, true);
                } else {
                    fallbackNow(gs, cb);
                }
            }

            @Override
            public void onError(String message) {
                fallbackNow(gs, cb);
            }
        });
    }

    private void fallbackNow(GameState gs, OnDecided cb) {
        List<Card> mv = fallback.decide(gs, seat);
        cb.onDecided(mv, false);
    }

    // ============================================================
    //                      提示词构造
    // ============================================================

    private String buildSystem() {
        return "你是一名斗地主高手，正在以「" + persona + "」的风格打牌。\n"
                + "你只输出 JSON，不要任何解释、不要 markdown 代码块。\n"
                + "格式严格为以下之一：\n"
                + "  {\"action\":\"play\",\"cards\":[\"S3\",\"H3\"]}\n"
                + "  {\"action\":\"pass\"}\n"
                + "牌的写法：花色字母+点数。S=黑桃 H=红桃 C=梅花 D=方块，大小王写作 SJ=BJ。\n"
                + "点数用 3 4 5 6 7 8 9 10 J Q K A 2，例如方块A写作 DA，大王写作 BJ。\n"
                + "必须从你手里的牌中选取，且组成的牌型必须合法、且能压过桌面积牌。";
    }

    private String buildUser(GameState gs, Combo prev) {
        StringBuilder sb = new StringBuilder();

        boolean lord = gs.isLandlord(seat);
        sb.append("我的座位：").append(seatName(seat))
                .append("，身份：").append(lord ? "地主" : "农民").append("\n");
        sb.append("地主是：").append(seatName(gs.landlord)).append("\n\n");

        sb.append("我的手牌（").append(gs.hands[seat].size()).append("张）：\n");
        sb.append(cardList(gs.hands[seat])).append("\n\n");

        sb.append("各家剩余张数：");
        for (int s = 0; s < 3; s++) {
            sb.append(seatName(s)).append("=").append(gs.hands[s].size());
            if (s < 2) sb.append("，");
        }
        sb.append("\n\n");

        if (prev == null) {
            sb.append("现在轮到我自由出牌（不需要压任何牌）。\n");
            sb.append("目标：尽量少留手数，优先出小牌保持大牌控场。\n");
        } else {
            sb.append("桌面待压：").append(prev.typeName())
                    .append("（").append(cardList(prev.cards)).append("）")
                    .append("，由 ").append(seatName(gs.tableOwner)).append(" 打出。\n");
            sb.append("我必须出同类型且更大的牌，或者炸弹，或者选择不要。\n");
        }

        // 队友情况
        if (!lord) {
            int mate = -1;
            for (int s = 0; s < 3; s++) {
                if (s != seat && !gs.isLandlord(s)) mate = s;
            }
            if (mate >= 0) {
                sb.append("我的农民队友是 ").append(seatName(mate))
                        .append("，剩 ").append(gs.hands[mate].size()).append(" 张。\n");
                if (gs.hands[mate].size() <= 2) {
                    sb.append("队友快出完了，尽量让他走，不要压队友的牌。\n");
                }
            }
        }

        sb.append("\n请给出你的决定（只输出 JSON）：");
        return sb.toString();
    }

    private static String hasJokerText(Card c) {
        if (c.rank == Card.RANK_BIG_JOKER) return "BJ";
        if (c.rank == Card.RANK_SMALL_JOKER) return "SJ";
        return null;
    }

    private static String cardCode(Card c) {
        String joker = hasJokerText(c);
        if (joker != null) return joker;
        char suit;
        switch (c.suit) {
            case Card.SUIT_SPADE: suit = 'S'; break;
            case Card.SUIT_HEART: suit = 'H'; break;
            case Card.SUIT_CLUB: suit = 'C'; break;
            default: suit = 'D'; break;
        }
        String rank;
        switch (c.rank) {
            case 11: rank = "J"; break;
            case 12: rank = "Q"; break;
            case 13: rank = "K"; break;
            case 14: rank = "A"; break;
            case 15: rank = "2"; break;
            case 10: rank = "10"; break;
            default: rank = String.valueOf(c.rank); break;
        }
        return suit + rank;
    }

    private static String cardList(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(cardCode(cards.get(i)));
        }
        return sb.toString();
    }

    public static String seatName(int seat) {
        switch (seat) {
            case GameState.SEAT_ME: return "我";
            case GameState.SEAT_RIGHT: return "右家";
            case GameState.SEAT_LEFT: return "左家";
            default: return "?";
        }
    }

    // ============================================================
    //                      解析模型输出
    // ============================================================

    /**
     * 解析模型回复。
     * @return 非 null 表示解析成功（空列表代表「不要」）；null 表示解析失败需要降级
     */
    private List<Card> parse(String text, GameState gs, Combo prev) {
        if (text == null) return null;

        String json = extractJson(text);
        if (json == null) return null;

        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String action = obj.optString("action", "").toLowerCase();

            if (action.equals("pass")) {
                if (prev == null) return null;   // 自由出牌不能不要
                return new ArrayList<Card>();
            }

            if (!action.equals("play")) return null;

            org.json.JSONArray arr = obj.optJSONArray("cards");
            if (arr == null || arr.length() == 0) return null;

            List<Card> chosen = new ArrayList<Card>(arr.length());
            List<Card> pool = new ArrayList<Card>(gs.hands[seat]);

            for (int i = 0; i < arr.length(); i++) {
                String code = arr.optString(i, "").trim().toUpperCase();
                Card c = matchCard(pool, code);
                if (c == null) return null;
                chosen.add(c);
                pool.remove(c);
            }

            // 校验牌型
            Combo combo = Combo.analyze(chosen);
            if (combo == null) return null;
            if (prev != null && !Combo.canBeat(prev, combo)) return null;

            return chosen;

        } catch (Exception e) {
            return null;
        }
    }

    /** 从回复中截取第一个 JSON 对象 */
    private static String extractJson(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    /** 按编码匹配手牌 */
    private static Card matchCard(List<Card> pool, String code) {
        for (Card c : pool) {
            if (cardCode(c).equalsIgnoreCase(code)) return c;
        }
        // 宽松匹配：只给了点数没给花色
        for (Card c : pool) {
            if (cardCode(c).substring(1).equalsIgnoreCase(code)) return c;
        }
        return null;
    }

    /** 检查是否有一手出完的机会，有就直接返回，省一次 API 调用 */
    private List<Card> instantWin(GameState gs) {
        List<Card> hand = gs.hands[seat];
        Combo prev = gs.isFreePlay(seat) ? null : gs.table;
        List<Combo> moves = MoveGenerator.allMoves(hand, prev);
        for (Combo m : moves) {
            if (m.size() == hand.size()) return m.cards;
        }
        return null;
    }
}