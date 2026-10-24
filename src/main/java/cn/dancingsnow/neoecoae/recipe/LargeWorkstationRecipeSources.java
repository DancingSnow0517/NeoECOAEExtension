package cn.dancingsnow.neoecoae.recipe;

import java.util.Set;

/** Module rights remain restricted to the existing Forge integration families. */
final class LargeWorkstationRecipeSources {
    private LargeWorkstationRecipeSources() {}

    static LargeWorkstationRecipeGroup.Variant direct(LargeWorkstationRecipe recipe) {
        var module = LargeWorkstationModule.fromSourceType(recipe.sourceType());
        return new LargeWorkstationRecipeGroup.Variant(recipe, recipe.id(), recipe.sourceType(),
            module == LargeWorkstationModule.REGULAR ? Set.of() : Set.of(module), module == LargeWorkstationModule.REGULAR, false);
    }

}
