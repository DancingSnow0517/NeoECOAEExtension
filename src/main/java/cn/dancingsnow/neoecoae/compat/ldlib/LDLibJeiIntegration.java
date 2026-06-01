package cn.dancingsnow.neoecoae.compat.ldlib;

import cn.dancingsnow.neoecoae.compat.jei.MultiblockJeiCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

public final class LDLibJeiIntegration {
    private LDLibJeiIntegration() {
    }

    public static void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MultiblockJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }

    public static void registerJeiCategories(IRecipeCategoryRegistration registration) {
        registerCategories(registration);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        MultiblockJeiCategory.registerRecipes(registration);
    }

    public static void registerJeiRecipes(IRecipeRegistration registration) {
        registerRecipes(registration);
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        MultiblockJeiCategory.registerRecipeCatalysts(registration);
    }

    public static void registerJeiRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registerRecipeCatalysts(registration);
    }
}
