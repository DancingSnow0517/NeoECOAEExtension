package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.integration.xei.HiddenEasterEggRecipes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.Recipe;

final class HiddenJeiRecipes {
    private HiddenJeiRecipes() {}

    static <T> void hide(IRecipeManager manager, RecipeType<T> type) {
        if (!Recipe.class.isAssignableFrom(type.getRecipeClass())) return;
        var hidden = manager.createRecipeLookup(type).includeHidden().get()
            .filter(recipe -> recipe instanceof Recipe<?> vanilla && HiddenEasterEggRecipes.isHidden(vanilla.getId()))
            .toList();
        if (!hidden.isEmpty()) manager.hideRecipes(type, hidden);
    }
}
