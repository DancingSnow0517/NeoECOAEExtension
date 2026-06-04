package cn.dancingsnow.neoecoae.gui.nativeui;

/**
 * Shared constants for all ECO machine native UIs.
 * <p>
 * Centralizes window size, colors, text positions, button geometry,
 * and log identifiers so concrete Screen classes stay thin.
 * </p>
 */
public final class NENativeUiConstants {

    // ── Window ──
    public static final int UI_WIDTH = 220;
    public static final int UI_HEIGHT = 110;

    // ── Background ──
    public static final int BG_COLOR = 0xFF2A2A3A;

    // ── Text colors (AE2-compatible dark gray / blue-gray palette) ──
    public static final int PANEL_TEXT_PRIMARY = 0xFF2A2A3A;
    public static final int PANEL_TEXT_SECONDARY = 0xFF404A5A;
    public static final int PANEL_TEXT_MUTED = 0xFF6A6F7A;
    public static final int PANEL_TEXT_HINT = 0xFF3A608A;
    public static final int PANEL_TEXT_SUCCESS = 0xFF1A7A3A;
    public static final int PANEL_TEXT_WARNING = 0xFF8A5A1A;
    public static final int PANEL_TEXT_ERROR = 0xFF9A2A3A;

    // ── Label colors (aliases for panel palette) ──
    public static final int TITLE_COLOR = PANEL_TEXT_PRIMARY;
    public static final int REBUILDING_TEXT_COLOR = PANEL_TEXT_MUTED;
    public static final int ACTIVE_TEXT_COLOR = PANEL_TEXT_SUCCESS;

    // ── Machine UI text colors (for AE2-style light panel background) ──
    public static final int MACHINE_TEXT_PRIMARY = 0xFF404040;
    public static final int MACHINE_TEXT_SECONDARY = 0xFF606060;
    public static final int MACHINE_TEXT_MUTED = 0xFF707070;
    public static final int MACHINE_TEXT_HINT = 0xFF2A5080;
    public static final int MACHINE_TEXT_SUCCESS = 0xFF1A6A3A;
    public static final int MACHINE_TEXT_WARNING = 0xFF7A5010;
    public static final int MACHINE_TEXT_ERROR = 0xFF8A1A2A;
    public static final int MACHINE_TEXT_VALUE = 0xFF3A5A8A;

    // ── Label positions (relative to guiLeft / guiTop) ──
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 8;
    public static final int REBUILDING_Y = 24;
    public static final int ACTIVE_Y = 40;

    // ── Test button ──
    public static final int BUTTON_X_OFFSET = 82;
    public static final int BUTTON_Y_OFFSET = 70;
    public static final int BUTTON_WIDTH = 56;
    public static final int BUTTON_HEIGHT = 20;
    public static final String TEST_BUTTON_TEXT = "Test";
    public static final String ACTIVE_TEXT = "Native UI active";

    // ── Logging ──
    public static final String LOGGER_NAME = "NeoECOAE/NativeUI";

    private NENativeUiConstants() {}
}
