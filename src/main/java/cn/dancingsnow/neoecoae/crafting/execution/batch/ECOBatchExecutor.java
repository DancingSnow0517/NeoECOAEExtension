package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Shared resource transaction. CPU task accounting follows the provider ownership receipt. */
public final class ECOBatchExecutor {
    private ECOBatchExecutor() {}

    public static ECOBatchAdmission execute(
            ListCraftingInventory inventory,
            List<GenericStack> inputs,
            Map<AEKey, BigInteger> exactInputs,
            long offered,
            boolean linear,
            ECOBatchEnergyLedger.Reservation energy,
            Supplier<ECOBatchAdmission> dispatch) {
        if (energy == null) return ECOBatchAdmission.rejected();
        ECOBatchInputLease lease = null;
        boolean retained = false;
        try {
            lease = ECOBatchInputLease.acquire(inventory, inputs, exactInputs);
            if (lease == null) return ECOBatchAdmission.rejected();
            ECOBatchAdmission admission;
            try {
                admission = dispatch.get();
                if (admission == null
                        || admission.status() == ECOBatchAdmission.Status.INDETERMINATE
                        || admission.acceptedCrafts() > offered
                        || !linear
                                && admission.status() == ECOBatchAdmission.Status.ACCEPTED
                                && admission.acceptedCrafts() != offered) {
                    throw new IllegalStateException("Invalid or uncertain batch admission");
                }
            } catch (RuntimeException failure) {
                retained = true;
                try {
                    lease.commit();
                } finally {
                    energy.commit();
                }
                throw failure instanceof ECOIndeterminateBatchException uncertain
                        ? uncertain
                        : new ECOIndeterminateBatchException("Batch provider ownership is uncertain", failure);
            }
            if (admission.status() == ECOBatchAdmission.Status.REJECTED) return admission;
            retained = true;
            try {
                try {
                    if (linear) lease.commitLinear(admission.acceptedCrafts(), offered);
                    else lease.commit();
                } finally {
                    if (linear) energy.commitLinear(admission.acceptedCrafts(), offered);
                    else energy.commit();
                }
            } catch (RuntimeException failure) {
                throw new ECOIndeterminateBatchException("Accepted batch settlement failed", failure);
            }
            return admission;
        } finally {
            if (!retained) {
                try {
                    if (lease != null) lease.rollback();
                } finally {
                    energy.rollback();
                }
            }
        }
    }
}
