package cn.dancingsnow.neoecoae.gui.ldlib1;

/**
 * Fixed-coordinate layout specifications extracted from the LDLib2 (Taffy/StylesheetManager)
 * original UI code. These are NOT an auto-layout system — they are design-reference
 * coordinate tables used by LDLib1MachineUIs to replicate LDLib2 visual placement.
 */
public final class NELDLib1UiSpecs {

    private NELDLib1UiSpecs() {
    }

    // =========================================================================
    // Integrated Working Station
    // =========================================================================
    public static final class IntegratedWorkingStationSpec {
        public static final int WIDTH = 198;
        public static final int HEIGHT = 222;

        public static final int TITLE_X = 8;
        public static final int TITLE_Y = 6;

        // Fluid tanks: input left, output right, vertical
        public static final int INPUT_TANK_X = 8;
        public static final int INPUT_TANK_Y = 26;
        public static final int TANK_W = 18;
        public static final int TANK_H = 56;

        // 3×3 input item grid
        public static final int INPUT_GRID_X = 32;
        public static final int INPUT_GRID_Y = 26;
        public static final int INPUT_GRID_COLS = 3;
        public static final int INPUT_GRID_ROWS = 3;

        // Single output slot
        public static final int OUTPUT_SLOT_X = 94;
        public static final int OUTPUT_SLOT_Y = 46;

        // Vertical progress bar
        public static final int PROGRESS_X = 120;
        public static final int PROGRESS_Y = 26;
        public static final int PROGRESS_W = 6;
        public static final int PROGRESS_H = 56;

        // Output fluid tank
        public static final int OUTPUT_TANK_X = 134;
        public static final int OUTPUT_TANK_Y = 26;

        // 4 upgrade slots vertically
        public static final int UPGRADE_SLOTS_X = 160;
        public static final int UPGRADE_SLOTS_Y = 26;

        // Player inventory
        public static final int PLAYER_INV_X = 8;
        public static final int PLAYER_INV_Y = 128;
    }

    // =========================================================================
    // Storage Controller — compact LDLib2-style layout
    // =========================================================================
    public static final class StorageControllerSpec {
        public static final int WIDTH = 260;
        public static final int HEIGHT = 178;

        // ── Left hammer button ──
        public static final int HAMMER_BTN_X = 4;
        public static final int HAMMER_BTN_Y = 30;
        public static final int HAMMER_BTN_SIZE = 20;

        // ── Main text panel (scroller-like, 220×160) ──
        public static final int TEXT_PANEL_X = 28;
        public static final int TEXT_PANEL_Y = 6;
        public static final int TEXT_PANEL_W = 220;
        public static final int TEXT_PANEL_H = 160;

        // ── Builder floating window (offset 6,6 from text panel top-left) ──
        public static final int BUILDER_FLOAT_X = 34;
        public static final int BUILDER_FLOAT_Y = 12;
        public static final int BUILDER_FLOAT_W = 158;
        public static final int BUILDER_FLOAT_H = 118;
    }

    // =========================================================================
    // Computation Controller — compact LDLib2-style layout
    // =========================================================================
    public static final class ComputationControllerSpec {
        public static final int WIDTH = 260;
        public static final int HEIGHT = 178;

        public static final int HAMMER_BTN_X = 4;
        public static final int HAMMER_BTN_Y = 30;
        public static final int HAMMER_BTN_SIZE = 20;

        public static final int TEXT_PANEL_X = 28;
        public static final int TEXT_PANEL_Y = 6;
        public static final int TEXT_PANEL_W = 220;
        public static final int TEXT_PANEL_H = 160;

        public static final int BUILDER_FLOAT_X = 34;
        public static final int BUILDER_FLOAT_Y = 12;
        public static final int BUILDER_FLOAT_W = 158;
        public static final int BUILDER_FLOAT_H = 118;
    }

    // =========================================================================
    // Crafting Controller
    // =========================================================================
    public static final class CraftingControllerSpec {
        public static final int WIDTH = 230;
        public static final int HEIGHT = 336;

        public static final int TITLE_X = 8;
        public static final int TITLE_Y = 6;

        // Status panel
        public static final int STATUS_PANEL_X = 4;
        public static final int STATUS_PANEL_Y = 22;
        public static final int STATUS_PANEL_W = 222;
        public static final int STATUS_PANEL_H = 86;

        // Toggle icons
        public static final int OVERCLOCK_ICON_X = 152;
        public static final int OVERCLOCK_ICON_Y = 26;
        public static final int COOLING_ICON_X = 174;
        public static final int COOLING_ICON_Y = 26;
        public static final int TOGGLE_ICON_SIZE = 18;

        // Status rows
        public static final int STATUS_ROW_START_X = 10;
        public static final int STATUS_ROW_START_Y = 26;
        public static final int STATUS_ROW_SPACING = 12;

        // Progress bars
        public static final int COOLANT_PROGRESS_X = 10;
        public static final int COOLANT_PROGRESS_Y = 74;
        public static final int COOLANT_PROGRESS_W = 86;
        public static final int COOLANT_PROGRESS_H = 6;

        public static final int LIMIT_PROGRESS_X = 104;
        public static final int LIMIT_PROGRESS_Y = 74;
        public static final int LIMIT_PROGRESS_W = 86;
        public static final int LIMIT_PROGRESS_H = 6;

        // Action buttons
        public static final int PREVIEW_BUTTON_X = 10;
        public static final int PREVIEW_BUTTON_Y = 86;
        public static final int BUILD_BUTTON_X = 66;
        public static final int BUILD_BUTTON_Y = 86;
        public static final int CLEAR_BUTTON_X = 122;
        public static final int CLEAR_BUTTON_Y = 86;
        public static final int ACTION_BUTTON_W = 52;
        public static final int ACTION_BUTTON_H = 16;

        // Builder panel
        public static final int BUILDER_PANEL_X = 4;
        public static final int BUILDER_PANEL_Y = 110;
        public static final int BUILDER_PANEL_W = 222;
        public static final int BUILDER_PANEL_H = 116;

        // Player inventory
        public static final int PLAYER_INV_X = 8;
        public static final int PLAYER_INV_Y = 232;
    }

    // =========================================================================
    // Smart Pattern Bus
    // =========================================================================
    public static final class PatternBusSpec {
        public static final int WIDTH = 198;
        // Taller window: 7 rows × 18px = 126 + title(18) + player inv(86) + padding
        public static final int HEIGHT = 250;

        public static final int TITLE_X = 8;
        public static final int TITLE_Y = 6;

        // Pattern slot grid: 9 cols × 7 rows = 63 total slots
        // LDLib2 uses ScrollerView; LDLib1 shows all slots without pagination.
        public static final int PATTERN_GRID_X = 8;
        public static final int PATTERN_GRID_Y = 24;
        public static final int PATTERN_GRID_COLS = 9;
        public static final int PATTERN_GRID_ROWS = 7;

        // Player inventory
        public static final int PLAYER_INV_X = 8;
        public static final int PLAYER_INV_Y = 156;
    }

    // =========================================================================
    // Fluid Hatch (Input / Output)
    // =========================================================================
    public static final class FluidHatchSpec {
        public static final int WIDTH = 198;
        public static final int HEIGHT = 222;

        public static final int TITLE_X = 8;
        public static final int TITLE_Y = 6;

        public static final int TANK_X = 48;
        public static final int TANK_Y = 28;
        public static final int TANK_W = 18;
        public static final int TANK_H = 56;

        public static final int FLUID_NAME_X = 74;
        public static final int FLUID_NAME_Y = 30;
        public static final int AMOUNT_LABEL_X = 74;
        public static final int AMOUNT_LABEL_Y = 44;
        public static final int CAPACITY_LABEL_X = 74;
        public static final int CAPACITY_LABEL_Y = 56;

        // Player inventory
        public static final int PLAYER_INV_X = 8;
        public static final int PLAYER_INV_Y = 128;
    }

    // =========================================================================
    // Builder Panel inner layout (floating window style)
    // =========================================================================
    public static final class BuilderPanelSpec {
        // ── Legacy constants (used by deprecated add() for Crafting Controller) ──
        // Title
        public static final int TITLE_X = 6;
        public static final int TITLE_Y = 4;
        // Length controls: [-] Label [+]
        public static final int LENGTH_DEC_X = 6;
        public static final int LENGTH_DEC_Y = 20;
        public static final int LENGTH_LABEL_X = 28;
        public static final int LENGTH_LABEL_Y = 24;
        public static final int LENGTH_INC_X = 88;
        public static final int LENGTH_INC_Y = 20;
        public static final int LENGTH_BUTTON_W = 18;
        public static final int LENGTH_BUTTON_H = 16;
        // Preview / Build buttons
        public static final int BTN_ROW_Y = 40;
        public static final int PREVIEW_BTN_X = 6;
        public static final int BUILD_BTN_X = 58;
        public static final int BTN_W = 48;
        public static final int BTN_H = 16;
        // Stats: two-column layout
        public static final int STATS_ROW_Y = 62;
        public static final int STATS_LEFT_X = 6;
        public static final int STATS_RIGHT_X = 96;
        public static final int STATS_ROW_SPACING = 12;
        // Status text at bottom
        public static final int STATUS_TEXT_X = 6;
        public static final int STATUS_TEXT_Y = 92;

        // ── New floating layout constants (used by addFloat()) ──
        // Title (top-left of float window)
        public static final int FLOAT_TITLE_X = 6;
        public static final int FLOAT_TITLE_Y = 4;
        // Close / hammer button (top-right of float window)
        public static final int CLOSE_BTN_X_OFFSET = -14;
        public static final int CLOSE_BTN_Y = 3;
        public static final int CLOSE_BTN_SIZE = 10;
        // Length controls
        public static final int FLOAT_LENGTH_ROW_Y = 18;
        public static final int FLOAT_LENGTH_DEC_X = 6;
        public static final int FLOAT_LENGTH_LABEL_X = 26;
        public static final int FLOAT_LENGTH_LABEL_Y_OFF = 2;
        public static final int FLOAT_LENGTH_INC_X = 86;
        public static final int FLOAT_LENGTH_BUTTON_W = 16;
        public static final int FLOAT_LENGTH_BUTTON_H = 14;
        // Preview / Build buttons
        public static final int FLOAT_BTN_ROW_Y = 36;
        public static final int FLOAT_PREVIEW_BTN_X = 6;
        public static final int FLOAT_BUILD_BTN_X = 54;
        public static final int FLOAT_BTN_W = 44;
        public static final int FLOAT_BTN_H = 14;
        // Stats: compact rows
        public static final int FLOAT_STATS_START_Y = 54;
        public static final int FLOAT_STATS_ROW_SPACING = 10;
        public static final int FLOAT_STATS_X = 6;
        // Status text at bottom
        public static final int FLOAT_STATUS_TEXT_X = 6;
        public static final int FLOAT_STATUS_TEXT_Y_OFFSET = -12;
    }

    // =========================================================================
    // Shared constants (also used by NELDLib1Layout)
    // =========================================================================
    public static final int SLOT = 18;
    public static final int STATUS_LINE_HEIGHT = 12;
}
