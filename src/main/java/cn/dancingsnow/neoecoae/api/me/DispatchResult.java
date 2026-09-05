package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import java.util.Map;

/** Typed outcome of one provider dispatch attempt. */
public sealed interface DispatchResult permits DispatchResult.Accepted, DispatchResult.Waiting,
        DispatchResult.Rejected, DispatchResult.Fatal {
    record Accepted(long count, Map<AEKey, Long> consumedInputs) implements DispatchResult {
        public Accepted(long count) {
            this(count, Map.of());
        }

        public Accepted {
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
            if (consumedInputs == null) {
                consumedInputs = Map.of();
            } else {
                for (var entry : consumedInputs.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0L) {
                        throw new IllegalArgumentException("Invalid consumed input contract");
                    }
                }
                consumedInputs = Map.copyOf(consumedInputs);
            }
        }
    }
    record Waiting(WaitReason reason) implements DispatchResult { }
    record Rejected(RejectReason reason) implements DispatchResult { }
    record Fatal(String reason) implements DispatchResult { }

    enum WaitReason { PROVIDER_BUSY, INPUTS_UNAVAILABLE, ENERGY_UNAVAILABLE, CAPACITY_UNAVAILABLE }
    enum RejectReason { PROVIDER_REJECTED, INVALID_PATTERN }
}
