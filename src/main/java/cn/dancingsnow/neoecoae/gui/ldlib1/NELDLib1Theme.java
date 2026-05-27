package cn.dancingsnow.neoecoae.gui.ldlib1;

/**
 * Centralized color palette and style constants for LDLib1 controller UIs.
 * Keeps the visual language consistent across all window components without
 * scattered hardcoded color values.
 */
public final class NELDLib1Theme {

    private NELDLib1Theme() {}

    // ── Text colors ──

    /** Title text on dark backgrounds (light gray-white). */
    public static final int TEXT_TITLE = 0xFFE8E8F0;
    /** Normal body text on dark backgrounds. */
    public static final int TEXT_NORMAL = 0xFFD8D8E0;
    /** Dim / secondary text. */
    public static final int TEXT_DIM = 0xFFB0B0B8;
    /** Dark text on light backgrounds. */
    public static final int TEXT_DARK = 0xFF303040;
    /** Highlight / accent color (soft purple). */
    public static final int TEXT_HIGHLIGHT = 0xFF8A6CFF;

    /** Button text — white with shadow for readability. */
    public static final int BUTTON_TEXT_LIGHT = 0xFFFFFFFF;
    /** Button text — dark variant for light button backgrounds. */
    public static final int BUTTON_TEXT_DARK = 0xFF2A2A38;
    /** Disabled button text. */
    public static final int BUTTON_TEXT_DISABLED = 0xFFB0B0B8;

    // ── Panel backgrounds ──

    /** Dark panel background color (for reference / fallback). */
    public static final int BG_DARK = 0xFF1A1A28;
    /** Light panel background color. */
    public static final int BG_LIGHT = 0xFFC8C8D0;

    // ── Style flags ──

    public static final boolean USE_TEXT_SHADOW_ON_DARK = true;
    public static final boolean USE_TEXT_SHADOW_ON_BUTTON = true;

    // ── Legacy aliases (keep existing code working) ──

    /** @deprecated Use {@link #TEXT_DARK} */
    @Deprecated
    public static final int TITLE_COLOR = TEXT_DARK;
    /** @deprecated Use {@link #TEXT_DARK} */
    @Deprecated
    public static final int LABEL_COLOR = 0xFF3F3D52;
    /** @deprecated Use {@link #TEXT_TITLE} */
    @Deprecated
    public static final int STATUS_COLOR_LIGHT_TEXT = TEXT_TITLE;
    /** @deprecated Use {@link #TEXT_DARK} */
    @Deprecated
    public static final int STATUS_COLOR_DARK_TEXT = TEXT_DARK;
    /** @deprecated Use {@link #BUTTON_TEXT_LIGHT} */
    @Deprecated
    public static final int BUTTON_TEXT_COLOR = BUTTON_TEXT_LIGHT;
}
