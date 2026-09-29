package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.ECOExactBatchProvider;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import java.math.BigInteger;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Exact material allowance and provider admission before any physical debit. */
public final class ECOExactBatchPlanner {
    private ECOExactBatchPlanner() {}

    @Nullable public static PreparedBatch prepare(
            ECOExactBatchProvider provider,
            ECOBatchDispatchContext context,
            ECOExactInventory inventory,
            BigInteger requested,
            Map<AEKey, Long> protectedSeeds) {
        if (!inventory.isEnabled() || requested.signum() <= 0) return null;
        var units = ECOExactInventory.totals(context.inputItems(), BigInteger.ONE);
        BigInteger allowance = requested;
        for (var entry : units.entrySet()) {
            BigInteger available = inventory
                    .amount(entry.getKey())
                    .subtract(BigInteger.valueOf(protectedSeeds.getOrDefault(entry.getKey(), 0L)))
                    .max(BigInteger.ZERO);
            allowance = allowance.min(available.divide(entry.getValue()));
        }
        if (allowance.signum() <= 0) return null;
        var admission = provider.eco$prepareExactBatch(context, allowance);
        if (admission == null) return null;
        BigInteger count = admission.craftCount();
        if (!count.equals(ECOBatchPlanner.planExact(requested, allowance, count, requested))) {
            throw new IllegalArgumentException("Exact provider exceeded material allowance");
        }
        return new PreparedBatch(
                inventory,
                admission,
                ECOExactInventory.totals(context.inputItems(), count),
                ECOExactInventory.totals(context.outputs(), count),
                ECOExactInventory.totals(context.containerItems(), count));
    }

    public static final class PreparedBatch {
        private final ECOExactInventory inventory;
        private final ECOExactBatchProvider.ExactPreparation preparation;
        private final Map<AEKey, BigInteger> inputs;
        private final Map<AEKey, BigInteger> outputs;
        private final Map<AEKey, BigInteger> remainders;
        private boolean submitted;

        private PreparedBatch(
                ECOExactInventory inventory,
                ECOExactBatchProvider.ExactPreparation preparation,
                Map<AEKey, BigInteger> inputs,
                Map<AEKey, BigInteger> outputs,
                Map<AEKey, BigInteger> remainders) {
            this.inventory = inventory;
            this.preparation = preparation;
            this.inputs = inputs;
            this.outputs = outputs;
            this.remainders = remainders;
        }

        public BigInteger craftCount() {
            return preparation.craftCount();
        }

        public Map<AEKey, BigInteger> outputs() {
            return outputs;
        }

        public Map<AEKey, BigInteger> remainders() {
            return remainders;
        }

        public ECOBatchAdmission submit(ECOBatchEnergyLedger.Reservation energy) {
            if (submitted) throw new IllegalStateException("Exact batch already submitted");
            submitted = true;
            return ECOBatchExecutor.execute(
                    inventory,
                    java.util.List.of(),
                    inputs,
                    1L,
                    false,
                    energy,
                    () -> preparation.dispatch().getAsBoolean()
                            ? ECOBatchAdmission.accepted(1L, false)
                            : ECOBatchAdmission.rejected());
        }
    }
}
