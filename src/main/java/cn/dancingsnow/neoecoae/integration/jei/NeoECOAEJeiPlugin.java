package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.jei.categories.CoolingCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.IntegrationWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.multiblock.MultiBlockInfoCategory;
import cn.dancingsnow.neoecoae.integration.jei.multiblock.MultiBlockRecipeTransferHandler;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.client.Minecraft;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

@JeiPlugin
public class NeoECOAEJeiPlugin implements IModPlugin {
    @Nullable
    private static volatile IJeiRuntime runtime;
    public static final RecipeType<CoolingRecipe> COOLING_TYPE = new RecipeType<>(NeoECOAE.id("cooling"), CoolingRecipe.class);
    public static final RecipeType<IntegratedWorkingStationRecipe> INTEGRATED_WORKING_STATION_TYPE = new RecipeType<>(NeoECOAE.id("integrated_working_station"), IntegratedWorkingStationRecipe.class);
    public static final RecipeType<LargeWorkstationRecipe> LARGE_WORKING_STATION_TYPE = new RecipeType<>(
        NeoECOAE.id("large_integrated_working_station"), LargeWorkstationRecipe.class);

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
        registration.addRecipeCategories(new LargeWorkingStationCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // JEI guards startup with a client level. Bind this whole registration to that one world and recipe set.
        var level = Objects.requireNonNull(Minecraft.getInstance().level,
            "JEI recipe registration requires an active client level");
        var recipes = level.getRecipeManager();
        CoolingCategory.registerRecipes(registration, recipes);
        MultiBlockInfoCategory.registerRecipes(registration);
        IntegrationWorkingStationCategory.registerRecipes(registration, recipes);
        registration.addRecipes(LARGE_WORKING_STATION_TYPE, LargeWorkstationRecipes.getAll(level));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        CoolingCategory.registerRecipeCatalysts(registration);
        MultiBlockInfoCategory.registerRecipeCatalysts(registration);
        IntegrationWorkingStationCategory.registerRecipeCatalysts(registration);
        registration.addRecipeCatalysts(LARGE_WORKING_STATION_TYPE,
            NEBlocks.INTEGRATED_WORKING_STATION, NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        NeoECOAEJeiPlugin.runtime = runtime;
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get()
            .forEach(category -> HiddenJeiRecipes.hide(manager, category.getRecipeType()));
    }

    public static @Nullable IJeiRuntime runtime() { return runtime; }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new MultiBlockRecipeTransferHandler(), MULTIBLOCK_TYPE);
    }

}
