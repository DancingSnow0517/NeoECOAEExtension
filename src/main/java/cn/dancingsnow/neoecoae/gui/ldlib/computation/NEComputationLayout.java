package cn.dancingsnow.neoecoae.gui.ldlib.computation;

import cn.dancingsnow.neoecoae.blocks.entity.computation.NEComputationUpgradeRules;

/** Pixel geometry matching the LDLib2 computation host while rendering through LDLib1. */
public final class NEComputationLayout {
    public static final int BASE_UI_WIDTH = 344;
    public static final int UI_HEIGHT = 232;

    public static final int PARALLEL_PANEL_W = 108;
    public static final int PARALLEL_PANEL_GAP = 4;
    public static final int MAIN_X =
            NEComputationUpgradeRules.isGregTechAvailable() ? PARALLEL_PANEL_W + PARALLEL_PANEL_GAP : 0;
    public static final int UI_WIDTH = BASE_UI_WIDTH + MAIN_X;

    public static final int PARALLEL_PANEL_X = 0;
    public static final int PARALLEL_PANEL_Y = 0;
    public static final int PARALLEL_PANEL_H = 48;
    public static final int PARALLEL_TITLE_X = PARALLEL_PANEL_X + 9;
    public static final int PARALLEL_TITLE_Y = PARALLEL_PANEL_Y + 4;
    /** Matches GTCEu's ConfiguratorPanel tab, which moves to the right edge when expanded. */
    public static final int PARALLEL_TOGGLE_X = PARALLEL_PANEL_X + PARALLEL_PANEL_W - 20;

    public static final int PARALLEL_TOGGLE_Y = PARALLEL_PANEL_Y + 4;
    public static final int PARALLEL_TOGGLE_W = 16;
    public static final int PARALLEL_TOGGLE_H = 16;
    public static final int PARALLEL_TAB_SIZE = 24;
    public static final int PARALLEL_BORDER = 4;
    /** Fallback step used only when the runtime GTCEu number widget is unavailable. */
    public static final int PARALLEL_STEP = 64;

    public static final int PARALLEL_FIELD_X = PARALLEL_PANEL_X + 4;
    public static final int PARALLEL_FIELD_Y = PARALLEL_PANEL_Y + 24;
    public static final int PARALLEL_FIELD_W = 100;
    public static final int PARALLEL_FIELD_H = 20;

    public static final int CAPACITY_PANEL_X = 6;
    public static final int CAPACITY_PANEL_Y = 24;
    public static final int CAPACITY_PANEL_W = 162;
    public static final int CAPACITY_PANEL_H = 108;
    public static final int CAPACITY_CONTENT_X = CAPACITY_PANEL_X + 6;
    public static final int CAPACITY_CONTENT_W = CAPACITY_PANEL_W - 12;
    public static final int CAPACITY_TITLE_Y = CAPACITY_PANEL_Y + 6;
    public static final int STORAGE_LABEL_Y = CAPACITY_PANEL_Y + 18;
    public static final int STORAGE_DETAIL_Y = CAPACITY_PANEL_Y + 28;
    public static final int THREAD_LABEL_Y = CAPACITY_PANEL_Y + 38;
    public static final int THREAD_DETAIL_Y = CAPACITY_PANEL_Y + 48;
    public static final int ACCELERATOR_Y = CAPACITY_PANEL_Y + 61;
    public static final int FREE_STORAGE_Y = CAPACITY_PANEL_Y + 73;
    public static final int PROGRESS_W = 70;
    public static final int PROGRESS_H = 4;
    public static final int PROGRESS_VALUE_X = CAPACITY_CONTENT_X + PROGRESS_W + 4;

    public static final int PLAYER_INV_X = CAPACITY_PANEL_X;
    public static final int PLAYER_INV_LABEL_Y = 136;
    public static final int PLAYER_INV_Y = 147;
    public static final int PLAYER_HOTBAR_Y = 205;

    public static final int TASK_PANEL_X = 180;
    public static final int TASK_PANEL_Y = 24;
    public static final int TASK_PANEL_W = 156;
    public static final int TASK_PANEL_H = 200;
    public static final int TASK_CARD_X = TASK_PANEL_X + 12;
    public static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    public static final int TASK_CARD_W = 132;
    public static final int TASK_CARD_H = 28;
    public static final int TASK_CARD_STRIDE = 30;
    public static final int TASK_LIST_BOTTOM_Y = TASK_PANEL_Y + TASK_PANEL_H - 3;
    public static final int TASK_SCROLLBAR_X = TASK_PANEL_X + TASK_PANEL_W - 5;
    public static final int TASK_SCROLLBAR_W = 3;

    public static final int CPU_BUTTON_W = 18;
    public static final int CPU_BUTTON_H = 18;
    public static final int CPU_BUTTON_X = BASE_UI_WIDTH - 6 - CPU_BUTTON_W;
    public static final int CPU_BUTTON_Y = 2;
    public static final int HEADER_STATUS_RIGHT = CPU_BUTTON_X - 6;

    /** Optional GregTech upgrade slot, inset 7px from the capacity card's bottom-right corner. */
    public static final int COMPUTATION_UPGRADE_SLOT_X = CAPACITY_PANEL_X + CAPACITY_PANEL_W - 18 - 7;

    public static final int COMPUTATION_UPGRADE_SLOT_Y = CAPACITY_PANEL_Y + CAPACITY_PANEL_H - 18 - 7;

    private NEComputationLayout() {}
}
