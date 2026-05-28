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

    // ── Text colors (panel palette for light background / Structure Terminal) ──
    public static final int PANEL_TEXT_PRIMARY   = 0xFF20232A;
    public static final int PANEL_TEXT_SECONDARY = 0xFF303846;
    public static final int PANEL_TEXT_MUTED     = 0xFF5A6070;
    public static final int PANEL_TEXT_HINT      = 0xFF1F4E79;
    public static final int PANEL_TEXT_SUCCESS   = 0xFF008A2E;
    public static final int PANEL_TEXT_WARNING   = 0xFF9A6500;
    public static final int PANEL_TEXT_ERROR     = 0xFFB00020;

    // ── Label colors (aliases for panel palette) ──
    public static final int TITLE_COLOR          = PANEL_TEXT_PRIMARY;
    public static final int REBUILDING_TEXT_COLOR = PANEL_TEXT_MUTED;
    public static final int ACTIVE_TEXT_COLOR    = PANEL_TEXT_SUCCESS;

    // ── Machine UI text colors (for light component-style background) ──
    public static final int MACHINE_TEXT_PRIMARY   = 0xFF20232A;
    public static final int MACHINE_TEXT_SECONDARY = 0xFF303846;
    public static final int MACHINE_TEXT_MUTED     = 0xFF5A6070;
    public static final int MACHINE_TEXT_HINT      = 0xFF1F4E79;
    public static final int MACHINE_TEXT_SUCCESS   = 0xFF008A2E;
    public static final int MACHINE_TEXT_WARNING   = 0xFF9A6500;
    public static final int MACHINE_TEXT_ERROR     = 0xFFB00020;
    public static final int MACHINE_TEXT_VALUE     = 0xFF5A49D6;

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

    private NENativeUiConstants() {
    }
}
