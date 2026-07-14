package cn.dancingsnow.neoecoae.gui.ldlib.storage;

/** Pixel geometry shared by the LDLib1 storage host screen and its focused render helpers. */
public final class NEStorageLayout {
    public static final int UI_WIDTH = 344;
    public static final int UI_HEIGHT = 232;

    public static final int LEFT_PANEL_X = 6;
    public static final int LEFT_PANEL_Y = 24;
    public static final int LEFT_PANEL_W = 162;
    public static final int LEFT_PANEL_H = 200;
    public static final int LEFT_PANEL_H_INFINITE = 108;
    public static final int LEFT_CONTENT_W = LEFT_PANEL_W;
    public static final int TEXT_START_X = LEFT_PANEL_X + 8;
    public static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    public static final int TEXT_LINE_STEP = 13;
    public static final int TEXT_MAX_W = LEFT_CONTENT_W - 16;

    public static final int USAGE_PANEL_X = 180;
    public static final int USAGE_PANEL_Y = 24;
    public static final int USAGE_PANEL_W = 156;
    public static final int USAGE_PANEL_H = 200;
    public static final int USAGE_CONTENT_X = 186;
    public static final int USAGE_CONTENT_W = 144;
    public static final int USAGE_CONTENT_SHIFT_Y = 6;
    public static final int USAGE_DARK_X = USAGE_CONTENT_X + 2;
    public static final int USAGE_DARK_Y = USAGE_PANEL_Y + 14 + USAGE_CONTENT_SHIFT_Y;
    public static final int USAGE_DARK_W = 141;
    public static final int USAGE_DARK_H = 169;
    public static final int STORAGE_GAUGE_X = USAGE_CONTENT_X + 10;
    public static final int STORAGE_GAUGE_Y = USAGE_PANEL_Y + 26 + USAGE_CONTENT_SHIFT_Y;
    public static final int STORAGE_GAUGE_W = 32;
    public static final int STORAGE_GAUGE_H = 143;
    public static final int USAGE_PERCENT_Y = USAGE_PANEL_Y + 171 + USAGE_CONTENT_SHIFT_Y;
    public static final int USAGE_DETAIL_X = STORAGE_GAUGE_X + STORAGE_GAUGE_W + 8;
    public static final int USAGE_DETAIL_Y = STORAGE_GAUGE_Y + 5;
    public static final int USAGE_DETAIL_W = 88;
    public static final int USAGE_DETAIL_LINE_H = 15;

    public static final int PRIORITY_BUTTON_X = UI_WIDTH - 22;
    public static final int PRIORITY_BUTTON_Y = 0;
    public static final int PRIORITY_BUTTON_W = 22;
    public static final int PRIORITY_BUTTON_H = 22;
    public static final int SLOT_SIZE = 18;
    public static final int PLAYER_INV_X = LEFT_PANEL_X;
    public static final int PLAYER_INV_LABEL_Y = 136;
    public static final int PLAYER_INV_Y = 147;
    public static final int PLAYER_HOTBAR_Y = 205;
    public static final int INFINITE_SLOT_X = USAGE_CONTENT_X + 120;
    public static final int INFINITE_SLOT_Y = USAGE_PANEL_Y + 160 + USAGE_CONTENT_SHIFT_Y;
    public static final int HUGE_STACK_PANEL_X = USAGE_DETAIL_X;
    public static final int HUGE_STACK_PANEL_Y = USAGE_DETAIL_Y + USAGE_DETAIL_LINE_H * 4 + 1;
    public static final int HUGE_STACK_PANEL_W = USAGE_DETAIL_W;
    public static final int HUGE_STACK_PANEL_H = INFINITE_SLOT_Y - HUGE_STACK_PANEL_Y - 3;
    public static final int HUGE_STACK_ROW_H = 18;
    public static final int HUGE_STACK_PAGE_FOOTER_H = 11;

    public static final int STORAGE_SCROLLBAR_W = 12;
    public static final int STORAGE_SCROLLBAR_TRACK_W = 6;
    public static final int STORAGE_SCROLLBAR_THUMB_H = 15;

    private NEStorageLayout() {}
}
