package cn.dancingsnow.neoecoae.integration.jei.categories.multiblock;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.client.multiblock.preview.NEMultiblockSceneRenderer;
import cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockPreviewStyle;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiblockInfoRecipe;
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
    private static final int WIDTH = 170;
    private static final int HEIGHT = 190;
    private static final int PADDING = 4;
    private static final int MATERIAL_PAGE_SIZE = 9;

    private static final Rect SCENE = new Rect(PADDING, 28, WIDTH - PADDING * 2, 129);
    private static final Rect LENGTH_BUTTON = new Rect(SCENE.right() - 46, SCENE.y() + 2, 44, 18);
    private static final Rect LAYER_BUTTON = new Rect(LENGTH_BUTTON.x(), LENGTH_BUTTON.bottom(), 44, 18);
    private static final Rect FORMED_BUTTON = new Rect(LAYER_BUTTON.x(), LAYER_BUTTON.bottom(), 44, 18);
    private static final int MATERIALS_X = PADDING;
    private static final int MATERIALS_Y = 163;
    private static final Rect PREVIOUS_PAGE_BUTTON = new Rect(SCENE.right() - 32, SCENE.bottom() - 14, 14, 12);
    private static final Rect NEXT_PAGE_BUTTON = new Rect(SCENE.right() - 16, SCENE.bottom() - 14, 14, 12);

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
        for (int i = 0; i < MATERIAL_PAGE_SIZE; i++) {
            int x = MATERIALS_X + i * MultiblockPreviewStyle.SLOT_SIZE;
            var slot = builder.addInputSlot(x, MATERIALS_Y).setSlotName("material_" + i);
            if (i < materials.size()) {
                slot.addItemStack(materials.get(i).copy());
            }
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
                if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    return false;
                }
                double recipeMouseX = mouseX + SCENE.x();
                double recipeMouseY = mouseY + SCENE.y();
                return !LENGTH_BUTTON.contains(recipeMouseX, recipeMouseY)
                        && !LAYER_BUTTON.contains(recipeMouseX, recipeMouseY)
                        && !FORMED_BUTTON.contains(recipeMouseX, recipeMouseY)
                        && !PREVIOUS_PAGE_BUTTON.contains(recipeMouseX, recipeMouseY)
                        && !NEXT_PAGE_BUTTON.contains(recipeMouseX, recipeMouseY);
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
                state.renderer().rotateFromMouseDrag(dragX, dragY);
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

        MultiblockPreviewStyle.drawPanel(graphics, WIDTH, HEIGHT);
        MultiblockPreviewStyle.drawFittedString(
                graphics, recipe.definition().getName(), 4, 4, WIDTH - 8, MultiblockPreviewStyle.TEXT_COLOR);

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
        drawButton(graphics, LENGTH_BUTTON, "E: " + state.expand(), mouseX, mouseY);
        drawButton(graphics, LAYER_BUTTON, state.layer() < 0 ? "L: *" : "L: " + state.layer(), mouseX, mouseY);
        drawButton(graphics, FORMED_BUTTON, state.formed() ? "F: true" : "F: false", mouseX, mouseY);

        for (int i = 0; i < MATERIAL_PAGE_SIZE; i++) {
            int x = MATERIALS_X + i * MultiblockPreviewStyle.SLOT_SIZE - 1;
            int y = MATERIALS_Y - 1;
            graphics.fill(x, y, x + MultiblockPreviewStyle.SLOT_SIZE, y + MultiblockPreviewStyle.SLOT_SIZE, 0xFF707070);
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + MultiblockPreviewStyle.SLOT_SIZE - 1,
                    y + MultiblockPreviewStyle.SLOT_SIZE - 1,
                    0xFFE8E8E8);
        }
        if (state.materialPages() > 1) {
            drawButton(graphics, PREVIOUS_PAGE_BUTTON, "<", mouseX, mouseY);
            drawButton(graphics, NEXT_PAGE_BUTTON, ">", mouseX, mouseY);
            String page = (state.materialPage() + 1) + "/" + state.materialPages();
            graphics.drawString(
                    font,
                    page,
                    PREVIOUS_PAGE_BUTTON.x() - 4 - font.width(page),
                    PREVIOUS_PAGE_BUTTON.y() + 2,
                    MultiblockPreviewStyle.TEXT_COLOR,
                    false);
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
        } else if (state.materialPages() > 1 && PREVIOUS_PAGE_BUTTON.contains(mouseX, mouseY)) {
            tooltip.add(Component.translatable("emi.neoecoae.multiblock.previous_page"));
        } else if (state.materialPages() > 1 && NEXT_PAGE_BUTTON.contains(mouseX, mouseY)) {
            tooltip.add(Component.translatable("emi.neoecoae.multiblock.next_page"));
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
        if (state.materialPages() > 1 && PREVIOUS_PAGE_BUTTON.contains(mouseX, mouseY)) {
            state.previousMaterialsPage();
            return true;
        }
        if (state.materialPages() > 1 && NEXT_PAGE_BUTTON.contains(mouseX, mouseY)) {
            state.nextMaterialsPage();
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

    private static void drawButton(GuiGraphics graphics, Rect rect, String text, double mouseX, double mouseY) {
        MultiblockPreviewStyle.drawButton(
                graphics, rect.x(), rect.y(), rect.width(), rect.height(), text, mouseX, mouseY);
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
        private int materialPage;
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
            if (materialPage >= materialPages()) {
                materialPage = Math.max(0, materialPages() - 1);
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
                int index = materialPage * MATERIAL_PAGE_SIZE + entry.getKey();
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
            rebuild();
        }

        private void nextLayer() {
            int maxLayer = scene == null ? 0 : scene.yMax();
            layer = layer + 1 > maxLayer ? -1 : layer + 1;
            if (formed) {
                formed = false;
                rebuild();
            }
        }

        private void toggleFormed() {
            formed = !formed;
            rebuild();
        }

        private int materialPages() {
            return Math.max(1, (materials.size() + MATERIAL_PAGE_SIZE - 1) / MATERIAL_PAGE_SIZE);
        }

        private void previousMaterialsPage() {
            materialPage = materialPage <= 0 ? materialPages() - 1 : materialPage - 1;
            refreshSlotOverrides();
        }

        private void nextMaterialsPage() {
            materialPage = materialPage + 1 >= materialPages() ? 0 : materialPage + 1;
            refreshSlotOverrides();
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

        private int materialPage() {
            return materialPage;
        }
    }
}
