package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** The resource transaction for every dispatch lane. CPU accounting follows successful settlement. */
public final class ECOBatchExecutor {
    private static final ECOBatchMaterializer MATERIALIZER = new ECOBatchMaterializer();

    private ECOBatchExecutor() {}

    public interface LinearEnergy extends ECOFastPathFacade.Reservation {
        void refundUnaccepted(long accepted, long offered);
    }

    public static ECOBatchAdmission execute(ECOBatchPlan plan, KeyCounter[] inputs, KeyCounter outputs,
            KeyCounter remainders, ListCraftingInventory inventory, Level level, UUID jobId,
            Supplier<? extends LinearEnergy> reserve, ECOBatchProvider provider) {
        if (plan == null) return ECOBatchAdmission.rejected();
        var energy = reserve.get();
        if (energy == null) return ECOBatchAdmission.rejected();
        ECOBatchMaterialized batch = null;
        boolean retained = false;
        try {
            batch = MATERIALIZER.materialize(plan, inputs, outputs, remainders, inventory, level);
            if (batch == null) return ECOBatchAdmission.rejected();
            var request = new ECOBatchDispatchRequest(plan.identity(), batch.executionView(),
                    batch.inputCounters(), batch.outputCounter(), batch.remainderCounter(),
                    plan.craftCount(), level, jobId);
            ECOBatchAdmission admission;
            try {
                admission = provider.eco$dispatchBatch(request);
                if (admission == null || admission.status() == ECOBatchAdmission.Status.INDETERMINATE
                        || admission.acceptedCrafts() > plan.craftCount()) {
                    throw new IllegalStateException("Provider returned an invalid or indeterminate admission");
                }
            } catch (RuntimeException failure) {
                retained = true;
                batch.commit();
                energy.commit();
                throw new ECOIndeterminateBatchException("Batch provider ownership is uncertain", failure);
            }
            if (admission.status() == ECOBatchAdmission.Status.REJECTED) return admission;
            retained = true;
            // From this point acceptance cannot be undone, including on a refund/accounting failure.
            try { batch.commitLinear(admission.acceptedCrafts()); }
            finally { energy.refundUnaccepted(admission.acceptedCrafts(), plan.craftCount()); }
            return admission;
        } finally {
            if (!retained) {
                try { if (batch != null) batch.rollback(); }
                finally { energy.refund(); }
            }
        }
    }

    /** Atomic prepared totals also cover arbitrary-precision inputs and non-linear tool remainders. */
    public static boolean executePrepared(ListCraftingInventory inventory, List<GenericStack> inputs,
            Map<AEKey, BigInteger> exactInputs, ECOFastPathFacade.Reservation energy,
            ECOStatefulBatchProvider provider) {
        ECOBatchInputLease lease = null;
        boolean retained = false;
        try {
            lease = MATERIALIZER.materializePrepared(inventory, inputs, exactInputs);
            if (lease == null) return false;
            try {
                retained = provider.eco$dispatchPreparedBatch();
            } catch (ECOIndeterminateBatchException failure) {
                retained = true;
                throw failure;
            }
            return retained;
        } finally {
            if (retained) {
                try { lease.commit(); }
                finally { energy.commit(); }
            } else {
                try { if (lease != null) lease.rollback(); }
                finally { energy.refund(); }
            }
        }
    }
}
