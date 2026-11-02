package cn.dancingsnow.neoecoae.integration.kubejs.recipe;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;

final class KubeSchemaUtil {
    private KubeSchemaUtil() {
    }

    /** Drops an optional key from the recipe JSON when its value is unset or empty (e.g. an empty stack). */
    static <T> void removeIfEmpty(KubeRecipe recipe, RecipeKey<T> key) {
        if (recipe.json == null) return;
        T value = recipe.getValue(key);
        if (value == null || key.component.isEmpty(value)) {
            for (String name : key.names) {
                recipe.json.remove(name);
            }
        }
    }
}
