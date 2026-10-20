package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

/** Codec-based bridge for both the 0.2.x and 0.3.x Genesis Synthesizer formats. */
final class AppliedGeneratorsWorkstationRecipes {
    private static final ResourceLocation TYPE = ResourceLocation.parse("appgen:synthesizing");

    private AppliedGeneratorsWorkstationRecipes() {}

    static LargeWorkstationRecipe decode(ResourceLocation id, JsonObject data, DynamicOps<JsonElement> ops) {
        // 0.3.0 renamed all three input fields and widened energy to long.
        boolean modern = data.has("item_inputs");
        String itemField = modern ? "item_inputs" : "input_items";
        String fluidField = modern ? "fluid_input" : "input_fluid";
        String energyField = modern ? "energy_input" : "input_energy";
        List<SizedIngredient> items = new ArrayList<>();
        for (var value : data.getAsJsonArray(itemField)) {
            var entry = value.getAsJsonObject();
            var ingredient = Ingredient.CODEC.parse(ops, entry.get("ingredient")).getOrThrow();
            int amount = amount(entry);
            // Charged ember crystals are regular consumed ingredients, including in 0.2.x.
            if (!ingredient.isEmpty()) items.add(new SizedIngredient(ingredient, amount));
        }
        if (items.size() > LargeIntegratedWorkingStationRecipe.MAX_ITEM_INPUTS) {
            throw new IllegalArgumentException("Too many workstation inputs");
        }
        SizedFluidIngredient fluid = new SizedFluidIngredient(FluidIngredient.empty(), 1);
        if (data.has(fluidField)) {
            var entry = data.getAsJsonObject(fluidField);
            var ingredient = FluidIngredient.CODEC.parse(ops, entry.get("ingredient")).getOrThrow();
            fluid = new SizedFluidIngredient(ingredient, amount(entry));
        }
        if (items.isEmpty() && fluid.ingredient().isEmpty()) {
            throw new IllegalArgumentException("Empty synthesizer input");
        }
        var output = GenericStack.CODEC.parse(ops, data.get("output")).getOrThrow();
        int count = Math.toIntExact(output.amount());
        if (count <= 0) throw new IllegalArgumentException("Non-positive synthesizer output");
        ItemStack result = ItemStack.EMPTY;
        FluidStack fluidResult = FluidStack.EMPTY;
        if (output.what() instanceof AEItemKey item) result = item.toStack(count);
        else if (output.what() instanceof AEFluidKey liquid) fluidResult = liquid.toStack(count);
        else throw new IllegalArgumentException("Unsupported synthesizer output key");
        long energy = data.get(energyField).getAsBigDecimal().longValueExact();
        if (energy < 0) throw new IllegalArgumentException("Negative synthesizer energy");
        var display = new IntegratedWorkingStationRecipe(items, fluid, result, fluidResult,
            (int) Math.min(Integer.MAX_VALUE, energy));
        return new LargeWorkstationRecipe(id, display, energy, List.of(), TYPE);
    }

    private static int amount(JsonObject entry) {
        int amount = entry.has("amount") ? entry.get("amount").getAsBigDecimal().intValueExact() : 1;
        if (amount <= 0) throw new IllegalArgumentException("Non-positive synthesizer input");
        return amount;
    }
}
