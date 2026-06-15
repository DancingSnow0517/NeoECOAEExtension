package cn.dancingsnow.neoecoae.client;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class NEClientRecipe {

    private static final Set<RecipeType<?>> supportedRecipeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    private static RecipeMap recipeMap = RecipeMap.EMPTY;

    public static void receivedRecipe(RecipesReceivedEvent event) {
        supportedRecipeTypes.clear();
        supportedRecipeTypes.addAll(event.getRecipeTypes());
        recipeMap = event.getRecipeMap();
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> getSyncedRecipes(RecipeType<T> recipeType) {
        if (supportedRecipeTypes.contains(recipeType)) {
            return recipeMap.byType(recipeType);
        }
        return List.of();
    }
}
