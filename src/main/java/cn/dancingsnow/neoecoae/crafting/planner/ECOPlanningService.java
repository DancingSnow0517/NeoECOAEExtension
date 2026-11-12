package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;

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
    private ECOPlanningService() {
    }

    public static Future<ICraftingPlan> begin(Level level, IGrid grid, IActionSource source, AEKey goal,
                                              long amount, CalculationStrategy strategy, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        ECOCraftingPlannerService.Session session = ECOCraftingPlannerService.INSTANCE.createSession(grid.getCraftingService(), goal, inventory,
                options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
        return begin(session, goal, amount, strategy);
    }

    static Request begin(ECOCraftingPlannerService.Session session, AEKey goal, long amount,
            CalculationStrategy strategy) {
        Request request = new Request();
        request.attach(ECOPlanningExecutor.submit(() -> {
            try {
                ICraftingPlan plan = plan(session, goal, amount, strategy, request::checkpoint, request::retainCandidate);
                var result = cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.findExact(plan);
                if (result != null) startImprovements(session, result);
                request.finish(plan);
            } catch (Throwable failure) {
                request.fail(failure);
            }
            return null;
        }));
        return request;
    }

    /** Retain the recipe context for CPU remaining-work searches; confirmation is already final. */
    public static @Nullable ECOPlanningImprovements startImprovements(ECOCraftingPlannerService.Session session,
            ECOPlanningResult result) {
        if (!result.executable() || !session.isStructureCurrent()) return null;
        if (result.improvements() != null) return result.improvements();
        var context = new ECOPlanningImprovements(session);
        result.setImprovements(context);
        return context;
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
        if (exact.executable()) {
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
                if (probe.executable()) {
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

    /** Candidates remain private until the worker selects one final plan for the confirmation page. */
    public static final class Request implements Future<ICraftingPlan> {
        public static final int SEARCHING = 1, COMPLETE = 2, LIMITED = 3, FAILED = 4, STOPPED = 5;
        private final CompletableFuture<ICraftingPlan> completion = new CompletableFuture<>();
        private @Nullable ICraftingPlan candidate;
        private @Nullable ECOPlanningResult candidateResult;
        private volatile int searchState = SEARCHING;
        private volatile boolean stopped;
        private @Nullable Future<?> worker;

        private synchronized void attach(Future<?> task) {
            worker = task;
            if (stopped) task.cancel(true);
        }

        private synchronized void retainCandidate(ECOPlanningResult result) {
            if (stopped || completion.isDone()) return;
            candidate = result.plan();
            candidateResult = result;
        }

        private synchronized void finish(ICraftingPlan plan) {
            if (stopped || completion.isDone()) return;
            var result = cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.findExact(plan);
            boolean executable = result != null && result.executable();
            boolean reportCompleted = result != null && candidateResult != null && !candidateResult.executable()
                && !result.exactMissingItems().isEmpty() && sameMaterialReport(candidateResult, result);
            // An interrupted/failed speculative search must never discard a validated candidate.
            // A retained full shortage report can acquire a final proof or limited-search diagnostic.
            if (candidate == null || executable || reportCompleted) {
                candidate = plan;
                candidateResult = result;
            }
            searchState = result != null && result.routeSearchIncomplete() ? LIMITED
                : candidate != plan && !executable ? FAILED : COMPLETE;
            completion.complete(candidate);
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
            if (stopped || completion.isDone()) return;
            searchState = FAILED;
            if (candidate != null) completion.complete(candidate);
            else completion.completeExceptionally(failure);
        }

        private void checkpoint() throws InterruptedException {
            ECOPlanningService.checkpoint();
            if (stopped) throw new InterruptedException("ECO confirmation closed or submitted");
        }

        public int searchState() { return searchState; }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            Future<?> task;
            synchronized (this) {
                if (stopped || completion.isDone()) return false;
                stopped = true;
                searchState = STOPPED;
                task = worker;
            }
            if (task != null) task.cancel(mayInterruptIfRunning);
            return completion.cancel(mayInterruptIfRunning);
        }
        @Override public boolean isCancelled() { return completion.isCancelled(); }
        @Override public boolean isDone() { return completion.isDone(); }
        @Override public ICraftingPlan get() throws InterruptedException, ExecutionException { return completion.get(); }
        @Override public ICraftingPlan get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException { return completion.get(timeout, unit); }
    }

    private static void checkpoint() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("ECO planning request cancelled");
        }
    }
}
