package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Immutable hand-off between planning/confirmation and CPU execution. */
public record ECOExecutionContract(UUID planningId, PlanIdentity.Signature planSignature,
        ExecutionMode mode, @Nullable ECOExecutionPlan executionPlan, @Nullable String error) {
    public ECOExecutionContract {
        Objects.requireNonNull(planningId, "planningId");
        Objects.requireNonNull(planSignature, "planSignature");
        Objects.requireNonNull(mode, "mode");
        if (mode == ExecutionMode.BLOCKED && (error == null || error.isBlank())) {
            throw new IllegalArgumentException("Blocked execution requires an error");
        }
        if ((mode == ExecutionMode.PHASED_DAG || mode == ExecutionMode.ORDERED_CYCLE)
                && executionPlan == null) {
            throw new IllegalArgumentException("Phased execution requires an immutable execution plan");
        }
    }

    public @Nullable ECOExecutionSchedule schedule() {
        return executionPlan == null ? null : executionPlan.schedule();
    }
    public boolean executable() { return mode != ExecutionMode.BLOCKED; }
    public boolean phased() { return mode == ExecutionMode.PHASED_DAG || mode == ExecutionMode.ORDERED_CYCLE; }

    public static ECOExecutionContract nativeContract(UUID planningId, PlanIdentity.Signature signature) {
        return new ECOExecutionContract(planningId, signature, ExecutionMode.NATIVE, null, null);
    }
}
