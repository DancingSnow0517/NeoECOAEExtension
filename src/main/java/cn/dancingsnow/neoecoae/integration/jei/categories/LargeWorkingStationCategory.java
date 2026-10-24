package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.Arrays;

/** Separate category makes the formed-multiblock restriction visible during recipe lookup. */
public final class LargeWorkingStationCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private static final int INFO_Y = 116;
    private static final int INFO_HEIGHT = 36;

    private final IntegrationWorkingStationCategory layout;

    public LargeWorkingStationCategory(IGuiHelper helper) { layout = new IntegrationWorkingStationCategory(helper, true); }

    @Override public RecipeType<LargeWorkstationRecipe> getRecipeType() { return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE; }
    @Override public Component getTitle() { return Component.translatable("category.neoecoae.large_integrated_working_station"); }
    @Override public IDrawable getIcon() { return layout.getIcon(); }
    @Override public int getWidth() { return layout.getWidth(); }
    @Override public int getHeight() { return INFO_Y + INFO_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        layout.setRecipe(builder, recipe.display(), focuses);
        int slot = 0;
        for (var fluid : recipe.additionalFluidInputs()) {
            builder.addInputSlot(4 + slot++ * 18, 76)
                .addIngredients(ForgeTypes.FLUID_STACK, Arrays.asList(fluid.getFluids()));
        }
        for (var extra : recipe.extraInputs()) {
            builder.addInputSlot(4 + slot++ * 18, 76).addItemStack(GenericStack.wrapInItemStack(extra.what(), extra.amount()));
        }
        slot = 0;
        for (var output : recipe.additionalOutputs()) {
            var outputSlot = builder.addOutputSlot(4 + slot++ * 18, 96);
            if (output.what() instanceof AEItemKey item) outputSlot.addItemStack(item.toStack(Math.toIntExact(output.amount())));
            else if (output.what() instanceof AEFluidKey fluid) outputSlot.addIngredients(ForgeTypes.FLUID_STACK,
                java.util.List.of(fluid.toStack(Math.toIntExact(output.amount()))));
            else outputSlot.addItemStack(GenericStack.wrapInItemStack(output.what(), output.amount()));
        }
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        layout.draw(recipe.display(), slots, graphics, x, y);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        var info = new ArrayList<FormattedText>();
        info.add(Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy", recipe.energy()));
        for (var extra : recipe.extraInputs()) {
            info.add(Component.translatable("gui.neoecoae.large_integrated_working_station.lightning_cost",
                extra.amount(), extra.what().getDisplayName()));
        }
        info.add(recipe.sourceTypeDescription());
        builder.addText(info, getWidth(), INFO_HEIGHT)
            .setPosition(0, INFO_Y).setLineSpacing(0).setColor(0x403e53);
    }
}
