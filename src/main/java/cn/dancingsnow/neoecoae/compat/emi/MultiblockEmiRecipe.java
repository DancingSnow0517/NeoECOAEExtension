package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
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
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MultiblockEmiRecipe implements EmiRecipe {
    private final MultiBlockDefinition definition;
    private final ResourceLocation id;
    private final MultiblockPreviewState state;
    private final NEMultiblockSceneRenderer renderer = new NEMultiblockSceneRenderer();
    private final MaterialRequirementStrip materialStrip;
    private final List<EmiStack> outputs = new ArrayList<>();

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        this.definition = definition;
        this.id = createId(definition);
        this.state = new MultiblockPreviewState(definition);
        this.materialStrip = new MaterialRequirementStrip(state);
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
        return state.inputs();
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return MultiblockPreviewLayout.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return MultiblockPreviewLayout.displayHeight();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new PreviewWidget(MultiblockPreviewLayout.WIDTH, MultiblockPreviewLayout.displayHeight()));
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
        private final MultiblockPreviewLayout layout;
        private boolean draggingScene = false;
        private int lastMouseX = 0;
        private int lastMouseY = 0;

        private PreviewWidget(int width, int height) {
            this.layout = new MultiblockPreviewLayout(width, height);
        }

        @Override
        public Bounds getBounds() {
            return new Bounds(0, 0, layout.width(), layout.height());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            updateDrag(mouseX, mouseY);

            Font font = Minecraft.getInstance().font;
            MultiblockPreviewStyle.drawPanel(g, layout.width(), layout.height());
            MultiblockPreviewStyle.drawFittedString(g, definition.getName(), 4, layout.titleY(), layout.width() - 8,
                    MultiblockPreviewStyle.TEXT_COLOR);
            renderButtons(g, mouseX, mouseY);
            renderScene(g, delta);
            g.drawString(font, Component.translatable("emi.neoecoae.multiblock.requirements"), 4, layout.materialTitleY(),
                    MultiblockPreviewStyle.TEXT_COLOR, false);
            materialStrip.render(g, layout, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button != 0) {
                return false;
            }
            if (layout.expandButton().contains(mouseX, mouseY)) {
                state.nextExpand();
                renderer.resetView();
                return true;
            }
            if (layout.layerButton().contains(mouseX, mouseY)) {
                state.nextLayer();
                renderer.resetView();
                return true;
            }
            if (layout.formedButton().contains(mouseX, mouseY)) {
                state.toggleFormed();
                renderer.resetView();
                return true;
            }
            if (materialStrip.mouseClicked(layout, mouseX, mouseY)) {
                return true;
            }
            if (layout.scene().contains(mouseX, mouseY)) {
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
            if (layout.expandButton().contains(mouseX, mouseY)) {
                return MultiblockPreviewStyle.tooltip(Component.translatable("emi.neoecoae.multiblock.change_length"));
            }
            if (layout.layerButton().contains(mouseX, mouseY)) {
                Component tooltip = state.layer() < 0
                        ? Component.translatable("emi.neoecoae.multiblock.show_all_layers")
                        : Component.translatable("emi.neoecoae.multiblock.show_layer", state.layer());
                return MultiblockPreviewStyle.tooltip(tooltip);
            }
            if (layout.formedButton().contains(mouseX, mouseY)) {
                Component tooltip = state.formed()
                        ? Component.translatable("emi.neoecoae.multiblock.show_formed")
                        : Component.translatable("emi.neoecoae.multiblock.show_unformed");
                return MultiblockPreviewStyle.tooltip(tooltip);
            }

            List<ClientTooltipComponent> materialTooltip = materialStrip.getTooltip(layout, mouseX, mouseY);
            if (!materialTooltip.isEmpty()) {
                return materialTooltip;
            }
            if (layout.scene().contains(mouseX, mouseY)) {
                return MultiblockPreviewStyle.tooltip(Component.translatable("emi.neoecoae.multiblock.drag_rotate"));
            }
            return List.of();
        }

        private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
            MultiblockPreviewStyle.drawButton(g, layout.expandButton(), "E:" + state.expand(), mouseX, mouseY);
            MultiblockPreviewStyle.drawButton(g, layout.layerButton(), state.layer() < 0 ? "L:*" : "L:" + state.layer(), mouseX, mouseY);
            MultiblockPreviewStyle.drawButton(g, layout.formedButton(), state.formed() ? "F:Y" : "F:N", mouseX, mouseY);
        }

        private void renderScene(GuiGraphics g, float delta) {
            MultiblockPreviewLayout.Rect sceneRect = layout.scene();
            g.fill(sceneRect.x(), sceneRect.y(), sceneRect.right(), sceneRect.bottom(), 0xFFE9E9E9);
            g.fill(sceneRect.x(), sceneRect.y(), sceneRect.right(), sceneRect.y() + 1, MultiblockPreviewStyle.PANEL_BORDER);
            g.fill(sceneRect.x(), sceneRect.bottom() - 1, sceneRect.right(), sceneRect.bottom(), MultiblockPreviewStyle.PANEL_BORDER);
            g.fill(sceneRect.x(), sceneRect.y(), sceneRect.x() + 1, sceneRect.bottom(), MultiblockPreviewStyle.PANEL_BORDER);
            g.fill(sceneRect.right() - 1, sceneRect.y(), sceneRect.right(), sceneRect.bottom(), MultiblockPreviewStyle.PANEL_BORDER);
            renderer.render(g, state.scene(), renderedPositions(), sceneRect.x() + 2, sceneRect.y() + 2,
                    sceneRect.width() - 4, sceneRect.height() - 4, delta, true);
        }

        private List<BlockPos> renderedPositions() {
            MultiblockPreviewScene scene = state.scene();
            if (scene == null) {
                return List.of();
            }
            return state.layer() < 0 ? scene.orderedPositions() : scene.positionsForLayer(state.layer());
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
    }
}
