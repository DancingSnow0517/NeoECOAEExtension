package cn.dancingsnow.neoecoae.gui.ldlib.widget;

final class NEStructureTerminalLayout {
    static final int WIDTH = 390;
    static final int HEIGHT = 252;

    static final int TAB_Y = 5;
    static final int TAB_H = 16;
    static final int PATTERN_TAB_X = 307;
    static final int PATTERN_TAB_W = 76;

    static final int CONTROL_Y = 25;
    static final int CONTROL_H = 18;
    static final int HOST_X = 7;
    static final int HOST_W = 42;
    static final int HOST_GAP = 3;
    static final int TIER_X = 148;
    static final int TIER_W = 25;
    static final int TIER_GAP = 3;
    static final int LENGTH_X = 231;
    static final int LENGTH_BUTTON_W = 18;
    static final int LENGTH_VALUE_W = 43;
    static final int MIRROR_X = 318;
    static final int MIRROR_W = 65;

    static final int PATTERN_PANEL_X = 7;
    static final int PATTERN_PANEL_Y = 50;
    static final int PATTERN_PANEL_W = 246;
    static final int PATTERN_PANEL_H = 166;
    static final int SCENE_X = PATTERN_PANEL_X + 5;
    static final int SCENE_Y = PATTERN_PANEL_Y + 23;
    static final int SCENE_W = PATTERN_PANEL_W - 10;
    static final int SCENE_H = PATTERN_PANEL_H - 28;
    static final int LAYER_PREV_X = PATTERN_PANEL_X + PATTERN_PANEL_W - 72;
    static final int LAYER_NEXT_X = PATTERN_PANEL_X + PATTERN_PANEL_W - 20;
    static final int LAYER_BUTTON_W = 18;
    static final int LAYER_LABEL_X = LAYER_PREV_X + LAYER_BUTTON_W;
    static final int LAYER_LABEL_W = LAYER_NEXT_X - LAYER_LABEL_X;

    static final int INFO_PANEL_X = 260;
    static final int INFO_PANEL_Y = PATTERN_PANEL_Y;
    static final int INFO_PANEL_W = 123;
    static final int INFO_PANEL_H = PATTERN_PANEL_H;
    static final int PATTERN_MATERIAL_Y = INFO_PANEL_Y + 61;
    static final int PATTERN_MATERIAL_COLS = 6;
    static final int PATTERN_MATERIAL_ROWS = 5;

    static final int SLOT_SIZE = 18;
    static final int FOOTER_Y = 224;
    static final int FOOTER_BUTTON_W = 42;
    static final int FOOTER_MIRROR_BUTTON_W = 58;
    static final int FOOTER_BUTTON_GAP = 4;
    static final int FOOTER_BUTTON_Y = FOOTER_Y - 1;
    static final int FOOTER_HINT_X = 7 + FOOTER_BUTTON_W * 2 + FOOTER_MIRROR_BUTTON_W + FOOTER_BUTTON_GAP * 2 + 7;
    static final int FORMED_PREVIEW_W = 65;
    static final int FORMED_PREVIEW_X = PATTERN_PANEL_X + 7;
    static final int FORMED_PREVIEW_Y = PATTERN_PANEL_Y + PATTERN_PANEL_H - CONTROL_H - 7;

    static final int ACTION_UPDATE_ID = 2;

    static int patternVisibleSlots() {
        return PATTERN_MATERIAL_COLS * PATTERN_MATERIAL_ROWS;
    }

    static int patternSlotX(int index) {
        return INFO_PANEL_X + 7 + index % PATTERN_MATERIAL_COLS * SLOT_SIZE;
    }

    static int patternSlotY(int index) {
        return PATTERN_MATERIAL_Y + index / PATTERN_MATERIAL_COLS * SLOT_SIZE;
    }

    static int footerButtonX(int index) {
        int x = 7;
        for (int i = 0; i < index; i++) {
            x += (i == 1 ? FOOTER_MIRROR_BUTTON_W : FOOTER_BUTTON_W) + FOOTER_BUTTON_GAP;
        }
        return x;
    }

    private NEStructureTerminalLayout() {}
}
