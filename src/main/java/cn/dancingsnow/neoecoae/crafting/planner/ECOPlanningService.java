package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

/**
 * ECO-owned planning boundary. It deliberately does not call
 * {@code ICraftingService.beginCraftingCalculation}, keeping this request outside other planner wrappers.
 */
public final class ECOPlanningService {
    private static final ECOCraftingPlannerService PLANNER = new ECOCraftingPlannerService();

    private ECOPlanningService() {
    }

    public static Request begin(Level level, IGrid grid, IActionSource source, AEKey goal,
                                              long amount, CalculationStrategy strategy, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        ECOCraftingPlannerService.Session session = PLANNER.createSession(grid.getCraftingService(), goal, inventory,
                options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
        return begin(session, goal, amount, strategy);
    }

    static Request begin(ECOCraftingPlannerService.Session session, AEKey goal, long amount,
            CalculationStrategy strategy) {
        Request request = new Request();
        request.attach(ECOPlanningExecutor.submit(() -> {
            try {
                request.finish(plan(session, goal, amount, strategy, request::checkpoint, request::publish));
            } catch (Throwable failure) {
                request.fail(failure);
            }
            return null;
        }));
        return request;
    }

    static ICraftingPlan plan(ECOCraftingPlannerService.Session session, AEKey goal, long requestedAmount,
                                      CalculationStrategy strategy) throws InterruptedException {
        return plan(session, goal, requestedAmount, strategy, ECOPlanningService::checkpoint, null);
    }

    private static ICraftingPlan plan(ECOCraftingPlannerService.Session session, AEKey goal, long requestedAmount,
            CalculationStrategy strategy, ECOCancellation cancellation, @Nullable Consumer<ECOPlanningResult> onReady)
            throws InterruptedException {
        if (requestedAmount <= 0L) {
            return session.plan(requestedAmount, true, cancellation).plan();
        }

        ECOPlanningResult exact = onReady == null ? session.plan(requestedAmount, false, cancellation)
            : session.plan(requestedAmount, false, cancellation, onReady);
        if (isExecutableSuccess(exact)) {
            return exact.plan();
        }

        if (strategy == CalculationStrategy.CRAFT_LESS) {
            long bestAmount = 0L;
            ICraftingPlan bestPlan = null;
            for (long step = Long.highestOneBit(requestedAmount); step > 0L; step >>= 1) {
                cancellation.checkpoint();
                long candidate = bestAmount + step;
                if (candidate >= requestedAmount) {
                    continue;
                }
                ECOPlanningResult probe = session.plan(candidate, false, cancellation);
                if (isExecutableSuccess(probe)) {
                    bestAmount = candidate;
                    bestPlan = probe.plan();
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        }

        // The exact calculation already builds the simulation report, including its material and cycle
        // diagnostics. Reuse it after any CRAFT_LESS probes rather than walking the original demand again.
        if (exact.plan() != null) {
            return exact.plan();
        }

        ECOPlanningResult report = session.plan(requestedAmount, true, cancellation);
        return report.plan();
    }

    private static boolean isExecutableSuccess(ECOPlanningResult result) {
        return result.executable();
    }

    /** The Future completes at the first usable preview; its worker remains owned by the open menu. */
    public static final class Request implements Future<ICraftingPlan> {
        public static final int SEARCHING = 1, COMPLETE = 2, LIMITED = 3, FAILED = 4, STOPPED = 5;
        private final CompletableFuture<ICraftingPlan> first = new CompletableFuture<>();
        private volatile @Nullable ICraftingPlan latest;
        private @Nullable ECOPlanningResult latestResult;
        private volatile int searchState = SEARCHING;
        private volatile boolean stopped;
        private @Nullable Future<?> worker;

        private synchronized void attach(Future<?> task) {
            worker = task;
            if (stopped) task.cancel(true);
        }

        private synchronized void publish(ECOPlanningResult result) {
            if (stopped) return;
            latest = result.plan();
            latestResult = result;
            first.complete(latest);
        }

        private synchronized void finish(ICraftingPlan plan) {
            if (stopped) return;
            var result = cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.findExact(plan);
            boolean executable = result != null && result.executable();
            boolean reportCompleted = result != null && latestResult != null && !latestResult.executable()
                && !result.exactMissingItems().isEmpty() && sameMaterialReport(latestResult, result);
            // An interrupted/failed speculative search must never replace an already usable preview.
            // A retained full shortage report can acquire a final proof or limited-search diagnostic.
            if (latest == null || executable || reportCompleted) {
                latest = plan;
                latestResult = result;
                first.complete(plan);
            }
            searchState = result != null && result.routeSearchIncomplete() ? LIMITED
                : latest != plan && !executable ? FAILED : COMPLETE;
        }

        private static boolean sameMaterialReport(ECOPlanningResult before, ECOPlanningResult after) {
            return java.util.Objects.equals(before.plan().finalOutput(), after.plan().finalOutput())
                && before.exactMissingItems().equals(after.exactMissingItems())
                && before.exactUsedItems().equals(after.exactUsedItems())
                && before.exactEmittedItems().equals(after.exactEmittedItems())
                && before.exactPatternTimes().equals(after.exactPatternTimes())
                && before.theoreticalBytes().equals(after.theoreticalBytes());
        }

        private synchronized void fail(Throwable failure) {
            if (stopped) return;
            searchState = FAILED;
            first.completeExceptionally(failure);
        }

        private void checkpoint() throws InterruptedException {
            ECOPlanningService.checkpoint();
            if (stopped) throw new InterruptedException("ECO confirmation closed or submitted");
        }

        public @Nullable ICraftingPlan latest() { return latest; }
        public int searchState() { return searchState; }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            Future<?> task;
            synchronized (this) {
                stopped = true;
                searchState = STOPPED;
                task = worker;
            }
            if (task != null) task.cancel(mayInterruptIfRunning);
            return first.cancel(mayInterruptIfRunning);
        }
        @Override public boolean isCancelled() { return first.isCancelled(); }
        @Override public boolean isDone() { return first.isDone(); }
        @Override public ICraftingPlan get() throws InterruptedException, ExecutionException { return first.get(); }
        @Override public ICraftingPlan get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException { return first.get(timeout, unit); }
    }

    private static void checkpoint() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("ECO planning request cancelled");
        }
    }
}
