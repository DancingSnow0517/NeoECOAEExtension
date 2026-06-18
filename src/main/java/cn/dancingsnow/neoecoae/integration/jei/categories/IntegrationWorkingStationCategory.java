package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.client.NEClientRecipe;
import cn.dancingsnow.neoecoae.integration.jei.NEJeiRecipeType;
import cn.dancingsnow.neoecoae.integration.xei.recipe.IntegratedWorkingStationRecipeWrapper;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

public class IntegrationWorkingStationCategory extends ModularUIRecipeCategory<RecipeHolder<IntegratedWorkingStationRecipe>> {
    private final IDrawable icon;
    private final Component title;


    public IntegrationWorkingStationCategory(IGuiHelper helper) {
        super(recipe -> new IntegratedWorkingStationRecipeWrapper(recipe).createModularUI());
        icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        title = Component.translatable("category.neoecoae.integrated_working_station");
    }

    @Override
    public IRecipeType<RecipeHolder<IntegratedWorkingStationRecipe>> getRecipeType() {
        return NEJeiRecipeType.INTEGRATED_WORKING_STATION;
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
        return 168;
    }

    @Override
    public int getHeight() {
        return 75;
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NEJeiRecipeType.INTEGRATED_WORKING_STATION,
            List.copyOf(NEClientRecipe.getSyncedRecipes(NERecipeTypes.INTEGRATED_WORKING_STATION.get()))
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(NEJeiRecipeType.INTEGRATED_WORKING_STATION, NEBlocks.INTEGRATED_WORKING_STATION);
    }
}
