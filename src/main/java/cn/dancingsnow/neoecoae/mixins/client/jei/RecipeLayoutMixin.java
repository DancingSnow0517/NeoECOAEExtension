package cn.dancingsnow.neoecoae.mixins.client.jei;

import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** JEI 19 has a category-wide size API, but its individual layout rectangles can differ. */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class RecipeLayoutMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
        target = "Lmezz/jei/api/recipe/category/IRecipeCategory;getHeight()I"))
    private int neoecoae$recipeHeight(int height, @Local(argsOnly = true) IRecipeCategory<?> category,
        @Local(argsOnly = true) Object recipe) {
        return category instanceof LargeWorkingStationCategory && recipe instanceof LargeWorkstationRecipe large
            ? LargeWorkstationRecipeDisplay.height(large) : height;
    }
}
