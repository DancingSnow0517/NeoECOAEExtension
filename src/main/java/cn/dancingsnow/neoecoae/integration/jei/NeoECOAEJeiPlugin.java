package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.jei.categories.CoolingCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.IntegrationWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.multiblock.MultiBlockInfoCategory;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public class NeoECOAEJeiPlugin implements IModPlugin {
    public static final RecipeType<RecipeHolder<CoolingRecipe>> COOLING_TYPE = createRecipeHolderType("cooling");
    public static final RecipeType<RecipeHolder<IntegratedWorkingStationRecipe>> INTEGRATED_WORKING_STATION_TYPE = createRecipeHolderType("integrated_working_station");

    public static final RecipeType<MultiBlockInfoWrapper> MULTIBLOCK_TYPE = new RecipeType<>(
        NeoECOAE.id("multiblock"),
        MultiBlockInfoWrapper.class
    );

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CoolingCategory(guiHelper));
        registration.addRecipeCategories(new MultiBlockInfoCategory(guiHelper));
        registration.addRecipeCategories(new IntegrationWorkingStationCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        CoolingCategory.registerRecipes(registration);
        MultiBlockInfoCategory.registerRecipes(registration);
        IntegrationWorkingStationCategory.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        CoolingCategory.registerRecipeCatalysts(registration);
        MultiBlockInfoCategory.registerRecipeCatalysts(registration);
        IntegrationWorkingStationCategory.registerRecipeCatalysts(registration);
    }

    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createRecipeHolderType(String name) {
        return RecipeType.createRecipeHolderType(NeoECOAE.id(name));
    }
}
