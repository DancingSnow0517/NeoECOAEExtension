package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prepares a batch for the selected provider; job accounting remains owned by the CPU. */
public final class ECOBatchCraftingExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOBatchCraftingExecutor() {}

    @Nullable
    public static PreparedBatch prepare(
            ECOBatchCapacityProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter containers,
            ListCraftingInventory inventory, int maxCrafts, IEnergyService energyService,
            Level level, UUID craftingJobId) {
        if (maxCrafts <= 0) return null;
        try {
            var context = ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
            // Capacity is local to this dispatch, never retained in a job, cursor, or save.
            long capacity = Math.max(0L, provider.eco$getBatchCapacity(context));
            if (capacity == 0L) return null;
            var perCopy = context.inputItems();
            int requested = (int) Math.min(maxCrafts, Math.min(capacity,
                ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                    perCopy, context.outputs(), context.containerItems())));
            if (requested <= 0) return null;
            requested = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, perCopy, requested);
            double power = CraftingCpuHelper.calculatePatternPower(inputs);
            int size = ECOBatchCraftingHelper.maxAffordableCrafts(power, requested,
                amount -> energyService.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
            if (size <= 0) return null;
            return new PreparedBatch(size, ECOBatchCraftingHelper.multiply(perCopy, size),
                ECOBatchCraftingHelper.multiply(context.outputs(), size),
                ECOBatchCraftingHelper.multiply(context.containerItems(), size), power * size,
                () -> provider.eco$pushBatch(context, size));
        } catch (RuntimeException unavailable) {
            LOGGER.debug("ECO batch preparation unavailable; no inputs extracted", unavailable);
            return null;
        }
    }

    public record PreparedBatch(int craftCount, List<GenericStack> inputTotal,
            List<GenericStack> outputs, List<GenericStack> remainders,
            double power, BooleanSupplier dispatch) {
        public PreparedBatch {
            inputTotal = List.copyOf(inputTotal);
            outputs = List.copyOf(outputs);
            remainders = List.copyOf(remainders);
        }

        /** Extract the prepared input total once and restore that exact total on rejection. */
        public boolean push(ListCraftingInventory inventory) {
            ECOBatchCraftingHelper.extractExact(inventory, inputTotal);
            boolean accepted = false;
            try {
                accepted = dispatch.getAsBoolean();
                return accepted;
            } finally {
                if (!accepted) ECOBatchCraftingHelper.insertAll(inventory, inputTotal);
            }
        }
    }
}
