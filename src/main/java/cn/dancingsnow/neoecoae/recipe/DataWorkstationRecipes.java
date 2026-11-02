package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Reads upstream codecs without linking optional Data Energistics classes on the base-mod class path. */
final class DataWorkstationRecipes {
    private DataWorkstationRecipes() {}

    static LargeWorkstationRecipe decode(ResourceLocation id, String type, JsonObject data, DynamicOps<JsonElement> ops) {
        List<SizedIngredient> items = new ArrayList<>();
        SizedFluidIngredient fluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        ItemStack result = ItemStack.EMPTY;
        FluidStack fluidResult = FluidStack.EMPTY;
        List<GenericStack> extras = List.of();
        List<SizedFluidIngredient> additionalFluids = new ArrayList<>();
        List<GenericStack> additionalOutputs = new ArrayList<>();
        long energy;
        switch (type) {
            case "data_energistics:data_reassembler" -> {
                var inputs = data.getAsJsonObject("inputs");
                readItems(inputs, "items", items, ops);
                var fluids = fluids(inputs, ops);
                if (!fluids.isEmpty()) {
                    fluid = fluidInput(fluids.getFirst());
                    for (int i = 1; i < fluids.size(); i++) additionalFluids.add(fluidInput(fluids.get(i)));
                }
                if (inputs.has("resource")) extras = List.of(resource(inputs.getAsJsonObject("resource"), ops));
                var outputs = data.getAsJsonObject("outputs");
                if (outputs.has("resource")) additionalOutputs.add(resource(outputs.getAsJsonObject("resource"), ops));
                if (outputs.has("items")) {
                    var outputsItems = outputs.getAsJsonArray("items");
                    for (var value : outputsItems) {
                        var output = value.getAsJsonObject();
                        if (output.has("modifier")) throw new IllegalArgumentException("Dynamic output modifiers are not supported");
                        var stack = parse(ItemStack.CODEC, ops, output);
                        if (result.isEmpty()) result = stack;
                        else additionalOutputs.add(GenericStack.fromItemStack(stack));
                    }
                }
                var outputFluids = fluids(outputs, ops);
                if (!outputFluids.isEmpty()) {
                    fluidResult = outputFluids.getFirst();
                    for (int i = 1; i < outputFluids.size(); i++) {
                        additionalOutputs.add(GenericStack.fromFluidStack(outputFluids.get(i)));
                    }
                }
                // Upstream charges idle AE but no per-operation AE for reassembly. The host retains its own idle drain.
                energy = 0;
            }
            case "data_energistics:data_charger" -> {
                items.add(new SizedIngredient(parse(Ingredient.CODEC_NONEMPTY, ops, data.get("ingredient")), 1));
                result = parse(ItemStack.CODEC, ops, data.get("result"));
                long amount = positive(data.get("data_flow").getAsLong());
                var key = new JsonObject();
                key.addProperty("key_type", "data_energistics:digitalization");
                key.addProperty("resource", "data_energistics:data_flow");
                key.addProperty("amount", amount);
                extras = List.of(resource(key, ops));
                energy = data.get("power").getAsBigDecimal().setScale(0, RoundingMode.CEILING).longValueExact();
                positive(energy);
            }
            case "data_energistics:data_integrated_charger", "data_energistics:data_charge_press" -> {
                readItems(data, "inputs", items, ops);
                result = parse(ItemStack.CODEC, ops, data.get("result"));
                if (type.endsWith(":data_charge_press")) {
                    var liquid = new JsonObject();
                    liquid.addProperty("fluid", "data_energistics:data_corrosion_liquid");
                    liquid.add("amount", data.get("fluid_amount"));
                    fluid = fluidInput(fluid(liquid, ops));
                }
                // The upstream integrated charger pays 1,600 AE for each inscribing/growth operation.
                energy = 1600;
            }
            default -> throw new IllegalArgumentException("Unsupported Data Energistics recipe: " + type);
        }
        if (items.isEmpty() && fluid.ingredient().isEmpty() && extras.isEmpty()) {
            throw new IllegalArgumentException("Empty input");
        }
        if (result.isEmpty() && fluidResult.isEmpty() && additionalOutputs.isEmpty()) throw new IllegalArgumentException("Empty output");
        var display = new IntegratedWorkingStationRecipe(items, fluid, result, fluidResult,
            (int) Math.min(Integer.MAX_VALUE, energy));
        var recipe = new LargeWorkstationRecipe(id, display, energy, extras, ResourceLocation.parse(type), additionalFluids, additionalOutputs);
        return type.equals("data_energistics:data_reassembler")
            ? recipe.processing(data.has("duration") ? data.get("duration").getAsInt() : 200, 0, false) : recipe;
    }

    private static void readItems(JsonObject data, String field, List<SizedIngredient> items, DynamicOps<JsonElement> ops) {
        if (!data.has(field)) return;
        for (var input : data.getAsJsonArray(field)) {
            var entry = input.getAsJsonObject();
            var ingredient = parse(Ingredient.CODEC_NONEMPTY, ops, entry.has("ingredient") ? entry.get("ingredient") : entry);
            int count = Math.toIntExact(positive(entry.has("count") ? entry.get("count").getAsLong() : 1));
            items.add(new SizedIngredient(ingredient, count));
        }
    }

    private static List<FluidStack> fluids(JsonObject data, DynamicOps<JsonElement> ops) {
        if (!data.has("fluids")) return List.of();
        var fluids = data.getAsJsonArray("fluids");
        var result = new ArrayList<FluidStack>();
        for (var entry : fluids) result.add(fluid(entry.getAsJsonObject(), ops));
        return result;
    }

    private static FluidStack fluid(JsonObject entry, DynamicOps<JsonElement> ops) {
        var liquid = parse(BuiltInRegistries.FLUID.byNameCodec(), ops, entry.get("fluid"));
        if (liquid == Fluids.EMPTY) throw new IllegalArgumentException("Empty fluid");
        return new FluidStack(liquid, Math.toIntExact(positive(entry.get("amount").getAsLong())));
    }

    private static SizedFluidIngredient fluidInput(FluidStack stack) {
        return stack.isEmpty() ? new SizedFluidIngredient(FluidIngredient.empty(), 1)
            : new SizedFluidIngredient(DataComponentFluidIngredient.of(true, stack), stack.getAmount());
    }

    private static GenericStack resource(JsonObject entry, DynamicOps<JsonElement> ops) {
        var type = parse(AEKeyType.CODEC, ops, entry.get("key_type"));
        var key = parse(type.codec().codec(), ops, entry);
        if (key instanceof AEItemKey || key instanceof AEFluidKey) throw new IllegalArgumentException("Expected a custom resource key");
        return new GenericStack(key, positive(entry.get("amount").getAsLong()));
    }

    private static long positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("Non-positive recipe cost");
        return value;
    }

    private static <T> T parse(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement value) {
        return codec.parse(ops, value).getOrThrow();
    }
}
