package com.lunar.ddz.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地存档。记录统计、设置、成就。
 */
public final class Prefs {

    private static final String FILE = "lunar_ddz";

    private static final String K_SKIN = "skin";
    private static final String K_DIFF = "difficulty";
    private static final String K_SCORE = "score";
    private static final String K_WIN = "win";
    private static final String K_LOSE = "lose";
    private static final String K_MAX_STREAK = "max_streak";
    private static final String K_CUR_STREAK = "cur_streak";
    private static final String K_GAMES = "games";
    private static final String K_BEST_MULT = "best_mult";
    private static final String K_LANDLORD_WINS = "lord_wins";
    private static final String K_FARMER_WINS = "farmer_wins";
    private static final String K_BOMBS = "bombs";
    private static final String K_SPRINGS = "springs";
    private static final String K_TUTORIAL = "tutorial_shown";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- 设置 ----
    public int skin() { return sp.getInt(K_SKIN, 0); }
    public void setSkin(int v) { sp.edit().putInt(K_SKIN, v).apply(); }

    public int difficulty() { return sp.getInt(K_DIFF, 1); }
    public void setDifficulty(int v) { sp.edit().putInt(K_DIFF, v).apply(); }

    public boolean tutorialShown() { return sp.getBoolean(K_TUTORIAL, false); }
    public void setTutorialShown() { sp.edit().putBoolean(K_TUTORIAL, true).apply(); }

    // ---- 统计 ----
    public int score() { return sp.getInt(K_SCORE, 0); }
    public int wins() { return sp.getInt(K_WIN, 0); }
    public int loses() { return sp.getInt(K_LOSE, 0); }
    public int games() { return sp.getInt(K_GAMES, 0); }
    public int maxStreak() { return sp.getInt(K_MAX_STREAK, 0); }
    public int curStreak() { return sp.getInt(K_CUR_STREAK, 0); }
    public int bestMultiplier() { return sp.getInt(K_BEST_MULT, 1); }
    public int landlordWins() { return sp.getInt(K_LANDLORD_WINS, 0); }
    public int farmerWins() { return sp.getInt(K_FARMER_WINS, 0); }
    public int totalBombs() { return sp.getInt(K_BOMBS, 0); }
    public int totalSprings() { return sp.getInt(K_SPRINGS, 0); }

    public int winRate() {
        int g = games();
        return g == 0 ? 0 : (int) (wins() * 100f / g);
    }

    /**
     * 记录一局结果。
     */
    public void recordGame(boolean win, int delta, int multiplier,
                           boolean wasLandlord, int bombs, boolean spring) {
        SharedPreferences.Editor e = sp.edit();

        e.putInt(K_SCORE, score() + delta);
        e.putInt(K_GAMES, games() + 1);

        if (win) {
            e.putInt(K_WIN, wins() + 1);
            int cur = curStreak() + 1;
            e.putInt(K_CUR_STREAK, cur);
            if (cur > maxStreak()) e.putInt(K_MAX_STREAK, cur);
            if (wasLandlord) e.putInt(K_LANDLORD_WINS, landlordWins() + 1);
            else e.putInt(K_FARMER_WINS, farmerWins() + 1);
        } else {
            e.putInt(K_LOSE, loses() + 1);
            e.putInt(K_CUR_STREAK, 0);
        }

        if (multiplier > bestMultiplier()) e.putInt(K_BEST_MULT, multiplier);
        if (bombs > 0) e.putInt(K_BOMBS, totalBombs() + bombs);
        if (spring) e.putInt(K_SPRINGS, totalSprings() + 1);

        e.apply();
    }

    public void resetStats() {
        sp.edit()
                .putInt(K_SCORE, 0)
                .putInt(K_WIN, 0)
                .putInt(K_LOSE, 0)
                .putInt(K_GAMES, 0)
                .putInt(K_MAX_STREAK, 0)
                .putInt(K_CUR_STREAK, 0)
                .putInt(K_BEST_MULT, 1)
                .putInt(K_LANDLORD_WINS, 0)
                .putInt(K_FARMER_WINS, 0)
                .putInt(K_BOMBS, 0)
                .putInt(K_SPRINGS, 0)
                .apply();
    }
}