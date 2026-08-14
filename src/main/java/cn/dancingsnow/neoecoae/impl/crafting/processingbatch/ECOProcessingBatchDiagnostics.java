package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;

/** Low-volume diagnostics for processing-provider batching decisions. */
public final class ECOProcessingBatchDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOProcessingBatchDiagnostics() {
    }

    public static void record(ECOProcessingBatchFallbackReason reason, String detail) {
        if (NEConfig.debugECOProcessingBatch) {
            LOGGER.debug("Processing-provider batch {}: {}", reason, detail);
        }
    }

    public enum ECOProcessingBatchFallbackReason {
        DISABLED,
        NOT_STANDARD_PROVIDER,
        BUSY,
        OFFLINE,
        PATTERN_NOT_PUBLISHED,
        LOCKED,
        BLOCKING,
        DEDICATED_MACHINE,
        UNSUPPORTED_PATTERN,
        NO_TARGET,
        NO_CAPACITY,
        INPUT_OVERFLOW,
        ENERGY_LIMIT,
        INPUT_RESERVATION_FAILED,
        PROVIDER_REJECTED,
        OWNERSHIP_AFTER_EXCEPTION,
        ACCOUNTING_FAILED
    }
}
