package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

final class LargeWorkstationCoolantLookup {
    private RecipeManager recipes;
    private long generation = Long.MIN_VALUE;
    private final Map<Fluid, Candidate> simple = new IdentityHashMap<>();
    private final List<Candidate> custom = new ArrayList<>();

    @Nullable
    RecipeHolder<CoolingRecipe> find(@Nullable Level level, FluidStack input) {
        if (level == null || input.isEmpty()) return null;
        var manager = level.getRecipeManager();
        long currentGeneration = AE2PatternIntrospection.reloadGeneration();
        if (recipes != manager || generation != currentGeneration) {
            simple.clear();
            custom.clear();
            int order = 0;
            for (var holder : manager.getAllRecipesFor(NERecipeTypes.COOLING.get())) {
                var candidate = new Candidate(holder, order++);
                var ingredient = holder.value().input().ingredient();
                if (ingredient.isSimple()) {
                    for (var stack : ingredient.getStacks()) {
                        simple.merge(stack.getFluid(), candidate, Candidate::better);
                    }
                } else {
                    custom.add(candidate);
                }
            }
            recipes = manager;
            generation = currentGeneration;
        }
        Candidate best = simple.get(input.getFluid());
        for (var candidate : custom) {
            if ((best == null || Candidate.better(best, candidate) == candidate)
                && candidate.holder.value().input().ingredient().test(input)) {
                best = candidate;
            }
        }
        return best == null ? null : best.holder;
    }

    private record Candidate(RecipeHolder<CoolingRecipe> holder, int order) {
        private static Candidate better(Candidate first, Candidate second) {
            int firstTier = first.holder.value().maxOverclock();
            int secondTier = second.holder.value().maxOverclock();
            return firstTier > secondTier || firstTier == secondTier && first.order <= second.order ? first : second;
        }
    }
}
