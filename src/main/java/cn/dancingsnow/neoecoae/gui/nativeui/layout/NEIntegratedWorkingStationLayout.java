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
    public static final int INPUT_COLS = 3;
    public static final int INPUT_ROWS = 3;
    public static final int INPUT_BG_X = 39;
    public static final int INPUT_BG_Y = 14;

    // ── Output slot ──
    public static final int OUTPUT_BG_X = 108;
    public static final int OUTPUT_BG_Y = 32;

    // ── Upgrade panel (extra_panels.png) ──
    public static final int UPGRADE_COUNT = 4;
    /** Left edge of the upgrade panel area (drawAeUpgradePanel x), relative to guiLeft. */
    public static final int UPGRADE_PANEL_X = 171;
    /** Top edge of the upgrade panel area, relative to guiTop. */
    public static final int UPGRADE_PANEL_Y = 0;
    /** First upgrade slot background X (= UPGRADE_PANEL_X, the visual slot starts at panel edge). */
    public static final int UPGRADE_FIRST_BG_X = UPGRADE_PANEL_X;
    /** First upgrade slot background Y (inside panel). */
    public static final int UPGRADE_FIRST_BG_Y = 2;

    // ── Player inventory area ──
    public static final int PLAYER_INV_BG_X = 3;
    public static final int PLAYER_INV_BG_Y = 88;

    // ── Hotbar area ──
    public static final int HOTBAR_BG_X = 3;
    public static final int HOTBAR_BG_Y = 148;

    // ── 16×16 item/click areas (Menu Slot origins = BG + ITEM_OFFSET) ──
    public static final int INPUT_SLOT_X = INPUT_BG_X + ITEM_OFFSET;
    public static final int INPUT_SLOT_Y = INPUT_BG_Y + ITEM_OFFSET;
    public static final int OUTPUT_SLOT_X = OUTPUT_BG_X + ITEM_OFFSET;
    public static final int OUTPUT_SLOT_Y = OUTPUT_BG_Y + ITEM_OFFSET;
    public static final int UPGRADE_SLOT_X = UPGRADE_FIRST_BG_X + ITEM_OFFSET;
    public static final int UPGRADE_FIRST_SLOT_Y = UPGRADE_FIRST_BG_Y + ITEM_OFFSET;
    public static final int PLAYER_INV_SLOT_X = PLAYER_INV_BG_X + ITEM_OFFSET;
    public static final int PLAYER_INV_SLOT_Y = PLAYER_INV_BG_Y + ITEM_OFFSET;
    public static final int HOTBAR_SLOT_X = HOTBAR_BG_X + ITEM_OFFSET;
    public static final int HOTBAR_SLOT_Y = HOTBAR_BG_Y + ITEM_OFFSET;

    // ── Progress bar ──
    public static final int PROGRESS_X = 128;
    public static final int PROGRESS_Y = 32;
    public static final int PROGRESS_W = 6;
    public static final int PROGRESS_H = 18;

    // ── Fluid tanks ──
    public static final int FLUID_IN_X = 6;
    public static final int FLUID_IN_Y = 14;
    public static final int FLUID_IN_W = 18;
    public static final int FLUID_IN_H = 54;

    public static final int FLUID_OUT_X = 142;
    public static final int FLUID_OUT_Y = 14;
    public static final int FLUID_OUT_W = 18;
    public static final int FLUID_OUT_H = 54;

    // ── Settings / toolbar panel ──
    public static final int SETTINGS_PANEL_X = -24;
    public static final int SETTINGS_PANEL_Y = 1;
    public static final int SETTINGS_PANEL_W = 20;
    public static final int SETTINGS_PANEL_H = 24;

    // ── Toggle auto-export button ──
    public static final int TOGGLE_BTN_X = SETTINGS_PANEL_X + 1;
    public static final int TOGGLE_BTN_Y = SETTINGS_PANEL_Y + 1;
    public static final int TOGGLE_BTN_W = 18;
    public static final int TOGGLE_BTN_H = 20;

    // ── Clear fluid buttons ──
    public static final int CLEAR_BTN_W = 8;
    public static final int CLEAR_BTN_H = 8;
    public static final int CLEAR_BTN_IN_X = 26;
    public static final int CLEAR_BTN_IN_Y = 60;
    public static final int CLEAR_BTN_OUT_X = 133;
    public static final int CLEAR_BTN_OUT_Y = 60;

    private NEIntegratedWorkingStationLayout() {
    }
}
