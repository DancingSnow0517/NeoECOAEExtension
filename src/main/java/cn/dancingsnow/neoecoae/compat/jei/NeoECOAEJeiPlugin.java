package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEIntegratedWorkingStationScreen;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * JEI plugin for NeoECOAE — registered in a package NOT excluded by build.gradle.
 */
@JeiPlugin
public final class NeoECOAEJeiPlugin implements IModPlugin {

    public static final RecipeType<IntegratedWorkingStationRecipe> IWS_RECIPE_TYPE =
        RecipeType.create(NeoECOAE.MOD_ID, "integrated_working_station", IntegratedWorkingStationRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new IntegratedWorkingStationJeiCategory(
            registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<IntegratedWorkingStationRecipe> recipes =
            minecraft.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get());

        registration.addRecipes(IWS_RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
            NEBlocks.INTEGRATED_WORKING_STATION.asStack(),
            IWS_RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(NEIntegratedWorkingStationScreen.class,
            new IGuiContainerHandler<>() {
                @Override
                public List<Rect2i> getGuiExtraAreas(NEIntegratedWorkingStationScreen screen) {
                    return screen.getJeiExtraAreas();
                }
            });
    }
}
