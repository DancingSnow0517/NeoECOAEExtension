package cn.dancingsnow.neoecoae.compat.emi;

import net.minecraft.client.Minecraft;

final class MultiblockPreviewLayout {
    static final int WIDTH = 176;
    private static final int MAX_HEIGHT = 170;
    private static final int MID_HEIGHT = 150;
    private static final int MIN_HEIGHT = 136;
    private static final int TITLE_Y = 4;
    private static final int BUTTON_Y = 20;
    private static final int BUTTON_W = 42;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_GAP = 4;
    private static final int SCENE_Y = 42;
    private static final int SCENE_PAD = 4;
    private static final int MATERIAL_BLOCK_H = 35;
    private static final int MATERIAL_TITLE_GAP = 4;
    private static final int MATERIAL_SLOT_GAP = 13;
    private static final int SLOT_SIZE = 18;
    private static final int PAGE_BUTTON_W = 14;
    private static final int PAGE_BUTTON_H = 12;

    private final int width;
    private final int height;

    MultiblockPreviewLayout(int width, int height) {
        this.width = width;
        this.height = height;
    }

    static int displayHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return MAX_HEIGHT;
        }
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        if (guiHeight <= 300) {
            return MIN_HEIGHT;
        }
        if (guiHeight <= 360) {
            return MID_HEIGHT;
        }
        return MAX_HEIGHT;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int titleY() {
        return TITLE_Y;
    }

    Rect expandButton() {
        return new Rect(4, BUTTON_Y, BUTTON_W, BUTTON_H);
    }

    Rect layerButton() {
        Rect expand = expandButton();
        return new Rect(expand.x() + BUTTON_W + BUTTON_GAP, BUTTON_Y, BUTTON_W, BUTTON_H);
    }

    Rect formedButton() {
        Rect layer = layerButton();
        return new Rect(layer.x() + BUTTON_W + BUTTON_GAP, BUTTON_Y, BUTTON_W, BUTTON_H);
    }

    Rect scene() {
        return new Rect(SCENE_PAD, SCENE_Y, width - SCENE_PAD * 2, Math.max(54, height - SCENE_Y - 4 - MATERIAL_BLOCK_H));
    }

    int materialTitleY() {
        Rect scene = scene();
        return scene.y() + scene.height() + MATERIAL_TITLE_GAP;
    }

    int slotsY() {
        return materialTitleY() + MATERIAL_SLOT_GAP;
    }

    int materialSlotsX() {
        return Math.max(4, (width - MultiblockPreviewState.MATERIAL_PAGE_SIZE * SLOT_SIZE) / 2);
    }

    Rect materialSlots() {
        return new Rect(materialSlotsX(), slotsY(), MultiblockPreviewState.MATERIAL_PAGE_SIZE * SLOT_SIZE, SLOT_SIZE);
    }

    Rect materialSlot(int slot) {
        return new Rect(materialSlotsX() + slot * SLOT_SIZE, slotsY(), SLOT_SIZE, SLOT_SIZE);
    }

    Rect previousPageButton() {
        return new Rect(width - PAGE_BUTTON_W * 2 - 8, materialTitleY() - 2, PAGE_BUTTON_W, PAGE_BUTTON_H);
    }

    Rect nextPageButton() {
        return new Rect(width - PAGE_BUTTON_W - 4, materialTitleY() - 2, PAGE_BUTTON_W, PAGE_BUTTON_H);
    }

    record Rect(int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }
}
