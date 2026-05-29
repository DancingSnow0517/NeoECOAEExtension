package cn.dancingsnow.neoecoae.gui.nativeui.layout;

/**
 * ECO 样板总线的共享布局常量。
 * <p>
 * {@code NECraftingPatternBusMenu}（槽位点击区域）和
 * {@code NECraftingPatternBusScreen}（视觉背景）共同导入
 * 这些常量以确保坐标同步。
 * </p>
 * <p>
 * 所有 *_BG 坐标表示 18×18 槽位背景左上角。
 * 所有 *_SLOT 坐标表示 16×16 物品/点击区域左上角。
 * </p>
 */
public final class NECraftingPatternBusLayout {

    // ── 核心尺寸 ──
    public static final int SLOT_SIZE = 18;
    public static final int ITEM_OFFSET = 1;

    // ── 面板尺寸 ──
    // 176 = 7px left margin + 9 * 18px slots + 7px right margin
    public static final int GUI_W = 176;
    public static final int GUI_H = 246;

    // ── 标题 ──
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 7;

    // ── 样板区域 ──
    public static final int PATTERN_COLS = 9;
    public static final int PATTERN_ROWS = 7;

    // 18×18 槽位背景。
    // 顶部标题占 9px 高度，y=22 可以保留；如果标题仍显得挤，可以改成 23 或 24。
    public static final int PATTERN_BG_X = 7;
    public static final int PATTERN_BG_Y = 22;

    // 16×16 物品/点击区域（Menu Slot 原点）
    public static final int PATTERN_SLOT_X = PATTERN_BG_X + ITEM_OFFSET;
    public static final int PATTERN_SLOT_Y = PATTERN_BG_Y + ITEM_OFFSET;

    // ── AE2 玩家背包布局 ──
    // 按 AE2 common/player_inventory 风格：slot left = 8, inventory bottom = 82, hotbar bottom = 24。
    public static final int AE2_PLAYER_SLOT_LEFT = 8;
    public static final int AE2_PLAYER_INV_BOTTOM = 82;
    public static final int AE2_HOTBAR_BOTTOM = 24;
    public static final int AE2_INV_LABEL_BOTTOM = 93;

    // 16×16 物品/点击区域
    public static final int INV_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int INV_SLOT_Y = GUI_H - AE2_PLAYER_INV_BOTTOM;

    public static final int HOTBAR_SLOT_X = AE2_PLAYER_SLOT_LEFT;
    public static final int HOTBAR_SLOT_Y = GUI_H - AE2_HOTBAR_BOTTOM;

    // 18×18 槽位背景
    public static final int INV_BG_X = INV_SLOT_X - ITEM_OFFSET;
    public static final int INV_BG_Y = INV_SLOT_Y - ITEM_OFFSET;

    public static final int HOTBAR_BG_X = HOTBAR_SLOT_X - ITEM_OFFSET;
    public static final int HOTBAR_BG_Y = HOTBAR_SLOT_Y - ITEM_OFFSET;

    // ── 背包标题 ──
    public static final int INV_LABEL_X = AE2_PLAYER_SLOT_LEFT;
    public static final int INV_LABEL_Y = GUI_H - AE2_INV_LABEL_BOTTOM;

    private NECraftingPatternBusLayout() {
    }
}