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
        if (!NEConfig.debugEcoFastPath) {
            return;
        }

        PatternDescription pattern = describe(execution.details());
        DiagnosticKey key = new DiagnosticKey(
            reason,
            pattern.definition(),
            pattern.identityHash(),
            pattern.primaryOutput(),
            pattern.implementation()
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
            "ECO FastPath not used for pattern: reason={} definition={} definitionHash={} primaryOutput={} implementation={} worker={} tick={}",
            reason.code(),
            pattern.definition(),
            Integer.toUnsignedString(pattern.identityHash(), 16),
            pattern.primaryOutput(),
            pattern.implementation(),
            workerPos.toShortString(),
            tick
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
        String definition,
        int identityHash,
        String primaryOutput,
        String implementation
    ) {
    }
}
