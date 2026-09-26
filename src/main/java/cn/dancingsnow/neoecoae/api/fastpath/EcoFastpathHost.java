package cn.dancingsnow.neoecoae.api.fastpath;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Optional server-side ECO/F-series execution capability. It does not accept foreign task objects. */
public interface EcoFastpathHost {
    int API_VERSION = 1;
    ResourceLocation CAPABILITY_ID = ResourceLocation.fromNamespaceAndPath("neoecoae", "eco_fastpath");

    FastpathCapability inspect(FastpathRequest request);

    FastpathSubmission submit(FastpathRequest request);

    record FastpathRequest(AEItemKey processingId, List<List<GenericStack>> inputsPerCraft,
            long requestedAmount, ResourceLocation targetCapabilityId, int apiVersion, UUID nonce) {
        public FastpathRequest {
            Objects.requireNonNull(processingId, "processingId");
            Objects.requireNonNull(targetCapabilityId, "targetCapabilityId");
            Objects.requireNonNull(nonce, "nonce");
            if (requestedAmount <= 0L) throw new IllegalArgumentException("requestedAmount must be positive");
            inputsPerCraft = List.copyOf(inputsPerCraft.stream().map(List::copyOf).toList());
        }
    }

    record FastpathCapability(int capabilityVersion, ResourceLocation capabilityId,
            HostTier tier, long acceptedAmount, DurationClass estimatedDuration,
            RejectionReason rejectionReason) {
        public FastpathCapability {
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(estimatedDuration, "estimatedDuration");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            if (acceptedAmount < 0L) throw new IllegalArgumentException("acceptedAmount must not be negative");
        }
    }

    record FastpathSubmission(Status status, long acceptedAmount, List<GenericStack> resultSnapshot,
            long unacceptedAmount, boolean retryable, RejectionReason rejectionReason) {
        public FastpathSubmission {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
            resultSnapshot = List.copyOf(resultSnapshot);
            if (acceptedAmount < 0L || unacceptedAmount < 0L) throw new IllegalArgumentException("invalid amounts");
        }
    }

    enum HostTier { FASTPATH, F4, F6, F9 }
    enum DurationClass { IMMEDIATE, SHORT, NORMAL, LONG }
    enum Status { ACCEPTED, PARTIAL, REJECTED }
    enum RejectionReason { NONE, BUSY, UNSUPPORTED_PROCESSING, NO_CAPACITY, VERSION_MISMATCH,
        CAPABILITY_MISMATCH, INVALID_REQUEST, NOT_SERVER_THREAD, SUBMISSION_FAILED }
}
