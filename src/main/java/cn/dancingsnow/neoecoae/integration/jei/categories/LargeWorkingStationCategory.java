package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.jei.WorkstationItemStackRenderer;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.integration.recipe.WorkstationRecipeInfoLayout;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Separate category makes the formed-multiblock restriction visible during recipe lookup. */
public final class LargeWorkingStationCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private final IntegrationWorkingStationCategory layout;

    public LargeWorkingStationCategory(IGuiHelper helper) { layout = new IntegrationWorkingStationCategory(helper, true); }

    @Override public RecipeType<LargeWorkstationRecipe> getRecipeType() { return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE; }
    @Override public Component getTitle() { return Component.translatable("category.neoecoae.large_integrated_working_station"); }
    @Override public IDrawable getIcon() { return layout.getIcon(); }
    @Override public int getWidth() { return layout.getWidth(); }
    // JEI 15 layout hooks below use each recipe's actual information height.
    @Override public int getHeight() { return LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        layout.setRecipe(builder, recipe.display(), focuses);
        int slot = 0;
        for (var fluid : recipe.additionalFluidInputs()) {
            builder.addInputSlot(LargeWorkstationRecipeDisplay.slotX(slot), LargeWorkstationRecipeDisplay.slotY(recipe, slot))
                .addIngredients(ForgeTypes.FLUID_STACK, Arrays.asList(fluid.getFluids()));
            slot++;
        }
        for (var extra : recipe.extraInputs()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .addItemStack(GenericStack.wrapInItemStack(extra.what(), extra.amount()));
        }
        var cards = LargeWorkstationRecipeDisplay.cards(recipe);
        var infoLayout = LargeWorkstationRecipeDisplay.layout(recipe);
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            builder.addSlot(RecipeIngredientRole.CATALYST, infoLayout.cardX(i), infoLayout.cardY(i))
                .addItemStack(card.stack()).addRichTooltipCallback((view, tooltip) -> tooltip.addAll(card.tooltip()));
        }
        slot = 0;
        for (var output : recipe.additionalOutputs()) {
            var outputSlot = builder.addOutputSlot(4 + slot % 9 * 18,
                LargeWorkstationRecipeDisplay.outputY(recipe) + slot / 9 * 18);
            slot++;
            if (output.what() instanceof AEItemKey item) outputSlot.addItemStack(item.toStack(Math.toIntExact(output.amount())));
            else if (output.what() instanceof AEFluidKey fluid) outputSlot.addIngredients(ForgeTypes.FLUID_STACK,
                java.util.List.of(fluid.toStack(Math.toIntExact(output.amount()))));
            else outputSlot.addItemStack(GenericStack.wrapInItemStack(output.what(), output.amount()));
            WorkstationItemStackRenderer.configure(outputSlot);
        }
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        layout.draw(recipe.display(), slots, graphics, x, y);
        LargeWorkstationRecipeDisplay.drawInfo(graphics, Minecraft.getInstance().font, recipe);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LargeWorkstationRecipe recipe, IRecipeSlotsView slots,
                                            double mouseX, double mouseY) {
        tooltip.addAll(LargeWorkstationRecipeDisplay.tooltip(recipe, mouseX, mouseY));
    }
}
