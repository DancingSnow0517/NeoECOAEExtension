package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final ThreadLocal<ECOPlannerFallbackReason> FAILURE_REASON =
        ThreadLocal.withInitial(() -> ECOPlannerFallbackReason.FAST_PATH);
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
            FAILURE_REASON.set(ECOPlannerFallbackReason.FAST_PATH);
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
                    markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
                    LOGGER.debug("ECO planning failed; the caller will use AE2 crafting calculation", failure);
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("ECO crafting planning was cancelled");
                }
                if (ecoPlan.isPresent()) {
                    if (NEConfig.ecoPlannerDifferentialVerification) {
                        ICraftingPlan ae2Plan = ae2Fallback.get();
                        if (!plansEquivalent(ecoPlan.get(), ae2Plan)) {
                            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.DIFFERENTIAL_MISMATCH);
                            ECOPlannerNoticeDispatcher.send(
                                snapshot.noticeTarget(), ECOPlannerFallbackReason.DIFFERENTIAL_MISMATCH
                            );
                            LOGGER.warn("ECO planning differential verification disagreed with AE2; using AE2 result");
                            return ae2Plan;
                        }
                    }
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.ECO_ACCEPTED);
                    ECOPlannerNoticeDispatcher.send(snapshot.noticeTarget(), ECOPlannerFallbackReason.FAST_PATH);
                    return ecoPlan.get();
                }
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.AE2_FALLBACK);
                ECOPlannerFallbackReason reason = FAILURE_REASON.get();
                ECOPlannerNoticeDispatcher.send(
                    snapshot.noticeTarget(),
                    reason == ECOPlannerFallbackReason.FAST_PATH ? ECOPlannerFallbackReason.PLANNING_FAILURE : reason
                );
                LOGGER.debug("ECO planning produced no executable plan; using AE2 crafting calculation");
                return ae2Fallback.get();
            } finally {
                lease.close();
                FAILURE_REASON.remove();
            }
        });
    }

    private static Optional<CraftingPlan> solve(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        long startedNanos = System.nanoTime();
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), lease.budget(), deadlineNanos);
            if (result.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status.NO_ROUTE
                || result.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SOLVER_REJECTED);
                markSolverFailure(result.status());
                LOGGER.debug(
                    "ECO planning solver rejected {} x{} with status {} after {} states and {} ms; "
                        + "requested={}, dependencies={}, sources={}, operations={}, inventoryKeys={}, "
                        + "maxStates={}, maxDepth={}, extraBatches={}, deadlineReached={}",
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    result.status(),
                    result.expandedStates(),
                    elapsedMillis(startedNanos),
                    result.candidate().requestedShortfall(),
                    result.candidate().dependencyShortfall(),
                    result.candidate().sourceShortfall(),
                    snapshot.problem().operations().size(),
                    snapshot.problem().inventory().size(),
                    lease.budget().maxExpandedStates(),
                    lease.budget().maxDepth(),
                    lease.budget().extraBatchChoices(),
                    ECOSolveBudget.shouldStop(deadlineNanos)
                );
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                        "ECO planning unresolved dependency frontier for {}: {}",
                        snapshot.requestedKey(),
                        ECOPlanningFrontierDiagnostics.describe(snapshot.problem(), result.candidate())
                    );
                }
                return Optional.empty();
            }
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result, deadlineNanos);
            if (plan.isEmpty()) {
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SOLVER_REJECTED);
                    markFailure(ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
                } else {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.ASSEMBLY_REJECTED);
                    markFailure(ECOPlannerFallbackReason.ASSEMBLY_REJECTED);
                }
                LOGGER.debug(
                    "ECO planning result for {} x{} could not be assembled after {} ms: status={}, states={}, "
                        + "requested={}, dependencies={}, sources={}",
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    elapsedMillis(startedNanos),
                    result.status(),
                    result.expandedStates(),
                    result.candidate().requestedShortfall(),
                    result.candidate().dependencyShortfall(),
                    result.candidate().sourceShortfall()
                );
            } else {
                LOGGER.debug(
                    "ECO planning accepted {} x{} in {} ms after {} states: status={}, patterns={}, bytes={}",
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    elapsedMillis(startedNanos),
                    result.expandedStates(),
                    result.status(),
                    plan.get().patternTimes().size(),
                    plan.get().bytes()
                );
            }
            return plan;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException | LinkageError failure) {
            markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
            LOGGER.debug("ECO plan assembly failed; using AE2 crafting calculation", failure);
            return Optional.empty();
        }
    }

    private static Optional<CraftingPlan> solveCraftLess(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph = ECOGraphPruner.targetReachable(
            new ECOPlanningGraph<>(snapshot.problem().operations()),
            snapshot.problem().requested().keySet()
        );
        long low = 0L;
        long high = snapshot.requestedAmount();
        CraftingPlan best = null;
        while (low < high) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                markFailure(ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
                return Optional.empty();
            }
            long middle = low + ((high - low + 1L) / 2L);
            Optional<CraftingPlan> candidate = calculate(snapshot.forAmount(middle), graph, lease, deadlineNanos);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            if (!candidate.get().simulation()) {
                low = middle;
                best = candidate.get();
            } else {
                high = middle - 1L;
            }
        }
        if (best == null) {
            markFailure(ECOPlannerFallbackReason.CRAFT_LESS_NO_CRAFTABLE);
        }
        return Optional.ofNullable(best);
    }

    private static Optional<CraftingPlan> calculate(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph,
        ECOPlanningHostLease lease,
        long deadlineNanos
    ) {
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), graph, lease.budget(), deadlineNanos);
            if (result.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status.NO_ROUTE
                || result.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                markSolverFailure(result.status());
                return Optional.empty();
            }
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result, deadlineNanos);
            if (plan.isEmpty()) {
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SOLVER_REJECTED);
                    markFailure(ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
                } else {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.ASSEMBLY_REJECTED);
                    markFailure(ECOPlannerFallbackReason.ASSEMBLY_REJECTED);
                }
            }
            return plan;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException | LinkageError failure) {
            markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
            LOGGER.debug("ECO CRAFT_LESS candidate calculation failed", failure);
            return Optional.empty();
        }
    }

    private static void markSolverFailure(
        cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status status
    ) {
        markFailure(status == cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult.Status.NO_ROUTE
            ? ECOPlannerFallbackReason.SOLVER_NO_ROUTE
            : ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
    }

    private static void markFailure(ECOPlannerFallbackReason reason) {
        FAILURE_REASON.set(reason);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static boolean plansEquivalent(ICraftingPlan eco, ICraftingPlan ae2) {
        return eco.simulation() == ae2.simulation()
            && eco.finalOutput().equals(ae2.finalOutput())
            && counterContents(eco.usedItems()).equals(counterContents(ae2.usedItems()))
            && counterContents(eco.emittedItems()).equals(counterContents(ae2.emittedItems()))
            && counterContents(eco.missingItems()).equals(counterContents(ae2.missingItems()))
            && eco.patternTimes().equals(ae2.patternTimes());
    }

    private static Map<AEKey, Long> counterContents(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) {
                result.put(entry.getKey(), entry.getLongValue());
            }
        }
        return Map.copyOf(result);
    }
}
