package com.lunar.ddz.ui;

/**
 * 主题配色。三套皮肤，均为深色系桌面 + 暖灰牌面，
 * 保证长时间游戏不刺眼。
 */
public final class Theme {

    public static final int SKIN_EMERALD = 0;
    public static final int SKIN_MIDNIGHT = 1;
    public static final int SKIN_SAKURA = 2;
    public static final int SKIN_COUNT = 3;

    public final int id;
    public final String name;

    // 桌面
    public final int tableTop;
    public final int tableBottom;
    public final int tableVignette;

    // 牌
    public final int cardRed;
    public final int cardBlack;
    public final int backColor1;
    public final int backColor2;
    public final int backLine;
    public final int slotColor;

    // 强调色
    public final int accent;
    public final int accentDark;
    public final int danger;
    public final int success;

    // 文字
    public final int textPrimary;
    public final int textSecondary;
    public final int textOnAccent;

    // 面板
    public final int panelBg;
    public final int panelBorder;

    private Theme(int id, String name,
                  int tableTop, int tableBottom, int tableVignette,
                  int cardRed, int cardBlack,
                  int backColor1, int backColor2, int backLine, int slotColor,
                  int accent, int accentDark, int danger, int success,
                  int textPrimary, int textSecondary, int textOnAccent,
                  int panelBg, int panelBorder) {
        this.id = id;
        this.name = name;
        this.tableTop = tableTop;
        this.tableBottom = tableBottom;
        this.tableVignette = tableVignette;
        this.cardRed = cardRed;
        this.cardBlack = cardBlack;
        this.backColor1 = backColor1;
        this.backColor2 = backColor2;
        this.backLine = backLine;
        this.slotColor = slotColor;
        this.accent = accent;
        this.accentDark = accentDark;
        this.danger = danger;
        this.success = success;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textOnAccent = textOnAccent;
        this.panelBg = panelBg;
        this.panelBorder = panelBorder;
    }

    /** 翡翠桌 */
    public static final Theme EMERALD = new Theme(
            SKIN_EMERALD, "翡翠",
            0xFF134E3A, 0xFF0A2B21, 0xFF04120D,
            0xFFD63B3B, 0xFF1E2430,
            0xFF1B4A6E, 0xFF0E2C44, 0xFF7FB4DA, 0xFFB8D4E8,
            0xFFFFC94D, 0xFFE0A425, 0xFFE2574C, 0xFF3FC98A,
            0xFFF2F6F4, 0xFF9DB5AC, 0xFF10241C,
            0xCC14382C, 0x407FB4DA);

    /** 午夜蓝 */
    public static final Theme MIDNIGHT = new Theme(
            SKIN_MIDNIGHT, "午夜",
            0xFF1B2340, 0xFF0D1228, 0xFF050814,
            0xFFE0575C, 0xFF202635,
            0xFF4A3B7A, 0xFF2A1F4D, 0xFF9C8AD4, 0xFFC0B4E4,
            0xFF5EC8F0, 0xFF35A0C4, 0xFFEC6A6A, 0xFF56D6A0,
            0xFFF0F2F8, 0xFF9AA3BD, 0xFF101528,
            0xCC161F38, 0x405EC8F0);

    /** 樱粉 */
    public static final Theme SAKURA = new Theme(
            SKIN_SAKURA, "樱粉",
            0xFF4A2530, 0xFF2E1520, 0xFF170A10,
            0xFFE0464E, 0xFF2A1E24,
            0xFF8C3B52, 0xFF5E2035, 0xFFE8A8B8, 0xFFF2C8D2,
            0xFFFFB3C6, 0xFFE08AA0, 0xFFE2574C, 0xFF5FC98A,
            0xFFFBF2F4, 0xFFC4A6AE, 0xFF2A1420,
            0xCC3A1D28, 0x60FFB3C6);

    private static final Theme[] ALL = {EMERALD, MIDNIGHT, SAKURA};

    public static Theme of(int id) {
        if (id < 0 || id >= ALL.length) return EMERALD;
        return ALL[id];
    }

    public static Theme next(Theme t) {
        return of((t.id + 1) % SKIN_COUNT);
    }

    public static int alpha(int color, float a) {
        int aa = (int) (255 * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (aa << 24);
    }
}