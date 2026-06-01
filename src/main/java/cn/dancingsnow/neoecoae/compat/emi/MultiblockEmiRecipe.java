package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.client.multiblock.preview.NEMultiblockSceneRenderer;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MultiblockEmiRecipe implements EmiRecipe {
    private static final int WIDTH = 176;
    private static final int MAX_HEIGHT = 170;
    private static final int MID_HEIGHT = 150;
    private static final int MIN_HEIGHT = 136;
    private static final int SLOT_SIZE = 18;
    private static final int MATERIAL_PAGE_SIZE = 9;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int PANEL_COLOR = 0xFFE3E3E3;
    private static final int PANEL_BORDER = 0xFF4F4F4F;
    private static final int BUTTON_BG = 0xFF8F8F8F;
    private static final int BUTTON_BG_HOVER = 0xFFABABAB;
    private static final int BUTTON_BORDER = 0xFF303030;

    private final MultiBlockDefinition definition;
    private final ResourceLocation id;
    private final NEMultiblockSceneRenderer renderer = new NEMultiblockSceneRenderer();
    private final List<EmiStack> materialStacks = new ArrayList<>();
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    private MultiblockPreviewScene scene;
    private int expand;
    private int layer = -1;
    private int materialPage = 0;
    private boolean formed = false;

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        this.definition = definition;
        this.id = createId(definition);
        this.expand = definition.getExpandMin();
        rebuildScene();
        outputs.add(EmiStack.of(definition.getOwner().value()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.MULTIBLOCK;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return displayHeight();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new PreviewWidget(WIDTH, displayHeight()));
    }

    private static int displayHeight() {
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

    private void nextExpand() {
        if (expand >= definition.getExpandMax()) {
            expand = definition.getExpandMin();
        } else {
            expand++;
        }
        if (formed) {
            formed = false;
        }
        renderer.resetView();
        rebuildScene();
    }

    private void nextLayer() {
        int maxLayer = scene == null ? 0 : scene.yMax();
        if (layer + 1 > maxLayer) {
            layer = -1;
        } else {
            layer++;
        }
        if (formed) {
            formed = false;
        }
        renderer.resetView();
        rebuildScene();
    }

    private void toggleFormed() {
        formed = !formed;
        renderer.resetView();
        rebuildScene();
    }

    private void previousMaterialsPage() {
        int pages = materialPages();
        if (pages > 1) {
            materialPage = materialPage <= 0 ? pages - 1 : materialPage - 1;
        }
    }

    private void nextMaterialsPage() {
        int pages = materialPages();
        if (pages > 1) {
            materialPage = materialPage + 1 >= pages ? 0 : materialPage + 1;
        }
    }

    private void rebuildScene() {
        scene = MultiblockPreviewContext.createScene(definition, expand, formed);
        if (layer > scene.yMax()) {
            layer = -1;
        }
        refreshMaterials(scene.requiredItems());
    }

    private void refreshMaterials(List<ItemStack> stacks) {
        materialStacks.clear();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                materialStacks.add(EmiStack.of(stack.copy()));
            }
        }
        inputs.clear();
        inputs.addAll(materialStacks);
        if (materialPage >= materialPages()) {
            materialPage = Math.max(0, materialPages() - 1);
        }
    }

    private int materialPages() {
        return Math.max(1, (materialStacks.size() + MATERIAL_PAGE_SIZE - 1) / MATERIAL_PAGE_SIZE);
    }

    private static ResourceLocation createId(MultiBlockDefinition definition) {
        Block owner = definition.getOwner().value();
        ResourceLocation ownerId = BuiltInRegistries.BLOCK.getKey(owner);
        if (ownerId == null) {
            return NeoECOAE.id("multiblock/unknown");
        }
        return NeoECOAE.id("multiblock/" + ownerId.getNamespace() + "/" + ownerId.getPath());
    }

    private final class PreviewWidget extends Widget {
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
        private static final int PAGE_BUTTON_W = 14;
        private static final int PAGE_BUTTON_H = 12;

        private final int width;
        private final int height;
        private boolean draggingScene = false;
        private int lastMouseX = 0;
        private int lastMouseY = 0;

        private PreviewWidget(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public Bounds getBounds() {
            return new Bounds(0, 0, width, height);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            updateDrag(mouseX, mouseY);

            Font font = Minecraft.getInstance().font;
            g.fill(0, 0, width, height, PANEL_COLOR);
            g.fill(0, 0, width, 1, PANEL_BORDER);
            g.fill(0, height - 1, width, height, PANEL_BORDER);
            g.fill(0, 0, 1, height, PANEL_BORDER);
            g.fill(width - 1, 0, width, height, PANEL_BORDER);

            drawFittedString(g, font, definition.getName(), 4, TITLE_Y, width - 8, TEXT_COLOR);
            drawButton(g, expandButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H, "E:" + expand, mouseX, mouseY);
            drawButton(g, layerButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H, layer < 0 ? "L:*" : "L:" + layer, mouseX, mouseY);
            drawButton(g, formedButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H, formed ? "F:Y" : "F:N", mouseX, mouseY);

            renderScene(g, delta);
            g.drawString(font, Component.literal("方块数量需求"), 4, materialTitleY(), TEXT_COLOR, false);
            renderMaterials(g, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) {
                return false;
            }
            if (contains(mouseX, mouseY, expandButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                nextExpand();
                return true;
            }
            if (contains(mouseX, mouseY, layerButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                nextLayer();
                return true;
            }
            if (contains(mouseX, mouseY, formedButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                toggleFormed();
                return true;
            }
            if (materialPages() > 1) {
                if (contains(mouseX, mouseY, leftPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                    previousMaterialsPage();
                    return true;
                }
                if (contains(mouseX, mouseY, rightPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                    nextMaterialsPage();
                    return true;
                }
            }
            if (contains(mouseX, mouseY, sceneX(), sceneY(), sceneW(), sceneH())) {
                draggingScene = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
            draggingScene = false;
            return false;
        }

        public boolean mouseReleased(int mouseX, int mouseY, int button) {
            if (button == 0 && draggingScene) {
                draggingScene = false;
                return true;
            }
            return false;
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            if (contains(mouseX, mouseY, expandButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                return tooltip(Component.literal("切换结构长度"));
            }
            if (contains(mouseX, mouseY, layerButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                return tooltip(Component.literal(layer < 0 ? "显示全部层" : "显示第 " + layer + " 层"));
            }
            if (contains(mouseX, mouseY, formedButtonX(), BUTTON_Y, BUTTON_W, BUTTON_H)) {
                return tooltip(Component.literal(formed ? "显示成型状态" : "显示未成型状态"));
            }
            if (materialPages() > 1 && contains(mouseX, mouseY, leftPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                return tooltip(Component.literal("上一页"));
            }
            if (materialPages() > 1 && contains(mouseX, mouseY, rightPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H)) {
                return tooltip(Component.literal("下一页"));
            }

            int hovered = hoveredMaterial(mouseX, mouseY);
            if (hovered >= 0 && hovered < materialStacks.size()) {
                return materialStacks.get(hovered).getTooltip();
            }
            if (contains(mouseX, mouseY, sceneX(), sceneY(), sceneW(), sceneH())) {
                return tooltip(Component.literal("按住左键拖动旋转"));
            }
            return List.of();
        }

        private void renderMaterials(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int slotsY = slotsY();
            int start = materialPage * MATERIAL_PAGE_SIZE;
            int count = Math.min(MATERIAL_PAGE_SIZE, Math.max(0, materialStacks.size() - start));
            int totalWidth = MATERIAL_PAGE_SIZE * SLOT_SIZE;
            int slotsX = materialSlotsX();
            g.fill(slotsX - 2, slotsY - 2, slotsX + totalWidth + 2, slotsY + SLOT_SIZE + 2, 0x66808080);

            for (int i = 0; i < MATERIAL_PAGE_SIZE; i++) {
                int x = slotsX + i * SLOT_SIZE;
                boolean hovered = contains(mouseX, mouseY, x, slotsY, SLOT_SIZE, SLOT_SIZE);
                g.fill(x, slotsY, x + SLOT_SIZE, slotsY + SLOT_SIZE, hovered ? 0xFFFFFFFF : 0xFFE8E8E8);
                g.fill(x, slotsY, x + SLOT_SIZE, slotsY + 1, 0xFF707070);
                g.fill(x, slotsY + SLOT_SIZE - 1, x + SLOT_SIZE, slotsY + SLOT_SIZE, 0xFF707070);
                g.fill(x, slotsY, x + 1, slotsY + SLOT_SIZE, 0xFF707070);
                g.fill(x + SLOT_SIZE - 1, slotsY, x + SLOT_SIZE, slotsY + SLOT_SIZE, 0xFF707070);
                if (i < count) {
                    materialStacks.get(start + i).render(g, x + 1, slotsY + 1, delta);
                }
            }

            if (materialPages() > 1) {
                drawButton(g, leftPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H, "<", mouseX, mouseY);
                drawButton(g, rightPageButtonX(), pageButtonY(), PAGE_BUTTON_W, PAGE_BUTTON_H, ">", mouseX, mouseY);
                String page = (materialPage + 1) + "/" + materialPages();
                Font font = Minecraft.getInstance().font;
                g.drawString(font, page, leftPageButtonX() - 4 - font.width(page), materialTitleY(), TEXT_COLOR, false);
            }
        }

        private void renderScene(GuiGraphics g, float delta) {
            int sceneX = sceneX();
            int sceneY = sceneY();
            int sceneW = sceneW();
            int sceneH = sceneH();
            g.fill(sceneX, sceneY, sceneX + sceneW, sceneY + sceneH, 0xFFE9E9E9);
            g.fill(sceneX, sceneY, sceneX + sceneW, sceneY + 1, PANEL_BORDER);
            g.fill(sceneX, sceneY + sceneH - 1, sceneX + sceneW, sceneY + sceneH, PANEL_BORDER);
            g.fill(sceneX, sceneY, sceneX + 1, sceneY + sceneH, PANEL_BORDER);
            g.fill(sceneX + sceneW - 1, sceneY, sceneX + sceneW, sceneY + sceneH, PANEL_BORDER);
            renderer.render(g, scene, renderedPositions(), sceneX + 2, sceneY + 2, sceneW - 4, sceneH - 4, delta, true);
        }

        private List<BlockPos> renderedPositions() {
            return layer < 0 ? scene.orderedPositions() : scene.positionsForLayer(layer);
        }

        private int hoveredMaterial(int mouseX, int mouseY) {
            if (!contains(mouseX, mouseY, materialSlotsX(), slotsY(), MATERIAL_PAGE_SIZE * SLOT_SIZE, SLOT_SIZE)) {
                return -1;
            }
            int slot = (mouseX - materialSlotsX()) / SLOT_SIZE;
            int index = materialPage * MATERIAL_PAGE_SIZE + slot;
            return index < materialStacks.size() ? index : -1;
        }

        private int expandButtonX() {
            return 4;
        }

        private int layerButtonX() {
            return expandButtonX() + BUTTON_W + BUTTON_GAP;
        }

        private int formedButtonX() {
            return layerButtonX() + BUTTON_W + BUTTON_GAP;
        }

        private int materialSlotsX() {
            return Math.max(4, (width - MATERIAL_PAGE_SIZE * SLOT_SIZE) / 2);
        }

        private int leftPageButtonX() {
            return width - PAGE_BUTTON_W * 2 - 8;
        }

        private int rightPageButtonX() {
            return width - PAGE_BUTTON_W - 4;
        }

        private int pageButtonY() {
            return materialTitleY() - 2;
        }

        private int sceneX() {
            return SCENE_PAD;
        }

        private int sceneY() {
            return SCENE_Y;
        }

        private int sceneW() {
            return width - SCENE_PAD * 2;
        }

        private int sceneH() {
            return Math.max(54, height - sceneY() - 4 - MATERIAL_BLOCK_H);
        }

        private int materialTitleY() {
            return sceneY() + sceneH() + MATERIAL_TITLE_GAP;
        }

        private int slotsY() {
            return materialTitleY() + MATERIAL_SLOT_GAP;
        }

        private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
            boolean hovered = contains(mouseX, mouseY, x, y, w, h);
            Font font = Minecraft.getInstance().font;
            g.fill(x, y, x + w, y + h, BUTTON_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
            int textX = x + (w - font.width(text)) / 2;
            int textY = y + (h - font.lineHeight) / 2;
            g.drawString(font, text, textX, textY, 0xFFFFFFFF, false);
        }

        private void drawFittedString(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int color) {
            int textW = font.width(text);
            if (textW <= maxW) {
                g.drawString(font, text, x, y, color, false);
                return;
            }

            float scale = Math.max(0.78F, maxW / (float) textW);
            Component renderedText = text;
            if (textW * scale > maxW) {
                String ellipsis = "...";
                int unscaledMaxW = Math.max(0, (int) (maxW / scale) - font.width(ellipsis));
                renderedText = Component.literal(font.plainSubstrByWidth(text.getString(), unscaledMaxW) + ellipsis);
            }
            g.pose().pushPose();
            try {
                g.pose().translate(x, y, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                g.drawString(font, renderedText, 0, 0, color, false);
            } finally {
                g.pose().popPose();
            }
        }

        private void updateDrag(int mouseX, int mouseY) {
            if (!draggingScene) {
                return;
            }
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                draggingScene = false;
                return;
            }
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;
            if (dx != 0 || dy != 0) {
                renderer.rotate(dx * 0.8F, dy * 0.55F);
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
        }

        private boolean contains(int mouseX, int mouseY, int x, int y, int w, int h) {
            return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        }

        private List<ClientTooltipComponent> tooltip(Component text) {
            return List.of(ClientTooltipComponent.create(text.getVisualOrderText()));
        }
    }
}
