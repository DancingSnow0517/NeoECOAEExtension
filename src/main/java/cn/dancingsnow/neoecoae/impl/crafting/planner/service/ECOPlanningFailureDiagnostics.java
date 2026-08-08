package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Structured, rate-limited diagnostics for ECO planning failures and fallbacks. */
public final class ECOPlanningFailureDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_RETAINED_ENTRIES = 4_096;
    private static final int MAX_FAILURE_LOGS_PER_SECOND = 64;
    private static final int MAX_TRACE_LOGS_PER_SECOND = 256;
    private static final int MAX_DETAIL_EVENTS_PER_REQUEST = 2_048;
    private static final int MAX_RENDERED_ENTRIES = 12;
    private static final int MAX_RENDERED_CHARS = 8_192;
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final Set<DiagnosticKey> LOGGED = new LinkedHashSet<>();
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final ThreadLocal<String> CURRENT_REQUEST = new ThreadLocal<>();
    private static final Map<String, AtomicLong> REQUEST_DETAIL_COUNTS = new ConcurrentHashMap<>();
    private static final Object FILE_LOCK = new Object();
    private static long budgetSecond = Long.MIN_VALUE;
    private static int failureLogsThisSecond;
    private static int traceLogsThisSecond;
    private static BufferedWriter diagnosticWriter;
    private static Path diagnosticPath;
    private static boolean fileFailureReported;

    private ECOPlanningFailureDiagnostics() {}

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

    /** Starts one player-visible crafting calculation and binds it to the current thread. */
    public static String beginRequest(Object requestedKey, long requestedAmount, Object strategy) {
        if (!NEConfig.debugECOPlanner) {
            return "disabled";
        }
        String requestId = Long.toUnsignedString(System.currentTimeMillis(), 36) + "-"
                + Long.toUnsignedString(REQUEST_IDS.incrementAndGet(), 36);
        try (RequestScope ignored = bindRequest(requestId)) {
            writeDiagnostic(
                    "request_start",
                    "requestedKey=" + describe(requestedKey)
                            + " requestedAmount=" + requestedAmount
                            + " strategy=" + (strategy == null ? "unknown" : strategy),
                    null);
        }
        return requestId;
    }

    /** Temporarily associates work on another thread with an existing request. */
    public static RequestScope bindRequest(String requestId) {
        String previous = CURRENT_REQUEST.get();
        if (requestId == null || requestId.isBlank() || "disabled".equals(requestId)) {
            CURRENT_REQUEST.remove();
        } else {
            CURRENT_REQUEST.set(requestId);
        }
        return new RequestScope(previous);
    }

    public static String currentRequestId() {
        String requestId = CURRENT_REQUEST.get();
        return requestId == null ? "unscoped" : requestId;
    }

    public static void endRequest(String requestId, String result) {
        if (!NEConfig.debugECOPlanner || requestId == null || "disabled".equals(requestId)) {
            return;
        }
        try (RequestScope ignored = bindRequest(requestId)) {
            String counterPrefix = requestId + "\0";
            long detailEvents = REQUEST_DETAIL_COUNTS.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(counterPrefix))
                    .mapToLong(entry -> entry.getValue().get())
                    .sum();
            writeDiagnostic("request_end", "result=" + sanitize(result) + " detailEvents=" + detailEvents, null);
        } finally {
            String counterPrefix = requestId + "\0";
            REQUEST_DETAIL_COUNTS.keySet().removeIf(key -> key.startsWith(counterPrefix));
        }
    }

    /** Writes request-scoped structured detail that does not imply a fallback. */
    public static boolean canLogDetail(Stage stage) {
        if (!NEConfig.debugECOPlanner) {
            return false;
        }
        AtomicLong count = REQUEST_DETAIL_COUNTS.get(detailCounterKey(currentRequestId(), stage));
        return count == null || count.get() <= MAX_DETAIL_EVENTS_PER_REQUEST;
    }

    public static void logDetail(Stage stage, String context) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        String requestId = currentRequestId();
        AtomicLong count =
                REQUEST_DETAIL_COUNTS.computeIfAbsent(detailCounterKey(requestId, stage), ignored -> new AtomicLong());
        long ordinal = count.incrementAndGet();
        if (ordinal > MAX_DETAIL_EVENTS_PER_REQUEST) {
            if (ordinal == MAX_DETAIL_EVENTS_PER_REQUEST + 1L) {
                writeDiagnostic(
                        "detail_limit",
                        "stage=" + stage.name().toLowerCase() + " limit=" + MAX_DETAIL_EVENTS_PER_REQUEST,
                        null);
            }
            return;
        }
        writeDiagnostic(
                "detail",
                "stage=" + stage.name().toLowerCase() + " ordinal=" + ordinal + " context=" + sanitize(context),
                null);
    }

    public static String describeMap(Map<?, ?> values) {
        if (values == null) {
            return "null";
        }
        return describeEntries(values.entrySet(), values.size());
    }

    public static String describeIterable(Iterable<?> values, int total) {
        if (values == null) {
            return "null";
        }
        return describeEntries(values, total);
    }

    public static void logFailure(
            Stage stage,
            ECOPlannerFallbackReason reason,
            Object requestedKey,
            long requestedAmount,
            Object strategy,
            String context) {
        logFailure(stage, reason, requestedKey, requestedAmount, strategy, context, null);
    }

    public static void logFailure(
            Stage stage,
            ECOPlannerFallbackReason reason,
            Object requestedKey,
            long requestedAmount,
            Object strategy,
            String context,
            Throwable failure) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        String exceptionClass = failure == null ? "none" : failure.getClass().getName();
        String exceptionMessage =
                failure == null || failure.getMessage() == null ? "none" : sanitize(failure.getMessage());
        String safeContext = sanitize(context);
        DiagnosticKey key = new DiagnosticKey(
                currentRequestId(), stage, reason, describe(requestedKey), safeContext, exceptionClass);
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
                stage.name().toLowerCase(),
                reason.id(),
                describe(requestedKey),
                requestedAmount,
                strategy == null ? "unknown" : strategy,
                safeContext,
                exceptionClass,
                exceptionMessage,
                failure);
    }

    /** Emits a bounded capture trace without changing the planner's fallback result. */
    public static void logTrace(Object requestedKey, long requestedAmount, Object strategy, String context) {
        if (!NEConfig.debugECOPlanner) {
            return;
        }
        String safeContext = sanitize(context);
        DiagnosticKey key = new DiagnosticKey(
                currentRequestId(),
                Stage.SNAPSHOT,
                ECOPlannerFallbackReason.FAST_PATH,
                describe(requestedKey),
                "trace " + safeContext,
                "trace");
        if (!reserve(key, true)) {
            return;
        }
        writeDiagnostic(
                "trace",
                "requestedKey=" + describe(requestedKey)
                        + " requestedAmount=" + requestedAmount
                        + " strategy=" + (strategy == null ? "unknown" : strategy)
                        + " context=" + safeContext,
                null);
    }

    /** Records a completed planning phase in milliseconds. */
    public static void logTiming(
            Stage stage,
            Object requestedKey,
            long requestedAmount,
            Object strategy,
            String phase,
            long startedNanos,
            String context) {
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
                null);
    }

    public static <K, R> void logSolverResult(
            Stage stage, ECOPlanningProblem<K, R> problem, ECOHyperflowResult<R> result, String context) {
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
                        + " surplus=" + candidate.surplus());
    }

    public static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
            REQUEST_DETAIL_COUNTS.clear();
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
                writer.write("requestId=");
                writer.write(currentRequestId());
                writer.write(' ');
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
        diagnosticPath = directory.resolve("eco-planner-" + FILE_TIMESTAMP.format(Instant.now()) + ".log");
        diagnosticWriter = Files.newBufferedWriter(
                diagnosticPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
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
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        return sanitized.length() <= MAX_RENDERED_CHARS
                ? sanitized
                : sanitized.substring(0, MAX_RENDERED_CHARS) + "...<truncated_chars>";
    }

    private static String describeEntries(Iterable<?> values, int total) {
        StringBuilder result = new StringBuilder("[");
        int shown = 0;
        for (Object value : values) {
            if (shown >= MAX_RENDERED_ENTRIES || result.length() >= MAX_RENDERED_CHARS) {
                break;
            }
            if (shown > 0) {
                result.append(", ");
            }
            result.append(describe(value));
            shown++;
        }
        result.append("] shown=").append(shown).append(" total=").append(Math.max(total, shown));
        if (shown < total) {
            result.append(" truncated=true");
        }
        return sanitize(result.toString());
    }

    private static String detailCounterKey(String requestId, Stage stage) {
        return requestId + "\0" + stage.name();
    }

    private static void trimRetainedEntries() {
        if (LOGGED.size() <= MAX_RETAINED_ENTRIES) {
            return;
        }
        Iterator<DiagnosticKey> iterator = LOGGED.iterator();
        iterator.next();
        iterator.remove();
    }

    public static final class RequestScope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private RequestScope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT_REQUEST.remove();
            } else {
                CURRENT_REQUEST.set(previous);
            }
        }
    }

    private record DiagnosticKey(
            String requestId,
            Stage stage,
            ECOPlannerFallbackReason reason,
            String requestedKey,
            String context,
            String exceptionClass) {}
}
