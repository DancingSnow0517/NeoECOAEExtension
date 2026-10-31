package cn.dancingsnow.neoecoae.mixins.client.jei;

import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Keep JEI's transfer and bookmark buttons aligned with the actual recipe rectangle. */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.layout.builder.RecipeLayoutBuilder", remap = false)
public abstract class RecipeLayoutBuilderMixin {
    @Shadow @Final private Object recipe;

    @WrapOperation(method = "getRecipeTransferButtonPosition", at = @At(value = "INVOKE",
        target = "Lmezz/jei/api/recipe/category/IRecipeCategory;getHeight()I"))
    private int neoecoae$recipeHeight(IRecipeCategory<?> category, Operation<Integer> original) {
        int height = original.call(category);
        return category instanceof LargeWorkingStationCategory && recipe instanceof LargeWorkstationRecipe large
            ? LargeWorkstationRecipeDisplay.height(large) : height;
    }
}
