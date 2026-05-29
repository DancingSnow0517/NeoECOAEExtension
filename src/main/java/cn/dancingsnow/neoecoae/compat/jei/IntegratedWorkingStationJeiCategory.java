package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/**
 * JEI recipe category for the ECO Integrated Working Station.
 * Background and progress bar textures match the 1.21.1 reference implementation.
 */
public class IntegratedWorkingStationJeiCategory
    implements IRecipeCategory<IntegratedWorkingStationRecipe> {

    private static final ResourceLocation TEX_BG =
        NeoECOAE.id("textures/gui/jei/integration_working_station.png");
    private static final ResourceLocation TEX_PROGRESS =
        NeoECOAE.id("textures/gui/jei/progress_bar.png");

    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawableAnimated progress;
    private final Component title;

    public IntegratedWorkingStationJeiCategory(IGuiHelper helper) {
        this.title = Component.translatable("category.neoecoae.integrated_working_station");
        this.icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        this.background = helper.drawableBuilder(TEX_BG, 0, 0, 168, 75)
            .setTextureSize(168, 75)
            .build();
        this.progress = helper.drawableBuilder(TEX_PROGRESS, 0, 0, 6, 18)
            .setTextureSize(6, 18)
            .buildAnimated(100, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public RecipeType<IntegratedWorkingStationRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.IWS_RECIPE_TYPE;
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
        return background.getWidth(); // 168
    }

    @Override
    public int getHeight() {
        return background.getHeight(); // 75
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                           IntegratedWorkingStationRecipe recipe,
                           IFocusGroup focuses) {

        // ── Input fluid (left side, matches 1.21.1: x=5, y=9, 16×58) ──
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            FluidStack[] rawFluids = inputFluid.getFluids();
            if (rawFluids != null && rawFluids.length > 0) {
                List<FluidStack> stacks = new java.util.ArrayList<>();
                for (FluidStack fs : rawFluids) {
                    FluidStack copy = fs.copy();
                    copy.setAmount(inputFluid.amount());
                    stacks.add(copy);
                }
                builder.addInputSlot(5, 9)
                    .addIngredients(ForgeTypes.FLUID_STACK, stacks)
                    .setFluidRenderer(16000, false, 16, 58);
            }
        }

        // ── Input items (3×3 grid, matches 1.21.1: x=38, y=12, 18px spacing) ──
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            if (input.ingredient().isEmpty()) {
                continue;
            }
            int col = i % 3;
            int row = i / 3;
            int x = 38 + col * 18;
            int y = 12 + row * 18;

            ItemStack[] rawStacks = input.ingredient().getItems();
            List<ItemStack> stacks = new java.util.ArrayList<>();
            for (ItemStack raw : rawStacks) {
                ItemStack copy = raw.copy();
                copy.setCount(input.count());
                stacks.add(copy);
            }

            builder.addInputSlot(x, y)
                .addIngredients(VanillaTypes.ITEM_STACK, stacks);
        }

        // ── Output item (matches 1.21.1: x=114, y=31) ──
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            builder.addOutputSlot(114, 31)
                .addItemStack(itemOutput.copy());
        }

        // ── Output fluid (matches 1.21.1: x=147, y=9, 16×58) ──
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            builder.addOutputSlot(147, 9)
                .addIngredient(ForgeTypes.FLUID_STACK, fluidOutput.copy())
                .setFluidRenderer(16000, false, 16, 58);
        }
    }

    @Override
    public void draw(IntegratedWorkingStationRecipe recipe,
                      IRecipeSlotsView slots, GuiGraphics g,
                      double mouseX, double mouseY) {
        // Draw the JEI background (168×75 custom texture)
        background.draw(g);
        // Draw the animated progress bar (matches 1.21.1: x=136, y=30)
        progress.draw(g, 136, 30);
        // Energy text (matches 1.21.1: x=24, y=66)
        Component energyText = Component.translatable(
            "gui.neoecoae.integrated_working_station.energy",
            recipe.energy() / 1000);
        g.drawString(Minecraft.getInstance().font, energyText, 24, 66, 0xFF403E53, false);
    }
}
