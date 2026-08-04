package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Structured, rate-limited diagnostics for ECO planning failures and fallbacks. */
public final class ECOPlanningFailureDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_FAILURE_LOGS_PER_SECOND = 64;
    private static final int MAX_TRACE_LOGS_PER_SECOND = 256;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();
    private static final Object FILE_LOCK = new Object();
    private static long budgetSecond = Long.MIN_VALUE;
    private static int failureLogsThisSecond;
    private static int traceLogsThisSecond;
    private static BufferedWriter diagnosticWriter;
    private static Path diagnosticPath;
    private static boolean fileFailureReported;

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
        if (!reserve(key, false)) {
            return;
        }
        String message = "stage=" + stage.name().toLowerCase()
            + " reason=" + reason.id()
            + " requestedKey=" + describe(requestedKey)
            + " requestedAmount=" + requestedAmount
            + " strategy=" + (strategy == null ? "unknown" : strategy)
            + " context=" + safeContext
            + " exceptionClass=" + exceptionClass
            + " exceptionMessage=" + exceptionMessage;
        writeDiagnostic("failure", message, failure);
        LOGGER.info(
            "ECO planning failure: stage={} reason={} requestedKey={} requestedAmount={} strategy={} context={} exceptionClass={} exceptionMessage={}",
            stage.name().toLowerCase(), reason.id(), describe(requestedKey), requestedAmount,
            strategy == null ? "unknown" : strategy, safeContext, exceptionClass, exceptionMessage,
            failure
        );
    }

    /** Emits a bounded capture trace without changing the planner's fallback result. */
    public static void logTrace(
        Object requestedKey,
        long requestedAmount,
        Object strategy,
        String context
    ) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        String safeContext = sanitize(context);
        DiagnosticKey key = new DiagnosticKey(
            Stage.SNAPSHOT,
            ECOPlannerFallbackReason.FAST_PATH,
            describe(requestedKey),
            "trace " + safeContext,
            "trace"
        );
        if (!reserve(key, true)) {
            return;
        }
        writeDiagnostic(
            "trace",
            "requestedKey=" + describe(requestedKey)
                + " requestedAmount=" + requestedAmount
                + " strategy=" + (strategy == null ? "unknown" : strategy)
                + " context=" + safeContext,
            null
        );
    }

    /** Records a completed planning phase in milliseconds. */
    public static void logTiming(
        Stage stage,
        Object requestedKey,
        long requestedAmount,
        Object strategy,
        String phase,
        long startedNanos,
        String context
    ) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedNanos);
        writeDiagnostic(
            "timing",
            "stage=" + stage.name().toLowerCase()
                + " phase=" + sanitize(phase)
                + " elapsedMs=" + String.format(java.util.Locale.ROOT, "%.3f", elapsedNanos / 1_000_000.0)
                + " requestedKey=" + describe(requestedKey)
                + " requestedAmount=" + requestedAmount
                + " strategy=" + (strategy == null ? "unknown" : strategy)
                + " context=" + sanitize(context),
            null
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
            failureLogsThisSecond = 0;
            traceLogsThisSecond = 0;
        }
    }

    /** Closes the per-world diagnostic log when the server releases its save. */
    public static void close() {
        synchronized (FILE_LOCK) {
            if (diagnosticWriter != null) {
                try {
                    diagnosticWriter.close();
                } catch (IOException failure) {
                    LOGGER.warn("Could not close ECO planner diagnostic log {}", diagnosticPath, failure);
                }
            }
            diagnosticWriter = null;
            diagnosticPath = null;
            fileFailureReported = false;
        }
        clear();
    }

    private static long safeTotalExecutions(ECOPlanCandidate<?> candidate) {
        try {
            return candidate.totalExecutions();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean reserve(DiagnosticKey key, boolean trace) {
        synchronized (LOGGED) {
            long second = System.currentTimeMillis() / 1_000L;
            if (budgetSecond != second) {
                budgetSecond = second;
                failureLogsThisSecond = 0;
                traceLogsThisSecond = 0;
            }
            int used = trace ? traceLogsThisSecond : failureLogsThisSecond;
            int limit = trace ? MAX_TRACE_LOGS_PER_SECOND : MAX_FAILURE_LOGS_PER_SECOND;
            if (LOGGED.contains(key) || used >= limit) {
                return false;
            }
            LOGGED.add(key);
            trimRetainedEntries();
            if (trace) {
                traceLogsThisSecond++;
            } else {
                failureLogsThisSecond++;
            }
            return true;
        }
    }

    private static void writeDiagnostic(String event, String message, Throwable failure) {
        synchronized (FILE_LOCK) {
            try {
                BufferedWriter writer = writer();
                if (writer == null) {
                    return;
                }
                writer.write(Instant.now().toString());
                writer.write(" [");
                writer.write(Thread.currentThread().getName());
                writer.write("] ");
                writer.write(event);
                writer.write(' ');
                writer.write(message);
                writer.newLine();
                if (failure != null) {
                    StringWriter stack = new StringWriter();
                    failure.printStackTrace(new PrintWriter(stack));
                    writer.write(stack.toString());
                    if (!stack.toString().endsWith(System.lineSeparator())) {
                        writer.newLine();
                    }
                }
                writer.flush();
            } catch (IOException | RuntimeException fileFailure) {
                if (!fileFailureReported) {
                    fileFailureReported = true;
                    LOGGER.warn("Could not write ECO planner diagnostic log {}", diagnosticPath, fileFailure);
                }
            }
        }
    }

    private static BufferedWriter writer() throws IOException {
        if (diagnosticWriter != null) {
            return diagnosticWriter;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        Path directory = server.getWorldPath(LevelResource.ROOT)
            .resolve("neoecoae-diagnostics")
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(directory);
        diagnosticPath = directory.resolve(
            "eco-planner-" + FILE_TIMESTAMP.format(Instant.now()) + ".log"
        );
        diagnosticWriter = Files.newBufferedWriter(
            diagnosticPath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );
        diagnosticWriter.write("ECO planner diagnostics started=" + Instant.now());
        diagnosticWriter.newLine();
        diagnosticWriter.flush();
        LOGGER.info("ECO planner diagnostics are being written to {}", diagnosticPath);
        return diagnosticWriter;
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
