package cn.dancingsnow.neoecoae.gui.nativeui.layout;

/**
 * Shared layout constants for the ECO Integrated Working Station.
 * <p>
 * Both {@code NEIntegratedWorkingStationMenu} (slot click areas) and
 * {@code NEIntegratedWorkingStationScreen} (visual backgrounds) import
 * these constants so that coordinates stay in sync.
 * </p>
 * <p>
 * All values are relative to guiLeft/guiTop. The Screen draws 18×18
 * slot backgrounds and the Menu places 16×16 item/click areas at +1/+1.
 * </p>
 */
public final class NEIntegratedWorkingStationLayout {

    // ── Core sizes ──
    public static final int SLOT_SIZE = 18;
    public static final int ITEM_OFFSET = 1;

    // ── Panel dimensions ──
    public static final int PANEL_W = 168;
    public static final int PANEL_H = 171;

    // ── 3×3 input area (18×18 slot backgrounds) ──
    // Shifted down by 6px to avoid title overlap (title at y=5, need ≥4px gap)
    public static final int INPUT_COLS = 3;
    public static final int INPUT_ROWS = 3;
    public static final int INPUT_BG_X = 39;
    public static final int INPUT_BG_Y = 20;

    // ── Output slot ──
    public static final int OUTPUT_BG_X = 108;
    public static final int OUTPUT_BG_Y = 40;

    // ── Upgrade panel (extra_panels.png) ──
    public static final int UPGRADE_COUNT = 4;
    public static final int UPGRADE_PADDING = 7;
    public static final int UPGRADE_PANEL_X = PANEL_W + 4;
    public static final int UPGRADE_PANEL_Y = 1;
    /** Full upgrade panel hitbox width (slot + both paddings). */
    public static final int UPGRADE_PANEL_W = SLOT_SIZE + UPGRADE_PADDING * 2;
    /** Full upgrade panel hitbox height (all slots + both paddings). */
    public static final int UPGRADE_PANEL_H = UPGRADE_COUNT * SLOT_SIZE + UPGRADE_PADDING * 2;
    public static final int UPGRADE_BG_X = UPGRADE_PANEL_X + UPGRADE_PADDING;
    public static final int UPGRADE_FIRST_BG_Y = UPGRADE_PANEL_Y + UPGRADE_PADDING;

    // ── Player inventory area ──
    public static final int PLAYER_INV_BG_X = 3;
    public static final int PLAYER_INV_BG_Y = 86;

    // ── Hotbar area ──
    public static final int HOTBAR_BG_X = 3;
    public static final int HOTBAR_BG_Y = 146;

    // ── 16×16 item/click areas (Menu Slot origins = BG + ITEM_OFFSET) ──
    public static final int INPUT_SLOT_X = INPUT_BG_X + ITEM_OFFSET;
    public static final int INPUT_SLOT_Y = INPUT_BG_Y + ITEM_OFFSET;
    public static final int OUTPUT_SLOT_X = OUTPUT_BG_X + ITEM_OFFSET;
    public static final int OUTPUT_SLOT_Y = OUTPUT_BG_Y + ITEM_OFFSET;
    public static final int UPGRADE_SLOT_X = UPGRADE_BG_X + ITEM_OFFSET;
    public static final int UPGRADE_FIRST_SLOT_Y = UPGRADE_FIRST_BG_Y + ITEM_OFFSET;
    public static final int PLAYER_INV_SLOT_X = PLAYER_INV_BG_X + ITEM_OFFSET;
    public static final int PLAYER_INV_SLOT_Y = PLAYER_INV_BG_Y + ITEM_OFFSET;
    public static final int HOTBAR_SLOT_X = HOTBAR_BG_X + ITEM_OFFSET;
    public static final int HOTBAR_SLOT_Y = HOTBAR_BG_Y + ITEM_OFFSET;

    // ── Progress bar ──
    public static final int PROGRESS_X = 128;
    public static final int PROGRESS_Y = 40;
    public static final int PROGRESS_W = 6;
    public static final int PROGRESS_H = 18;

    // ── Fluid tanks ──
    public static final int FLUID_IN_X = 6;
    public static final int FLUID_IN_Y = 20;
    public static final int FLUID_IN_W = 18;
    public static final int FLUID_IN_H = 54;

    public static final int FLUID_OUT_X = 142;
    public static final int FLUID_OUT_Y = 20;
    public static final int FLUID_OUT_W = 18;
    public static final int FLUID_OUT_H = 54;

    // ── Auto-export toggle button (inside main panel, top-right) ──
    public static final int TOGGLE_BTN_X = 145;
    public static final int TOGGLE_BTN_Y = 4;
    public static final int TOGGLE_BTN_W = 18;
    public static final int TOGGLE_BTN_H = 18;

    // ── Clear fluid buttons (16×16 Icon.CLEAR) ──
    public static final int CLEAR_BTN_SIZE = 16;
    public static final int CLEAR_BTN_IN_X = 7;
    public static final int CLEAR_BTN_IN_Y = FLUID_IN_Y + FLUID_IN_H + 2;   // 76
    public static final int CLEAR_BTN_OUT_X = 143;
    public static final int CLEAR_BTN_OUT_Y = FLUID_OUT_Y + FLUID_OUT_H + 2; // 76

    // ── Inventory label position ──
    public static final int INV_LABEL_X = 3;
    public static final int INV_LABEL_Y = 75;

    private NEIntegratedWorkingStationLayout() {
    }
}
