package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationRecipeAdapters;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
        "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe", "ae2cs:crystal_pulverizer_recipe", "appgen:synthesizing",
        "advanced_ae:reaction", "extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler",
        "data_energistics:data_reassembler", "data_energistics:data_charger",
        "data_energistics:data_integrated_charger", "data_energistics:data_charge_press");
    // Keyed by the recipe source owner: a server RecipeManager, or the client's synced RecipeMap.
    private static final Map<Object, Cache> CACHE = new WeakHashMap<>();
    /** Recipes the server sent through {@code OnDatapackSyncEvent#sendRecipes}; used only on remote clients. */
    private static volatile RecipeMap clientRecipes = RecipeMap.EMPTY;
    // Lightning recipes have explicit priority. AE cost, lightning amount and ID break ties.
    private static final Comparator<LargeWorkstationRecipe> COST_ORDER = (first, second) -> {
        int costs = compareCosts(first, 1, second, 1);
        return costs != 0 ? costs : first.id().toString().compareTo(second.id().toString());
    };

    private LargeWorkstationRecipes() {}

    /** Tags may change ingredient equivalence without replacing the recipe collection. */
    public static synchronized void invalidate() { CACHE.clear(); }

    /**
     * 26.1 no longer exposes a full RecipeManager on the client. Use the server's manager (integrated server in
     * single player); returns null on a remote client, which reads {@link #clientRecipes} instead.
     */
    @Nullable
    static RecipeManager recipeManager(@Nullable Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) return serverLevel.recipeAccess();
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getRecipeManager();
    }

    /** Called by the client recipe receiver after each datapack sync; an empty map clears the remote catalog. */
    public static synchronized void setClientRecipes(@Nullable RecipeMap recipes) {
        var next = recipes == null ? RecipeMap.EMPTY : recipes;
        if (clientRecipes != next) CACHE.remove(clientRecipes);
        clientRecipes = next;
    }

    /** Server, integrated-server or synced-client catalog for this level; null only without a level. */
    @Nullable
    private static synchronized Cache cache(@Nullable Level level) {
        if (level == null) return null;
        var manager = recipeManager(level);
        if (manager != null) {
            getAll(manager, level.registryAccess());
            return CACHE.get(manager);
        }
        var synced = clientRecipes;
        build(synced, synced.values(), level.registryAccess());
        return CACHE.get(synced);
    }

    /**
     * Recipe types whose holders feed the large workstation catalog. The server sends exactly these to clients so
     * remote viewers rebuild the same groups (including adapters and borrowed sources) from the real recipes.
     */
    public static synchronized Set<RecipeType<?>> sourceTypes(RecipeManager manager, HolderLookup.Provider registries) {
        getAll(manager, registries);
        var cache = CACHE.get(manager);
        Set<RecipeType<?>> types = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var holder : cache.sources) {
            if (!cache.recipesById.containsKey(holder.id().identifier())) continue;
            var type = holder.value().getType();
            if (type != null) types.add(type);
        }
        return Set.copyOf(types);
    }

    public static List<LargeWorkstationRecipeGroup> getGroups(Level level) {
        var cache = cache(level);
        return cache == null ? List.of() : cache.groups;
    }

    public static synchronized List<LargeWorkstationRecipeGroup> getGroups(RecipeManager manager, HolderLookup.Provider registries) {
        getAll(manager, registries);
        return CACHE.get(manager).groups;
    }

    /** Accepts any original or borrowed variant ID, including a representative returned by getAll(). */
    @Nullable
    public static LargeWorkstationRecipeGroup getGroup(Level level, Identifier recipeId) {
        var cache = cache(level);
        return cache == null ? null : cache.groupsById.get(recipeId);
    }

    @Nullable
    public static synchronized LargeWorkstationRecipeGroup getGroup(RecipeManager manager,
            HolderLookup.Provider registries, Identifier recipeId) {
        getAll(manager, registries);
        return CACHE.get(manager).groupsById.get(recipeId);
    }

    public static List<LargeWorkstationRecipe> getAll(Level level) {
        var cache = cache(level);
        return cache == null ? List.of() : cache.recipes;
    }

    public static synchronized List<LargeWorkstationRecipe> getAll(RecipeManager manager, HolderLookup.Provider registries) {
        // RecipeManager replaces its immutable by-name map on both datapack reload and client sync.
        // Its values view is stable between reloads, including for unregistered RecipeType.simple types.
        return build(manager, manager.getRecipes(), registries);
    }

    /** Builds (or reuses) the catalog of one recipe source; {@code owner} identifies it in the weak cache. */
    private static synchronized List<LargeWorkstationRecipe> build(Object owner, Collection<RecipeHolder<?>> sources,
            HolderLookup.Provider registries) {
        var previous = CACHE.get(owner);
        if (previous != null && previous.sources == sources
            && previous.adapterRevision == ECOWorkstationRecipeAdapters.revision()
            && previous.moduleRevision == ECOWorkstationModules.revision()) return previous.recipes;
        List<LargeWorkstationRecipe> recipes = new ArrayList<>();
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        for (var holder : sources) {
            try {
                if (holder.value() instanceof LargeIntegratedWorkingStationRecipe largeRecipe) {
                    var nativeRecipe = largeRecipe.recipe();
                    recipes.add(new LargeWorkstationRecipe(holder.id().identifier(), nativeRecipe, nativeRecipe.energy(), List.of()));
                } else if (holder.value() instanceof IntegratedWorkingStationRecipe nativeRecipe) {
                    recipes.add(new LargeWorkstationRecipe(holder.id().identifier(), nativeRecipe, nativeRecipe.energy(), List.of()));
                } else if (holder.value() instanceof appeng.recipes.entropy.EntropyRecipe entropy
                        && CrystalScienceWorkstationRecipes.isPresent()) {
                    var adapted = CrystalScienceWorkstationRecipes.entropy(holder.id().identifier(), entropy);
                    if (adapted != null) recipes.add(adapted);
                } else {
                    String type = typeId(holder.value().getType());
                    var sourceType = Identifier.tryParse(type);
                    if (sourceType == null) continue;
                    var adapted = ECOWorkstationRecipeAdapters.adapt(
                        sourceType, holder, registries);
                    if (adapted != null) recipes.add(adapted);
                    else if (COMPAT_TYPES.contains(type)) recipes.add(decode(holder.id().identifier(), type, encode(holder.value(), ops), ops));
                }
            } catch (RuntimeException failure) {
                // A changed upstream format must fail closed, never silently omit a cost or result.
                LOGGER.warn("Cannot adapt large workstation recipe {}", holder.id(), failure);
            }
        }
        Map<Identifier, LargeWorkstationRecipe> recipesById = new HashMap<>();
        for (var recipe : recipes) recipesById.put(recipe.id(), recipe);
        var sourceIds = sources.stream().map(holder -> holder.id().identifier()).collect(Collectors.toSet());
        for (var recipe : CrystalScienceWorkstationRecipes.growthRecipes()) {
            if (sourceIds.contains(recipe.id())) {
                LOGGER.warn("Crystal growth recipe ID collides with a datapack recipe: {}", recipe.id());
                continue;
            }
            recipes.add(recipe);
            recipesById.put(recipe.id(), recipe);
        }
        var variants = new ArrayList<LargeWorkstationRecipeGroup.Variant>();
        for (var recipe : recipes) variants.add(LargeWorkstationRecipeSources.direct(recipe));
        // Add only explicitly supported execution capabilities, never a recursively combined upstream catalog.
        for (var holder : sources) {
            var recipe = recipesById.get(holder.id().identifier());
            if (recipe == null) continue;
            try {
                var borrowed = LargeWorkstationRecipeSources.borrowed(holder, recipe);
                if (borrowed != null) {
                    if (recipesById.putIfAbsent(borrowed.recipe().id(), borrowed.recipe()) == null) variants.add(borrowed);
                    else LOGGER.warn("Borrowed workstation recipe ID collides with a datapack recipe: {}", borrowed.recipe().id());
                }
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn("Cannot borrow large workstation recipe {}", holder.id(), failure);
            }
        }
        var groups = group(variants, ops);
        var result = groups.stream().map(LargeWorkstationRecipeGroup::representative).toList();
        Map<Identifier, LargeWorkstationRecipeGroup> groupsById = new HashMap<>();
        for (var group : groups) for (var variant : group.variants()) groupsById.put(variant.recipe().id(), group);
        var executionRecipes = groups.stream().flatMap(group -> selectExecutionVariants(group.variants()).stream())
            .map(LargeWorkstationRecipeGroup.Variant::recipe)
            .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList();
        CACHE.put(owner, new Cache(sources, result, executionRecipes, groups,
            Map.copyOf(recipesById), Map.copyOf(groupsById),
            new EnumMap<>(LargeWorkstationModule.class), new HashMap<>(),
            new IdentityHashMap<>(), new IdentityHashMap<>(),
            ECOWorkstationRecipeAdapters.revision(),
            ECOWorkstationModules.revision()));
        return result;
    }

    public static synchronized List<LargeWorkstationRecipe> getAvailable(Level level, Identifier moduleId) {
        var description = ECOWorkstationModules.resolve(moduleId);
        if (!description.custom()) return getAvailable(level, description.legacy());
        Cache cache = cache(level);
        if (cache == null) return List.of();
        if (NEConfig.largeWorkstationIgnoreUpgradeCards) return cache.executionRecipes;
        return cache.customModuleRecipes.computeIfAbsent(moduleId, installed -> available(cache.groups, installed));
    }

    public static synchronized List<LargeWorkstationRecipe> getAvailable(Level level, LargeWorkstationModule module) {
        Cache cache = cache(level);
        if (cache == null) return List.of();
        return available(cache, module);
    }

    static synchronized List<LargeWorkstationRecipe> getAvailable(RecipeManager manager,
            HolderLookup.Provider registries, LargeWorkstationModule module) {
        getAll(manager, registries);
        return available(CACHE.get(manager), module);
    }

    private static List<LargeWorkstationRecipe> available(Cache cache, LargeWorkstationModule module) {
        if (NEConfig.largeWorkstationIgnoreUpgradeCards) return cache.executionRecipes;
        return cache.moduleRecipes.computeIfAbsent(module, installed -> available(cache.groups,
            ECOWorkstationModules.builtin(installed).id()));
    }

    /** Filter by card before fixing a route; retain different legal material batches. */
    private static List<LargeWorkstationRecipe> available(List<LargeWorkstationRecipeGroup> groups,
            Identifier installed) {
        return groups.stream().flatMap(group -> group.availableVariants(installed).stream())
            .map(LargeWorkstationRecipeGroup.Variant::recipe)
            .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList();
    }

    /** Within one proportional material group, equal output quantities identify an exact material batch. */
    static List<LargeWorkstationRecipeGroup.Variant> selectExecutionVariants(
            List<LargeWorkstationRecipeGroup.Variant> variants) {
        Map<Map<AEKey, Long>, LargeWorkstationRecipeGroup.Variant> selected = new HashMap<>();
        for (var variant : variants) {
            Map<AEKey, Long> batch = new HashMap<>();
            for (var output : variant.recipe().outputs()) batch.merge(output.what(), output.amount(), Math::addExact);
            selected.merge(Map.copyOf(batch), variant, (first, second) ->
                COST_ORDER.compare(first.recipe(), second.recipe()) <= 0 ? first : second);
        }
        return selected.values().stream()
            .sorted(Comparator.comparing(variant -> variant.recipe().id().toString())).toList();
    }

    /** Filter before deduplication: a locked cheaper adapter must not hide an unlocked native recipe. */
    static List<LargeWorkstationRecipe> selectForModule(Collection<LargeWorkstationRecipe> recipes,
            LargeWorkstationModule module, DynamicOps<JsonElement> ops) {
        return deduplicate(recipes.stream().filter(recipe -> ECOWorkstationModules.unlocks(
            ECOWorkstationModules.builtin(module).id(), recipe.sourceType())).toList(), ops);
    }

    /** Keeps original recipes addressable for batches saved before equivalent recipes were merged. */
    @Nullable
    public static LargeWorkstationRecipe getById(Level level, Identifier id) {
        var cache = cache(level);
        return cache == null ? null : cache.recipesById.get(id);
    }

    @Nullable
    static synchronized LargeWorkstationRecipe getById(RecipeManager manager, HolderLookup.Provider registries, Identifier id) {
        getAll(manager, registries);
        return CACHE.get(manager).recipesById.get(id);
    }

    /** Legacy selection helper; the same grouping rules are used by the runtime catalog. */
    static List<LargeWorkstationRecipe> deduplicate(List<LargeWorkstationRecipe> recipes, DynamicOps<JsonElement> ops) {
        return group(recipes.stream().map(LargeWorkstationRecipeSources::direct).toList(), ops).stream()
            .map(LargeWorkstationRecipeGroup::representative).toList();
    }

    /** Proportional material contracts share a viewer entry; no execution recipe is divided or discarded. */
    static List<LargeWorkstationRecipeGroup> group(List<LargeWorkstationRecipeGroup.Variant> variants,
            DynamicOps<JsonElement> ops) {
        Map<MaterialContract, List<LargeWorkstationRecipeGroup.Variant>> grouped = new HashMap<>();
        Map<Identifier, Long> scales = new HashMap<>();
        List<LargeWorkstationRecipeGroup> result = new ArrayList<>();
        for (var variant : variants) {
            var recipe = variant.recipe();
            try {
                var contract = materialContract(recipe, ops);
                long scale = contract.scale();
                scales.put(recipe.id(), scale);
                grouped.computeIfAbsent(contract.divide(scale), ignored -> new ArrayList<>()).add(variant);
            } catch (RuntimeException failure) {
                // A predicate that cannot be compared is still executable, but never guessed equivalent.
                LOGGER.warn("Cannot group large workstation recipe {}", recipe.id(), failure);
                result.add(new LargeWorkstationRecipeGroup(recipe, List.of(variant)));
            }
        }
        for (var entries : grouped.values()) {
            entries.sort(Comparator.comparing(variant -> variant.recipe().id().toString()));
            // Viewer/bookmark identities stay on a real datapack source, not a virtual borrowing alias.
            var displayed = entries.stream().filter(variant -> !variant.borrowed()).toList();
            if (displayed.isEmpty()) displayed = entries;
            var representative = displayed.stream().map(LargeWorkstationRecipeGroup.Variant::recipe).min((first, second) -> {
                // Compare both candidates at the same material scale, without long multiplication overflow.
                int costs = compareCosts(first, scales.get(second.id()), second, scales.get(first.id()));
                return costs != 0 ? costs : first.id().toString().compareTo(second.id().toString());
            }).orElseThrow();
            result.add(new LargeWorkstationRecipeGroup(representative, entries));
        }
        result.sort(Comparator.comparing(group -> group.representative().id().toString()));
        return List.copyOf(result);
    }

    private static MaterialContract materialContract(LargeWorkstationRecipe recipe, DynamicOps<JsonElement> ops) {
        Map<Object, Long> items = new HashMap<>();
        for (var input : recipe.display().inputItems()) {
            items.merge(itemPredicate(input.ingredient(), ops), (long) input.count(), Math::addExact);
        }
        Map<Object, Long> fluids = new HashMap<>();
        for (var fluid : recipe.inputFluids()) {
            fluids.merge(fluidPredicate(fluid.ingredient(), ops), (long) fluid.amount(), Math::addExact);
        }
        Map<AEKey, Long> resources = new HashMap<>();
        for (var extra : recipe.extraInputs()) {
            if (!isLightning(extra)) resources.merge(extra.what(), extra.amount(), Math::addExact);
        }
        Map<AEKey, Long> outputs = new HashMap<>();
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

    private static synchronized List<LargeWorkstationRecipe> executionRecipes(Level level) {
        var cache = cache(level);
        return cache == null ? List.of() : cache.executionRecipes;
    }

    private static Object itemPredicate(Ingredient ingredient, DynamicOps<JsonElement> ops) {
        if (ingredient.isSimple() && ingredient.items().findAny().isPresent()) {
            return ingredient.items().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()))
                .collect(Collectors.toUnmodifiableSet());
        }
        if (ingredient.getCustomIngredient() instanceof DataComponentIngredient components) {
            return new ComponentPredicate(components.items().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()))
                .collect(Collectors.toUnmodifiableSet()), components.components(), components.componentsExhaustive());
        }
        // Custom ingredients can constrain components or have incomplete display alternatives.
        return Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
    }

    private static Object fluidPredicate(FluidIngredient ingredient, DynamicOps<JsonElement> ops) {
        if (ingredient.isSimple() && !ingredient.fluids().isEmpty()) {
            return ingredient.fluids().stream().map(holder -> BuiltInRegistries.FLUID.getKey(holder.value()))
                .collect(Collectors.toUnmodifiableSet());
        }
        if (ingredient instanceof DataComponentFluidIngredient components) {
            return new ComponentPredicate(components.fluids().stream().map(holder -> BuiltInRegistries.FLUID.getKey(holder.value()))
                .collect(Collectors.toUnmodifiableSet()), components.components(), components.isStrict());
        }
        return FluidIngredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
    }

    private record ComponentPredicate(Set<Identifier> alternatives, Object components, boolean strict) {}
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

    private static boolean isLightning(GenericStack stack) {
        return stack.what().getType().getId().getNamespace().equals("ae2lt");
    }

    @Nullable
    public static LargeWorkstationRecipe find(Level level, KeyCounter inputs, KeyCounter outputs) {
        var match = index(level, null).find(inputs, outputs, false);
        return match == null ? null : match.recipe();
    }

    /** Matches original legal batches; proportional viewer grouping never enables fractional operations. */
    @Nullable
    public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs) {
        return index(level, null).find(inputs, outputs, true);
    }

    @Nullable
    public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs, Identifier moduleId) {
        return index(level, moduleId).find(inputs, outputs, true);
    }

    @Nullable
    public static PatternMatch findScaled(Level level, KeyCounter inputs, KeyCounter outputs, LargeWorkstationModule module) {
        return index(level, ECOWorkstationModules.builtin(module).id()).find(inputs, outputs, true);
    }

    @Nullable
    static PatternMatch findScaled(List<LargeWorkstationRecipe> recipes, KeyCounter inputs, KeyCounter outputs) {
        // Standalone lists have no recipe-manager lifecycle and must not enter a global cache.
        return new LargeWorkstationRecipeIndex(recipes).find(inputs, outputs, true);
    }

    private static synchronized LargeWorkstationRecipeIndex index(Level level, @Nullable Identifier moduleId) {
        // Resolve the available list and its owning generation under the same lock as reload invalidation.
        var recipes = moduleId == null ? executionRecipes(level) : getAvailable(level, moduleId);
        var cache = cache(level);
        // Independently supplied recipe lists have no manager generation to retain them under.
        if (cache == null) return new LargeWorkstationRecipeIndex(recipes);
        return cache.indexes.computeIfAbsent(recipes,
            available -> new LargeWorkstationRecipeIndex(available, cache.prepared));
    }

    static int compareMatches(PatternMatch first, PatternMatch second) {
        int costs = compareCosts(first.recipe(), first.multiplier(), second.recipe(), second.multiplier());
        return costs != 0 ? costs : first.recipe().id().toString().compareTo(second.recipe().id().toString());
    }

    public record PatternMatch(LargeWorkstationRecipe recipe, long multiplier) {}

    private static String typeId(RecipeType<?> type) {
        var id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? type.toString() : id.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonObject encode(Recipe<?> recipe, DynamicOps<JsonElement> ops) {
        Codec codec = recipe.getSerializer().codec().codec();
        return ((JsonElement) codec.encodeStart(ops, recipe).getOrThrow()).getAsJsonObject();
    }

    static LargeWorkstationRecipe decode(Identifier id, String type, JsonObject data, DynamicOps<JsonElement> ops) {
        if (type.startsWith("data_energistics:")) return DataWorkstationRecipes.decode(id, type, data, ops);
        if (type.equals("appgen:synthesizing")) return AppliedGeneratorsWorkstationRecipes.decode(id, data, ops);
        List<SizedIngredient> items = new ArrayList<>();
        SizedFluidIngredient fluid = null;
        ItemStack result = ItemStack.EMPTY;
        FluidStack fluidResult = FluidStack.EMPTY;
        List<GenericStack> extras = List.of();
        long energy;
        long feEnergy = 0;
        boolean feAlternative = false;
        switch (type) {
            case "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation" -> {
                if (data.has("inputs")) for (var input : data.getAsJsonArray("inputs")) {
                    var entry = input.getAsJsonObject();
                    items.add(new SizedIngredient(parse(Ingredient.CODEC, ops, entry.get("ingredient")), entry.get("count").getAsInt()));
                }
                if (type.equals("ae2lt:overload_processing")) {
                    if (data.has("inputFluid")) {
                        var stack = parse(FluidStack.OPTIONAL_CODEC, ops, data.get("inputFluid"));
                        if (!stack.isEmpty()) fluid = new SizedFluidIngredient(
                            net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient.of(true, stack), stack.getAmount());
                    }
                    if (data.has("results")) {
                        var results = data.getAsJsonArray("results");
                        if (results.size() > 1) throw new IllegalArgumentException("Multiple item outputs");
                        if (!results.isEmpty()) result = parse(ItemStack.CODEC, ops, results.get(0));
                    }
                    if (data.has("resultFluid")) fluidResult = parse(FluidStack.OPTIONAL_CODEC, ops, data.get("resultFluid"));
                } else result = parse(ItemStack.CODEC, ops, data.get("result"));
                // Preserve the original FE budget; AE substitution is settled by the host per tick.
                feEnergy = data.get("totalEnergy").getAsLong();
                energy = 0;
                extras = List.of(LightningInput.decode(data));
            }
            case "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe", "ae2cs:crystal_pulverizer_recipe" -> {
                for (String name : List.of("input", "input_a", "input_b", "input_c")) {
                    if (data.has(name)) {
                        var item = parse(SizedIngredient.NESTED_CODEC, ops, data.get(name));
                        if (!item.ingredient().isEmpty()) items.add(item);
                    }
                }
                result = parse(ItemStack.CODEC, ops, data.get("result"));
                if (data.has("fluid_input")) fluid = parse(SizedFluidIngredient.CODEC, ops, data.get("fluid_input"));
                if (data.has("fluid_output")) fluidResult = parse(FluidStack.OPTIONAL_CODEC, ops, data.get("fluid_output"));
                energy = data.has("energy_cost") ? data.get("energy_cost").getAsLong()
                    : type.equals("ae2cs:circuit_etcher_recipe") ? 3200 : 200;
            }
            case "advanced_ae:reaction", "extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler" -> {
                for (var input : data.getAsJsonArray("input_items")) {
                    var entry = input.getAsJsonObject();
                    var ingredient = parse(Ingredient.CODEC, ops, entry.get("ingredient"));
                    int amount = entry.has("amount") ? entry.get("amount").getAsInt() : 1;
                    if (!ingredient.isEmpty() && amount > 0) items.add(new SizedIngredient(ingredient, amount));
                }
                if (data.has("input_fluid")) {
                    var entry = data.getAsJsonObject("input_fluid");
                    var ingredient = parse(FluidIngredient.CODEC, ops, entry.get("ingredient"));
                    int amount = entry.has("amount") ? entry.get("amount").getAsInt() : 1;
                    if (amount > 0) fluid = new SizedFluidIngredient(ingredient, amount);
                }
                if (type.equals("advanced_ae:reaction")) {
                    var output = parse(ECOKeyFactory.STACK_CODEC, ops, data.get("output"));
                    int count = Math.toIntExact(output.amount());
                    if (output.what() instanceof AEItemKey item) result = item.toStack(count);
                    else if (output.what() instanceof AEFluidKey key) fluidResult = key.toStack(count);
                    else throw new IllegalArgumentException("Unsupported output key");
                    energy = data.get("input_energy").getAsLong();
                    // The reaction chamber can refill its AE power sink with network FE through Applied Flux.
                    feEnergy = feEquivalent(energy);
                    feAlternative = feEnergy > 0 && energy > 0;
                } else {
                    result = parse(ItemStack.CODEC, ops, data.get("output"));
                    energy = 2000; // EAEP: 200 progress * 10 AE per progress per operation.
                }
            }
            default -> throw new IllegalArgumentException("Unsupported recipe type: " + type);
        }
        if (result.isEmpty() && fluidResult.isEmpty()) throw new IllegalArgumentException("Empty output");
        var display = IntegratedWorkingStationRecipe.of(items, fluid, result, fluidResult,
            (int) Math.min(Integer.MAX_VALUE, energy));
        var recipe = new LargeWorkstationRecipe(id, display, energy, extras, Identifier.parse(type));
        int ticks = recipe.processingTicks();
        if (type.startsWith("ae2lt:")) ticks = LightningWorkstationTiming.ticks(type, feEnergy);
        return recipe.processing(ticks, feEnergy, feAlternative);
    }

    static long feEquivalent(long ae) {
        return WorkstationEnergy.feEquivalent(ae);
    }

    private static <T> T parse(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement value) {
        return codec.parse(ops, value).getOrThrow();
    }

    /** Loaded only when an AE2LT recipe is actually present. */
    private static final class LightningInput {
        static GenericStack decode(JsonObject data) {
            var tier = data.has("lightningTier")
                ? com.moakiee.ae2lt.me.key.LightningKey.Tier.CODEC.parse(JsonOps.INSTANCE, data.get("lightningTier")).getOrThrow()
                : com.moakiee.ae2lt.me.key.LightningKey.Tier.HIGH_VOLTAGE;
            int amount = data.has("lightningCost") ? data.get("lightningCost").getAsInt() : 4;
            if (amount <= 0) throw new IllegalArgumentException("Invalid lightning cost");
            return new GenericStack(com.moakiee.ae2lt.me.key.LightningKey.of(tier), amount);
        }
    }

    private record Cache(Collection<RecipeHolder<?>> sources, List<LargeWorkstationRecipe> recipes,
                         List<LargeWorkstationRecipe> executionRecipes,
                         List<LargeWorkstationRecipeGroup> groups,
                         Map<Identifier, LargeWorkstationRecipe> recipesById,
                         Map<Identifier, LargeWorkstationRecipeGroup> groupsById,
                         Map<LargeWorkstationModule, List<LargeWorkstationRecipe>> moduleRecipes,
                         Map<Identifier, List<LargeWorkstationRecipe>> customModuleRecipes,
                         Map<LargeWorkstationRecipe, LargeWorkstationRecipe.Prepared> prepared,
                         Map<List<LargeWorkstationRecipe>, LargeWorkstationRecipeIndex> indexes,
                         long adapterRevision, long moduleRevision) {}
}
