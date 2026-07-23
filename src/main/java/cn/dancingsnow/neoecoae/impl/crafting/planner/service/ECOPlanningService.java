package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2SnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ECOPlanningService {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ExecutorService PLANNING_POOL = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "ECO Crafting Planner " + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private ECOPlanningService() {
    }

    public static Future<ICraftingPlan> submit(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration,
        ECOPlanningHostLease lease,
        Supplier<ICraftingPlan> ae2Fallback
    ) {
        return PLANNING_POOL.submit(() -> {
            try {
                var snapshot = ECOAE2SnapshotFactory.capture(
                    grid,
                    requester,
                    requestedKey,
                    requestedAmount,
                    strategy,
                    craftableGeneration
                );
                if (snapshot.isEmpty()) {
                    return ae2Fallback.get();
                }
                return solve(snapshot.get(), lease, ae2Fallback);
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (RuntimeException | LinkageError ignored) {
                return ae2Fallback.get();
            } finally {
                lease.close();
            }
        });
    }

    private static ICraftingPlan solve(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningHostLease lease,
        Supplier<ICraftingPlan> ae2Fallback
    ) {
        Optional<CraftingPlan> ecoPlan = Optional.empty();
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), lease.budget());
            ecoPlan = ECOAE2PlanAssembler.assemble(snapshot, result);
        } catch (RuntimeException | LinkageError ignored) {
            // Compatibility and bounded-search failures are routed through AE2's original calculation.
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("ECO crafting planning was cancelled");
        }
        return ecoPlan.<ICraftingPlan>map(plan -> plan).orElseGet(ae2Fallback);
    }
}
