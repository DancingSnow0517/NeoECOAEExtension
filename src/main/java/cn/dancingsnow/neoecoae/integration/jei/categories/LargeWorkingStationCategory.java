package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Arrays;

/** Separate category makes the formed-multiblock restriction visible during recipe lookup. */
public final class LargeWorkingStationCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private final IntegrationWorkingStationCategory layout;
    private final int height;

    public LargeWorkingStationCategory(IGuiHelper helper) {
        layout = new IntegrationWorkingStationCategory(helper, true);
        var level = Minecraft.getInstance().level;
        height = level == null ? LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT
            : LargeWorkstationRecipes.getAll(level).stream()
                .mapToInt(recipe -> LargeWorkstationRecipeDisplay.height(recipe.additionalFluidInputs().size(), recipe.additionalOutputs().size()))
                .max().orElse(LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT);
    }

    @Override public RecipeType<LargeWorkstationRecipe> getRecipeType() { return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE; }
    @Override public Component getTitle() { return Component.translatable("category.neoecoae.large_integrated_working_station"); }
    @Override public IDrawable getIcon() { return layout.getIcon(); }
    @Override public int getWidth() { return layout.getWidth(); }
    @Override public int getHeight() { return height; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        layout.setRecipe(builder, new RecipeHolder<>(recipe.id(), recipe.display()), focuses);
        int slot = 0;
        for (var fluid : recipe.additionalFluidInputs()) {
            builder.addInputSlot(LargeWorkstationRecipeDisplay.slotX(slot), LargeWorkstationRecipeDisplay.slotY(slot++))
                .setStandardSlotBackground()
                .addIngredients(NeoForgeTypes.FLUID_STACK, Arrays.asList(fluid.getFluids()));
        }
        for (var extra : recipe.extraInputs()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .addItemStack(GenericStack.wrapInItemStack(extra.what(), extra.amount()));
        }
        slot = 0;
        for (var output : recipe.additionalOutputs()) {
            var outputSlot = builder.addOutputSlot(LargeWorkstationRecipeDisplay.slotX(slot),
                LargeWorkstationRecipeDisplay.slotY(slot++) + LargeWorkstationRecipeDisplay.rows(recipe.additionalFluidInputs().size()) * 18)
                .setStandardSlotBackground();
            if (output.what() instanceof AEItemKey item) outputSlot.addItemStack(item.toStack(Math.toIntExact(output.amount())));
            else if (output.what() instanceof AEFluidKey fluid) outputSlot.addIngredients(NeoForgeTypes.FLUID_STACK,
                java.util.List.of(fluid.toStack(Math.toIntExact(output.amount()))));
            else outputSlot.addItemStack(GenericStack.wrapInItemStack(output.what(), output.amount()));
        }
        var card = LargeWorkstationRecipeDisplay.upgradeCard(recipe);
        if (!card.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, LargeWorkstationRecipeDisplay.CARD_X, LargeWorkstationRecipeDisplay.CARD_Y)
                .addItemStack(card)
                .addRichTooltipCallback((view, tooltip) -> tooltip.addAll(LargeWorkstationRecipeDisplay.moduleTooltip(recipe)));
        }
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        layout.draw(new RecipeHolder<>(recipe.id(), recipe.display()), slots, graphics, x, y);
        LargeWorkstationRecipeDisplay.drawInfo(graphics, recipe);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LargeWorkstationRecipe recipe, IRecipeSlotsView slots, double x, double y) {
        if (x >= LargeWorkstationRecipeDisplay.INFO_X && x < LargeWorkstationRecipeDisplay.CARD_X - 2
            && y >= LargeWorkstationRecipeDisplay.INFO_Y
            && y < LargeWorkstationRecipeDisplay.INFO_Y + LargeWorkstationRecipeDisplay.INFO_HEIGHT) {
            tooltip.addAll(LargeWorkstationRecipeDisplay.infoTooltip(recipe));
        }
    }
}
