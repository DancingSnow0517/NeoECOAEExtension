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

import java.math.BigInteger;
import java.util.LinkedHashMap;

/** Grouped viewer recipes with complete original and borrowed execution contracts. */
public final class LargeWorkstationRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeWorkstationRecipes.class);
    private static final Set<String> COMPAT_TYPES = Set.of(
        "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation",
        "advanced_ae:reaction", "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe", "ae2cs:crystal_pulverizer_recipe");
    private static BooleanSupplier ignoreUpgradeCards = () -> false;
    private static final Map<RecipeManager, Cache> CACHE = new WeakHashMap<>();
    private static final Comparator<LargeWorkstationRecipe> COST_ORDER = (a, b) -> {
        int cost = compareCosts(a, 1, b, 1);
        return cost != 0 ? cost : a.id().toString().compareTo(b.id().toString());
    };
    private LargeWorkstationRecipes() {}
    public static void configure(BooleanSupplier supplier) { ignoreUpgradeCards = Objects.requireNonNull(supplier); }
    public static synchronized void invalidate() { CACHE.clear(); }

    public static List<LargeWorkstationRecipe> getAll(Level level) { return getAll(level.getRecipeManager(), level.registryAccess()); }

    public static synchronized List<LargeWorkstationRecipe> getAll(RecipeManager manager, RegistryAccess registries) {
        Map<ResourceLocation, Recipe<?>> sourceMap = manager instanceof RecipeManagerAccessor accessor ? accessor.neoecoae$recipesByName() : null;
        Collection<Recipe<?>> sources = sourceMap == null ? manager.getRecipes() : sourceMap.values();
        var previous = CACHE.get(manager);
        if (previous != null && (sourceMap != null ? previous.generation == sourceMap : sameSources(previous.sources, sources))) return previous.recipes;
        List<LargeWorkstationRecipeGroup.Variant> variants = new ArrayList<>();
        for (var source : sources) {
            try {
                LargeWorkstationRecipe recipe;
                if (source instanceof ExtendedCrystalAssemblerRecipe extended) {
                    recipe = new LargeWorkstationRecipe(source.getId(), extended.display(), extended.display().energy(), List.of(), ExtendedCrystalAssemblerRecipe.SOURCE_TYPE)
                        .processing(extended.display().processingTicks(), 0, false);
                } else if (source instanceof LargeIntegratedWorkingStationRecipe large) {
                    recipe = new LargeWorkstationRecipe(source.getId(), large.display(), large.display().energy(), List.of());
                } else if (source instanceof IntegratedWorkingStationRecipe nativeRecipe) {
                    recipe = new LargeWorkstationRecipe(source.getId(), nativeRecipe, nativeRecipe.energy(), List.of());
                } else if (source instanceof appeng.recipes.entropy.EntropyRecipe entropy && CrystalScienceWorkstationRecipes.isPresent()) {
                    recipe = CrystalScienceWorkstationRecipes.entropy(source.getId(), entropy);
                    if (recipe == null) continue;
                } else {
                    String type = typeId(source.getType());
                    if (!COMPAT_TYPES.contains(type)) continue;
                    recipe = LargeWorkstationRecipeAdapters.adapt(source, type);
                }
                variants.add(LargeWorkstationRecipeSources.direct(recipe));
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn("Cannot adapt large workstation recipe {}", source.getId(), failure);
            }
        }
        var groups = group(variants, JsonOps.INSTANCE);
        var recipes = groups.stream().map(LargeWorkstationRecipeGroup::representative).toList();
        var byId = new HashMap<ResourceLocation, LargeWorkstationRecipe>();
        var byGroup = new HashMap<ResourceLocation, LargeWorkstationRecipeGroup>();
        for (var variant : variants) byId.put(variant.recipe().id(), variant.recipe());
        for (var entry : groups) for (var variant : entry.variants()) byGroup.put(variant.recipe().id(), entry);
        CACHE.put(manager, new Cache(sourceMap, List.copyOf(sources), recipes, Map.copyOf(byId),
            new EnumMap<>(LargeWorkstationModule.class), groups, Map.copyOf(byGroup), List.copyOf(variants),
            variants.stream().map(LargeWorkstationRecipeGroup.Variant::recipe).toList()));
        return recipes;
    }

    public static List<LargeWorkstationRecipeGroup> getGroups(Level level) { return getGroups(level.getRecipeManager(), level.registryAccess()); }
    static synchronized List<LargeWorkstationRecipeGroup> getGroups(RecipeManager manager, RegistryAccess registries) {
        getAll(manager, registries); return CACHE.get(manager).groups;
    }
    @Nullable public static synchronized LargeWorkstationRecipeGroup getGroup(Level level, ResourceLocation id) {
        getAll(level); return CACHE.get(level.getRecipeManager()).groupsById.get(id);
    }
    public static List<LargeWorkstationRecipe> getAvailable(Level level, LargeWorkstationModule module) {
        return getAvailable(level.getRecipeManager(), level.registryAccess(), module);
    }
    static synchronized List<LargeWorkstationRecipe> getAvailable(RecipeManager manager, RegistryAccess registries, LargeWorkstationModule module) {
        getAll(manager, registries);
        var cache = CACHE.get(manager);
        if (ignoreUpgradeCards.getAsBoolean()) return cache.executionRecipes;
        return cache.moduleRecipes.computeIfAbsent(module, installed -> cache.variants.stream()
            .filter(variant -> variant.unlocks(installed)).map(LargeWorkstationRecipeGroup.Variant::recipe).toList());
    }
    static List<LargeWorkstationRecipe> selectForModule(Collection<LargeWorkstationRecipe> recipes, LargeWorkstationModule module, DynamicOps<JsonElement> ops) {
        return deduplicate(recipes.stream().filter(recipe -> module.unlocks(recipe.sourceType())).toList(), ops);
    }
    @Nullable public static LargeWorkstationRecipe getById(Level level, ResourceLocation id) { return getById(level.getRecipeManager(), level.registryAccess(), id); }
    @Nullable static synchronized LargeWorkstationRecipe getById(RecipeManager manager, RegistryAccess registries, ResourceLocation id) {
        getAll(manager, registries); return CACHE.get(manager).recipesById.get(id);
    }
    static List<LargeWorkstationRecipe> deduplicate(List<LargeWorkstationRecipe> recipes, DynamicOps<JsonElement> ops) {
        return group(recipes.stream().map(LargeWorkstationRecipeSources::direct).toList(), ops).stream().map(LargeWorkstationRecipeGroup::representative).toList();
    }
    static List<LargeWorkstationRecipeGroup> group(List<LargeWorkstationRecipeGroup.Variant> variants, DynamicOps<JsonElement> ops) {
        Map<MaterialContract, List<LargeWorkstationRecipeGroup.Variant>> contracts = new LinkedHashMap<>();
        List<LargeWorkstationRecipeGroup> groups = new ArrayList<>();
        for (var variant : variants) {
            try {
                var contract = materialContract(variant.recipe(), ops);
                contracts.computeIfAbsent(contract.divide(contract.scale()), ignored -> new ArrayList<>()).add(variant);
            } catch (RuntimeException failure) {
                LOGGER.warn("Cannot compare large workstation recipe {}", variant.recipe().id(), failure);
                groups.add(new LargeWorkstationRecipeGroup(variant.recipe(), List.of(variant)));
            }
        }
        for (var entries : contracts.values()) {
            var originals = entries.stream().filter(entry -> !entry.borrowed()).map(LargeWorkstationRecipeGroup.Variant::recipe).toList();
            var representative = (originals.isEmpty() ? entries.stream().map(LargeWorkstationRecipeGroup.Variant::recipe) : originals.stream()).min(COST_ORDER).orElseThrow();
            groups.add(new LargeWorkstationRecipeGroup(representative, entries));
        }
        groups.sort(Comparator.comparing(entry -> entry.representative().id().toString()));
        return List.copyOf(groups);
    }
    private static MaterialContract materialContract(LargeWorkstationRecipe recipe, DynamicOps<JsonElement> ops) {
        Map<Object, Long> items = new HashMap<>(), fluids = new HashMap<>();
        Map<AEKey, Long> resources = new HashMap<>(), outputs = new HashMap<>();
        for (var input : recipe.display().inputItems()) items.merge(itemPredicate(input.ingredient(), ops), (long) input.count(), Math::addExact);
        for (var fluid : recipe.inputFluids()) fluids.merge(fluidPredicate(fluid.ingredient(), ops), (long) fluid.amount(), Math::addExact);
        for (var extra : recipe.extraInputs()) if (!isLightning(extra)) resources.merge(extra.what(), extra.amount(), Math::addExact);
        for (var output : recipe.outputs()) outputs.merge(output.what(), output.amount(), Math::addExact);
        return new MaterialContract(Map.copyOf(items), Map.copyOf(fluids), Map.copyOf(resources), Map.copyOf(outputs));
    }
    private static int compareCosts(LargeWorkstationRecipe first, long firstMultiplier,
            LargeWorkstationRecipe second, long secondMultiplier) {
        int lightning = Boolean.compare(first.extraInputs().stream().noneMatch(LargeWorkstationRecipes::isLightning),
            second.extraInputs().stream().noneMatch(LargeWorkstationRecipes::isLightning));
        if (lightning != 0) return lightning;
        // Different currencies have no universal price ordering. Keep a deterministic contract order.
        int currency = Integer.compare(energyKind(first), energyKind(second));
        if (currency != 0) return currency;
        int energy = BigInteger.valueOf(first.energy()).multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(BigInteger.valueOf(second.energy()).multiply(BigInteger.valueOf(secondMultiplier)));
        if (energy != 0) return energy;
        int fe = BigInteger.valueOf(first.feEnergy()).multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(BigInteger.valueOf(second.feEnergy()).multiply(BigInteger.valueOf(secondMultiplier)));
        if (fe != 0) return fe;
        int extras = extraCost(first).multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(extraCost(second).multiply(BigInteger.valueOf(secondMultiplier)));
        if (extras != 0) return extras;
        return BigInteger.valueOf(first.processingTicks()).multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(BigInteger.valueOf(second.processingTicks()).multiply(BigInteger.valueOf(secondMultiplier)));
    }

    private static BigInteger extraCost(LargeWorkstationRecipe recipe) {
        return recipe.extraInputs().stream().map(input -> BigInteger.valueOf(input.amount()))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static int energyKind(LargeWorkstationRecipe recipe) {
        return recipe.feEnergy() == 0 ? 0 : recipe.energy() == 0 ? 1 : recipe.feAlternative() ? 2 : 3;
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

    private record MaterialContract(Map<Object, Long> items, Map<Object, Long> fluids,
                                    Map<AEKey, Long> resources, Map<AEKey, Long> outputs) {
        long scale() {
            long scale = 0;
            for (var entries : List.of(items, fluids, resources, outputs)) {
                for (long amount : entries.values()) {
                    if (amount <= 0) throw new IllegalArgumentException("Non-positive material quantity");
                    long next = amount;
                    while (next != 0) {
                        long remainder = scale % next;
                        scale = next;
                        next = remainder;
                    }
                }
            }
            if (scale == 0 || outputs.isEmpty()) throw new IllegalArgumentException("Empty material contract");
            return scale;
        }

        MaterialContract divide(long divisor) {
            return new MaterialContract(divide(items, divisor), divide(fluids, divisor),
                divide(resources, divisor), divide(outputs, divisor));
        }

        private static <K> Map<K, Long> divide(Map<K, Long> amounts, long divisor) {
            Map<K, Long> result = new HashMap<>();
            amounts.forEach((key, amount) -> result.put(key, amount / divisor));
            return Map.copyOf(result);
        }
    }


    private static boolean isLightning(GenericStack stack) { return stack.what().getType().getId().getNamespace().equals("ae2lt"); }
    @Nullable public static LargeWorkstationRecipe find(Level level, KeyCounter inputs, KeyCounter outputs) {
        var match = findScaled(level, inputs, outputs); return match == null || match.multiplier() != 1 ? null : match.recipe();
    }
    @Nullable public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs) {
        getAll(level);
        return findScaled(CACHE.get(level.getRecipeManager()).variants.stream().map(LargeWorkstationRecipeGroup.Variant::recipe).toList(), inputs, outputs);
    }
    @Nullable public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs, LargeWorkstationModule module) {
        return findScaled(getAvailable(level, module), inputs, outputs);
    }

    static PatternMatch findScaled(List<LargeWorkstationRecipe> recipes, KeyCounter inputs, KeyCounter outputs) {
        PatternMatch best = null;
        for (var recipe : recipes) {
            long multiplier = recipe.matchingMultiplier(inputs, outputs);
            if (multiplier == 0) continue;
            try {
                Math.multiplyExact(recipe.energy(), multiplier);
                Math.multiplyExact(recipe.feEnergy(), multiplier);
                Math.toIntExact(Math.multiplyExact((long) recipe.processingTicks(), multiplier));
                for (var extra : recipe.extraInputs()) Math.multiplyExact(extra.amount(), multiplier);
            } catch (ArithmeticException overflow) {
                // An unpayable preferred route must not hide another legal variant of this pattern.
                continue;
            }
            var match = new PatternMatch(recipe, multiplier);
            if (best == null) best = match;
            else {
                int costs = compareCosts(recipe, multiplier, best.recipe(), best.multiplier());
                if (costs < 0 || costs == 0 && recipe.id().toString().compareTo(best.recipe().id().toString()) < 0) best = match;
            }
        }
        return best;
    }

    public record PatternMatch(LargeWorkstationRecipe recipe, long multiplier) {}

    private static String typeId(RecipeType<?> type) {
        var id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? type.toString() : id.toString();
    }


    private record Cache(Object generation, Collection<Recipe<?>> sources, List<LargeWorkstationRecipe> recipes,
        Map<ResourceLocation, LargeWorkstationRecipe> recipesById, Map<LargeWorkstationModule, List<LargeWorkstationRecipe>> moduleRecipes,
        List<LargeWorkstationRecipeGroup> groups, Map<ResourceLocation, LargeWorkstationRecipeGroup> groupsById,
        List<LargeWorkstationRecipeGroup.Variant> variants, List<LargeWorkstationRecipe> executionRecipes) {}
}
