package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.mixins.minecraft.RecipeManagerAccessor;
import java.util.function.BooleanSupplier;
import java.util.Objects;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/** Loads supported recipes by default; installed cards select each workstation's available recipes. */
public final class LargeWorkstationRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeWorkstationRecipes.class);
    private static final Set<String> COMPAT_TYPES = Set.of(
        "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation",
        "advanced_ae:reaction");
    private static BooleanSupplier ignoreUpgradeCards = () -> false;

    public static void configure(BooleanSupplier supplier) { ignoreUpgradeCards = Objects.requireNonNull(supplier); }

    private static final Map<RecipeManager, Cache> CACHE = new WeakHashMap<>();
    // Lightning recipes have explicit priority. AE cost, lightning amount and ID break ties.
    private static final Comparator<LargeWorkstationRecipe> COST_ORDER = Comparator
        .comparing((LargeWorkstationRecipe recipe) -> recipe.extraInputs().stream().noneMatch(LargeWorkstationRecipes::isLightning))
        .thenComparingLong(LargeWorkstationRecipe::energy)
        .thenComparingLong(recipe -> recipe.extraInputs().stream().mapToLong(GenericStack::amount).sum())
        .thenComparing(recipe -> recipe.id().toString());

    private LargeWorkstationRecipes() {}

    public static List<LargeWorkstationRecipe> getAll(Level level) {
        return getAll(level.getRecipeManager(), level.registryAccess());
    }

    public static synchronized List<LargeWorkstationRecipe> getAll(RecipeManager manager, RegistryAccess registries) {
        Map<ResourceLocation, Recipe<?>> sourceMap = manager instanceof RecipeManagerAccessor accessor
            ? accessor.neoecoae$recipesByName() : null;
        Collection<Recipe<?>> sources = sourceMap == null ? manager.getRecipes() : sourceMap.values();
        var previous = CACHE.get(manager);
        if (previous != null && (sourceMap != null ? previous.generation == sourceMap : sameSources(previous.sources, sources))) return previous.recipes;
        List<LargeWorkstationRecipe> recipes = new ArrayList<>();
        DynamicOps<JsonElement> ops = JsonOps.INSTANCE;
        for (var source : sources) {
            try {
                if (source instanceof ExtendedCrystalAssemblerRecipe extended) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), extended.display(), extended.display().energy(),
                        List.of(), ExtendedCrystalAssemblerRecipe.SOURCE_TYPE));
                } else if (source instanceof LargeIntegratedWorkingStationRecipe large) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), large.display(), large.display().energy(), List.of()));
                } else if (source instanceof IntegratedWorkingStationRecipe nativeRecipe) {
                    recipes.add(new LargeWorkstationRecipe(source.getId(), nativeRecipe, nativeRecipe.energy(), List.of()));
                } else {
                    String type = typeId(source.getType());
                    if (!COMPAT_TYPES.contains(type)) continue;
                    recipes.add(LargeWorkstationRecipeAdapters.adapt(source, type));
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Cannot adapt large workstation recipe {}", source.getId(), failure);
            }
        }
        var result = deduplicate(recipes, ops);
        Map<ResourceLocation, LargeWorkstationRecipe> recipesById = new HashMap<>();
        for (var recipe : recipes) recipesById.put(recipe.id(), recipe);
        CACHE.put(manager, new Cache(sourceMap, List.copyOf(sources), result, Map.copyOf(recipesById),
            new EnumMap<>(LargeWorkstationModule.class)));
        return result;
    }

    public static List<LargeWorkstationRecipe> getAvailable(Level level, LargeWorkstationModule module) {
        return getAvailable(level.getRecipeManager(), level.registryAccess(), module);
    }

    static synchronized List<LargeWorkstationRecipe> getAvailable(RecipeManager manager,
            RegistryAccess registries, LargeWorkstationModule module) {
        getAll(manager, registries);
        Cache cache = CACHE.get(manager);
        if (ignoreUpgradeCards.getAsBoolean()) return cache.recipes;
        return cache.moduleRecipes.computeIfAbsent(module, installed -> selectForModule(
            cache.recipesById.values(), installed, JsonOps.INSTANCE));
    }

    /** Filter before deduplication: a locked cheaper adapter must not hide an unlocked native recipe. */
    static List<LargeWorkstationRecipe> selectForModule(Collection<LargeWorkstationRecipe> recipes,
            LargeWorkstationModule module, DynamicOps<JsonElement> ops) {
        return deduplicate(recipes.stream().filter(recipe -> module.unlocks(recipe.sourceType())).toList(), ops);
    }

    /** Keeps original recipes addressable for batches saved before equivalent recipes were merged. */
    @Nullable
    public static LargeWorkstationRecipe getById(Level level, ResourceLocation id) {
        return getById(level.getRecipeManager(), level.registryAccess(), id);
    }

    @Nullable
    static synchronized LargeWorkstationRecipe getById(RecipeManager manager, RegistryAccess registries, ResourceLocation id) {
        getAll(manager, registries);
        return CACHE.get(manager).recipesById.get(id);
    }

    /** Material inputs and outputs define equivalence; source mod and processing costs do not. */
    static List<LargeWorkstationRecipe> deduplicate(List<LargeWorkstationRecipe> recipes, DynamicOps<JsonElement> ops) {
        Map<MaterialContract, LargeWorkstationRecipe> unique = new HashMap<>();
        List<LargeWorkstationRecipe> result = new ArrayList<>();
        for (var recipe : recipes) {
            try {
                var display = recipe.display();
                Map<Object, Long> items = new HashMap<>();
                for (var input : display.inputItems()) {
                    Object predicate = itemPredicate(input.ingredient(), ops);
                    items.merge(predicate, (long) input.count(), Math::addExact);
                }
                var fluid = display.inputFluid();
                Map<AEKey, Long> resources = new HashMap<>();
                for (var extra : recipe.extraInputs()) {
                    if (!isLightning(extra)) resources.merge(extra.what(), extra.amount(), Math::addExact);
                }
                var contract = new MaterialContract(Map.copyOf(items), fluid.ingredient().isEmpty() ? null
                    : new FluidInput(fluidPredicate(fluid.ingredient(), ops), fluid.amount()),
                    Map.copyOf(resources),
                    display.hasItemOutput() ? new GenericStack(AEItemKey.of(display.itemOutput()), display.itemOutput().getCount()) : null,
                    display.hasFluidOutput() ? new GenericStack(AEFluidKey.of(display.fluidOutput()), display.fluidOutput().getAmount()) : null);
                unique.merge(contract, recipe, (first, second) -> COST_ORDER.compare(first, second) <= 0 ? first : second);
            } catch (RuntimeException failure) {
                // Unknown predicates remain available rather than being merged by their display stacks.
                LOGGER.warn("Cannot compare large workstation recipe {} for duplicates", recipe.id(), failure);
                result.add(recipe);
            }
        }
        result.addAll(unique.values());
        result.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return List.copyOf(result);
    }

    private static Object itemPredicate(Ingredient ingredient, DynamicOps<JsonElement> ops) {
        if (ingredient.isSimple() && ingredient.getItems().length > 0) {
            return Arrays.stream(ingredient.getItems()).map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .collect(Collectors.toUnmodifiableSet());
        }
        // Unknown custom predicates retain their complete JSON rather than being merged by display stacks.
        return ingredient.toJson();
    }

    private static Object fluidPredicate(FluidIngredient ingredient, DynamicOps<JsonElement> ops) {
        if (ingredient.isSimple() && ingredient.getStacks().length > 0) {
            return Arrays.stream(ingredient.getStacks()).map(stack -> BuiltInRegistries.FLUID.getKey(stack.getFluid()))
                .collect(Collectors.toUnmodifiableSet());
        }
        return ingredient.toJson();
    }

    private static boolean sameSources(Collection<Recipe<?>> previous, Collection<Recipe<?>> current) {
        if (previous.size() != current.size()) return false;
        Map<ResourceLocation, Recipe<?>> byId = new HashMap<>();
        for (var recipe : previous) byId.put(recipe.getId(), recipe);
        return current.stream().allMatch(recipe -> byId.get(recipe.getId()) == recipe);
    }

    private record FluidInput(Object predicate, long amount) {}
    private record MaterialContract(Map<Object, Long> items, @Nullable FluidInput fluid, Map<AEKey, Long> resources,
                                    @Nullable GenericStack itemOutput, @Nullable GenericStack fluidOutput) {}

    private static boolean isLightning(GenericStack stack) {
        return stack.what().getType().getId().getNamespace().equals("ae2lt");
    }

    @Nullable
    public static LargeWorkstationRecipe find(Level level, KeyCounter inputs, KeyCounter outputs) {
        return find(getAll(level), inputs, outputs);
    }

    private static LargeWorkstationRecipe find(List<LargeWorkstationRecipe> recipes, KeyCounter inputs, KeyCounter outputs) {
        LargeWorkstationRecipe fallback = null;
        for (var recipe : recipes) {
            if (!recipe.matches(inputs, outputs)) continue;
            // Distinct ingredient predicates can still overlap for one concrete pattern.
            // Duplicate material contracts have already been resolved by COST_ORDER.
            if (recipe.extraInputs().isEmpty()) return recipe;
            if (fallback == null) fallback = recipe;
        }
        return fallback;
    }

    /** Prefer an exact recipe before interpreting an encoded pattern as multiple recipe operations. */
    @Nullable
    public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs) {
        return findScaled(getAll(level), inputs, outputs);
    }

    @Nullable
    public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs, LargeWorkstationModule module) {
        return findScaled(getAvailable(level, module), inputs, outputs);
    }

    @Nullable
    private static PatternMatch findScaled(List<LargeWorkstationRecipe> recipes, KeyCounter inputs, KeyCounter outputs) {
        var exact = find(recipes, inputs, outputs);
        if (exact != null) return new PatternMatch(exact, 1);
        PatternMatch fallback = null;
        for (var recipe : recipes) {
            long multiplier = recipe.matchingMultiplier(inputs, outputs);
            if (multiplier == 0) continue;
            var match = new PatternMatch(recipe, multiplier);
            if (recipe.extraInputs().isEmpty()) return match;
            if (fallback == null) fallback = match;
        }
        return fallback;
    }

    public record PatternMatch(LargeWorkstationRecipe recipe, long multiplier) {}

    private static String typeId(RecipeType<?> type) {
        var id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? type.toString() : id.toString();
    }

    private record Cache(Object generation, Collection<Recipe<?>> sources, List<LargeWorkstationRecipe> recipes,
                         Map<ResourceLocation, LargeWorkstationRecipe> recipesById,
                         Map<LargeWorkstationModule, List<LargeWorkstationRecipe>> moduleRecipes) {}
}
