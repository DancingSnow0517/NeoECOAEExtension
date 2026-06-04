package cn.dancingsnow.neoecoae.gui.nativeui.layout;

/**
 * ECO 集成工作站的共享布局常量。
 */
public final class NEIntegratedWorkingStationLayout {

    // ── 核心尺寸 ──
    public static final int SLOT_SIZE = 18;
    public static final int ITEM_OFFSET = 1;

    // ── 主面板大小 ──
    // AE2 IO Port 是 176×166。这里高度保留 171，宽度改成 176，
    // 这样 9 列玩家背包可以做到左右各 7px 的 AE2 风格边距。
    public static final int PANEL_W = 176;
    public static final int PANEL_H = 171;

    // ── 3×3 输入区域（18×18 槽位背景）──
    public static final int INPUT_COLS = 3;
    public static final int INPUT_ROWS = 3;

    // 顶部工作区整体放在标题下方。这里比原来的 39 稍微右移，
    // 让扩大后的 176px 面板看起来更平衡。
    public static final int INPUT_BG_X = 43;
    public static final int INPUT_BG_Y = 20;

    // ── 输出槽位 ──
    public static final int OUTPUT_BG_X = 112;
    public static final int OUTPUT_BG_Y = 38;

    // 产物大框：26×26，类似 AE2 压印器输出框。内部 16×16 item 居中。
    public static final int OUTPUT_FRAME_X = OUTPUT_BG_X - 4;
    public static final int OUTPUT_FRAME_Y = OUTPUT_BG_Y - 4;
    public static final int OUTPUT_FRAME_W = 26;
    public static final int OUTPUT_FRAME_H = 26;

    // ── 升级面板（extra_panels.png）──
    public static final int UPGRADE_COUNT = 4;
    public static final int UPGRADE_PADDING = 7;

    // AE2 外接升级栏：主面板右侧留 4px 间隔。
    public static final int UPGRADE_PANEL_X = PANEL_W + 4;
    public static final int UPGRADE_PANEL_Y = 0;

    public static final int UPGRADE_PANEL_W = SLOT_SIZE + UPGRADE_PADDING * 2;
    public static final int UPGRADE_PANEL_H = UPGRADE_COUNT * SLOT_SIZE + UPGRADE_PADDING * 2;

    public static final int UPGRADE_BG_X = UPGRADE_PANEL_X + UPGRADE_PADDING;
    public static final int UPGRADE_FIRST_BG_Y = UPGRADE_PANEL_Y + UPGRADE_PADDING;

    // ── AE2 玩家背包布局 ──
    public static final int AE2_PLAYER_SLOT_LEFT = 8;
    public static final int AE2_PLAYER_INV_BOTTOM = 82;
    public static final int AE2_HOTBAR_BOTTOM = 24;
    public static final int AE2_INV_LABEL_BOTTOM = 93;

    // 16×16 物品/点击区域坐标
    public static final int PLAYER_INV_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int PLAYER_INV_SLOT_Y = PANEL_H - AE2_PLAYER_INV_BOTTOM;

    public static final int HOTBAR_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int HOTBAR_SLOT_Y = PANEL_H - AE2_HOTBAR_BOTTOM;

    // 18×18 槽位背景坐标
    public static final int PLAYER_INV_BG_X = PLAYER_INV_SLOT_X - ITEM_OFFSET;
    public static final int PLAYER_INV_BG_Y = PLAYER_INV_SLOT_Y - ITEM_OFFSET;

    public static final int HOTBAR_BG_X = HOTBAR_SLOT_X - ITEM_OFFSET;
    public static final int HOTBAR_BG_Y = HOTBAR_SLOT_Y - ITEM_OFFSET;

    // ── 16×16 物品/点击区域（Menu Slot 原点 = 背景坐标 + ITEM_OFFSET）──
    public static final int INPUT_SLOT_X = INPUT_BG_X + ITEM_OFFSET;
    public static final int INPUT_SLOT_Y = INPUT_BG_Y + ITEM_OFFSET;

    public static final int OUTPUT_SLOT_X = OUTPUT_FRAME_X + (OUTPUT_FRAME_W - 16) / 2;
    public static final int OUTPUT_SLOT_Y = OUTPUT_FRAME_Y + (OUTPUT_FRAME_H - 16) / 2;

    public static final int UPGRADE_SLOT_X = UPGRADE_BG_X + ITEM_OFFSET;
    public static final int UPGRADE_FIRST_SLOT_Y = UPGRADE_FIRST_BG_Y + ITEM_OFFSET;

    // —— 进度条 ——
    public static final int PROGRESS_X = OUTPUT_FRAME_X + 30;
    public static final int PROGRESS_Y = OUTPUT_FRAME_Y + 4;
    public static final int PROGRESS_W = 6;
    public static final int PROGRESS_H = 18;

    // ── 流体槽 ──
    public static final int FLUID_IN_X = 7;
    public static final int FLUID_IN_Y = 20;
    public static final int FLUID_IN_W = 18;
    public static final int FLUID_IN_H = 54;

    public static final int FLUID_OUT_X = 151;
    public static final int FLUID_OUT_Y = 20;
    public static final int FLUID_OUT_W = 18;
    public static final int FLUID_OUT_H = 54;

    // ── 标题位置 ──
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 7;

    // ── 自动导出开关按钮 ──
    public static final int TOGGLE_BTN_X = -18;
    public static final int TOGGLE_BTN_Y = 3;
    public static final int TOGGLE_BTN_W = 14;
    public static final int TOGGLE_BTN_H = 14;

    // ── 清空流体按钮 ──
    public static final int CLEAR_BTN_SIZE = 8;
    public static final int CLEAR_BTN_W = CLEAR_BTN_SIZE;
    public static final int CLEAR_BTN_H = CLEAR_BTN_SIZE;

    // 输入清空按钮：放在输入流体槽右下侧，底部留 2px 间距。
    public static final int CLEAR_BTN_IN_X = FLUID_IN_X + FLUID_IN_W + 2;
    public static final int CLEAR_BTN_IN_Y = FLUID_IN_Y + FLUID_IN_H - CLEAR_BTN_SIZE;

    // 输出清空按钮：放在输出流体槽左下侧，底部留 2px 间距。
    public static final int CLEAR_BTN_OUT_X = FLUID_OUT_X - CLEAR_BTN_SIZE - 2;
    public static final int CLEAR_BTN_OUT_Y = FLUID_OUT_Y + FLUID_OUT_H - CLEAR_BTN_SIZE;

    // ── 背包标签位置 ──
    public static final int INV_LABEL_X = AE2_PLAYER_SLOT_LEFT;
    public static final int INV_LABEL_Y = PANEL_H - AE2_INV_LABEL_BOTTOM;

    private NEIntegratedWorkingStationLayout() {}
}
