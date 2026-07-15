package cn.dancingsnow.neoecoae.integration.emi.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class MultiblockPreviewLayout {
    static final int WIDTH = 170;
    private static final int MAX_HEIGHT = 190;
    private static final int MIN_HEIGHT = 120;
    private static final int EMI_VERTICAL_RESERVE = 105;
    private static final int PADDING = 4;
    private static final int GAP = 2;
    private static final int HEADER_HEIGHT = 22;
    private static final int MATERIALS_HEIGHT = 27;
    private static final int TITLE_Y = 4;
    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 18;
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
        return Mth.clamp(guiHeight - EMI_VERTICAL_RESERVE, MIN_HEIGHT, MAX_HEIGHT);
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
        Rect scene = scene();
        return new Rect(scene.right() - BUTTON_W - 2, scene.y() + 2, BUTTON_W, BUTTON_H);
    }

    Rect layerButton() {
        Rect expand = expandButton();
        return new Rect(expand.x(), expand.bottom(), BUTTON_W, BUTTON_H);
    }

    Rect formedButton() {
        Rect layer = layerButton();
        return new Rect(layer.x(), layer.bottom(), BUTTON_W, BUTTON_H);
    }

    Rect scene() {
        int y = PADDING + HEADER_HEIGHT + GAP;
        int sceneHeight = height - PADDING * 2 - GAP * 2 - HEADER_HEIGHT - MATERIALS_HEIGHT;
        return new Rect(PADDING, y, width - PADDING * 2, Math.max(1, sceneHeight));
    }

    Rect materials() {
        Rect scene = scene();
        return new Rect(PADDING, scene.bottom() + GAP, width - PADDING * 2, MATERIALS_HEIGHT);
    }

    int slotsY() {
        Rect materials = materials();
        return materials.y() + (materials.height() - SLOT_SIZE) / 2;
    }

    int materialSlotsX() {
        return PADDING;
    }

    Rect materialSlots() {
        return new Rect(materialSlotsX(), slotsY(), MultiblockPreviewState.MATERIAL_PAGE_SIZE * SLOT_SIZE, SLOT_SIZE);
    }

    Rect materialSlot(int slot) {
        return new Rect(materialSlotsX() + slot * SLOT_SIZE, slotsY(), SLOT_SIZE, SLOT_SIZE);
    }

    Rect previousPageButton() {
        Rect scene = scene();
        return new Rect(
                scene.right() - PAGE_BUTTON_W * 2 - 4,
                scene.bottom() - PAGE_BUTTON_H - 2,
                PAGE_BUTTON_W,
                PAGE_BUTTON_H);
    }

    Rect nextPageButton() {
        Rect scene = scene();
        return new Rect(
                scene.right() - PAGE_BUTTON_W - 2, scene.bottom() - PAGE_BUTTON_H - 2, PAGE_BUTTON_W, PAGE_BUTTON_H);
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
