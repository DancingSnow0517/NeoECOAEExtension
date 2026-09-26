package cn.dancingsnow.neoecoae.compat.omnisequence;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Uniform, stateless deliveries only; Omni owns allocation, refunds and CPU accounting. */
public final class ECOOmniSequenceBatchAdapter {
    private ECOOmniSequenceBatchAdapter() {}

    @Nullable
    public static OmniBatchAdmission prepare(ICraftingProvider provider, Level level, OmniBatchProbe probe) {
        if (level == null || probe.requestedMaxCrafts() < 2) return null;
        KeyCounter[] inputs = counters(probe.pattern().getInputs().length);
        for (var input : probe.oneCraftInputs()) {
            if (input.slot() < 0 || input.slot() >= inputs.length || input.amount() <= 0) return null;
            inputs[input.slot()].add(input.key(), input.amount());
        }
        var preview = ECOFastPathFacade.prepareAllocated(provider, probe.pattern(), inputs,
            probe.requestedMaxCrafts(), level, null);
        if (preview == null || preview.craftCount() < 2) return null;
        return new OmniBatchAdmission() {
            private boolean closed;
            public long maxCrafts() { return preview.craftCount(); }
            public void close() { closed = true; }

            public void commit(OmniBatchDelivery delivery) {
                if (closed) throw new IllegalStateException("Admission already closed or submitted");
                closed = true;
                var request = delivery.request();
                if (!request.pattern().equals(probe.pattern()) || request.craftCount() < 2
                        || request.craftCount() > maxCrafts()) {
                    reject(delivery);
                    return;
                }
                var selected = counters(inputs.length);
                for (var input : request.inputs()) {
                    if (input.slot() < 0 || input.slot() >= selected.length || input.amount() <= 0
                            || input.amount() % request.craftCount() != 0) {
                        reject(delivery);
                        return;
                    }
                    selected[input.slot()].add(input.key(), input.amount() / request.craftCount());
                }
                var batch = ECOFastPathFacade.prepareAllocated(provider, request.pattern(), selected,
                    request.craftCount(), level, request.craftingJobId());
                if (batch == null || batch.craftCount() != request.craftCount()
                        || !sameOutputs(batch, request.expectedOutputs())) {
                    reject(delivery);
                    return;
                }
                // Once submit succeeds, no catch block may turn a failing receipt callback into rejection.
                if (!batch.submit(amount -> new ECOFastPathFacade.Reservation() {
                    public void commit() {}
                    public void refund() {}
                })) {
                    reject(delivery);
                    return;
                }
                delivery.accept(new OmniBatchDelivery.Receipt(
                    OmniBatchDelivery.Ownership.TRANSFERRED_TO_DURABLE_TARGET,
                    OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
            }
        };
    }

    private static boolean sameOutputs(ECOFastPathFacade.PreparedBatch batch,
            java.util.List<appeng.api.stacks.GenericStack> expected) {
        var actual = new KeyCounter();
        var claimed = new KeyCounter();
        for (var stack : batch.outputs()) actual.add(stack.what(), stack.amount());
        for (var stack : expected) {
            if (stack.amount() <= 0) return false;
            Math.addExact(claimed.get(stack.what()), stack.amount());
            claimed.add(stack.what(), stack.amount());
        }
        for (var entry : actual) {
            if (claimed.get(entry.getKey()) != entry.getLongValue()) return false;
            claimed.remove(entry.getKey(), entry.getLongValue());
        }
        claimed.removeZeros();
        return claimed.isEmpty();
    }

    private static KeyCounter[] counters(int count) {
        var result = new KeyCounter[count];
        for (int i = 0; i < count; i++) result[i] = new KeyCounter();
        return result;
    }

    private static void reject(OmniBatchDelivery delivery) {
        delivery.reject(new OmniBatchDelivery.Rejection(OmniBatchDelivery.RejectReason.UNSUPPORTED_INPUT));
    }
}
