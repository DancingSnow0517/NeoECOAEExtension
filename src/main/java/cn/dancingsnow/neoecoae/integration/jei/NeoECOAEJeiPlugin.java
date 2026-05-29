package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.jei.categories.CoolingCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.IntegrationWorkingStationCategory;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.mojang.logging.LogUtils;
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
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    public static final RecipeType<RecipeHolder<CoolingRecipe>> COOLING_TYPE = createRecipeHolderType("cooling");
    public static final RecipeType<RecipeHolder<IntegratedWorkingStationRecipe>> INTEGRATED_WORKING_STATION_TYPE = createRecipeHolderType("integrated_working_station");

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        LOGGER.info("JEI registerCategories called");
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CoolingCategory(guiHelper));
        registration.addRecipeCategories(new IntegrationWorkingStationCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        LOGGER.info("JEI registerRecipes called");
        CoolingCategory.registerRecipes(registration);
        IntegrationWorkingStationCategory.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        LOGGER.info("JEI registerRecipeCatalysts called");
        CoolingCategory.registerRecipeCatalysts(registration);
        IntegrationWorkingStationCategory.registerRecipeCatalysts(registration);
    }

    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createRecipeHolderType(String name) {
        return RecipeType.createRecipeHolderType(NeoECOAE.id(name));
    }
}
