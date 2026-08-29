package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.stacks.AEKey;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record ComponentPlanningResult(
    int componentId,
    Type type,
    Status status,
    Map<AEKey, Long> requiredOutputs,
    @Nullable CyclePlanningStatus cycleStatus,
    @Nullable String diagnostic
) {
    public enum Type { ACYCLIC, CYCLIC }
    public enum Status { PLANNED, NOT_REQUIRED, UNRESOLVED, UNSUPPORTED }

    public ComponentPlanningResult {
        requiredOutputs = Map.copyOf(requiredOutputs);
    }
}
