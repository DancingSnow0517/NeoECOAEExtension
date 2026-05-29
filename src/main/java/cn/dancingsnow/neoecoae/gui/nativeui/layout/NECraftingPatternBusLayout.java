package cn.dancingsnow.neoecoae.gui.nativeui.layout;

/**
 * Shared layout constants for the ECO Crafting Pattern Bus.
 * <p>
 * Both {@code NECraftingPatternBusMenu} (slot click areas) and
 * {@code NECraftingPatternBusScreen} (visual backgrounds) import
 * these constants so that coordinates stay in sync.
 * </p>
 */
public final class NECraftingPatternBusLayout {

    // ── Core sizes ──
    public static final int SLOT_SIZE = 18;

    // ── Panel dimensions ──
    public static final int GUI_W = 172;
    public static final int GUI_H = 246;

    // ── Pattern area ──
    public static final int PATTERN_COLS = 9;
    public static final int PATTERN_ROWS = 7;

    // 18×18 slot backgrounds
    public static final int PATTERN_BG_X = 5;
    public static final int PATTERN_BG_Y = 28;

    // 16×16 item/click areas (Menu Slot origins)
    public static final int PATTERN_SLOT_X = 6;
    public static final int PATTERN_SLOT_Y = 29;

    // ── Player inventory area ──
    public static final int INV_BG_X = 5;
    public static final int INV_BG_Y = 161;
    public static final int INV_SLOT_X = 6;
    public static final int INV_SLOT_Y = 162;

    // ── Hotbar area ──
    public static final int HOTBAR_BG_X = 5;
    public static final int HOTBAR_BG_Y = 220;
    public static final int HOTBAR_SLOT_X = 6;
    public static final int HOTBAR_SLOT_Y = 221;

    // ── Slot group panels (inset backgrounds for grouped slots) ──
    public static final int PATTERN_PANEL_X = PATTERN_BG_X - 4;
    public static final int PATTERN_PANEL_Y = PATTERN_BG_Y - 4;
    public static final int PATTERN_PANEL_W = PATTERN_COLS * SLOT_SIZE + 8;
    public static final int PATTERN_PANEL_H = PATTERN_ROWS * SLOT_SIZE + 8;

    public static final int PLAYER_INV_PANEL_X = INV_BG_X - 4;
    public static final int PLAYER_INV_PANEL_Y = INV_BG_Y - 4;
    public static final int PLAYER_INV_PANEL_W = 9 * SLOT_SIZE + 8;
    public static final int PLAYER_INV_PANEL_H = 3 * SLOT_SIZE + 8;

    public static final int HOTBAR_PANEL_X = HOTBAR_BG_X - 4;
    public static final int HOTBAR_PANEL_Y = HOTBAR_BG_Y - 4;
    public static final int HOTBAR_PANEL_W = 9 * SLOT_SIZE + 8;
    public static final int HOTBAR_PANEL_H = 1 * SLOT_SIZE + 8;

    // ── Title ──
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 5;

    private NECraftingPatternBusLayout() {
    }
}
