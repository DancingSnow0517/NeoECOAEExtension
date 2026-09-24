package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LargeWorkingStationJeiCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private final IntegratedWorkingStationJeiCategory layout;

    public LargeWorkingStationJeiCategory(IGuiHelper helper) {
        layout = new IntegratedWorkingStationJeiCategory(helper);
    }

    @Override
    public RecipeType<LargeWorkstationRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.neoecoae.large_integrated_working_station");
    }

    @Override
    public IDrawable getIcon() {
        return layout.getIcon();
    }

    @Override
    public int getWidth() {
        return layout.getWidth();
    }

    @Override
    public int getHeight() {
        return 101;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        layout.setRecipe(builder, recipe.display(), focuses);
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        layout.drawLayout(recipe.display(), slots, graphics, false);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
                Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy", recipe.energy()),
                36, 64, 0xFF404040, false);
        if (!recipe.extraInputs().isEmpty()) {
            var extra = recipe.extraInputs().get(0);
            graphics.drawString(font,
                    Component.translatable("gui.neoecoae.large_integrated_working_station.lightning_cost",
                            extra.amount(), extra.what().getDisplayName()),
                    8, 79, 0xFF404040, false);
        }
    }
}
