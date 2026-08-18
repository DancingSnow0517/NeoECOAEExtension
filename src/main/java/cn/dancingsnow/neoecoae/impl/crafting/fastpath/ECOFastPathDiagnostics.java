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
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOFastPathDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_LOGS_PER_TICK = 32;
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();
    private static final Set<BatchDecisionKey> BATCH_DECISIONS = new LinkedHashSet<>();

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
        if (isExpectedFallback(reason)) {
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
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        logDiagnostic(execution.details(), reason, stage, ownerPos, tick, context, "fallback");
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

    public static void logBatchDecision(
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
        long lookupMicros,
        int busesScanned,
        int candidateBuses,
        int verifiedCandidates,
        long finalBatch
    ) {
        if (!NEConfig.debugEcoFastPath) {
            return;
        }
        PatternDescription pattern = describe(execution.details());
        BatchDecisionKey key = new BatchDecisionKey(tick, pattern.definition(), pattern.identityHash());
        synchronized (LOGGED) {
            if (!BATCH_DECISIONS.add(key)) {
                return;
            }
            trimBatchDecisions();
        }
        LOGGER.info(
            "ECO FastPath batch decision: definition={} definitionHash={} primaryOutput={} owner={} tick={} taskRemaining={} requested={} laneLimit={} safeLimit={} energyLimit={} coolantLimit={} inventoryLimit={} inventoryConstraint={} currentCraftIncluded={} reservedForCurrentCraft={} inventoryAvailable={} additionalCraftsByInventory={} inventoryFormula={} lookupMicros={} busesScanned={} candidateBuses={} verifiedCandidates={} finalBatch={}",
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
            inventoryFormula(currentCraftIncluded, reservedForCurrentCraft, inventoryAvailable,
                inventoryPerCraft, additionalCraftsByInventory),
            lookupMicros,
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            finalBatch
        );
    }

    public static void logCacheLookup(
        ECOExtractedPatternExecution execution,
        BlockPos ownerPos,
        long tick,
        int patternBuses,
        int busesScanned,
        int candidateBuses,
        int verifiedCandidates,
        long lookupMicros
    ) {
        if (!NEConfig.debugEcoFastPath) {
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
        synchronized (LOGGED) {
            if (budgetTick != tick) {
                budgetTick = tick;
                logsThisTick = 0;
            }
            if (LOGGED.contains(key) || logsThisTick >= MAX_LOGS_PER_TICK) {
                return;
            }
            LOGGED.add(key);
            trimRetainedEntries();
            logsThisTick++;
        }
        LOGGER.info(
            "ECO FastPath cache lookup: definition={} definitionHash={} primaryOutput={} owner={} tick={} patternBuses={} busesScanned={} candidateBuses={} verifiedCandidates={} lookupMicros={}",
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            ownerPos.toShortString(),
            tick,
            patternBuses,
            busesScanned,
            candidateBuses,
            verifiedCandidates,
            lookupMicros
        );
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
        long patternCrafts = 0L;
        for (long count : plan.patternTimes().values()) {
            patternCrafts = saturatingAdd(patternCrafts, count);
        }
        LOGGER.info(
            "ECO CPU job reservation: finalOutput={} owner={} tick={} patternCrafts={} patternCount={} usedItems={} reservedItems={}",
            plan.finalOutput(),
            ownerPos.toShortString(),
            tick,
            patternCrafts,
            plan.patternTimes().size(),
            describeCounter(plan.usedItems()),
            describeCounter(reservedItems)
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
        logDiagnostic(details, reason, stage, ownerPos, tick, context, "failure");
    }

    private static void logDiagnostic(
        IPatternDetails details,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context,
        String kind
    ) {
        PatternDescription pattern = describe(details);
        DiagnosticKey key = new DiagnosticKey(
            reason,
            stage,
            pattern.definition(),
            pattern.identityHash(),
            pattern.primaryOutput(),
            pattern.implementation(),
            context
        );
        synchronized (LOGGED) {
            if (budgetTick != tick) {
                budgetTick = tick;
                logsThisTick = 0;
            }
            if (LOGGED.contains(key) || logsThisTick >= MAX_LOGS_PER_TICK) {
                return;
            }
            LOGGED.add(key);
            trimRetainedEntries();
            logsThisTick++;
        }

        LOGGER.info(
            "ECO FastPath {}: stage={} reason={} definition={} definitionHash={} primaryOutput={} implementation={} owner={} tick={} context={}",
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

    public static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
            BATCH_DECISIONS.clear();
            budgetTick = Long.MIN_VALUE;
            logsThisTick = 0;
        }
    }

    private static PatternDescription describe(IPatternDetails details) {
        Object identity;
        try {
            identity = AE2PatternIntrospection.getStablePatternIdentity(details);
        } catch (Throwable ignored) {
            identity = details;
        }
        int identityHash = safeHashCode(identity);
        String definition = identity instanceof AEKey key ? describeKey(key) : identity.getClass().getName();
        String primaryOutput = "unknown";
        try {
            GenericStack output = details.getPrimaryOutput();
            if (output != null) {
                primaryOutput = describeKey(output.what()) + " x" + output.amount();
            }
        } catch (Throwable ignored) {
            // Diagnostics must never affect pattern execution.
        }
        return new PatternDescription(
            definition,
            identityHash,
            primaryOutput,
            details.getClass().getName()
        );
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

    private static String inventoryFormula(
        boolean currentCraftIncluded,
        long reservedForCurrentCraft,
        long inventoryAvailable,
        long inventoryPerCraft,
        long additionalCraftsByInventory
    ) {
        if (!currentCraftIncluded) {
            return "currentCraftIncluded=false";
        }
        if (inventoryPerCraft <= 0L) {
            return reservedForCurrentCraft + "+no_consumed_inputs="
                + (reservedForCurrentCraft + additionalCraftsByInventory);
        }
        return reservedForCurrentCraft + "+floor(" + inventoryAvailable + "/" + inventoryPerCraft + ")="
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
}
