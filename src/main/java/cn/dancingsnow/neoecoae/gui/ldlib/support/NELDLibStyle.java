package cn.dancingsnow.neoecoae.gui.ldlib.support;

import net.minecraft.util.Mth;

public final class NELDLibStyle {
    public static final int DARK_PANEL_OUTER = 0xFF17141E;
    public static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    public static final int DARK_PANEL_INNER = 0xFF665F6D;
    public static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    public static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    public static final int DARK_TEXT_VALUE = 0xFF8377FF;
    public static final int DARK_TEXT_USED = 0xFF00FC00;
    public static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    public static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    public static final int DARK_TEXT_WARNING = 0xFFFFD65A;
    public static final int DARK_TEXT_BLUE = 0xFF3FD6FF;
    public static final int DARK_TEXT_ORANGE = 0xFFFF9A3D;
    public static final int DARK_TEXT_ERROR = 0xFFFF6A75;

    public static final int HOVER_OVERLAY = 0x40FFFFFF;

    private NELDLibStyle() {}

    public static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return DARK_TEXT_USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return DARK_TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return DARK_TEXT_ORANGE;
        }
        if (pct >= 0.75D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_USED;
    }

    public static int metricColor(int accentColor, long max, double pct) {
        if (max <= 0) {
            return DARK_TEXT_MUTED;
        }
        return lerpColor(darken(accentColor, 0.72D), accentColor, Mth.clamp(pct + 0.2D, 0.0D, 1.0D));
    }

    public static int darken(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lerpColor(int start, int end, double t) {
        double safeT = Mth.clamp(t, 0.0D, 1.0D);
        int a = (int) Mth.lerp(safeT, (start >>> 24) & 0xFF, (end >>> 24) & 0xFF);
        int r = (int) Mth.lerp(safeT, (start >>> 16) & 0xFF, (end >>> 16) & 0xFF);
        int g = (int) Mth.lerp(safeT, (start >>> 8) & 0xFF, (end >>> 8) & 0xFF);
        int b = (int) Mth.lerp(safeT, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
