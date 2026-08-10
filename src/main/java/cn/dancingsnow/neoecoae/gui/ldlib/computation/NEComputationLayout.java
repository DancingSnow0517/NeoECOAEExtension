package cn.dancingsnow.neoecoae.gui.ldlib.computation;

/** Pixel geometry matching the LDLib2 computation host while rendering through LDLib1. */
public final class NEComputationLayout {
    public static final int BASE_UI_WIDTH = 340;
    public static final int UI_HEIGHT = 248;

    public static final int PARALLEL_PANEL_W = 108;
    public static final int PARALLEL_PANEL_GAP = 4;
    /** The AE2 panel remains centered; the optional controller opens into negative x. */
    public static final int MAIN_X = 0;

    public static final int UI_WIDTH = BASE_UI_WIDTH;

    /** Fast task planning is controlled from a compact tab immediately left of the host header. */
    public static final int FAST_TASK_PLANNING_BUTTON_X = -24;

    public static final int FAST_TASK_PLANNING_BUTTON_Y = 0;
    public static final int FAST_TASK_PLANNING_BUTTON_W = 24;
    public static final int FAST_TASK_PLANNING_BUTTON_H = 24;

    public static final int PARALLEL_PANEL_X = -(PARALLEL_PANEL_W + PARALLEL_PANEL_GAP);
    public static final int PARALLEL_PANEL_H = 48;
    public static final int PARALLEL_TAB_SIZE = 24;
    /** The expanded page is anchored to the bottom-left and opens upward. */
    public static final int PARALLEL_PANEL_Y = UI_HEIGHT - PARALLEL_PANEL_H - 4;
    /** The collapsed tab stays beside the controller's lower-left corner. */
    public static final int PARALLEL_COLLAPSED_X = PARALLEL_PANEL_X + PARALLEL_PANEL_W - PARALLEL_TAB_SIZE;

    public static final int PARALLEL_COLLAPSED_Y = UI_HEIGHT - PARALLEL_TAB_SIZE - 4;
    public static final int PARALLEL_TITLE_X = PARALLEL_PANEL_X + 9;
    public static final int PARALLEL_TITLE_Y = PARALLEL_PANEL_Y + 4;
    /** GTCEu's 24px tab moves from the left edge to the right edge when expanded. */
    public static final int PARALLEL_TOGGLE_X = PARALLEL_COLLAPSED_X;

    public static final int PARALLEL_TOGGLE_Y = PARALLEL_COLLAPSED_Y;
    public static final int PARALLEL_TOGGLE_W = PARALLEL_TAB_SIZE;
    public static final int PARALLEL_TOGGLE_H = PARALLEL_TAB_SIZE;
    public static final int PARALLEL_BORDER = 4;
    /** Fallback step used only when the runtime GTCEu number widget is unavailable. */
    public static final int PARALLEL_STEP = 64;

    public static final int PARALLEL_FIELD_X = PARALLEL_PANEL_X + 4;
    public static final int PARALLEL_FIELD_Y = PARALLEL_PANEL_Y + 24;
    public static final int PARALLEL_FIELD_W = 100;
    public static final int PARALLEL_FIELD_H = 20;

    public static final int CAPACITY_PANEL_X = 6;
    public static final int CAPACITY_PANEL_Y = 36;
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
    public static final int PLAYER_INV_LABEL_Y = 148;
    public static final int PLAYER_INV_Y = 159;
    public static final int PLAYER_HOTBAR_Y = 217;

    public static final int TASK_PANEL_X = 178;
    public static final int TASK_PANEL_Y = 36;
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
    public static final int NETWORK_FREQUENCY_BUTTON_W = 22;
    public static final int NETWORK_FREQUENCY_BUTTON_H = 18;
    public static final int NETWORK_FREQUENCY_BUTTON_X = CPU_BUTTON_X - 6 - NETWORK_FREQUENCY_BUTTON_W;
    public static final int NETWORK_FREQUENCY_BUTTON_Y = CPU_BUTTON_Y;
    public static final int HEADER_STATUS_RIGHT = NETWORK_FREQUENCY_BUTTON_X - 6;
    public static final int HEADER_TITLE_X = 8;
    public static final int HEADER_TITLE_Y = 5;
    public static final int HEADER_STATUS_Y = 17;
    public static final int HEADER_MODE_W = 84;
    public static final int HEADER_CONNECTION_W = 62;
    public static final int HEADER_GAP = 4;

    /** Optional GregTech upgrade slot, inset 7px from the capacity card's bottom-right corner. */
    public static final int COMPUTATION_UPGRADE_SLOT_X = CAPACITY_PANEL_X + CAPACITY_PANEL_W - 18 - 7;

    public static final int COMPUTATION_UPGRADE_SLOT_Y = CAPACITY_PANEL_Y + CAPACITY_PANEL_H - 18 - 7;

    private NEComputationLayout() {}
}
