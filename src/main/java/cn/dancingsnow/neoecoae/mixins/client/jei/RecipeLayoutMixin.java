package cn.dancingsnow.neoecoae.mixins.client.jei;

import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** JEI 15 has a category-wide size API, but its individual layout rectangles can differ. */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class RecipeLayoutMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lmezz/jei/api/recipe/category/IRecipeCategory;getHeight()I"))
    private static int neoecoae$recipeHeight(IRecipeCategory<?> category, Operation<Integer> original,
        @Local(argsOnly = true) Object recipe) {
        // Optimized JEI releases reuse the category argument slot for the width before this call.
        // The invocation receiver remains the category even after its original local is overwritten.
        int height = original.call(category);
        return category instanceof LargeWorkingStationCategory && recipe instanceof LargeWorkstationRecipe large
            ? LargeWorkstationRecipeDisplay.height(large) : height;
    }
}
