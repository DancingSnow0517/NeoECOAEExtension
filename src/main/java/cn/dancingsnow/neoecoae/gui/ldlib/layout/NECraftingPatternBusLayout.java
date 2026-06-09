package cn.dancingsnow.neoecoae.gui.ldlib.layout;

public final class NECraftingPatternBusLayout {
    public static final int SLOT_SIZE = 18;
    public static final int ITEM_OFFSET = 1;

    public static final int GUI_W = 176;
    public static final int GUI_H = 246;

    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 7;

    public static final int PATTERN_COLS = 9;
    public static final int PATTERN_ROWS = 7;

    public static final int PATTERN_BG_X = 7;
    public static final int PATTERN_BG_Y = 22;

    public static final int PATTERN_SLOT_X = PATTERN_BG_X + ITEM_OFFSET;
    public static final int PATTERN_SLOT_Y = PATTERN_BG_Y + ITEM_OFFSET;

    public static final int AE2_PLAYER_SLOT_LEFT = 8;
    public static final int AE2_PLAYER_INV_BOTTOM = 82;
    public static final int AE2_HOTBAR_BOTTOM = 24;
    public static final int AE2_INV_LABEL_BOTTOM = 93;

    public static final int INV_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int INV_SLOT_Y = GUI_H - AE2_PLAYER_INV_BOTTOM;

    public static final int HOTBAR_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int HOTBAR_SLOT_Y = GUI_H - AE2_HOTBAR_BOTTOM;

    public static final int INV_BG_X = INV_SLOT_X - ITEM_OFFSET;
    public static final int INV_BG_Y = INV_SLOT_Y - ITEM_OFFSET;

    public static final int HOTBAR_BG_X = HOTBAR_SLOT_X - ITEM_OFFSET;
    public static final int HOTBAR_BG_Y = HOTBAR_SLOT_Y - ITEM_OFFSET;

    public static final int INV_LABEL_X = AE2_PLAYER_SLOT_LEFT;
    public static final int INV_LABEL_Y = GUI_H - AE2_INV_LABEL_BOTTOM;

    private NECraftingPatternBusLayout() {}
}
