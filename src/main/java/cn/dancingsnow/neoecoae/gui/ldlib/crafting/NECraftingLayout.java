package cn.dancingsnow.neoecoae.gui.ldlib.crafting;

/** Canonical geometry for the crafting host UI, matching the 1.21.1 panel arrangement. */
public final class NECraftingLayout {
    public static final int UI_WIDTH = 304;
    public static final int UI_HEIGHT = 196;
    public static final int PANEL_MARGIN = 6;
    public static final int MAIN_PANEL_Y = 27;
    public static final int MAIN_PANEL_H = 70;

    public static final int TOOLBAR_BUTTON_SIZE = 16;
    public static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 4;
    public static final int TOOLBAR_X = UI_WIDTH - PANEL_MARGIN - TOOLBAR_BUTTON_SIZE * 3 - 8;
    public static final int TOOLBAR_Y = 4;

    public static final int STATUS_AREA_X = PANEL_MARGIN;
    public static final int STATUS_AREA_Y = MAIN_PANEL_Y;
    public static final int STATUS_AREA_W = 76;
    public static final int STATUS_AREA_H = MAIN_PANEL_H;
    public static final int STATUS_ROW_X = STATUS_AREA_X + 8;
    public static final int STATUS_TEXT_GAP = 16;
    public static final int STATUS_VALUE_RIGHT_PAD = 6;
    public static final int STATS_AREA_X = STATUS_AREA_X + STATUS_AREA_W + 6;
    public static final int STATS_AREA_Y = MAIN_PANEL_Y;
    public static final int STATS_AREA_W = 114;
    public static final int STATS_AREA_H = MAIN_PANEL_H;
    public static final int GAUGE_AREA_X = STATS_AREA_X + STATS_AREA_W + 6;
    public static final int GAUGE_AREA_Y = MAIN_PANEL_Y;
    public static final int GAUGE_AREA_W = UI_WIDTH - PANEL_MARGIN - GAUGE_AREA_X;
    public static final int GAUGE_AREA_H = MAIN_PANEL_H;
    public static final int GAUGE_BAR_Y = GAUGE_AREA_Y + 26;
    public static final int GAUGE_BAR_H = 32;
    public static final int ENERGY_GAUGE_W = 20;
    public static final int COOLANT_GAUGE_W = 23;
    public static final int GAUGE_GAP = 14;

    public static final int SLOT_SIZE = 18;
    public static final int PLAYER_INV_X = PANEL_MARGIN;
    public static final int PLAYER_INV_LABEL_Y = 102;
    public static final int PLAYER_INV_Y = PLAYER_INV_LABEL_Y + 11;
    public static final int PLAYER_HOTBAR_Y = PLAYER_INV_Y + SLOT_SIZE * 3 + 2;

    public static final int TASK_PANEL_X = PLAYER_INV_X + SLOT_SIZE * 9 + 8;
    public static final int TASK_PANEL_Y = PLAYER_INV_LABEL_Y;
    public static final int TASK_PANEL_W = 122;
    public static final int TASK_PANEL_H = 88;
    public static final int TASK_CARD_X = TASK_PANEL_X + 8;
    public static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    public static final int TASK_CARD_W = TASK_PANEL_W - 16;
    public static final int TASK_CARD_H = 16;
    public static final int TASK_CARD_STRIDE = 18;
    public static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 4;
    public static final int TASK_SCROLLBAR_W = 3;

    public static int energyGaugeX() {
        int groupWidth = ENERGY_GAUGE_W + GAUGE_GAP + COOLANT_GAUGE_W;
        return GAUGE_AREA_X + (GAUGE_AREA_W - groupWidth) / 2;
    }

    public static int coolantGaugeX() {
        return energyGaugeX() + ENERGY_GAUGE_W + GAUGE_GAP;
    }

    private NECraftingLayout() {}
}
