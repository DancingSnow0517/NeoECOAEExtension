package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOFastPathDiagnostics {
    private static final String LOGGER_PREFIX = NeoECOAE.MOD_ID + ".fastpath";
    private static final Logger CONSTRAINT_LOGGER = logger("constraint");
    private static final Logger CACHE_LOGGER = logger("cache");
    private static final Logger APPLY_LOGGER = logger("apply");
    private static final Logger TICK_LOGGER = logger("tick");
    private static final Logger RESERVATION_LOGGER = logger("reservation");
    private static final Logger FAILURE_LOGGER = logger("failure");
    private static final Logger FALLBACK_LOGGER = logger("fallback");
    private static final Logger INELIGIBLE_LOGGER = logger("ineligible");
    private static final Logger CPU_TICK_LOGGER = logger("cpu_tick");
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_LOGS_PER_TICK = 32;
    private static final long CACHE_DEBUG_MICROS = 500L;
    private static final long CACHE_WARN_MICROS = 1_000L;
    private static final long APPLY_DEBUG_MICROS = 2_000L;
    private static final long APPLY_INFO_MICROS = 10_000L;
    private static final long APPLY_WARN_MICROS = 50_000L;
    private static final long APPLY_CAPTURE_UNATTRIBUTED_MICROS = 10_000L;
    private static final int FALLBACK_REPEAT_INFO_THRESHOLD = 32;
    private static final long PROFILE_ANOMALY_MICROS = 10_000L;
    private static final long PROFILE_WARN_MICROS = 50_000L;
    private static final long PROFILE_SUMMARY_INTERVAL_TICKS = 200L;
    private static final int PROFILE_HISTOGRAM_BUCKETS = 64;
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();
    private static final Set<BatchDecisionKey> BATCH_DECISIONS = new LinkedHashSet<>();
    private static final Map<FallbackKey, Integer> FALLBACK_COUNTS = new LinkedHashMap<>();
    private static final Set<FallbackKey> FALLBACK_REPEAT_LOGGED = new LinkedHashSet<>();
    private static final Set<RoutineFallbackKey> ROUTINE_FALLBACK_LOGGED = new LinkedHashSet<>();
    private static final Set<String> ROUTINE_INELIGIBLE_LOGGED = new LinkedHashSet<>();

    private static long profileTick = Long.MIN_VALUE;
    private static long profileApplyCount;
    private static long profileTotalApplyMicros;
    private static long profileMaxApplyMicros;
    private static long profileTotalWorkerCommitMicros;
    private static long profileTotalPrepareMicros;
    private static long profileTotalUnattributedMicros;
    private static long profileSlowApplyCount;
    private static final long[] profileApplyHistogram = new long[PROFILE_HISTOGRAM_BUCKETS];

    private static long profileWindowStartTick = Long.MIN_VALUE;
    private static long profileWindowApplyCount;
    private static long profileWindowTotalApplyMicros;
    private static long profileWindowMaxApplyMicros;
    private static long profileWindowTotalWorkerCommitMicros;
    private static long profileWindowTotalPrepareMicros;
    private static long profileWindowTotalUnattributedMicros;
    private static long profileWindowSlowApplyCount;

    private static long budgetTick = Long.MIN_VALUE;
    private static int logsThisTick;

    private ECOFastPathDiagnostics() {
    }

    public static void logSlowPath(
        ECOExtractedPatternExecution execution,
        ECOFastPathFallbackReason reason,
        BlockPos workerPos,
        long tick
    ) {
        if (reason == ECOFastPathFallbackReason.NO_ECO_PATTERN_BUS) {
            logIneligible(execution, reason, stageFor(reason), workerPos, tick, "fallback_to_ae2");
        } else if (isExpectedFallback(reason)) {
            logExpectedFallback(execution, reason, stageFor(reason), workerPos, tick, "fallback_to_ae2");
        } else {
            logFailure(execution, reason, stageFor(reason), workerPos, tick, "fallback_to_ae2");
        }
    }

    public static void logExpectedFallback(
        ECOExtractedPatternExecution execution,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        boolean debugEnabled = FALLBACK_LOGGER.isDebugEnabled();
        boolean infoEnabled = FALLBACK_LOGGER.isInfoEnabled();
        if (!NEConfig.debugEcoFastPath || !debugEnabled && !infoEnabled) {
            return;
        }
        PatternDescription pattern = describe(execution.details());
        FallbackKey fallbackKey = new FallbackKey(
            reason, pattern.definition(), pattern.identityHash(), pattern.implementation());
        RoutineFallbackKey routineKey = new RoutineFallbackKey(reason, pattern.implementation());
        int count;
        boolean first;
        boolean repeatThresholdReached;
        synchronized (LOGGED) {
            count = Math.min(
                FALLBACK_REPEAT_INFO_THRESHOLD,
                FALLBACK_COUNTS.getOrDefault(fallbackKey, 0) + 1);
            FALLBACK_COUNTS.put(fallbackKey, count);
            trimFallbackCounts();
            first = ROUTINE_FALLBACK_LOGGED.add(routineKey);
            repeatThresholdReached = infoEnabled && count == FALLBACK_REPEAT_INFO_THRESHOLD
                && FALLBACK_REPEAT_LOGGED.add(fallbackKey);
        }
        if (first && debugEnabled) {
            logDiagnostic(
                FALLBACK_LOGGER, execution.details(), reason, stage, ownerPos, tick,
                context, "fallback", false, "normal_fallback", false);
        }
        if (repeatThresholdReached) {
            DiagnosticKey key = new DiagnosticKey(
                reason, stage, pattern.definition(), pattern.identityHash(), "",
                pattern.implementation(), "fallback_repeat");
            if (!reserveLog(key, tick)) {
                return;
            }
            FALLBACK_LOGGER.info(
                "ECO FastPath [FP-FALLBACK] repeated fallback: stage={} reason={} definition={} definitionHash={} implementation={} owner={} tick={} repeats={}",
                stage,
                reason.code(),
                pattern.definition(),
                Integer.toUnsignedString(pattern.identityHash(), 16),
                pattern.implementation(),
                ownerPos.toShortString(),
                tick,
                count
            );
        }
    }

    public static void logNotBatched(
        ECOExtractedPatternExecution execution,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        if (!NEConfig.debugEcoFastPath || !CONSTRAINT_LOGGER.isDebugEnabled()) {
            return;
        }
        logDiagnostic(
            CONSTRAINT_LOGGER, execution.details(), reason, stage, ownerPos, tick,
            context, "not_batched", false, context, true);
    }

    public static void logFailure(
        ECOExtractedPatternExecution execution,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }

        logFailure(execution.details(), reason, stage, ownerPos, tick, context);
    }

    public static void logIneligible(
        ECOExtractedPatternExecution execution,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        if (!INELIGIBLE_LOGGER.isDebugEnabled()) {
            return;
        }
        PatternDescription pattern = describe(execution.details());
        synchronized (LOGGED) {
            if (!ROUTINE_INELIGIBLE_LOGGED.add(pattern.implementation())) {
                return;
            }
        }
        logDiagnostic(
            INELIGIBLE_LOGGER, execution.details(), reason, stage, ownerPos, tick,
            context, "ineligible", false, "normal_ineligible", false);
    }

    public static void logBatchFailure(
        ECOBatchCraftingRequest request,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        logFailure(request.details(), reason, stage, ownerPos, tick,
            "batch=" + request.batchSize() + " " + context);
    }

    public static PatternPrepareTiming logBatchDecision(
        ECOExtractedPatternExecution execution,
        BlockPos ownerPos,
        long tick,
        long taskRemaining,
        long requested,
        long laneLimit,
        long safeLimit,
        long energyLimit,
        long coolantLimit,
        long inventoryLimit,
        String inventoryConstraint,
        boolean currentCraftIncluded,
        long reservedForCurrentCraft,
        long inventoryAvailable,
        long inventoryPerCraft,
        long additionalCraftsByInventory,
        long requestedAdditionalCrafts,
        long lookupMicros,
        int busesScanned,
        int candidateBuses,
        int verifiedCandidates,
        int offerLookups,
        long finalBatch
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return PatternPrepareTiming.empty();
        }
        long gateStarted = System.nanoTime();
        boolean enabled = shouldLogBatchDecision(requested, finalBatch);
        long gateMicros = elapsedMicros(gateStarted);
        if (!enabled) {
            return PatternPrepareTiming.empty();
        }
        return logBatchDecisionEnabled(
            execution,
            ownerPos,
            tick,
            taskRemaining,
            requested,
            laneLimit,
            safeLimit,
            energyLimit,
            coolantLimit,
            inventoryLimit,
            inventoryConstraint,
            currentCraftIncluded,
            reservedForCurrentCraft,
            inventoryAvailable,
            inventoryPerCraft,
            additionalCraftsByInventory,
            requestedAdditionalCrafts,
            lookupMicros,
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            offerLookups,
            finalBatch,
            gateMicros
        );
    }

    private static PatternPrepareTiming logBatchDecisionEnabled(
        ECOExtractedPatternExecution execution,
        BlockPos ownerPos,
        long tick,
        long taskRemaining,
        long requested,
        long laneLimit,
        long safeLimit,
        long energyLimit,
        long coolantLimit,
        long inventoryLimit,
        String inventoryConstraint,
        boolean currentCraftIncluded,
        long reservedForCurrentCraft,
        long inventoryAvailable,
        long inventoryPerCraft,
        long additionalCraftsByInventory,
        long requestedAdditionalCrafts,
        long lookupMicros,
        int busesScanned,
        int candidateBuses,
        int verifiedCandidates,
        int offerLookups,
        long finalBatch,
        long patternGateMicros
    ) {
        long setupStarted = System.nanoTime();
        boolean info = isSignificantBatchConstraint(requested, finalBatch);
        PatternPrepareTimingBuilder timing = new PatternPrepareTimingBuilder();
        timing.patternGateMicros = patternGateMicros;
        timing.patternDecisionSetupMicros = elapsedMicros(setupStarted);
        PatternDescription pattern = describe(execution.details(), timing);
        long keyStarted = System.nanoTime();
        BatchDecisionKey key = new BatchDecisionKey(tick, pattern.definition(), pattern.identityHash());
        synchronized (LOGGED) {
            if (!BATCH_DECISIONS.add(key)) {
                timing.patternKeySetMicros = elapsedMicros(keyStarted);
                return timing.build();
            }
            trimBatchDecisions();
        }
        timing.patternKeySetMicros = elapsedMicros(keyStarted);
        long formulaStarted = System.nanoTime();
        String formula = inventoryFormula(currentCraftIncluded, reservedForCurrentCraft, inventoryAvailable,
            inventoryPerCraft, additionalCraftsByInventory, requestedAdditionalCrafts);
        timing.patternFormulaMicros = elapsedMicros(formulaStarted);
        long loggerStarted = System.nanoTime();
        logInfoOrDebug(CONSTRAINT_LOGGER, info,
            "ECO FastPath [FP-CONSTRAINT] batch constrained: definition={} definitionHash={} primaryOutput={} owner={} tick={} taskRemaining={} requested={} laneLimit={} safeLimit={} energyLimit={} coolantLimit={} inventoryLimit={} inventoryConstraint={} currentCraftIncluded={} reservedForCurrentCraft={} inventoryAvailable={} additionalCraftsByInventory={} inventoryFormula={} lookupMicros={} busesScanned={} candidateBuses={} verifiedCandidates={} offerLookups={} finalBatch={}",
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            ownerPos.toShortString(),
            tick,
            taskRemaining,
            requested,
            laneLimit,
            safeLimit,
            energyLimit,
            coolantLimit,
            inventoryLimit,
            inventoryConstraint,
            currentCraftIncluded,
            reservedForCurrentCraft,
            inventoryAvailable,
            additionalCraftsByInventory,
            formula,
            lookupMicros,
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            offerLookups,
            finalBatch
        );
        timing.patternDiagnosticEmitMicros = elapsedMicros(loggerStarted);
        return timing.build();
    }

    /** Returns whether a batch decision can produce a diagnostic at the current log level. */
    public static boolean shouldLogBatchDecision(long requested, long finalBatch) {
        if (!NEConfig.debugEcoFastPath || finalBatch >= requested) {
            return false;
        }
        return isSignificantBatchConstraint(requested, finalBatch) || CONSTRAINT_LOGGER.isDebugEnabled();
    }

    /** A one-craft pipeline boundary is debug-only; materially constrained batches stay visible at INFO. */
    static boolean isSignificantBatchConstraint(long requested, long finalBatch) {
        if (requested <= 0L || finalBatch >= requested) {
            return false;
        }
        long reduction = requested - finalBatch;
        long tenPercent = requested / 10L;
        return reduction > tenPercent || finalBatch <= 32L && requested >= 1_024L;
    }

    public static void logCacheLookup(
        ECOExtractedPatternExecution execution,
        BlockPos ownerPos,
        long tick,
        int patternBuses,
        int busesScanned,
        int candidateBuses,
        int verifiedCandidates,
        int offerLookups,
        long lookupMicros
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        if (lookupMicros <= CACHE_DEBUG_MICROS) {
            return;
        }
        boolean warning = lookupMicros > CACHE_WARN_MICROS;
        if (!warning && !CACHE_LOGGER.isDebugEnabled()) {
            return;
        }
        PatternDescription pattern = describe(execution.details());
        DiagnosticKey key = new DiagnosticKey(
            ECOFastPathFallbackReason.NO_BATCH_OFFER,
            ECOFastPathStage.CACHE_LOOKUP,
            pattern.definition(),
            pattern.identityHash(),
            pattern.primaryOutput(),
            pattern.implementation(),
            "cache_lookup_performance"
        );
        if (!reserveLog(key, tick)) {
            return;
        }
        logWarnOrDebug(CACHE_LOGGER, warning,
            "ECO FastPath [FP-CACHE] cache lookup: definition={} definitionHash={} primaryOutput={} owner={} tick={} patternBuses={} busesScanned={} candidateBuses={} verifiedCandidates={} offerLookups={} lookupMicros={}",
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            ownerPos.toShortString(),
            tick,
            patternBuses,
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            offerLookups,
            lookupMicros
        );
    }

    public static void logBatchApplyTiming(
        ECOExtractedPatternExecution execution,
        BlockPos ownerPos,
        long tick,
        long batchSize,
        long lookupMicros,
        long resourceLimitMicros,
        long energyReservationMicros,
        long inputExtractionMicros,
        long workerCommitMicros,
        long cpuAccountingMicros,
        long prepareMicros,
        long preparePatternMicros,
        long patternDetailsMicros,
        long patternInputsMicros,
        long patternOutputsMicros,
        long patternKeyNormalizeMicros,
        long patternHashOrLookupMicros,
        long patternMiscMicros,
        long prepareInventorySnapshotMicros,
        long prepareInputsMicros,
        long prepareWorkerStateMicros,
        long prepareMiscMicros,
        long postExtractionMicros,
        long postCommitMicros,
        long finalizeMicros,
        long unattributedMicros,
        long totalApplyMicros,
        PatternPrepareTiming patternTiming
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        if (!shouldCaptureBatchApplyTiming(totalApplyMicros, unattributedMicros)) {
            return;
        }
        boolean info = totalApplyMicros >= APPLY_INFO_MICROS
            || unattributedMicros >= APPLY_CAPTURE_UNATTRIBUTED_MICROS;
        boolean warning = totalApplyMicros >= APPLY_WARN_MICROS
            || unattributedMicros >= APPLY_WARN_MICROS;
        PatternDescription pattern = describe(execution.details());
        DiagnosticKey key = new DiagnosticKey(
            ECOFastPathFallbackReason.NO_BATCH_OFFER,
            ECOFastPathStage.PROVIDER_DISPATCH,
            pattern.definition(),
            pattern.identityHash(),
            pattern.primaryOutput(),
            pattern.implementation(),
            warning ? "slow_apply_warn" : info ? "slow_apply_info" : "slow_apply_debug"
        );
        if (!reserveLog(key, tick)) {
            return;
        }
        long patternUnaccountedMicros = patternUnaccountedMicros(
            preparePatternMicros,
            patternDetailsMicros,
            patternInputsMicros,
            patternOutputsMicros,
            patternKeyNormalizeMicros,
            patternHashOrLookupMicros,
            patternMiscMicros,
            patternTiming
        );
        logSlowTiming(APPLY_LOGGER, warning, info,
            info
                ? "ECO FastPath [FP-APPLY] slow apply: definition={} definitionHash={} primaryOutput={} owner={} tick={} batch={} lookupMicros={} resourceLimitMicros={} energyReservationMicros={} inputExtractionMicros={} workerCommitMicros={} cpuAccountingMicros={} prepareMicros={} preparePatternMicros={} patternBreakdownEnabled={} patternGateMicros={} patternDecisionSetupMicros={} patternDetailsMicros={} patternInputsMicros={} patternOutputsMicros={} patternKeyNormalizeMicros={} patternHashOrLookupMicros={} patternKeySetMicros={} patternFormulaMicros={} patternDiagnosticEmitMicros={} patternReturnMicros={} patternMiscMicros={} patternUnaccountedMicros={} prepareInventorySnapshotMicros={} prepareInputsMicros={} prepareWorkerStateMicros={} prepareMiscMicros={} postExtractionMicros={} postCommitMicros={} finalizeMicros={} unattributedMicros={} totalApplyMicros={}"
                : "ECO FastPath [FP-APPLY] slow apply debug: definition={} definitionHash={} primaryOutput={} owner={} tick={} batch={} lookupMicros={} resourceLimitMicros={} energyReservationMicros={} inputExtractionMicros={} workerCommitMicros={} cpuAccountingMicros={} prepareMicros={} preparePatternMicros={} patternBreakdownEnabled={} patternGateMicros={} patternDecisionSetupMicros={} patternDetailsMicros={} patternInputsMicros={} patternOutputsMicros={} patternKeyNormalizeMicros={} patternHashOrLookupMicros={} patternKeySetMicros={} patternFormulaMicros={} patternDiagnosticEmitMicros={} patternReturnMicros={} patternMiscMicros={} patternUnaccountedMicros={} prepareInventorySnapshotMicros={} prepareInputsMicros={} prepareWorkerStateMicros={} prepareMiscMicros={} postExtractionMicros={} postCommitMicros={} finalizeMicros={} unattributedMicros={} totalApplyMicros={}",
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            ownerPos.toShortString(),
            tick,
            batchSize,
            lookupMicros,
            resourceLimitMicros,
            energyReservationMicros,
            inputExtractionMicros,
            workerCommitMicros,
            cpuAccountingMicros,
            prepareMicros,
            preparePatternMicros,
            patternTiming.breakdownEnabled(),
            patternTiming.patternGateMicros(),
            patternTiming.patternDecisionSetupMicros(),
            patternDetailsMicros,
            patternInputsMicros,
            patternOutputsMicros,
            patternKeyNormalizeMicros,
            patternHashOrLookupMicros,
            patternTiming.patternKeySetMicros(),
            patternTiming.patternFormulaMicros(),
            patternTiming.patternDiagnosticEmitMicros(),
            patternTiming.patternReturnMicros(),
            patternMiscMicros,
            patternUnaccountedMicros,
            prepareInventorySnapshotMicros,
            prepareInputsMicros,
            prepareWorkerStateMicros,
            prepareMiscMicros,
            postExtractionMicros,
            postCommitMicros,
            finalizeMicros,
            unattributedMicros,
            totalApplyMicros
        );
    }

    static long patternUnaccountedMicros(
        long preparePatternMicros,
        long patternDetailsMicros,
        long patternInputsMicros,
        long patternOutputsMicros,
        long patternKeyNormalizeMicros,
        long patternHashOrLookupMicros,
        long patternMiscMicros
    ) {
        long accounted = patternDetailsMicros
            + patternInputsMicros
            + patternOutputsMicros
            + patternKeyNormalizeMicros
            + patternHashOrLookupMicros
            + patternMiscMicros;
        return Math.max(0L, preparePatternMicros - accounted);
    }

    static long patternUnaccountedMicros(
        long preparePatternMicros,
        long patternDetailsMicros,
        long patternInputsMicros,
        long patternOutputsMicros,
        long patternKeyNormalizeMicros,
        long patternHashOrLookupMicros,
        long patternMiscMicros,
        PatternPrepareTiming timing
    ) {
        if (timing == null || !timing.breakdownEnabled()) {
            return -1L;
        }
        long accounted = patternDetailsMicros
            + patternInputsMicros
            + patternOutputsMicros
            + patternKeyNormalizeMicros
            + patternHashOrLookupMicros
            + patternMiscMicros
            + timing.patternGateMicros()
            + timing.patternDecisionSetupMicros()
            + timing.patternKeySetMicros()
            + timing.patternFormulaMicros()
            + timing.patternDiagnosticEmitMicros()
            + timing.patternReturnMicros();
        return Math.max(0L, preparePatternMicros - accounted);
    }

    /** Single-call detail is reserved for genuine outliers; ordinary samples are represented by the tick profile. */
    static boolean shouldCaptureBatchApplyTiming(long totalApplyMicros, long unattributedMicros) {
        return totalApplyMicros > APPLY_DEBUG_MICROS
            && (totalApplyMicros >= APPLY_INFO_MICROS
                || unattributedMicros >= APPLY_CAPTURE_UNATTRIBUTED_MICROS);
    }

    public static void recordBatchApplyTiming(
        long tick,
        long totalApplyMicros,
        long workerCommitMicros,
        long prepareMicros,
        long unattributedMicros
    ) {
        if (!NEConfig.debugEcoFastPath
            || !TICK_LOGGER.isDebugEnabled() && !TICK_LOGGER.isInfoEnabled() && !TICK_LOGGER.isWarnEnabled()) {
            return;
        }
        TickProfileSnapshot completed = null;
        synchronized (LOGGED) {
            if (profileTick != Long.MIN_VALUE && profileTick != tick && profileApplyCount > 0L) {
                completed = snapshotProfile();
                resetProfile(tick);
            } else if (profileTick != tick) {
                resetProfile(tick);
            }
            profileApplyCount = saturatingAdd(profileApplyCount, 1L);
            profileTotalApplyMicros = saturatingAdd(profileTotalApplyMicros, Math.max(0L, totalApplyMicros));
            profileMaxApplyMicros = Math.max(profileMaxApplyMicros, Math.max(0L, totalApplyMicros));
            profileTotalWorkerCommitMicros = saturatingAdd(
                profileTotalWorkerCommitMicros, Math.max(0L, workerCommitMicros));
            profileTotalPrepareMicros = saturatingAdd(
                profileTotalPrepareMicros, Math.max(0L, prepareMicros));
            profileTotalUnattributedMicros = saturatingAdd(
                profileTotalUnattributedMicros, Math.max(0L, unattributedMicros));
            if (totalApplyMicros > APPLY_DEBUG_MICROS) {
                profileSlowApplyCount = saturatingAdd(profileSlowApplyCount, 1L);
            }
            profileApplyHistogram[profileHistogramBucket(totalApplyMicros)] = saturatingAdd(
                profileApplyHistogram[profileHistogramBucket(totalApplyMicros)], 1L);
            recordProfileWindow(tick, totalApplyMicros, workerCommitMicros, prepareMicros, unattributedMicros);
        }
        if (completed != null) {
            logTickProfile(completed);
        }
    }

    public static long currentTickApplyMicros(long tick) {
        if (!NEConfig.debugEcoFastPath) {
            return 0L;
        }
        synchronized (LOGGED) {
            return profileTick == tick ? profileTotalApplyMicros : 0L;
        }
    }

    public static void flushTickProfile(long nextTick) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        TickProfileSnapshot completed = null;
        ProfileWindowSnapshot window = null;
        synchronized (LOGGED) {
            if (profileApplyCount > 0L && profileTick != nextTick) {
                completed = snapshotProfile();
            }
            if (profileTick != nextTick) {
                resetProfile(nextTick);
            }
            if (profileWindowStartTick != Long.MIN_VALUE
                && nextTick >= profileWindowStartTick
                && nextTick - profileWindowStartTick >= PROFILE_SUMMARY_INTERVAL_TICKS) {
                if (profileWindowApplyCount > 0L) {
                    window = snapshotProfileWindow(nextTick);
                }
                resetProfileWindow(nextTick);
            }
        }
        if (completed != null) {
            logTickProfile(completed);
        }
        if (window != null) {
            logProfileWindow(window);
        }
    }

    public static void logBatchEjectionTiming(
        BlockPos ownerPos,
        long tick,
        KeyCounter outputs,
        KeyCounter remainder,
        long collectMicros,
        long deliveryMicros,
        long totalMicros
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        if (totalMicros <= APPLY_DEBUG_MICROS) {
            return;
        }
        boolean info = totalMicros >= APPLY_INFO_MICROS;
        boolean warning = totalMicros >= APPLY_WARN_MICROS;
        if (!info && !APPLY_LOGGER.isDebugEnabled()) {
            return;
        }
        DiagnosticKey key = new DiagnosticKey(
            ECOFastPathFallbackReason.NO_BATCH_OFFER,
            ECOFastPathStage.PROVIDER_DISPATCH,
            "worker=" + ownerPos.toShortString(),
            ownerPos.hashCode(),
            "batch_output",
            "ejectOutputs",
            warning ? "slow_ejection_warn" : info ? "slow_ejection_info" : "slow_ejection_debug"
        );
        if (!reserveLog(key, tick)) {
            return;
        }
        CounterSummary outputSummary = summarizeCounter(outputs);
        CounterSummary remainderSummary = summarizeCounter(remainder);
        logSlowTiming(APPLY_LOGGER, warning, info,
            info
                ? "ECO FastPath slow ejection: owner={} tick={} outputKeys={} outputUnits={} remainderKeys={} remainderUnits={} collectMicros={} deliveryMicros={} totalEjectionMicros={}"
                : "ECO FastPath slow ejection debug: owner={} tick={} outputKeys={} outputUnits={} remainderKeys={} remainderUnits={} collectMicros={} deliveryMicros={} totalEjectionMicros={}",
            ownerPos.toShortString(),
            tick,
            outputSummary.keys(),
            outputSummary.units(),
            remainderSummary.keys(),
            remainderSummary.units(),
            collectMicros,
            deliveryMicros,
            totalMicros
        );
    }

    public static void logCpuTickProfile(
        BlockPos ownerPos,
        long tick,
        long totalCpuTickMicros,
        long fastPathApplyMicros,
        long fastPathCoordinationMicros,
        long ejectionMicros,
        long schedulerMicros,
        long dependencyMicros,
        long fallbackMicros,
        long fallbackFastPathMicros,
        long fallbackAe2ltMicros,
        long fallbackMegacellsMicros,
        long fallbackProviderMicros,
        long fastPathFallbackCount,
        long ae2ltFallbackCount,
        long megacellsFallbackCount,
        long providerAttemptCount,
        long taskIterationMicros,
        long taskStateMicros,
        long patternPreparationMicros,
        long pendingInputSnapshotMicros,
        long inputDiagnosticMicros,
        long accountingMicros,
        long statusChangeMicros,
        long setupMicros,
        long trueOtherMicros,
        long inputDiagnosticCount,
        long inputDiagnosticUniqueKeys,
        long inputDiagnosticCacheHits,
        long inputDiagnosticCacheMisses,
        long inputDiagnosticNetworkMicros,
        long dependencyWaitDiagnosticCount,
        long hardFailureDiagnosticCount,
        int taskCount
    ) {
        if (!NEConfig.debugEcoFastPath || totalCpuTickMicros < APPLY_INFO_MICROS) {
            return;
        }
        boolean warning = totalCpuTickMicros >= APPLY_WARN_MICROS
            || trueOtherMicros >= APPLY_WARN_MICROS;
        DiagnosticKey key = new DiagnosticKey(
            ECOFastPathFallbackReason.NO_BATCH_OFFER,
            ECOFastPathStage.ACCOUNTING,
            "owner=" + ownerPos.toShortString() + " tick=" + tick,
            ownerPos.hashCode(),
            "cpu_tick",
            "ECOCraftingCPULogic",
            warning ? "cpu_tick_warn" : "cpu_tick_info"
        );
        if (!reserveLog(key, tick)) {
            return;
        }
        if (warning) {
            CPU_TICK_LOGGER.warn(
                "ECO crafting CPU tick profile: owner={} tick={} taskCount={} inputDiagnosticCallsThisTick={} inputDiagnosticUniqueKeysThisTick={} inputDiagnosticCacheHitsThisTick={} inputDiagnosticCacheMissesThisTick={} inputDiagnosticNetworkMicrosThisTick={} dependencyWaitDiagnosticsThisTick={} hardFailureDiagnosticsThisTick={} totalCpuTickMicros={} fastPathApplyMicros={} fastPathCoordinationMicros={} ejectionMicros={} schedulerMicros={} dependencyMicros={} fallbackMicros={} fallbackFastPathMicros={} fallbackAe2ltMicros={} fallbackMegacellsMicros={} fallbackProviderMicros={} fastPathFallbackCountThisTick={} ae2ltFallbackCountThisTick={} megacellsFallbackCountThisTick={} providerAttemptCountThisTick={} taskIterationMicros={} taskStateMicros={} patternPreparationMicros={} pendingInputSnapshotMicros={} inputDiagnosticMicros={} accountingMicros={} statusChangeMicros={} setupMicros={} trueOtherMicros={}",
                ownerPos.toShortString(), tick, taskCount, inputDiagnosticCount, inputDiagnosticUniqueKeys,
                inputDiagnosticCacheHits, inputDiagnosticCacheMisses, inputDiagnosticNetworkMicros,
                dependencyWaitDiagnosticCount, hardFailureDiagnosticCount, totalCpuTickMicros,
                fastPathApplyMicros, fastPathCoordinationMicros, ejectionMicros,
                schedulerMicros, dependencyMicros, fallbackMicros, fallbackFastPathMicros,
                fallbackAe2ltMicros, fallbackMegacellsMicros, fallbackProviderMicros,
                fastPathFallbackCount, ae2ltFallbackCount, megacellsFallbackCount, providerAttemptCount,
                taskIterationMicros, taskStateMicros,
                patternPreparationMicros, pendingInputSnapshotMicros, inputDiagnosticMicros, accountingMicros,
                statusChangeMicros, setupMicros, trueOtherMicros
            );
        } else {
            CPU_TICK_LOGGER.debug(
                "ECO crafting CPU tick profile: owner={} tick={} taskCount={} inputDiagnosticCallsThisTick={} inputDiagnosticUniqueKeysThisTick={} inputDiagnosticCacheHitsThisTick={} inputDiagnosticCacheMissesThisTick={} inputDiagnosticNetworkMicrosThisTick={} dependencyWaitDiagnosticsThisTick={} hardFailureDiagnosticsThisTick={} totalCpuTickMicros={} fastPathApplyMicros={} fastPathCoordinationMicros={} ejectionMicros={} schedulerMicros={} dependencyMicros={} fallbackMicros={} fallbackFastPathMicros={} fallbackAe2ltMicros={} fallbackMegacellsMicros={} fallbackProviderMicros={} fastPathFallbackCountThisTick={} ae2ltFallbackCountThisTick={} megacellsFallbackCountThisTick={} providerAttemptCountThisTick={} taskIterationMicros={} taskStateMicros={} patternPreparationMicros={} pendingInputSnapshotMicros={} inputDiagnosticMicros={} accountingMicros={} statusChangeMicros={} setupMicros={} trueOtherMicros={}",
                ownerPos.toShortString(), tick, taskCount, inputDiagnosticCount, inputDiagnosticUniqueKeys,
                inputDiagnosticCacheHits, inputDiagnosticCacheMisses, inputDiagnosticNetworkMicros,
                dependencyWaitDiagnosticCount, hardFailureDiagnosticCount, totalCpuTickMicros,
                fastPathApplyMicros, fastPathCoordinationMicros, ejectionMicros,
                schedulerMicros, dependencyMicros, fallbackMicros, fallbackFastPathMicros,
                fallbackAe2ltMicros, fallbackMegacellsMicros, fallbackProviderMicros,
                fastPathFallbackCount, ae2ltFallbackCount, megacellsFallbackCount, providerAttemptCount,
                taskIterationMicros, taskStateMicros,
                patternPreparationMicros, pendingInputSnapshotMicros, inputDiagnosticMicros, accountingMicros,
                statusChangeMicros, setupMicros, trueOtherMicros
            );
        }
    }

    public static void logCpuReservation(
        ICraftingPlan plan,
        KeyCounter reservedItems,
        BlockPos ownerPos,
        long tick
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        ReservationSummary reservation = summarizeReservation(plan.usedItems(), reservedItems);
        boolean deficit = !reservation.complete();
        if (!deficit && !RESERVATION_LOGGER.isDebugEnabled()) {
            return;
        }
        long patternCrafts = 0L;
        for (long count : plan.patternTimes().values()) {
            patternCrafts = saturatingAdd(patternCrafts, count);
        }
        logInfoOrDebug(RESERVATION_LOGGER, deficit,
            deficit
                ? "ECO CPU [FP-RESERVATION] reservation deficit: finalOutput={} owner={} tick={} patternCrafts={} patternCount={} requestedKeys={} reservedKeys={} deficitKeys={} reservationComplete={} reservationDetails={}"
                : "ECO CPU [FP-RESERVATION] job reservation: finalOutput={} owner={} tick={} patternCrafts={} patternCount={} requestedKeys={} reservedKeys={} deficitKeys={} reservationComplete={} reservationDetails={}",
            plan.finalOutput(),
            ownerPos.toShortString(),
            tick,
            patternCrafts,
            plan.patternTimes().size(),
            reservation.requestedKeys(),
            reservation.reservedKeys(),
            reservation.deficitKeys(),
            reservation.complete(),
            reservation.complete()
                ? "omitted_when_complete"
                : "usedItems=" + describeCounter(plan.usedItems()) + " reservedItems=" + describeCounter(reservedItems)
        );
    }

    public static void logCpuPreflightFailure(
        IPatternDetails details,
        BlockPos ownerPos,
        long tick,
        long taskRemaining,
        boolean plannedInputsPresent,
        long plannedInputCount,
        int inputExtractionAttempts,
        String missingInputs
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        logFailure(
            details,
            ECOFastPathFallbackReason.INPUT_RESERVATION_FAILED,
            ECOFastPathStage.INPUT_RESERVATION,
            ownerPos,
            tick,
            "first_input_unavailable taskRemaining=" + taskRemaining
                + " attempts=" + inputExtractionAttempts
                + " plannedInputsPresent=" + plannedInputsPresent
                + " plannedInputCount=" + plannedInputCount
                + " missingInputs=" + missingInputs
        );
    }

    private static void logFailure(
        IPatternDetails details,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
    ) {
        logDiagnostic(FAILURE_LOGGER, details, reason, stage, ownerPos, tick, context, "failure", true, context, true);
    }

    private static void logDiagnostic(
        Logger logger,
        IPatternDetails details,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context,
        String kind,
        boolean info,
        String dedupeContext,
        boolean includePrimaryOutputInKey
    ) {
        PatternDescription pattern = describe(details);
        DiagnosticKey key = new DiagnosticKey(
            reason,
            stage,
            pattern.definition(),
            pattern.identityHash(),
            includePrimaryOutputInKey ? pattern.primaryOutput() : "",
            pattern.implementation(),
            dedupeContext
        );
        if (!reserveLog(key, tick)) {
            return;
        }

        logInfoOrDebug(logger, info,
            "ECO FastPath [FP-FAILURE] {}: stage={} reason={} definition={} definitionHash={} primaryOutput={} implementation={} owner={} tick={} context={}",
            kind,
            stage,
            reason.code(),
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            pattern.implementation(),
            ownerPos.toShortString(),
            tick,
            context
        );
    }

    private static boolean isExpectedFallback(ECOFastPathFallbackReason reason) {
        return reason == ECOFastPathFallbackReason.CACHE_MISS_VERIFYING
            || reason == ECOFastPathFallbackReason.NO_BATCH_OFFER;
    }

    private static TickProfileSnapshot snapshotProfile() {
        long rank = Math.max(1L, (profileApplyCount * 95L + 99L) / 100L);
        long seen = 0L;
        long p95BucketUpper = 0L;
        for (int index = 0; index < profileApplyHistogram.length; index++) {
            seen = saturatingAdd(seen, profileApplyHistogram[index]);
            if (seen >= rank) {
                p95BucketUpper = profileHistogramUpperBound(index);
                break;
            }
        }
        return new TickProfileSnapshot(
            profileTick,
            profileApplyCount,
            profileTotalApplyMicros,
            profileMaxApplyMicros,
            p95BucketUpper,
            profileTotalPrepareMicros,
            profileTotalWorkerCommitMicros,
            profileTotalUnattributedMicros,
            profileSlowApplyCount
        );
    }

    private static void resetProfile(long tick) {
        profileTick = tick;
        profileApplyCount = 0L;
        profileTotalApplyMicros = 0L;
        profileMaxApplyMicros = 0L;
        profileTotalWorkerCommitMicros = 0L;
        profileTotalPrepareMicros = 0L;
        profileTotalUnattributedMicros = 0L;
        profileSlowApplyCount = 0L;
        for (int index = 0; index < profileApplyHistogram.length; index++) {
            profileApplyHistogram[index] = 0L;
        }
    }

    private static void recordProfileWindow(
        long tick,
        long totalApplyMicros,
        long workerCommitMicros,
        long prepareMicros,
        long unattributedMicros
    ) {
        if (profileWindowStartTick == Long.MIN_VALUE) {
            profileWindowStartTick = tick;
        }
        profileWindowApplyCount = saturatingAdd(profileWindowApplyCount, 1L);
        profileWindowTotalApplyMicros = saturatingAdd(
            profileWindowTotalApplyMicros, Math.max(0L, totalApplyMicros));
        profileWindowMaxApplyMicros = Math.max(profileWindowMaxApplyMicros, Math.max(0L, totalApplyMicros));
        profileWindowTotalWorkerCommitMicros = saturatingAdd(
            profileWindowTotalWorkerCommitMicros, Math.max(0L, workerCommitMicros));
        profileWindowTotalPrepareMicros = saturatingAdd(
            profileWindowTotalPrepareMicros, Math.max(0L, prepareMicros));
        profileWindowTotalUnattributedMicros = saturatingAdd(
            profileWindowTotalUnattributedMicros, Math.max(0L, unattributedMicros));
        if (totalApplyMicros > APPLY_DEBUG_MICROS) {
            profileWindowSlowApplyCount = saturatingAdd(profileWindowSlowApplyCount, 1L);
        }
    }

    private static ProfileWindowSnapshot snapshotProfileWindow(long endTick) {
        return new ProfileWindowSnapshot(
            profileWindowStartTick,
            endTick,
            profileWindowApplyCount,
            profileWindowTotalApplyMicros,
            profileWindowMaxApplyMicros,
            profileWindowTotalPrepareMicros,
            profileWindowTotalWorkerCommitMicros,
            profileWindowTotalUnattributedMicros,
            profileWindowSlowApplyCount
        );
    }

    private static void resetProfileWindow(long nextTick) {
        profileWindowStartTick = nextTick;
        profileWindowApplyCount = 0L;
        profileWindowTotalApplyMicros = 0L;
        profileWindowMaxApplyMicros = 0L;
        profileWindowTotalWorkerCommitMicros = 0L;
        profileWindowTotalPrepareMicros = 0L;
        profileWindowTotalUnattributedMicros = 0L;
        profileWindowSlowApplyCount = 0L;
    }

    private static int profileHistogramBucket(long micros) {
        if (micros <= 0L) {
            return 0;
        }
        return Math.min(
            PROFILE_HISTOGRAM_BUCKETS - 1,
            Long.SIZE - Long.numberOfLeadingZeros(micros)
        );
    }

    private static long profileHistogramUpperBound(int bucket) {
        if (bucket <= 0) {
            return 0L;
        }
        if (bucket >= PROFILE_HISTOGRAM_BUCKETS - 1) {
            return Long.MAX_VALUE;
        }
        return 1L << bucket;
    }

    private static void logTickProfile(TickProfileSnapshot profile) {
        boolean abnormal = profile.totalApplyMicros() >= PROFILE_ANOMALY_MICROS
            || profile.maxApplyMicros() >= PROFILE_ANOMALY_MICROS
            || profile.totalUnattributedMicros() >= PROFILE_ANOMALY_MICROS;
        if (!abnormal && !TICK_LOGGER.isDebugEnabled()) {
            return;
        }
        if (!abnormal) {
            return;
        }
        boolean warning = profile.totalApplyMicros() >= PROFILE_WARN_MICROS
            || profile.totalUnattributedMicros() >= PROFILE_WARN_MICROS;
        String template = "ECO FastPath [FP-TICK] tick profile: tick={} applyCount={} totalApplyMicros={} maxApplyMicros={} p95BucketUpperMicros={} totalPrepareMicros={} totalWorkerCommitMicros={} totalUnattributedMicros={} slowApplyCount={}";
        if (warning) {
            TICK_LOGGER.warn(
                template,
                profile.tick(),
                profile.applyCount(),
                profile.totalApplyMicros(),
                profile.maxApplyMicros(),
                profile.p95BucketUpperMicros(),
                profile.totalPrepareMicros(),
                profile.totalWorkerCommitMicros(),
                profile.totalUnattributedMicros(),
                profile.slowApplyCount()
            );
        } else {
            logInfoOrDebug(TICK_LOGGER,
                abnormal,
                template,
                profile.tick(),
                profile.applyCount(),
                profile.totalApplyMicros(),
                profile.maxApplyMicros(),
                profile.p95BucketUpperMicros(),
                profile.totalPrepareMicros(),
                profile.totalWorkerCommitMicros(),
                profile.totalUnattributedMicros(),
                profile.slowApplyCount()
            );
        }
    }

    private static void logProfileWindow(ProfileWindowSnapshot profile) {
        if (!TICK_LOGGER.isInfoEnabled()) {
            return;
        }
        TICK_LOGGER.info(
            "ECO FastPath [FP-TICK] periodic profile: startTick={} endTick={} applyCount={} totalApplyMicros={} maxApplyMicros={} totalPrepareMicros={} totalWorkerCommitMicros={} totalUnattributedMicros={} slowApplyCount={}",
            profile.startTick(),
            profile.endTick(),
            profile.applyCount(),
            profile.totalApplyMicros(),
            profile.maxApplyMicros(),
            profile.totalPrepareMicros(),
            profile.totalWorkerCommitMicros(),
            profile.totalUnattributedMicros(),
            profile.slowApplyCount()
        );
    }

    public static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
            BATCH_DECISIONS.clear();
            FALLBACK_COUNTS.clear();
            FALLBACK_REPEAT_LOGGED.clear();
            ROUTINE_FALLBACK_LOGGED.clear();
            ROUTINE_INELIGIBLE_LOGGED.clear();
            budgetTick = Long.MIN_VALUE;
            logsThisTick = 0;
            resetProfile(Long.MIN_VALUE);
            resetProfileWindow(Long.MIN_VALUE);
        }
    }

    private static void logInfoOrDebug(Logger logger, boolean info, String template, Object... arguments) {
        if (info) {
            logger.info(template, arguments);
        } else {
            logger.debug(template, arguments);
        }
    }

    private static void logWarnOrDebug(Logger logger, boolean warning, String template, Object... arguments) {
        if (warning) {
            logger.warn(template, arguments);
        } else {
            logger.debug(template, arguments);
        }
    }

    private static void logSlowTiming(
        Logger logger,
        boolean warning,
        boolean info,
        String template,
        Object... arguments
    ) {
        if (warning) {
            logger.warn(template, arguments);
        } else if (info) {
            logger.info(template, arguments);
        } else {
            logger.debug(template, arguments);
        }
    }

    private static Logger logger(String category) {
        return LoggerFactory.getLogger(LOGGER_PREFIX + "." + category);
    }

    private static boolean reserveLog(DiagnosticKey key, long tick) {
        synchronized (LOGGED) {
            if (budgetTick != tick) {
                budgetTick = tick;
                logsThisTick = 0;
            }
            if (LOGGED.contains(key) || logsThisTick >= MAX_LOGS_PER_TICK) {
                return false;
            }
            LOGGED.add(key);
            trimRetainedEntries();
            logsThisTick++;
            return true;
        }
    }

    private static PatternDescription describe(IPatternDetails details) {
        return describe(details, null);
    }

    private static PatternDescription describe(
        IPatternDetails details,
        PatternPrepareTimingBuilder timing
    ) {
        long detailsStarted = timing == null ? 0L : System.nanoTime();
        Object identity;
        try {
            identity = AE2PatternIntrospection.getStablePatternIdentity(details);
        } catch (Throwable ignored) {
            identity = details;
        }
        if (timing != null) {
            timing.patternDetailsMicros = elapsedMicros(detailsStarted);
        }

        long hashStarted = timing == null ? 0L : System.nanoTime();
        int identityHash = safeHashCode(identity);
        if (timing != null) {
            timing.patternHashOrLookupMicros = elapsedMicros(hashStarted);
        }

        long normalizeStarted = timing == null ? 0L : System.nanoTime();
        String definition = identity instanceof AEKey key ? describeKey(key) : identity.getClass().getName();
        String implementation = details.getClass().getName();
        if (timing != null) {
            timing.patternKeyNormalizeMicros = elapsedMicros(normalizeStarted);
        }

        long outputsStarted = timing == null ? 0L : System.nanoTime();
        String primaryOutput = "unknown";
        try {
            GenericStack output = details.getPrimaryOutput();
            if (output != null) {
                primaryOutput = describeKey(output.what()) + " x" + output.amount();
            }
        } catch (Throwable ignored) {
            // Diagnostics must never affect pattern execution.
        }
        if (timing != null) {
            timing.patternOutputsMicros = elapsedMicros(outputsStarted);
        }
        long descriptionStarted = timing == null ? 0L : System.nanoTime();
        PatternDescription description = new PatternDescription(
            definition,
            identityHash,
            primaryOutput,
            implementation
        );
        if (timing != null) {
            timing.patternMiscMicros = elapsedMicros(descriptionStarted);
        }
        return description;
    }

    private static long elapsedMicros(long started) {
        return started == 0L ? 0L : Math.max(0L, (System.nanoTime() - started) / 1_000L);
    }

    private static int safeHashCode(Object value) {
        try {
            return value.hashCode();
        } catch (Throwable ignored) {
            return System.identityHashCode(value);
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String describeKey(AEKey key) {
        try {
            return key.getId().toString();
        } catch (Throwable ignored) {
            return key.getClass().getName();
        }
    }

    private static String describeCounter(KeyCounter counter) {
        List<String> entries = new ArrayList<>();
        int total = 0;
        for (var entry : counter) {
            total++;
            if (entries.size() < 32) {
                entries.add("key=" + entry.getKey() + " amount=" + entry.getLongValue());
            }
        }
        entries.sort(String::compareTo);
        if (total > entries.size()) {
            entries.add("... total=" + total);
        }
        return entries.toString();
    }

    private static CounterSummary summarizeCounter(KeyCounter counter) {
        int keys = 0;
        long units = 0L;
        if (counter != null) {
            for (var entry : counter) {
                if (entry.getLongValue() <= 0L) {
                    continue;
                }
                keys++;
                units = saturatingAdd(units, entry.getLongValue());
            }
        }
        return new CounterSummary(keys, units);
    }

    private static ReservationSummary summarizeReservation(KeyCounter requested, KeyCounter reserved) {
        Map<AEKey, Long> requestedAmounts = amountsByKey(requested);
        Map<AEKey, Long> reservedAmounts = amountsByKey(reserved);
        int deficitKeys = 0;
        for (Map.Entry<AEKey, Long> entry : requestedAmounts.entrySet()) {
            if (reservedAmounts.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                deficitKeys++;
            }
        }
        return new ReservationSummary(
            requestedAmounts.size(),
            reservedAmounts.size(),
            deficitKeys,
            deficitKeys == 0
        );
    }

    private static Map<AEKey, Long> amountsByKey(KeyCounter counter) {
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        if (counter == null) {
            return amounts;
        }
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) {
                amounts.put(entry.getKey(), entry.getLongValue());
            }
        }
        return amounts;
    }

    private static String inventoryFormula(
        boolean currentCraftIncluded,
        long reservedForCurrentCraft,
        long inventoryAvailable,
        long inventoryPerCraft,
        long additionalCraftsByInventory,
        long requestedAdditionalCrafts
    ) {
        if (!currentCraftIncluded) {
            return "currentCraftIncluded=false";
        }
        if (inventoryPerCraft <= 0L) {
            return "currentCrafts=" + reservedForCurrentCraft + "+min(" + requestedAdditionalCrafts
                + ",unlimited)=" + (reservedForCurrentCraft + additionalCraftsByInventory);
        }
        return "currentCrafts=" + reservedForCurrentCraft + "+min(" + requestedAdditionalCrafts
            + ",floor(" + inventoryAvailable + "/" + inventoryPerCraft + "))="
            + (reservedForCurrentCraft + additionalCraftsByInventory);
    }

    private static ECOFastPathStage stageFor(ECOFastPathFallbackReason reason) {
        return switch (reason) {
            case FAST_PATH_DISABLED, POST_CRAFTING_EVENT, NO_ECO_PATTERN_BUS,
                    INTROSPECTION_UNAVAILABLE, DYNAMIC_SPECIAL, UNSUPPORTED_PATTERN_TYPE, KEY_BUILD_FAILED,
                    OUTPUT_COUNT_NOT_ONE, UNSAFE_EXPECTED_OUTPUT, UNSAFE_CONTAINER_ITEM,
                    UNSAFE_INPUT, STATEFUL_ITEM -> ECOFastPathStage.ELIGIBILITY;
            case CACHE_MISS_VERIFYING, NEGATIVE_CACHE, CACHE_ENTRY_MISMATCH -> ECOFastPathStage.CACHE_LOOKUP;
            case RUNTIME_STACK_CONVERSION_FAILED, OUTPUT_MISMATCH, CONTAINER_MISMATCH,
                    INPUT_MISMATCH, CACHE_VALIDATION_REJECTED -> ECOFastPathStage.CACHE_VERIFY;
            case NO_BATCH_OFFER -> ECOFastPathStage.CACHE_LOOKUP;
            case BATCH_AMOUNT_OVERFLOW -> ECOFastPathStage.RESOURCE_LIMIT;
            case NO_THREAD_SLOT, ENERGY_LIMIT, COOLANT_LIMIT, INVENTORY_LIMIT -> ECOFastPathStage.RESOURCE_LIMIT;
            case INPUT_RESERVATION_FAILED -> ECOFastPathStage.INPUT_RESERVATION;
            case PROVIDER_REJECTED, WORKER_REJECTED, INVALID_BATCH_REQUEST -> ECOFastPathStage.PROVIDER_DISPATCH;
            case ACCOUNTING_FAILED -> ECOFastPathStage.ACCOUNTING;
            case LEGACY_SLOW_EXECUTION -> ECOFastPathStage.SLOW_EXECUTION;
        };
    }

    private static void trimRetainedEntries() {
        if (LOGGED.size() <= MAX_RETAINED_ENTRIES) {
            return;
        }
        Iterator<DiagnosticKey> iterator = LOGGED.iterator();
        iterator.next();
        iterator.remove();
    }

    private static void trimBatchDecisions() {
        while (BATCH_DECISIONS.size() > MAX_RETAINED_ENTRIES) {
            Iterator<BatchDecisionKey> iterator = BATCH_DECISIONS.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private record PatternDescription(
        String definition,
        int identityHash,
        String primaryOutput,
        String implementation
    ) {
    }

    private record DiagnosticKey(
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        String definition,
        int identityHash,
        String primaryOutput,
        String implementation,
        String context
    ) {
    }

    private record BatchDecisionKey(long tick, String definition, int identityHash) {
    }

    private record FallbackKey(
        ECOFastPathFallbackReason reason,
        String definition,
        int identityHash,
        String implementation
    ) {
    }

    private record RoutineFallbackKey(
        ECOFastPathFallbackReason reason,
        String implementation
    ) {
    }

    private record ReservationSummary(int requestedKeys, int reservedKeys, int deficitKeys, boolean complete) {
    }

    private record CounterSummary(int keys, long units) {
    }

    public record PatternPrepareTiming(
        boolean breakdownEnabled,
        long patternGateMicros,
        long patternDecisionSetupMicros,
        long patternDetailsMicros,
        long patternInputsMicros,
        long patternOutputsMicros,
        long patternKeyNormalizeMicros,
        long patternHashOrLookupMicros,
        long patternKeySetMicros,
        long patternFormulaMicros,
        long patternDiagnosticEmitMicros,
        long patternReturnMicros,
        long patternMiscMicros
    ) {
        public static PatternPrepareTiming empty() {
            return new PatternPrepareTiming(false, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

    }

    private static void trimFallbackCounts() {
        while (FALLBACK_COUNTS.size() > MAX_RETAINED_ENTRIES) {
            FallbackKey oldest = FALLBACK_COUNTS.keySet().iterator().next();
            FALLBACK_COUNTS.remove(oldest);
            FALLBACK_REPEAT_LOGGED.remove(oldest);
        }
    }

    private static final class PatternPrepareTimingBuilder {
        private boolean breakdownEnabled = true;
        private long patternGateMicros;
        private long patternDecisionSetupMicros;
        private long patternDetailsMicros;
        private long patternInputsMicros;
        private long patternOutputsMicros;
        private long patternKeyNormalizeMicros;
        private long patternHashOrLookupMicros;
        private long patternKeySetMicros;
        private long patternFormulaMicros;
        private long patternDiagnosticEmitMicros;
        private long patternReturnMicros;
        private long patternMiscMicros;

        private PatternPrepareTiming build() {
            long returnStarted = System.nanoTime();
            new PatternPrepareTiming(
                breakdownEnabled,
                patternGateMicros,
                patternDecisionSetupMicros,
                patternDetailsMicros,
                patternInputsMicros,
                patternOutputsMicros,
                patternKeyNormalizeMicros,
                patternHashOrLookupMicros,
                patternKeySetMicros,
                patternFormulaMicros,
                patternDiagnosticEmitMicros,
                0L,
                patternMiscMicros
            );
            patternReturnMicros = elapsedMicros(returnStarted);
            return new PatternPrepareTiming(
                breakdownEnabled,
                patternGateMicros,
                patternDecisionSetupMicros,
                patternDetailsMicros,
                patternInputsMicros,
                patternOutputsMicros,
                patternKeyNormalizeMicros,
                patternHashOrLookupMicros,
                patternKeySetMicros,
                patternFormulaMicros,
                patternDiagnosticEmitMicros,
                patternReturnMicros,
                patternMiscMicros
            );
        }
    }

    private record TickProfileSnapshot(
        long tick,
        long applyCount,
        long totalApplyMicros,
        long maxApplyMicros,
        long p95BucketUpperMicros,
        long totalPrepareMicros,
        long totalWorkerCommitMicros,
        long totalUnattributedMicros,
        long slowApplyCount
    ) {
    }

    private record ProfileWindowSnapshot(
        long startTick,
        long endTick,
        long applyCount,
        long totalApplyMicros,
        long maxApplyMicros,
        long totalPrepareMicros,
        long totalWorkerCommitMicros,
        long totalUnattributedMicros,
        long slowApplyCount
    ) {
    }
}
