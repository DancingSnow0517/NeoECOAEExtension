package cn.dancingsnow.neoecoae.api.me;

/** Typed outcome of one provider dispatch attempt. */
public sealed interface DispatchResult permits DispatchResult.Accepted, DispatchResult.Waiting,
        DispatchResult.Rejected, DispatchResult.Fatal {
    record Accepted(long count) implements DispatchResult {
        public Accepted { if (count <= 0) throw new IllegalArgumentException("count must be positive"); }
    }
    record Waiting(WaitReason reason) implements DispatchResult { }
    record Rejected(RejectReason reason) implements DispatchResult { }
    record Fatal(String reason) implements DispatchResult { }

    enum WaitReason { PROVIDER_BUSY, INPUTS_UNAVAILABLE, ENERGY_UNAVAILABLE, CAPACITY_UNAVAILABLE }
    enum RejectReason { PROVIDER_REJECTED, INVALID_PATTERN }
}
