package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Carries fractional coolant byproducts across processing ticks and batches. */
final class LargeWorkstationCoolingOutput {
    private static final String TAG_KEY = "coolingOutputRemainders";
    private static final int MAX_REMAINDERS = 4096;
    private final Map<ResourceLocation, Remainder> remainders = new HashMap<>();

    @Nullable
    Plan plan(ResourceLocation recipeId, FluidStack output, int recipeInput, int recipeOutput, int coolantConsumed) {
        if (output.isEmpty()) return new Plan(recipeId, null, FluidStack.EMPTY);
        if (recipeInput <= 0 || recipeOutput <= 0 || coolantConsumed <= 0) return null;

        Remainder previous = remainders.get(recipeId);
        if (previous != null && (previous.recipeInput != recipeInput || previous.recipeOutput != recipeOutput
            || !FluidStack.isSameFluidSameComponents(previous.fluid, output))) previous = null;
        int oldAmount = previous == null ? 0 : previous.amount;
        long numerator = (long) recipeOutput * coolantConsumed + oldAmount;
        long produced = numerator / recipeInput;
        if (produced > Integer.MAX_VALUE) return null;
        int remainder = (int) (numerator % recipeInput);
        if (remainder > 0 && previous == null && !remainders.containsKey(recipeId)
            && remainders.size() >= MAX_REMAINDERS) return null;
        Remainder next = remainder == 0 ? null : new Remainder(recipeInput, recipeOutput, output.copyWithAmount(1), remainder);
        return new Plan(recipeId, next,
            produced == 0 ? FluidStack.EMPTY : output.copyWithAmount((int) produced));
    }

    void commit(Plan plan) {
        if (plan.next == null) remainders.remove(plan.recipeId);
        else remainders.put(plan.recipeId, plan.next);
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var entry : remainders.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putString("recipe", entry.getKey().toString());
            value.putInt("recipeInput", entry.getValue().recipeInput);
            value.putInt("recipeOutput", entry.getValue().recipeOutput);
            value.put("fluid", FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
                entry.getValue().fluid).getOrThrow());
            value.putInt("amount", entry.getValue().amount);
            entries.add(value);
        }
        tag.put(TAG_KEY, entries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        remainders.clear();
        ListTag entries = tag.getList(TAG_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(entries.size(), MAX_REMAINDERS); i++) {
            CompoundTag value = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(value.getString("recipe"));
            int recipeInput = value.getInt("recipeInput");
            int recipeOutput = value.getInt("recipeOutput");
            int amount = value.getInt("amount");
            if (!value.contains("fluid", Tag.TAG_COMPOUND)) continue;
            FluidStack fluid;
            try {
                fluid = FluidStack.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), value.get("fluid"))
                    .result().orElse(FluidStack.EMPTY);
            } catch (RuntimeException invalid) {
                continue;
            }
            if (id != null && recipeInput > 0 && recipeOutput > 0 && amount > 0 && amount < recipeInput
                && !fluid.isEmpty()) {
                remainders.put(id, new Remainder(recipeInput, recipeOutput, fluid.copyWithAmount(1), amount));
            }
        }
    }

    record Plan(ResourceLocation recipeId, @Nullable Remainder next, FluidStack output) {
        Plan { output = output.copy(); }
    }

    private record Remainder(int recipeInput, int recipeOutput, FluidStack fluid, int amount) {
    }
}
