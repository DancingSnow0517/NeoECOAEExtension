package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusMatrixBridge;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessBatchProviderBridge;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * NeoECO-owned synchronous CPU integration boundary. Prepare and submit on the owning server thread
 * in the same tick. External CPUs integrate with this facade directly; providers are not required to
 * implement an API owned by the calling CPU or its infrastructure library.
 * Input slots describe one copy and are previews: all physical inputs must still be in the CPU inventory.
 * The facade owns extraction and rollback. The CPU must never extract or refund those inputs itself.
 * Provider verification and stateful material calculations remain in the provider's preparation contract.
 * After acceptance the CPU owns task/output accounting, exactly once, using the returned batch totals.
 */
public final class ECOFastPathFacade {
    private ECOFastPathFacade() {}

    public static boolean supports(ICraftingProvider provider) {
        return provider instanceof ECOFastPathDispatchProvider
            || ECOExtendedAEPlusMatrixBridge.supportsProvider(provider)
            || ECOUselessBatchProviderBridge.supports(provider);
    }

    @Nullable
    public static ECOFastPathDispatchProvider resolveProvider(ICraftingProvider provider) {
        if (provider instanceof ECOFastPathDispatchProvider nativeProvider) return nativeProvider;
        var matrix = ECOExtendedAEPlusMatrixBridge.adapt(provider);
        return matrix != null ? matrix : ECOUselessBatchProviderBridge.adapt(provider);
    }

    /** Null means no resources moved and ordinary dispatch is available. craftCount is the live upper bound. */
    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory,
            long maxCrafts, double singlePower, IEnergyService energy, Level level, @Nullable UUID jobId) {
        return prepare(provider, pattern, inputs, outputs, remainders, inventory, maxCrafts,
            singlePower, energy, level, jobId, false);
    }

    /** Explicit opt-in from the owning CPU's exact-order flag, never inferred from an amount or inventory. */
    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory,
            long maxCrafts, double singlePower, IEnergyService energy, Level level, @Nullable UUID jobId,
            boolean exactOrder) {
        var target = resolveProvider(provider);
        if (target == null) return null;
        var batch = ECOBatchCraftingExecutor.prepare(target, pattern, inputs, outputs, remainders,
            inventory, maxCrafts, singlePower, energy, level, jobId, exactOrder);
        return batch == null ? null : new PreparedBatch(batch, inventory);
    }

    /** CPU-owned energy ledger; a failed reservation must restore any partial debit before returning null. */
    @FunctionalInterface
    public interface EnergyAccount {
        @Nullable Reservation reserve(double amount);
    }

    /** Commit must not throw. Refund must retain any energy the network cannot accept in persistent CPU credit. */
    public interface Reservation {
        void commit();
        void refund();
    }

    /**
     * Adapter for CPUs that already allocated a uniform batch and own its energy/accounting transaction.
     * Only stateless recipes without returned inputs are supported by this delivery convention. No physical
     * CPU inventory is accessed: the private ledger describes the supplied materials. On rejection the CPU
     * still owns them; on success it owns only the unaccepted copies. Submit with its existing reservation.
     */
    @Nullable
    public static PreparedBatch prepareAllocated(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopy, long allocatedCopies, Level level, @Nullable UUID jobId) {
        if (!(provider instanceof ECOFastPathDispatchProvider) || allocatedCopies <= 0) return null;
        var execution = ECOExtractedPatternExecution.fromProviderPush(pattern, oneCopy, level);
        if (!execution.canUseFastPath() || !execution.expectedContainerItems().isEmpty()
                || execution.fastPathType() != cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier.Type.NORMAL) {
            return null;
        }
        long bounded = Math.min(allocatedCopies, execution.arithmeticBatchLimit());
        if (bounded <= 0) return null;
        var allocated = new ListCraftingInventory(ignored -> {});
        for (var stack : ECOBatchCraftingHelper.multiply(execution.inputItems(), bounded)) {
            allocated.list.add(stack.what(), stack.amount());
        }
        var outputs = new KeyCounter();
        for (var stack : execution.expectedOutputs()) outputs.add(stack.what(), stack.amount());
        return prepare(provider, pattern, oneCopy, outputs, new KeyCounter(), allocated, bounded,
            0, null, level, jobId);
    }

    public static final class PreparedBatch {
        private final ECOBatchCraftingExecutor.PreparedBatch batch;
        private final ListCraftingInventory inventory;
        private boolean submitted;

        private PreparedBatch(ECOBatchCraftingExecutor.PreparedBatch batch, ListCraftingInventory inventory) {
            this.batch = batch;
            this.inventory = inventory;
        }

        public long craftCount() { return batch.craftCount(); }
        public double power() { return batch.power(); }
        public List<GenericStack> inputTotal() { return batch.inputTotal(); }
        public java.util.Map<appeng.api.stacks.AEKey, java.math.BigInteger> exactInputTotal() {
            return batch.exactInputTotal();
        }
        public List<GenericStack> outputs() { return batch.outputs(); }
        public List<GenericStack> remainders() { return batch.remainders(); }

        /**
         * Single-use, including rejection. False restores inputs and energy. Ordinary provider exceptions
         * propagate after rollback. Indeterminate exceptions retain both resources and require the CPU to
         * stop the job for reconciliation, never fall back. Accounting failures after true cannot undo acceptance.
         */
        public boolean submit(EnergyAccount energy) {
            Objects.requireNonNull(energy);
            if (submitted) throw new IllegalStateException("Batch already submitted");
            submitted = true;
            var reservation = energy.reserve(power());
            if (reservation == null) return false;
            boolean retained = false;
            try {
                retained = batch.push(inventory);
                return retained;
            } catch (ECOIndeterminateBatchException failure) {
                retained = true;
                throw failure;
            } finally {
                if (retained) reservation.commit();
                else reservation.refund();
            }
        }
    }
}
