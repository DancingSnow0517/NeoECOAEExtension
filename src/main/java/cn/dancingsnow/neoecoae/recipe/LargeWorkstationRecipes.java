package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Adapts native workstation recipes for the large workstation runtime. */
public final class LargeWorkstationRecipes {
    private LargeWorkstationRecipes() {}

    public static List<LargeWorkstationRecipe> getAll(Level level) {
        List<LargeWorkstationRecipe> recipes = new ArrayList<>();
        for (IntegratedWorkingStationRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            recipes.add(new LargeWorkstationRecipe(recipe.id(), recipe, recipe.energy(), List.of()));
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return List.copyOf(recipes);
    }

    @Nullable
    public static LargeWorkstationRecipe find(Level level, KeyCounter inputs, KeyCounter outputs) {
        for (LargeWorkstationRecipe recipe : getAll(level)) {
            if (recipe.matches(inputs, outputs)) return recipe;
        }
        return null;
    }
}
