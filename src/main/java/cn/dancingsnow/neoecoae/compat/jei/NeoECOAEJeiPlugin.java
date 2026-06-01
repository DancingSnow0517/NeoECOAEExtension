package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.xei.MultiblockInfoRecipe;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NEIntegratedWorkingStationScreen;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@JeiPlugin
public final class NeoECOAEJeiPlugin implements IModPlugin {

    public static final RecipeType<IntegratedWorkingStationRecipe> IWS_RECIPE_TYPE = RecipeType.create(NeoECOAE.MOD_ID,
            "integrated_working_station", IntegratedWorkingStationRecipe.class);

    public static final RecipeType<CoolingRecipe> COOLING_RECIPE_TYPE = RecipeType.create(NeoECOAE.MOD_ID, "cooling",
            CoolingRecipe.class);

    public static final RecipeType<MultiblockInfoRecipe> MULTIBLOCK_RECIPE_TYPE = RecipeType.create(NeoECOAE.MOD_ID,
            "multiblock", MultiblockInfoRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new IntegratedWorkingStationJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CoolingJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        if (hasLDLib1()) {
            invokeLDLib("registerJeiCategories", IRecipeCategoryRegistration.class, registration);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<IntegratedWorkingStationRecipe> iwsRecipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get());
        registration.addRecipes(IWS_RECIPE_TYPE, iwsRecipes);

        List<CoolingRecipe> coolingRecipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.COOLING.get());
        registration.addRecipes(COOLING_RECIPE_TYPE, coolingRecipes);

        if (hasLDLib1()) {
            invokeLDLib("registerJeiRecipes", IRecipeRegistration.class, registration);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                NEBlocks.INTEGRATED_WORKING_STATION.asStack(),
                IWS_RECIPE_TYPE);

        registration.addRecipeCatalyst(
                NEBlocks.CRAFTING_SYSTEM_L4.asStack(),
                COOLING_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NEBlocks.CRAFTING_SYSTEM_L6.asStack(),
                COOLING_RECIPE_TYPE);
        registration.addRecipeCatalyst(
                NEBlocks.CRAFTING_SYSTEM_L9.asStack(),
                COOLING_RECIPE_TYPE);

        if (hasLDLib1()) {
            invokeLDLib("registerJeiRecipeCatalysts", IRecipeCatalystRegistration.class, registration);
        }
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

    private static boolean hasLDLib1() {
        return ModList.get().isLoaded("ldlib");
    }

    private static void invokeLDLib(String methodName, Class<?> parameterType, Object parameter) {
        try {
            Class<?> bridge = Class.forName("cn.dancingsnow.neoecoae.compat.ldlib.LDLibJeiIntegration");
            bridge.getMethod(methodName, parameterType).invoke(null, parameter);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to initialize LDLib1 JEI multiblock integration", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("LDLib1 JEI multiblock integration failed", e.getCause());
        }
    }
}
