package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import org.jetbrains.annotations.Nullable;

public record CycleSolveResult(CyclePlanningStatus status, @Nullable String diagnostic) {
    public CycleSolveResult {
        if (status == CyclePlanningStatus.NOT_REQUIRED || status == CyclePlanningStatus.DISABLED) {
            throw new IllegalArgumentException("CycleSolver is called only for enabled, required components");
        }
    }
}
