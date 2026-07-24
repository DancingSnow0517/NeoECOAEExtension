package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOPlanningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ExecutorService PLANNING_POOL = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "ECO Crafting Planner " + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private ECOPlanningService() {
    }

    public static Future<ICraftingPlan> submit(
        ECOAE2PlanningSnapshot snapshot,
        CalculationStrategy strategy,
        ECOPlanningHostLease lease,
        Supplier<ICraftingPlan> ae2Fallback
    ) {
        return PLANNING_POOL.submit(() -> {
            try {
                long deadlineNanos = lease.budget().deadlineNanos();
                Optional<CraftingPlan> ecoPlan = Optional.empty();
                try {
                    ecoPlan = strategy == CalculationStrategy.CRAFT_LESS
                        ? solveCraftLess(snapshot, lease, deadlineNanos)
                        : solve(snapshot, lease, deadlineNanos);
                } catch (CancellationException cancelled) {
                    throw cancelled;
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.debug("ECO planning failed; the caller will use AE2 crafting calculation", failure);
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("ECO crafting planning was cancelled");
                }
                if (ecoPlan.isPresent()) {
                    return ecoPlan.get();
                }
                LOGGER.debug("ECO planning produced no executable plan; using AE2 crafting calculation");
                return ae2Fallback.get();
            } finally {
                lease.close();
            }
        });
    }

    private static Optional<CraftingPlan> solve(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), lease.budget(), deadlineNanos);
            return ECOAE2PlanAssembler.assemble(snapshot, result);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("ECO plan assembly failed; using AE2 crafting calculation", failure);
            return Optional.empty();
        }
    }

    private static Optional<CraftingPlan> solveCraftLess(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        ECOPlanningGraph<AEKey, IPatternDetails> graph = ECOGraphPruner.targetReachable(
            new ECOPlanningGraph<>(snapshot.problem().operations()),
            snapshot.problem().requested().keySet()
        );
        long low = 0L;
        long high = snapshot.requestedAmount();
        CraftingPlan best = null;
        while (low < high) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return Optional.empty();
            }
            long middle = low + ((high - low + 1L) / 2L);
            Optional<CraftingPlan> candidate = calculate(snapshot.forAmount(middle), graph, lease, deadlineNanos);
            if (candidate.isPresent() && !candidate.get().simulation()) {
                low = middle;
                best = candidate.get();
            } else {
                high = middle - 1L;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<CraftingPlan> calculate(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningGraph<AEKey, IPatternDetails> graph,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), graph, lease.budget(), deadlineNanos);
            return ECOAE2PlanAssembler.assemble(snapshot, result);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("ECO CRAFT_LESS candidate calculation failed", failure);
            return Optional.empty();
        }
    }
}
