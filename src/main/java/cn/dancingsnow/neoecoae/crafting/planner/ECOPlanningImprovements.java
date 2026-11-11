package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import java.math.BigInteger;
import java.util.concurrent.Future;
import org.jetbrains.annotations.Nullable;

/** Retains the frozen recipe context used for CPU remaining-work searches. */
public final class ECOPlanningImprovements {
    static final long MAX_WORK = 2_000_000L;
    static final long MAX_NANOS = 3_000_000_000L;
    private final ECOCraftingPlannerService.Session source;

    ECOPlanningImprovements(ECOCraftingPlannerService.Session source) {
        this.source = source;
    }

    public boolean isCurrent() { return source.isStructureCurrent(); }
    public boolean belongsTo(appeng.api.networking.crafting.ICraftingService service) { return source.belongsTo(service); }

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

    public static BigInteger firings(ECOPlanningResult result) {
        return result.exactPatternTimes().values().stream().map(value -> value.toBigInteger())
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
