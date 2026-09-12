package cn.dancingsnow.neoecoae.api.me.provider;

import org.jetbrains.annotations.Nullable;

/**
 * Separate extension point for processing-pattern dispatch.
 *
 * <p>The dispatch strategy is intentionally unspecified for now. In particular, this contract
 * does not yet define adaptive doubling, partial ownership, energy settlement, or rollback rules.
 * Those rules must be agreed before this hook is connected to the CPU scheduler.</p>
 */
public interface ECOProcessingPatternDispatchProvider {
    /** Returns this provider's processing dispatch capability, or {@code null} when unavailable. */
    @Nullable
    default ProcessingDispatchCapability eco$processingPatternDispatch(
            ECOProcessingPatternDispatchContext context) {
        return null;
    }

    /** Marker capability reserved for the future processing dispatch strategy. */
    interface ProcessingDispatchCapability {
    }
}
