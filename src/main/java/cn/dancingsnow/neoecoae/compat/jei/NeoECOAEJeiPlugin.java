package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.xei.MultiblockInfoRecipe;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import java.util.List;
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
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class NeoECOAEJeiPlugin implements IModPlugin {

    public static final RecipeType<IntegratedWorkingStationRecipe> IWS_RECIPE_TYPE =
            RecipeType.create(NeoECOAE.MOD_ID, "integrated_working_station", IntegratedWorkingStationRecipe.class);

    public static final RecipeType<CoolingRecipe> COOLING_RECIPE_TYPE =
            RecipeType.create(NeoECOAE.MOD_ID, "cooling", CoolingRecipe.class);

    public static final RecipeType<MultiblockInfoRecipe> MULTIBLOCK_RECIPE_TYPE =
            RecipeType.create(NeoECOAE.MOD_ID, "multiblock", MultiblockInfoRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new IntegratedWorkingStationJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(
                new CoolingJeiCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(
                new MultiblockJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                MULTIBLOCK_RECIPE_TYPE,
                NEMultiBlocks.DEFINITIONS.stream()
                        .map(MultiblockInfoRecipe::new)
                        .toList());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<IntegratedWorkingStationRecipe> iwsRecipes =
                minecraft.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get());
        registration.addRecipes(IWS_RECIPE_TYPE, iwsRecipes);

        List<CoolingRecipe> coolingRecipes =
                minecraft.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get());
        registration.addRecipes(COOLING_RECIPE_TYPE, coolingRecipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(NEBlocks.INTEGRATED_WORKING_STATION.asStack(), IWS_RECIPE_TYPE);

        registration.addRecipeCatalyst(NEBlocks.CRAFTING_SYSTEM_L4.asStack(), COOLING_RECIPE_TYPE);
        registration.addRecipeCatalyst(NEBlocks.CRAFTING_SYSTEM_L6.asStack(), COOLING_RECIPE_TYPE);
        registration.addRecipeCatalyst(NEBlocks.CRAFTING_SYSTEM_L9.asStack(), COOLING_RECIPE_TYPE);

        for (var definition : NEMultiBlocks.DEFINITIONS) {
            registration.addRecipeCatalyst(
                    definition.getOwner().value().asItem().getDefaultInstance(), MULTIBLOCK_RECIPE_TYPE);
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ModularUIGuiContainer.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ModularUIGuiContainer screen) {
                return screen.getGuiExtraAreas();
            }
        });
    }
}
