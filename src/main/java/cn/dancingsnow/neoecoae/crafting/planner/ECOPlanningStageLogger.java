package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;

import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request-scoped planner timing log. The context is deliberately thread-local because every ECO calculation
 * runs on one planning-executor thread, while the setting itself belongs to the originating AE network.
 */
public final class ECOPlanningStageLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.planner");
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private ECOPlanningStageLogger() {
    }

    public static long nextRequestId() {
        return NEXT_REQUEST_ID.incrementAndGet();
    }

    public static Scope open(boolean enabled, long requestId, AEKey goal, long amount, boolean simulation) {
        Context previous = CURRENT.get();
        if (enabled) {
            CURRENT.set(new Context(requestId, goal, amount, simulation));
        } else {
            CURRENT.remove();
        }
        return new Scope(previous);
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void finish(String stage, long startedNanos, boolean success, @Nullable String reason) {
        Context context = CURRENT.get();
        if (context == null) return;
        long now = System.nanoTime();
        long elapsedNanos = now >= startedNanos ? now - startedNanos : 0L;
        LOGGER.info(
                "[ECO-PLANNER-STAGE] request={} stage={} elapsedMs={} success={} reason={} goal={} amount={} simulation={}",
                context.requestId(), stage, elapsedNanos / 1_000_000.0D, success, normalize(reason),
                context.goal(), context.amount(), context.simulation());
    }

    public static String resultReason(PlanningStatus status, ECOPlanTrace trace) {
        if (trace != null && !trace.diagnostics().isEmpty()) {
            var diagnostic = trace.diagnostics().getLast();
            return status + ":" + diagnostic.code() + ":" + diagnostic.message();
        }
        return status.name();
    }

    public static String exceptionReason(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ":" + message);
    }

    private static String normalize(@Nullable String reason) {
        if (reason == null || reason.isBlank()) return "OK";
        String normalized = reason.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512) + "...";
    }

    private record Context(long requestId, AEKey goal, long amount, boolean simulation) {
    }

    public static final class Scope implements AutoCloseable {
        private final Context previous;
        private boolean closed;

        private Scope(@Nullable Context previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
