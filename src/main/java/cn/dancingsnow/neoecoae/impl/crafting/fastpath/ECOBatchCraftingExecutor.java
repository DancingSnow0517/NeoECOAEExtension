package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
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
            ECOFastPathDispatchProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter containers,
            ListCraftingInventory inventory, long maxCrafts, double power, IEnergyService energyService,
            Level level, UUID craftingJobId) {
        if (maxCrafts <= 0) return null;
        try {
            var context = ECOBatchDispatchContext.create(pattern, inputs, outputs, containers, level, craftingJobId);
            var preparation = provider.eco$prepareFastPath(context);
            if (preparation == null) return null;
            long capacity = preparation.capacity();
            var perCopy = context.inputItems();
            var statefulCalculator = preparation.statefulCalculator();
            if (preparation.statefulCalculatorRequired()
                    && context.execution().fastPathType() != ECORecipeClassifier.Type.NORMAL
                    && statefulCalculator == null) {
                return null;
            }
            long materialLimit = statefulCalculator == null
                ? ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
                    perCopy, context.outputs(), context.containerItems())
                : statefulCalculator.arithmeticBatchLimit();
            long requested = Math.min(maxCrafts, Math.min(capacity,
                materialLimit));
            if (requested <= 0) return null;
            ECOStatefulBatchCalculator.BatchContract statefulBatch = null;
            if (statefulCalculator == null) {
                requested = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, perCopy, requested);
            } else {
                statefulBatch = statefulCalculator.prepareBatch(inventory, requested);
                requested = statefulBatch == null ? 0L : statefulBatch.craftCount();
            }
            long size = ECOBatchCraftingHelper.maxAffordableCrafts(power, requested,
                amount -> energyService.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG));
            if (size <= 0) return null;
            if (statefulCalculator != null && size != requested) {
                statefulBatch = statefulCalculator.prepareBatch(inventory, size);
                if (statefulBatch == null || statefulBatch.craftCount() != size) return null;
            }
            var inputTotal = statefulCalculator == null
                ? ECOBatchCraftingHelper.multiply(perCopy, size) : statefulBatch.inputs();
            var remainderTotal = statefulCalculator == null
                ? ECOBatchCraftingHelper.multiply(context.containerItems(), size)
                : statefulBatch.remainders();
            var batch = new ECOFastPathDispatchProvider.Batch(size, inputTotal,
                ECOBatchCraftingHelper.multiply(context.outputs(), size), remainderTotal);
            return new PreparedBatch(batch.craftCount(), batch.inputTotal(), batch.outputTotal(),
                batch.remainingTotal(), power * size, () -> preparation.push(batch));
        } catch (RuntimeException unavailable) {
            LOGGER.debug("ECO batch preparation unavailable; no inputs extracted", unavailable);
            return null;
        }
    }

    public record PreparedBatch(long craftCount, List<GenericStack> inputTotal,
            List<GenericStack> outputs, List<GenericStack> remainders,
            double power, BooleanSupplier dispatch) {
        public PreparedBatch {
            inputTotal = List.copyOf(inputTotal);
            outputs = List.copyOf(outputs);
            remainders = List.copyOf(remainders);
            BooleanSupplier target = dispatch;
            AtomicBoolean submitted = new AtomicBoolean();
            dispatch = () -> {
                if (!submitted.compareAndSet(false, true)) {
                    throw new IllegalStateException("Batch already submitted");
                }
                return target.getAsBoolean();
            };
        }

        /** Extract the prepared input total once and restore that exact total on rejection. */
        public boolean push(ListCraftingInventory inventory) {
            if (!ECOBatchCraftingHelper.extractExact(inventory, inputTotal)) {
                return false;
            }
            boolean accepted = false;
            try {
                accepted = dispatch.getAsBoolean();
                return accepted;
            } catch (ECOIndeterminateBatchException failure) {
                accepted = true;
                throw failure;
            } finally {
                if (!accepted) ECOBatchCraftingHelper.insertAll(inventory, inputTotal);
            }
        }
    }
}
