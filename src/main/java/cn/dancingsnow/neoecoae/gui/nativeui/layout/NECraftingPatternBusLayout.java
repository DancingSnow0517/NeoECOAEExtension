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

    // ── Title ──
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 5;

    private NECraftingPatternBusLayout() {
    }
}
