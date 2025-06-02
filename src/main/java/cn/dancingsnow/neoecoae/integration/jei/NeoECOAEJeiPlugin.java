package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
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

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CoolingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        CoolingCategory.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        CoolingCategory.registerRecipeCatalysts(registration);
    }

    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createRecipeHolderType(String name) {
        return RecipeType.createRecipeHolderType(NeoECOAE.id(name));
    }
}
