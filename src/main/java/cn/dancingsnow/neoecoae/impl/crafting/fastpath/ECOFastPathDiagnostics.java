package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOFastPathDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_LOGS_PER_TICK = 32;
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();

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
        logFailure(execution, reason, stageFor(reason), workerPos, tick, "fallback_to_ae2");
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

    private static void logFailure(
        IPatternDetails details,
        ECOFastPathFallbackReason reason,
        ECOFastPathStage stage,
        BlockPos ownerPos,
        long tick,
        String context
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
            "ECO FastPath failure: stage={} reason={} definition={} definitionHash={} primaryOutput={} implementation={} owner={} tick={} context={}",
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

    public static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
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

    private static String describeKey(AEKey key) {
        try {
            return key.getId().toString();
        } catch (Throwable ignored) {
            return key.getClass().getName();
        }
    }

    private static ECOFastPathStage stageFor(ECOFastPathFallbackReason reason) {
        return switch (reason) {
            case FAST_PATH_DISABLED, POST_CRAFTING_EVENT, NO_ECO_PATTERN_BUS,
                    INTROSPECTION_UNAVAILABLE, UNSUPPORTED_PATTERN_TYPE, KEY_BUILD_FAILED,
                    OUTPUT_COUNT_NOT_ONE, UNSAFE_EXPECTED_OUTPUT, UNSAFE_CONTAINER_ITEM,
                    UNSAFE_INPUT -> ECOFastPathStage.ELIGIBILITY;
            case CACHE_MISS_VERIFYING, NEGATIVE_CACHE, CACHE_ENTRY_MISMATCH -> ECOFastPathStage.CACHE_LOOKUP;
            case RUNTIME_STACK_CONVERSION_FAILED, OUTPUT_MISMATCH, CONTAINER_MISMATCH,
                    INPUT_MISMATCH, CACHE_VALIDATION_REJECTED -> ECOFastPathStage.CACHE_VERIFY;
            case NO_BATCH_OFFER -> ECOFastPathStage.CACHE_LOOKUP;
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
}
