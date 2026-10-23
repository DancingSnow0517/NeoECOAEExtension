package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * ECO-owned planning boundary. It deliberately does not call
 * {@code ICraftingService.beginCraftingCalculation}, keeping this request outside other planner wrappers.
 */
public final class ECOPlanningService {
    private static final ECOCraftingPlannerService PLANNER = new ECOCraftingPlannerService();

    private ECOPlanningService() {
    }

    public static Future<ICraftingPlan> begin(Level level, IGrid grid, IActionSource source, AEKey goal,
                                              long amount, CalculationStrategy strategy, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        ECOCraftingPlannerService.Session session = PLANNER.createSession(grid.getCraftingService(), goal, inventory,
                options.cyclePlanningEnabled(), options.ignorePatternSubstitutions(), options.fuzzyPlanningItemIds());
        return begin(session, amount, strategy);
    }

    static Future<ICraftingPlan> begin(ECOCraftingPlannerService.Session session, long amount,
            CalculationStrategy strategy) {
        var cancelled = new AtomicBoolean();
        var improvements = new AtomicReference<ECOPlanningImprovements>();
        Future<ICraftingPlan> foreground = ECOPlanningExecutor.submit(() -> {
            ECOCancellation cancellation = ECOCancellation.firstSolution(() -> {
                checkpoint();
                if (cancelled.get()) throw new InterruptedException("ECO planning request cancelled");
            });
            ECOPlanningResult first = calculate(session, amount, strategy, cancellation);
            var optional = startImprovements(session, first);
            if (optional != null) {
                improvements.set(optional);
                if (cancelled.get()) optional.close();
            }
            return first.plan();
        });
        return new Future<>() {
            @Override public boolean cancel(boolean interrupt) {
                cancelled.set(true);
                var optional = improvements.get();
                if (optional != null) optional.close();
                return foreground.cancel(interrupt);
            }
            @Override public boolean isCancelled() { return foreground.isCancelled(); }
            @Override public boolean isDone() { return foreground.isDone(); }
            @Override public ICraftingPlan get() throws InterruptedException, ExecutionException {
                return foreground.get();
            }
            @Override public ICraftingPlan get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                return foreground.get(timeout, unit);
            }
        };
    }

    /** Also used after AE2 selects the final result of an explicitly tagged ECO calculation. */
    public static @Nullable ECOPlanningImprovements startImprovements(ECOCraftingPlannerService.Session session,
            ECOPlanningResult first) {
        if (!isExecutableSuccess(first) || !session.isStructureCurrent()) return null;
        if (first.improvements() != null) return first.improvements();
        var optional = new ECOPlanningImprovements(session, first);
        first.setImprovements(optional);
        optional.start();
        return optional;
    }

    static ICraftingPlan plan(ECOCraftingPlannerService.Session session, AEKey goal, long requestedAmount,
                                      CalculationStrategy strategy) throws InterruptedException {
        return calculate(session, requestedAmount, strategy,
                ECOCancellation.firstSolution(ECOPlanningService::checkpoint)).plan();
    }

    private static ECOPlanningResult calculate(ECOCraftingPlannerService.Session session, long requestedAmount,
            CalculationStrategy strategy, ECOCancellation cancellation) throws InterruptedException {
        if (requestedAmount <= 0L) {
            return session.plan(requestedAmount, true, cancellation);
        }

        ECOPlanningResult exact = session.plan(requestedAmount, false, cancellation);
        if (isExecutableSuccess(exact)) {
            return exact;
        }

        if (strategy == CalculationStrategy.CRAFT_LESS) {
            long bestAmount = 0L;
            ECOPlanningResult bestPlan = null;
            for (long step = Long.highestOneBit(requestedAmount); step > 0L; step >>= 1) {
                cancellation.checkpoint();
                long candidate = bestAmount + step;
                if (candidate >= requestedAmount) {
                    continue;
                }
                ECOPlanningResult probe = session.plan(candidate, false, cancellation);
                if (isExecutableSuccess(probe)) {
                    bestAmount = candidate;
                    bestPlan = probe;
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        }

        // The exact calculation already builds the simulation report, including its material and cycle
        // diagnostics. Reuse it after any CRAFT_LESS probes rather than walking the original demand again.
        if (exact.plan() != null) {
            return exact;
        }

        return session.plan(requestedAmount, true, cancellation);
    }

    private static boolean isExecutableSuccess(ECOPlanningResult result) {
        return ECOPlanningImprovements.executable(result);
    }

    private static void checkpoint() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("ECO planning request cancelled");
        }
    }
}
