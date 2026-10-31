package com.lunar.ddz.ui;

/**
 * 主题配色 —— 经典棋牌室风格。
 *
 * 三套皮肤都基于「深色毛毡桌面 + 木质/金属边」的实体牌桌逻辑，
 * 而不是扁平化的 App UI。
 */
public final class Theme {

    public static final int SKIN_GREEN = 0;    // 经典绿毡
    public static final int SKIN_NAVY = 1;     // 深蓝绒
    public static final int SKIN_WINE = 2;     // 酒红绒
    public static final int SKIN_COUNT = 3;

    public final int id;
    public final String name;

    // 桌面（从上到下的毛毡渐变）
    public final int feltTop;
    public final int feltMid;
    public final int feltBottom;
    // 毛毡噪点色
    public final int feltNoise;
    // 木框
    public final int woodDark;
    public final int woodLight;
    // 桌沿高光
    public final int rimGlow;

    // 牌
    public final int cardRed;
    public final int cardBlack;
    public final int backColor1;
    public final int backColor2;
    public final int backLine;

    // 强调色（按钮、高亮、选中）
    public final int accent;
    public final int accentDeep;
    public final int gold;
    public final int danger;
    public final int success;

    // 文字
    public final int textPrimary;
    public final int textSecondary;
    public final int textOnAccent;
    public final int textShadow;

    // 面板 / 气泡
    public final int panelBg;
    public final int panelBorder;

    private Theme(int id, String name,
                  int feltTop, int feltMid, int feltBottom, int feltNoise,
                  int woodDark, int woodLight, int rimGlow,
                  int cardRed, int cardBlack,
                  int backColor1, int backColor2, int backLine,
                  int accent, int accentDeep, int gold, int danger, int success,
                  int textPrimary, int textSecondary, int textOnAccent, int textShadow,
                  int panelBg, int panelBorder) {
        this.id = id;
        this.name = name;
        this.feltTop = feltTop;
        this.feltMid = feltMid;
        this.feltBottom = feltBottom;
        this.feltNoise = feltNoise;
        this.woodDark = woodDark;
        this.woodLight = woodLight;
        this.rimGlow = rimGlow;
        this.cardRed = cardRed;
        this.cardBlack = cardBlack;
        this.backColor1 = backColor1;
        this.backColor2 = backColor2;
        this.backLine = backLine;
        this.accent = accent;
        this.accentDeep = accentDeep;
        this.gold = gold;
        this.danger = danger;
        this.success = success;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textOnAccent = textOnAccent;
        this.textShadow = textShadow;
        this.panelBg = panelBg;
        this.panelBorder = panelBorder;
    }

    /** 经典绿毡 + 深棕木框 */
    public static final Theme GREEN = new Theme(
            SKIN_GREEN, "经典绿",
            0xFF1F6B45, 0xFF175638, 0xFF0E3A26, 0xFF0A2E1E,
            0xFF3D2415, 0xFF6B4426, 0x33FFFFFF,
            0xFFD22E2E, 0xFF1A1F26,
            0xFF2C5F8A, 0xFF17395C, 0xFF8FC3E8,
            0xFFFFC53D, 0xFFD99A14, 0xFFFFD96B,
            0xFFE04848, 0xFF3FC98A,
            0xFFFFFFFF, 0xFFB9D4C5, 0xFF1A2E22, 0x66000000,
            0xF2122B20, 0x66FFD96B);

    /** 深蓝绒 + 银灰金属边 */
    public static final Theme NAVY = new Theme(
            SKIN_NAVY, "夜空蓝",
            0xFF20304F, 0xFF172541, 0xFF0D1729, 0xFF0A1322,
            0xFF2A3038, 0xFF4A5563, 0x33D0E4FF,
            0xFFE04248, 0xFF1A1F26,
            0xFF4A3B7A, 0xFF2A1F4D, 0xFFB0A2E8,
            0xFF5EC8F0, 0xFF2E9CC4, 0xFFA8E6FF,
            0xFFEC6A6A, 0xFF56D6A0,
            0xFFF5F8FF, 0xFFA3B2CC, 0xFF0F1828, 0x66000000,
            0xF20E1828, 0x665EC8F0);

    /** 酒红绒 + 金边 */
    public static final Theme WINE = new Theme(
            SKIN_WINE, "酒红",
            0xFF5C1F2A, 0xFF44151F, 0xFF2C0D14, 0xFF22090F,
            0xFF3A1A12, 0xFF6B3520, 0x33FFD9B3,
            0xFFD93A3A, 0xFF1A1F26,
            0xFF7A2F3F, 0xFF4D1826, 0xFFE8A8B8,
            0xFFFFB84D, 0xFFD98A14, 0xFFFFDFA0,
            0xFFE04848, 0xFF5FC98A,
            0xFFFFF5EE, 0xFFD4AFA8, 0xFF2A0F14, 0x66000000,
            0xF2220C12, 0x66FFB84D);

    private static final Theme[] ALL = {GREEN, NAVY, WINE};

    public static Theme of(int id) {
        if (id < 0 || id >= ALL.length) return GREEN;
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