package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Exact-order transaction; provider admission fixes the quantity before any resource is debited. */
public final class ECOExactBatchPlanner {
    private ECOExactBatchPlanner() {}

    public static @Nullable PreparedBatch prepare(ECOFastPathDispatchProvider provider,
            ECOBatchDispatchContext context, ECOExactInventory inventory, BigInteger requested,
            Map<AEKey, Long> protectedSeeds) {
        return prepare(provider, context, inventory, requested, protectedSeeds, ignored -> {});
    }

    /** Reports the material-limited request before asking the provider for admission. */
    public static @Nullable PreparedBatch prepare(ECOFastPathDispatchProvider provider,
            ECOBatchDispatchContext context, ECOExactInventory inventory, BigInteger requested,
            Map<AEKey, Long> protectedSeeds, java.util.function.Consumer<BigInteger> materialAllowance) {
        Map<AEKey, PlannerAmount> exactSeeds = new java.util.LinkedHashMap<>();
        protectedSeeds.forEach((key, amount) -> exactSeeds.put(key, PlannerAmount.of(amount)));
        return prepare(provider, context, inventory, PlannerAmount.of(requested), exactSeeds,
                amount -> materialAllowance.accept(amount.toBigInteger()));
    }

    public static @Nullable PreparedBatch prepare(ECOFastPathDispatchProvider provider,
            ECOBatchDispatchContext context, ECOExactInventory inventory, PlannerAmount requested,
            Map<AEKey, PlannerAmount> protectedSeeds, java.util.function.Consumer<PlannerAmount> materialAllowance) {
        if (!inventory.isEnabled() || requested.signum() <= 0) return null;
        var initialRequest = requested;
        var unitInputs = ECOExactInventory.totalsQuantities(context.inputItems(), PlannerAmount.ONE);
        for (var input : unitInputs.entrySet()) {
            var available = inventory.quantity(input.getKey()).subtract(
                protectedSeeds.getOrDefault(input.getKey(), PlannerAmount.ZERO)).max(PlannerAmount.ZERO);
            requested = requested.min(available.divide(input.getValue()));
        }
        requested = new ECOBatchPlanner().planExact(initialRequest, requested, initialRequest, initialRequest);
        materialAllowance.accept(requested);
        if (requested.signum() <= 0) return null;
        var admission = provider.eco$prepareExactFastPath(context, requested.toBigInteger());
        if (admission == null) return null;
        PlannerAmount admitted = PlannerAmount.of(admission.craftCount());
        if (admitted.compareTo(requested) > 0)
            throw new IllegalArgumentException("Provider exceeded exact allowance");
        var count = new ECOBatchPlanner().planExact(initialRequest, requested, admitted, requested);
        if (!count.equals(admitted)) throw new IllegalArgumentException("Exact admission exceeds plan");
        return new PreparedBatch(inventory, admission,
            ECOExactInventory.totalsQuantities(context.inputItems(), admitted),
            ECOExactInventory.totalsQuantities(context.outputs(), admitted),
            ECOExactInventory.totalsQuantities(context.containerItems(), admitted));
    }

    public static final class PreparedBatch {
        private final ECOExactInventory inventory;
        private final ECOFastPathDispatchProvider.ExactPreparation admission;
        private final PlannerAmount craftQuantity;
        private final Map<AEKey, PlannerAmount> inputs;
        private final Map<AEKey, PlannerAmount> outputs;
        private final Map<AEKey, PlannerAmount> remainders;
        private boolean submitted;

        private PreparedBatch(ECOExactInventory inventory, ECOFastPathDispatchProvider.ExactPreparation admission,
                Map<AEKey, PlannerAmount> inputs, Map<AEKey, PlannerAmount> outputs, Map<AEKey, PlannerAmount> remainders) {
            this.inventory = inventory;
            this.admission = admission;
            this.craftQuantity = PlannerAmount.of(admission.craftCount());
            this.inputs = inputs;
            this.outputs = outputs;
            this.remainders = remainders;
        }

        public BigInteger craftCount() { return admission.craftCount(); }
        public Map<AEKey, BigInteger> outputs() { return ECOExactInventory.toBigIntegers(outputs); }
        public Map<AEKey, BigInteger> remainders() { return ECOExactInventory.toBigIntegers(remainders); }
        public PlannerAmount craftQuantity() { return craftQuantity; }
        public Map<AEKey, PlannerAmount> outputQuantities() { return outputs; }
        public Map<AEKey, PlannerAmount> remainderQuantities() { return remainders; }

        public boolean submit(ECOFastPathFacade.Reservation energy) {
            if (submitted) throw new IllegalStateException("Exact batch already submitted");
            submitted = true;
            return ECOBatchExecutor.executePreparedQuantities(inventory, java.util.List.of(), inputs, energy,
                    admission.dispatch()::getAsBoolean);
        }
    }
}
