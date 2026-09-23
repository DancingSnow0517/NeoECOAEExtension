package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;

/** Separate category makes the formed-multiblock restriction visible during recipe lookup. */
public final class LargeWorkingStationCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private final IntegrationWorkingStationCategory layout;

    public LargeWorkingStationCategory(IGuiHelper helper) { layout = new IntegrationWorkingStationCategory(helper); }

    @Override public RecipeType<LargeWorkstationRecipe> getRecipeType() { return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE; }
    @Override public Component getTitle() { return Component.translatable("category.neoecoae.large_integrated_working_station"); }
    @Override public IDrawable getIcon() { return layout.getIcon(); }
    @Override public int getWidth() { return layout.getWidth(); }
    @Override public int getHeight() { return 103; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        layout.setRecipe(builder, new RecipeHolder<>(recipe.id(), recipe.display()), focuses);
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        layout.draw(new RecipeHolder<>(recipe.id(), recipe.display()), slots, graphics, x, y);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        builder.addText(Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy", recipe.energy()), 144, 12)
            .setPosition(24, 66).setColor(0x403e53);
        if (!recipe.extraInputs().isEmpty()) {
            var extra = recipe.extraInputs().getFirst();
            builder.addText(Component.translatable("gui.neoecoae.large_integrated_working_station.lightning_cost",
                extra.amount(), extra.what().getDisplayName()), 168, 26).setPosition(0, 78).setColor(0x403e53);
        }
    }
}
