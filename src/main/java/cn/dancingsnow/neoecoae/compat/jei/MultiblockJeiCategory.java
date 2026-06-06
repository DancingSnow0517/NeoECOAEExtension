package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.client.multiblock.preview.NEMultiblockSceneRenderer;
import cn.dancingsnow.neoecoae.compat.xei.MultiblockInfoRecipe;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class MultiblockJeiCategory implements IRecipeCategory<MultiblockInfoRecipe> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 200;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int PANEL_COLOR = 0xFFE3E3E3;
    private static final int PANEL_BORDER = 0xFF4F4F4F;
    private static final int BUTTON_BG = 0xFF8F8F8F;
    private static final int BUTTON_BG_HOVER = 0xFFABABAB;
    private static final int BUTTON_BORDER = 0xFF303030;

    private static final Rect LENGTH_BUTTON = new Rect(4, 20, 52, 14);
    private static final Rect LAYER_BUTTON = new Rect(60, 20, 52, 14);
    private static final Rect FORMED_BUTTON = new Rect(116, 20, 56, 14);
    private static final Rect SCENE = new Rect(4, 38, 168, 88);

    private static final int MATERIALS_TITLE_Y = 130;
    private static final int MATERIALS_X = 7;
    private static final int MATERIALS_Y = 142;
    private static final int MATERIAL_COLUMNS = 9;
    private static final int SLOT_SIZE = 18;

    private final IDrawable icon;
    private final Component title;
    private final Map<MultiblockInfoRecipe, PreviewState> states = new IdentityHashMap<>();

    public MultiblockJeiCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(NEBlocks.STORAGE_SYSTEM_L4.asStack());
        this.title = Component.translatable("category.neoecoae.multiblock");
    }

    @Override
    public RecipeType<MultiblockInfoRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.MULTIBLOCK_RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockInfoRecipe recipe, IFocusGroup focuses) {
        PreviewState state = state(recipe);
        List<ItemStack> materials = state.materials();
        for (int i = 0; i < materials.size(); i++) {
            int x = MATERIALS_X + i % MATERIAL_COLUMNS * SLOT_SIZE;
            int y = MATERIALS_Y + i / MATERIAL_COLUMNS * SLOT_SIZE;
            builder.addInputSlot(x, y)
                    .setSlotName("material_" + i)
                    .addItemStack(materials.get(i).copy());
        }
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemLike(recipe.ownerBlock());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiblockInfoRecipe recipe, IFocusGroup focuses) {
        PreviewState state = state(recipe);
        builder.addInputHandler(new IJeiInputHandler() {
            @Override
            public ScreenRectangle getArea() {
                return new ScreenRectangle(SCENE.x(), SCENE.y(), SCENE.width(), SCENE.height());
            }

            @Override
            public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
                InputConstants.Key key = input.getKey();
                return key.getType() == InputConstants.Type.MOUSE && key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
            }

            @Override
            public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta) {
                if (scrollDelta == 0.0D) {
                    return false;
                }
                state.renderer().adjustZoom(scrollDelta);
                return true;
            }

            @Override
            public boolean handleMouseDragged(
                    double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
                if (mouseKey.getType() != InputConstants.Type.MOUSE
                        || mouseKey.getValue() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    return false;
                }
                state.renderer().rotate((float) dragX * 0.8F, (float) dragY * 0.55F);
                return true;
            }
        });
    }

    @Override
    public void onDisplayedIngredientsUpdate(
            MultiblockInfoRecipe recipe, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        state(recipe).bindSlots(recipeSlots);
    }

    @Override
    public void draw(
            MultiblockInfoRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY) {
        PreviewState state = state(recipe);
        Font font = Minecraft.getInstance().font;

        drawPanel(graphics);
        drawFittedString(graphics, recipe.definition().getName(), 4, 4, WIDTH - 8, TEXT_COLOR);
        drawButton(graphics, LENGTH_BUTTON, "E: " + state.expand(), mouseX, mouseY);
        drawButton(graphics, LAYER_BUTTON, state.layer() < 0 ? "Y: *" : "Y: " + state.layer(), mouseX, mouseY);
        drawButton(graphics, FORMED_BUTTON, state.formed() ? "F: 1" : "F: 0", mouseX, mouseY);

        state.renderer()
                .render(
                        graphics,
                        state.scene(),
                        state.renderedPositions(),
                        SCENE.x(),
                        SCENE.y(),
                        SCENE.width(),
                        SCENE.height(),
                        Minecraft.getInstance().getFrameTime());
        graphics.drawString(
                font,
                Component.translatable("emi.neoecoae.multiblock.requirements"),
                4,
                MATERIALS_TITLE_Y,
                TEXT_COLOR,
                false);
        for (int i = 0; i < state.materials().size(); i++) {
            int x = MATERIALS_X + i % MATERIAL_COLUMNS * SLOT_SIZE - 1;
            int y = MATERIALS_Y + i / MATERIAL_COLUMNS * SLOT_SIZE - 1;
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF707070);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFFE8E8E8);
        }
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            MultiblockInfoRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY) {
        PreviewState state = state(recipe);
        if (LENGTH_BUTTON.contains(mouseX, mouseY)) {
            tooltip.add(Component.translatable("gui.neoecoae.multiblock.length", state.expand()));
            tooltip.add(Component.translatable("emi.neoecoae.multiblock.change_length"));
        } else if (LAYER_BUTTON.contains(mouseX, mouseY)) {
            tooltip.add(
                    state.layer() < 0
                            ? Component.translatable("emi.neoecoae.multiblock.show_all_layers")
                            : Component.translatable("emi.neoecoae.multiblock.show_layer", state.layer()));
        } else if (FORMED_BUTTON.contains(mouseX, mouseY)) {
            tooltip.add(
                    state.formed()
                            ? Component.translatable("emi.neoecoae.multiblock.show_formed")
                            : Component.translatable("emi.neoecoae.multiblock.show_unformed"));
        }
    }

    @Override
    @SuppressWarnings("removal")
    public boolean handleInput(MultiblockInfoRecipe recipe, double mouseX, double mouseY, InputConstants.Key input) {
        if (input.getType() != InputConstants.Type.MOUSE || input.getValue() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        PreviewState state = state(recipe);
        if (LENGTH_BUTTON.contains(mouseX, mouseY)) {
            state.nextExpand();
            return true;
        }
        if (LAYER_BUTTON.contains(mouseX, mouseY)) {
            state.nextLayer();
            return true;
        }
        if (FORMED_BUTTON.contains(mouseX, mouseY)) {
            state.toggleFormed();
            return true;
        }
        return false;
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(MultiblockInfoRecipe recipe) {
        return recipe.id();
    }

    private PreviewState state(MultiblockInfoRecipe recipe) {
        return states.computeIfAbsent(recipe, ignored -> new PreviewState(recipe));
    }

    private static void drawPanel(GuiGraphics graphics) {
        graphics.fill(0, 0, WIDTH, HEIGHT, PANEL_COLOR);
        graphics.fill(0, 0, WIDTH, 1, PANEL_BORDER);
        graphics.fill(0, HEIGHT - 1, WIDTH, HEIGHT, PANEL_BORDER);
        graphics.fill(0, 0, 1, HEIGHT, PANEL_BORDER);
        graphics.fill(WIDTH - 1, 0, WIDTH, HEIGHT, PANEL_BORDER);
    }

    private static void drawButton(GuiGraphics graphics, Rect rect, String text, double mouseX, double mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        Font font = Minecraft.getInstance().font;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), BUTTON_BORDER);
        graphics.fill(
                rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
        int textX = rect.x() + (rect.width() - font.width(text)) / 2;
        int textY = rect.y() + (rect.height() - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY, 0xFFFFFFFF, false);
    }

    private static void drawFittedString(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }

        String ellipsis = "...";
        int available = Math.max(0, maxWidth - font.width(ellipsis));
        Component rendered = Component.literal(font.plainSubstrByWidth(text.getString(), available) + ellipsis);
        graphics.drawString(font, rendered, x, y, color, false);
    }

    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private static final class PreviewState {
        private final MultiblockInfoRecipe recipe;
        private final NEMultiblockSceneRenderer renderer = new NEMultiblockSceneRenderer();
        private final Map<Integer, IRecipeSlotDrawable> materialSlots = new java.util.HashMap<>();

        private MultiblockPreviewScene scene;
        private List<ItemStack> materials = List.of();
        private int expand;
        private int layer = -1;
        private boolean formed;

        private PreviewState(MultiblockInfoRecipe recipe) {
            this.recipe = recipe;
            this.expand = recipe.definition().getExpandMin();
            rebuild();
        }

        private void rebuild() {
            scene = MultiblockPreviewContext.createScene(recipe.definition(), expand, formed);
            materials =
                    copyItems(StructureTerminalMaterialRequirements.collectRequiredItems(recipe.definition(), expand));
            if (scene != null && layer > scene.yMax()) {
                layer = -1;
            }
            refreshSlotOverrides();
        }

        private static List<ItemStack> copyItems(List<ItemStack> stacks) {
            List<ItemStack> result = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                result.add(stack.copy());
            }
            return List.copyOf(result);
        }

        private void bindSlots(List<IRecipeSlotDrawable> slots) {
            materialSlots.clear();
            for (IRecipeSlotDrawable slot : slots) {
                if (slot.getRole() != RecipeIngredientRole.INPUT) {
                    continue;
                }
                slot.getSlotName().ifPresent(name -> {
                    if (!name.startsWith("material_")) {
                        return;
                    }
                    try {
                        materialSlots.put(Integer.parseInt(name.substring("material_".length())), slot);
                    } catch (NumberFormatException ignored) {
                        // Ignore slots not created by this category.
                    }
                });
            }
            refreshSlotOverrides();
        }

        private void refreshSlotOverrides() {
            for (Map.Entry<Integer, IRecipeSlotDrawable> entry : materialSlots.entrySet()) {
                int index = entry.getKey();
                IRecipeSlotDrawable slot = entry.getValue();
                if (index >= 0 && index < materials.size()) {
                    slot.createDisplayOverrides()
                            .addItemStack(materials.get(index).copy());
                } else {
                    slot.createDisplayOverrides().addItemStacks(List.of());
                }
            }
        }

        private void nextExpand() {
            expand = expand >= recipe.definition().getExpandMax()
                    ? recipe.definition().getExpandMin()
                    : expand + 1;
            if (formed) {
                formed = false;
            }
            renderer.resetView();
            rebuild();
        }

        private void nextLayer() {
            int maxLayer = scene == null ? 0 : scene.yMax();
            layer = layer + 1 > maxLayer ? -1 : layer + 1;
            if (formed) {
                formed = false;
                rebuild();
            }
            renderer.resetView();
        }

        private void toggleFormed() {
            formed = !formed;
            renderer.resetView();
            rebuild();
        }

        private List<BlockPos> renderedPositions() {
            if (scene == null) {
                return List.of();
            }
            return layer < 0 ? scene.orderedPositions() : scene.positionsForLayer(layer);
        }

        private NEMultiblockSceneRenderer renderer() {
            return renderer;
        }

        private MultiblockPreviewScene scene() {
            return scene;
        }

        private List<ItemStack> materials() {
            return materials;
        }

        private int expand() {
            return expand;
        }

        private int layer() {
            return layer;
        }

        private boolean formed() {
            return formed;
        }
    }
}
