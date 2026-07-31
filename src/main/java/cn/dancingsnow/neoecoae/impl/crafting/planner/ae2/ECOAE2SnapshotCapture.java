package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import java.util.Objects;
import java.util.Optional;

/** Result of capturing AE2 state, including an actionable fallback reason on rejection. */
public record ECOAE2SnapshotCapture(
    Optional<ECOAE2PlanningSnapshot> snapshot,
    ECOPlannerFallbackReason fallbackReason,
    String detail
) {
    public ECOAE2SnapshotCapture {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        fallbackReason = Objects.requireNonNull(fallbackReason, "fallbackReason");
        detail = Objects.requireNonNull(detail, "detail");
        if (snapshot.isPresent() != (fallbackReason == ECOPlannerFallbackReason.FAST_PATH)) {
            throw new IllegalArgumentException("Successful captures must use FAST_PATH and rejections must not");
        }
    }

    public static ECOAE2SnapshotCapture accepted(ECOAE2PlanningSnapshot snapshot) {
        return new ECOAE2SnapshotCapture(Optional.of(snapshot), ECOPlannerFallbackReason.FAST_PATH, "");
    }

    public static ECOAE2SnapshotCapture rejected(ECOPlannerFallbackReason reason, String detail) {
        if (reason == ECOPlannerFallbackReason.FAST_PATH) {
            throw new IllegalArgumentException("A rejected capture needs a fallback reason");
        }
        return new ECOAE2SnapshotCapture(Optional.empty(), reason, detail);
    }
}
