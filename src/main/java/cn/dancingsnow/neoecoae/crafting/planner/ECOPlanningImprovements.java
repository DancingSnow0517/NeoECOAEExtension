package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.math.BigInteger;
import java.util.concurrent.Future;
import org.jetbrains.annotations.Nullable;

/** Owns optional confirmation work and the frozen recipe context used for remaining-work searches. */
public final class ECOPlanningImprovements implements AutoCloseable {
    static final long MAX_WORK = 2_000_000L;
    static final long MAX_NANOS = 3_000_000_000L;
    private final ECOCraftingPlannerService.Session source;
    private ECOPlanningResult best;
    private @Nullable Future<?> confirmationSearch;
    private boolean closed;

    ECOPlanningImprovements(ECOCraftingPlannerService.Session source, ECOPlanningResult first) {
        this.source = source;
        this.best = first;
    }

    synchronized void start() {
        if (closed || !isCurrent()) return;
        confirmationSearch = ECOPlanningExecutor.tryImprove(() -> {
            var fork = source.fork(source.initialInventory(), this::checkpoint, false);
            ECOPlanningResult candidate = fork.plan(best.plan().finalOutput().amount(), false, this::checkpoint);
            offer(candidate);
            return null;
        });
    }

    public boolean isCurrent() { return source.isStructureCurrent(); }
    public boolean belongsTo(appeng.api.networking.crafting.ICraftingService service) { return source.belongsTo(service); }

    public synchronized @Nullable ECOPlanningResult latest(ECOPlanningResult displayed) {
        return !closed && isCurrent() && best != displayed && better(best, displayed) ? best : null;
    }

    boolean offer(ECOPlanningResult candidate) {
        // Resolve/validate the candidate outside the publication lock: closing a menu must never wait for it.
        if (!executable(candidate)) return false;
        synchronized (this) {
            if (closed || !isCurrent() || !better(candidate, best)) return false;
            candidate.setImprovements(this);
            best = candidate;
            return true;
        }
    }

    /** The caller snapshots CPU-owned stock on the server thread before submitting this task. */
    public @Nullable Future<ECOPlanningResult> remaining(KeyCounter stock, long amount) {
        if (!isCurrent() || amount <= 0) return null;
        var snapshot = PlannerInventorySnapshot.of(stock);
        return ECOPlanningExecutor.tryImprove(() -> {
            ECOCancellation cancellation = () -> {
                if (Thread.currentThread().isInterrupted() || !isCurrent())
                    throw new InterruptedException("Remaining-work search cancelled or stale");
            };
            return source.fork(snapshot, cancellation, true).plan(amount, false, cancellation);
        });
    }

    private synchronized void checkpoint() throws InterruptedException {
        if (closed || Thread.currentThread().isInterrupted() || !isCurrent())
            throw new InterruptedException("Plan improvement cancelled or stale");
    }

    /** Freeze the displayed plan at submission; the CPU can still use the independent recipe context. */
    @Override public synchronized void close() {
        closed = true;
        if (confirmationSearch != null) confirmationSearch.cancel(true);
        confirmationSearch = null;
    }

    public static boolean executable(@Nullable ECOPlanningResult result) {
        return result != null && result.status() == PlanningStatus.SUCCESS && result.plan() != null
                && !result.plan().simulation() && result.executionPlanError() == null;
    }

    static boolean better(ECOPlanningResult candidate, ECOPlanningResult incumbent) {
        if (!executable(candidate) || !executable(incumbent)
                || !candidate.plan().finalOutput().equals(incumbent.plan().finalOutput())) return false;
        boolean retained = retainsSeeds(incumbent);
        boolean retaining = retainsSeeds(candidate);
        if (retained != retaining) return retaining;
        int cost = firings(candidate).compareTo(firings(incumbent));
        return cost < 0 || cost == 0 && candidate.plan().bytes() < incumbent.plan().bytes();
    }

    private static boolean retainsSeeds(ECOPlanningResult result) {
        return result.trace().diagnostics().stream()
                .anyMatch(d -> d.code() == PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
    }

    public static BigInteger firings(ECOPlanningResult result) {
        return result.exactPatternTimes().values().stream().map(value -> value.toBigInteger())
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
