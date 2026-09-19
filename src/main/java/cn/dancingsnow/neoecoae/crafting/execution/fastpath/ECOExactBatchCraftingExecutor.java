package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Exact-order transaction; provider admission fixes the quantity before any resource is debited. */
public final class ECOExactBatchCraftingExecutor {
    private ECOExactBatchCraftingExecutor() {}

    public static @Nullable PreparedBatch prepare(ECOFastPathDispatchProvider provider,
            ECOBatchDispatchContext context, ECOExactInventory inventory, BigInteger requested,
            Map<AEKey, Long> protectedSeeds) {
        if (!inventory.isEnabled() || requested.signum() <= 0) return null;
        var unitInputs = ECOExactInventory.totals(context.inputItems(), BigInteger.ONE);
        for (var input : unitInputs.entrySet()) {
            var available = inventory.amount(input.getKey()).subtract(
                BigInteger.valueOf(protectedSeeds.getOrDefault(input.getKey(), 0L))).max(BigInteger.ZERO);
            requested = requested.min(available.divide(input.getValue()));
        }
        if (requested.signum() <= 0) return null;
        var admission = provider.eco$prepareExactFastPath(context, requested);
        if (admission == null) return null;
        if (admission.craftCount().compareTo(requested) > 0)
            throw new IllegalArgumentException("Provider exceeded exact allowance");
        return new PreparedBatch(inventory, admission,
            ECOExactInventory.totals(context.inputItems(), admission.craftCount()),
            ECOExactInventory.totals(context.outputs(), admission.craftCount()),
            ECOExactInventory.totals(context.containerItems(), admission.craftCount()));
    }

    public static final class PreparedBatch {
        private final ECOExactInventory inventory;
        private final ECOFastPathDispatchProvider.ExactPreparation admission;
        private final Map<AEKey, BigInteger> inputs;
        private final Map<AEKey, BigInteger> outputs;
        private final Map<AEKey, BigInteger> remainders;
        private boolean submitted;

        private PreparedBatch(ECOExactInventory inventory, ECOFastPathDispatchProvider.ExactPreparation admission,
                Map<AEKey, BigInteger> inputs, Map<AEKey, BigInteger> outputs, Map<AEKey, BigInteger> remainders) {
            this.inventory = inventory;
            this.admission = admission;
            this.inputs = inputs;
            this.outputs = outputs;
            this.remainders = remainders;
        }

        public BigInteger craftCount() { return admission.craftCount(); }
        public Map<AEKey, BigInteger> outputs() { return outputs; }
        public Map<AEKey, BigInteger> remainders() { return remainders; }

        public boolean submit(ECOFastPathFacade.Reservation energy) {
            if (submitted) throw new IllegalStateException("Exact batch already submitted");
            submitted = true;
            boolean debited = false;
            boolean retained = false;
            try {
                debited = inventory.debit(inputs);
                if (!debited) return false;
                retained = admission.dispatch().getAsBoolean();
                return retained;
            } catch (ECOIndeterminateBatchException failure) {
                retained = true;
                throw failure;
            } finally {
                if (retained) energy.commit();
                else {
                    try { if (debited) inventory.restore(inputs); }
                    finally { energy.refund(); }
                }
            }
        }
    }
}
