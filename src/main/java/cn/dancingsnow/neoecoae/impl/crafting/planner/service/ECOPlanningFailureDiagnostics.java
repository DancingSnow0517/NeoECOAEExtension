package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Structured, rate-limited diagnostics for ECO planning failures and fallbacks. */
public final class ECOPlanningFailureDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_LOGS_PER_SECOND = 64;
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();
    private static long budgetSecond = Long.MIN_VALUE;
    private static int logsThisSecond;

    private ECOPlanningFailureDiagnostics() {
    }

    public enum Stage {
        ENTRY,
        HOST_SELECTION,
        SNAPSHOT,
        GRAPH,
        OPERATION_MATERIALIZATION,
        SOLVER_SELECTION,
        DAG_SOLVER,
        COMPONENT_SOLVER,
        INTEGER_SOLVER,
        SCHEDULER,
        ASSEMBLER,
        FALLBACK
    }

    public static void logFailure(
        Stage stage,
        ECOPlannerFallbackReason reason,
        Object requestedKey,
        long requestedAmount,
        Object strategy,
        String context
    ) {
        logFailure(stage, reason, requestedKey, requestedAmount, strategy, context, null);
    }

    public static void logFailure(
        Stage stage,
        ECOPlannerFallbackReason reason,
        Object requestedKey,
        long requestedAmount,
        Object strategy,
        String context,
        Throwable failure
    ) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        String exceptionClass = failure == null ? "none" : failure.getClass().getName();
        String exceptionMessage = failure == null || failure.getMessage() == null
            ? "none"
            : sanitize(failure.getMessage());
        String safeContext = sanitize(context);
        DiagnosticKey key = new DiagnosticKey(stage, reason, describe(requestedKey), safeContext, exceptionClass);
        synchronized (LOGGED) {
            long second = System.currentTimeMillis() / 1_000L;
            if (budgetSecond != second) {
                budgetSecond = second;
                logsThisSecond = 0;
            }
            if (LOGGED.contains(key) || logsThisSecond >= MAX_LOGS_PER_SECOND) {
                return;
            }
            LOGGED.add(key);
            trimRetainedEntries();
            logsThisSecond++;
        }
        LOGGER.info(
            "ECO planning failure: stage={} reason={} requestedKey={} requestedAmount={} strategy={} context={} exceptionClass={} exceptionMessage={}",
            stage.name().toLowerCase(), reason.id(), describe(requestedKey), requestedAmount,
            strategy == null ? "unknown" : strategy, safeContext, exceptionClass, exceptionMessage,
            failure
        );
    }

    public static <K, R> void logSolverResult(
        Stage stage,
        ECOPlanningProblem<K, R> problem,
        ECOHyperflowResult<R> result,
        String context
    ) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        ECOPlanCandidate<R> candidate = result.candidate();
        logFailure(
            stage,
            result.status() == ECOHyperflowResult.Status.COMPLETE
                ? ECOPlannerFallbackReason.FAST_PATH
                : result.status() == ECOHyperflowResult.Status.BUDGET_EXHAUSTED
                    ? ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED
                    : ECOPlannerFallbackReason.SOLVER_NO_ROUTE,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "solver",
            context
                + " status=" + result.status()
                + " expandedStates=" + result.expandedStates()
                + " operations=" + candidate.executions().size()
                + " executions=" + safeTotalExecutions(candidate)
                + " requestedShortfall=" + candidate.requestedShortfall()
                + " dependencyShortfall=" + candidate.dependencyShortfall()
                + " sourceShortfall=" + candidate.sourceShortfall()
                + " surplus=" + candidate.surplus()
        );
    }

    public static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
            budgetSecond = Long.MIN_VALUE;
            logsThisSecond = 0;
        }
    }

    private static long safeTotalExecutions(ECOPlanCandidate<?> candidate) {
        try {
            return candidate.totalExecutions();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return value.toString();
        } catch (Throwable ignored) {
            return value.getClass().getName();
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void trimRetainedEntries() {
        if (LOGGED.size() <= MAX_RETAINED_ENTRIES) {
            return;
        }
        Iterator<DiagnosticKey> iterator = LOGGED.iterator();
        iterator.next();
        iterator.remove();
    }

    private record DiagnosticKey(Stage stage, ECOPlannerFallbackReason reason, String requestedKey,
                                 String context, String exceptionClass) {
    }
}
