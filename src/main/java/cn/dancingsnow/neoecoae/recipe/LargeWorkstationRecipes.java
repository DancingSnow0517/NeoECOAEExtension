package cn.dancingsnow.neoecoae.recipe;

import appeng.api.config.PowerUnit;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Adapts the loaded recipes through their public data codecs, with no optional recipe-class linkage. */
public final class LargeWorkstationRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeWorkstationRecipes.class);
    private static final Set<String> TYPES = Set.of(
        "neoecoae:integrated_working_station", "ae2lt:overload_processing",
        "ae2lt:lightning_assembly", "ae2lt:lightning_simulation", "ae2cs:circuit_etcher_recipe",
        "ae2cs:crystal_aggregator_recipe", "appgen:synthesizing", "advanced_ae:reaction",
        "extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler");
    private static final Map<RecipeManager, Cache> CACHE = new WeakHashMap<>();

    private LargeWorkstationRecipes() {}

    public static List<LargeWorkstationRecipe> getAll(Level level) {
        return getAll(level.getRecipeManager(), level.registryAccess());
    }

    public static synchronized List<LargeWorkstationRecipe> getAll(RecipeManager manager, HolderLookup.Provider registries) {
        // RecipeManager replaces its immutable by-name map on both datapack reload and client sync.
        // Its values view is stable between reloads, including for unregistered RecipeType.simple types.
        Collection<RecipeHolder<?>> sources = manager.getRecipes();
        var previous = CACHE.get(manager);
        if (previous != null && previous.sources == sources) return previous.recipes;
        List<LargeWorkstationRecipe> recipes = new ArrayList<>();
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        for (var holder : sources) {
            try {
                if (holder.value() instanceof IntegratedWorkingStationRecipe nativeRecipe) {
                    recipes.add(new LargeWorkstationRecipe(holder.id(), nativeRecipe, nativeRecipe.energy(), List.of()));
                } else {
                    String type = typeId(holder.value().getType());
                    if (!TYPES.contains(type)) continue;
                    if (type.equals("extendedae:crystal_assembler") && !ModList.get().isLoaded("extendedae_plus")) continue;
                    recipes.add(decode(holder.id(), type, encode(holder.value(), ops), ops));
                }
            } catch (RuntimeException failure) {
                // A changed upstream format must fail closed, never silently omit a cost or result.
                LOGGER.warn("Cannot adapt large workstation recipe {}", holder.id(), failure);
            }
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        var result = List.copyOf(recipes);
        CACHE.put(manager, new Cache(sources, result));
        return result;
    }

    @Nullable
    public static LargeWorkstationRecipe find(Level level, KeyCounter inputs, KeyCounter outputs) {
        LargeWorkstationRecipe fallback = null;
        for (var recipe : getAll(level)) {
            if (!recipe.matches(inputs, outputs)) continue;
            // A newly installed lightning recipe must not add a network cost to an otherwise
            // identical existing material-only recipe. Explicit lightning inputs still select it.
            if (recipe.extraInputs().isEmpty()) return recipe;
            if (fallback == null) fallback = recipe;
        }
        return fallback;
    }

    private static String typeId(RecipeType<?> type) {
        var id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? type.toString() : id.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonObject encode(Recipe<?> recipe, DynamicOps<JsonElement> ops) {
        Codec codec = recipe.getSerializer().codec().codec();
        return ((JsonElement) codec.encodeStart(ops, recipe).getOrThrow()).getAsJsonObject();
    }

    static LargeWorkstationRecipe decode(ResourceLocation id, String type, JsonObject data, DynamicOps<JsonElement> ops) {
        List<SizedIngredient> items = new ArrayList<>();
        SizedFluidIngredient fluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        ItemStack result = ItemStack.EMPTY;
        FluidStack fluidResult = FluidStack.EMPTY;
        List<GenericStack> extras = List.of();
        long energy;
        switch (type) {
            case "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation" -> {
                if (data.has("inputs")) for (var input : data.getAsJsonArray("inputs")) {
                    var entry = input.getAsJsonObject();
                    items.add(new SizedIngredient(parse(Ingredient.CODEC_NONEMPTY, ops, entry.get("ingredient")), entry.get("count").getAsInt()));
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
                // AE2LT Reborn's totalEnergy is FE; the workstation consumes its AE equivalent.
                energy = (long) Math.ceil(PowerUnit.FE.convertTo(PowerUnit.AE, data.get("totalEnergy").getAsLong()));
                extras = List.of(LightningInput.decode(data));
            }
            case "ae2cs:circuit_etcher_recipe", "ae2cs:crystal_aggregator_recipe" -> {
                for (String name : List.of("input_a", "input_b", "input_c")) {
                    if (data.has(name)) {
                        var item = parse(SizedIngredient.FLAT_CODEC, ops, data.get(name));
                        if (!item.ingredient().isEmpty()) items.add(item);
                    }
                }
                result = parse(ItemStack.CODEC, ops, data.get("result"));
                if (data.has("fluid_input")) fluid = parse(SizedFluidIngredient.FLAT_CODEC, ops, data.get("fluid_input"));
                if (data.has("fluid_output")) fluidResult = parse(FluidStack.OPTIONAL_CODEC, ops, data.get("fluid_output"));
                energy = data.has("energy_cost") ? data.get("energy_cost").getAsLong()
                    : type.equals("ae2cs:circuit_etcher_recipe") ? 3200 : 200;
            }
            case "appgen:synthesizing", "advanced_ae:reaction", "extendedae_plus:crystal_assembler_plus", "extendedae:crystal_assembler" -> {
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
                    if (!ingredient.isEmpty() && amount > 0) fluid = new SizedFluidIngredient(ingredient, amount);
                }
                if (type.equals("appgen:synthesizing") || type.equals("advanced_ae:reaction")) {
                    var output = parse(GenericStack.CODEC, ops, data.get("output"));
                    int count = Math.toIntExact(output.amount());
                    if (output.what() instanceof AEItemKey item) result = item.toStack(count);
                    else if (output.what() instanceof AEFluidKey key) fluidResult = key.toStack(count);
                    else throw new IllegalArgumentException("Unsupported output key");
                    energy = data.get("input_energy").getAsLong();
                } else {
                    result = parse(ItemStack.CODEC, ops, data.get("output"));
                    energy = 2000; // EAEP: 200 progress * 10 AE per progress per operation.
                }
            }
            default -> throw new IllegalArgumentException("Unsupported recipe type: " + type);
        }
        if (result.isEmpty() && fluidResult.isEmpty()) throw new IllegalArgumentException("Empty output");
        var display = new IntegratedWorkingStationRecipe(List.copyOf(items), fluid, result, fluidResult,
            (int) Math.min(Integer.MAX_VALUE, energy));
        return new LargeWorkstationRecipe(id, display, energy, extras);
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

    private record Cache(Collection<RecipeHolder<?>> sources, List<LargeWorkstationRecipe> recipes) {}
}
