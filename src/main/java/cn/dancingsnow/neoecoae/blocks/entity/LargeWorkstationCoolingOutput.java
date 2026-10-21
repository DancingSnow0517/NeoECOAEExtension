package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
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
            || !previous.fluid.isFluidEqual(output))) previous = null;
        int oldAmount = previous == null ? 0 : previous.amount;
        long numerator = (long) recipeOutput * coolantConsumed + oldAmount;
        long produced = numerator / recipeInput;
        if (produced > Integer.MAX_VALUE) return null;
        int remainder = (int) (numerator % recipeInput);
        if (remainder > 0 && previous == null && !remainders.containsKey(recipeId)
            && remainders.size() >= MAX_REMAINDERS) return null;
        Remainder next = remainder == 0 ? null : new Remainder(recipeInput, recipeOutput, copy(output, 1), remainder);
        return new Plan(recipeId, next,
            produced == 0 ? FluidStack.EMPTY : copy(output, (int) produced));
    }

    void commit(Plan plan) {
        if (plan.next == null) remainders.remove(plan.recipeId);
        else remainders.put(plan.recipeId, plan.next);
    }

    void save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (var entry : remainders.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putString("recipe", entry.getKey().toString());
            value.putInt("recipeInput", entry.getValue().recipeInput);
            value.putInt("recipeOutput", entry.getValue().recipeOutput);
            value.put("fluid", entry.getValue().fluid.writeToNBT(new CompoundTag()));
            value.putInt("amount", entry.getValue().amount);
            entries.add(value);
        }
        tag.put(TAG_KEY, entries);
    }

    void load(CompoundTag tag) {
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
                fluid = FluidStack.loadFluidStackFromNBT(value.getCompound("fluid"));
            } catch (RuntimeException invalid) {
                continue;
            }
            if (id != null && recipeInput > 0 && recipeOutput > 0 && amount > 0 && amount < recipeInput
                && !fluid.isEmpty()) {
                remainders.put(id, new Remainder(recipeInput, recipeOutput, copy(fluid, 1), amount));
            }
        }
    }

    record Plan(ResourceLocation recipeId, @Nullable Remainder next, FluidStack output) {
        Plan { output = output.copy(); }
    }

    private static FluidStack copy(FluidStack fluid, int amount) {
        FluidStack copy = fluid.copy();
        copy.setAmount(amount);
        return copy;
    }

    private record Remainder(int recipeInput, int recipeOutput, FluidStack fluid, int amount) {
    }
}
