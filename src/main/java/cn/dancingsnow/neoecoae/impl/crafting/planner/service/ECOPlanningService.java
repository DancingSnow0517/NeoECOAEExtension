package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOPlanningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadLocal<ECOPlannerFallbackReason> FAILURE_REASON =
        ThreadLocal.withInitial(() -> ECOPlannerFallbackReason.PLANNING_FAILURE);
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
        ECOPlannerNoticeDispatcher.Target noticeTarget,
        Supplier<ICraftingPlan> ae2Fallback
    ) {
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        FutureTask<ICraftingPlan> task = new FutureTask<>(() -> {
            try (var diagnosticScope = ECOPlanningFailureDiagnostics.bindRequest(
                snapshot.diagnosticRequestId()
            )) {
            String diagnosticResult = "started";
            if (cancellationRequested.get()) {
                throw new CancellationException("ECO crafting planning was cancelled before execution");
            }
            FAILURE_REASON.set(ECOPlannerFallbackReason.PLANNING_FAILURE);
            long planningStarted = System.nanoTime();
            ECOPlannerNoticeDispatcher.sendCycleDiagnostics(noticeTarget, ECOCyclePlanningDiagnostics.EMPTY);
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ENTRY,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                strategy,
                "operations=" + snapshot.problem().operations().size()
                    + " inventory=" + snapshot.problem().inventory().size()
                    + " multiplePaths=" + snapshot.multiplePaths()
            );
            try {
                ECOSolveBudget budget = NEConfig.debugECOPlanner
                    ? lease.budget().forDebug()
                    : lease.budget();
                long deadlineNanos = budget.deadlineNanos();
                ECOPlanningFailureDiagnostics.logTrace(
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    strategy,
                    "solve_budget debugExtended=" + budget.extendedForDebug()
                        + " maxDurationMs=" + (budget.maxDurationNanos() / 1_000_000L)
                        + " maxExpandedStates=" + budget.maxExpandedStates()
                        + " maxDepth=" + budget.maxDepth()
                        + " extraBatchChoices=" + budget.extraBatchChoices()
                );
                Optional<CraftingPlan> ecoPlan = Optional.empty();
                try {
                    ecoPlan = strategy == CalculationStrategy.CRAFT_LESS
                        ? solveCraftLess(snapshot, budget, deadlineNanos, noticeTarget)
                        : solve(snapshot, budget, deadlineNanos, noticeTarget);
                } catch (CancellationException cancelled) {
                    throw cancelled;
                } catch (StackOverflowError overflow) {
                    markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
                        ECOPlannerFallbackReason.PLANNING_FAILURE,
                        snapshot.requestedKey(),
                        snapshot.requestedAmount(),
                        strategy,
                        "solver_stack_overflow_ae2_fallback",
                        overflow
                    );
                } catch (RuntimeException | LinkageError failure) {
                    markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.FALLBACK,
                        ECOPlannerFallbackReason.PLANNING_FAILURE,
                        snapshot.requestedKey(),
                        snapshot.requestedAmount(),
                        strategy,
                        "planning_worker_failed",
                        failure
                    );
                    LOGGER.debug("ECO planning failed; the caller will use AE2 crafting calculation", failure);
                }
                if (cancellationRequested.get() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("ECO crafting planning was cancelled");
                }
                if (ecoPlan.isPresent()) {
                    ECOPlanningFailureDiagnostics.logTiming(
                        ECOPlanningFailureDiagnostics.Stage.ENTRY,
                        snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                        "eco_attempt_total", planningStarted, "result=success"
                    );
                    ECOPlannerNoticeDispatcher.send(noticeTarget, ECOPlannerFallbackReason.FAST_PATH);
                    diagnosticResult = "eco_success";
                    return ecoPlan.get();
                }
                ECOPlanningFailureDiagnostics.logTiming(
                    ECOPlanningFailureDiagnostics.Stage.ENTRY,
                    snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                    "eco_attempt_total", planningStarted,
                    "result=fallback reason=" + FAILURE_REASON.get()
                );
                ECOPlannerNoticeDispatcher.send(noticeTarget, FAILURE_REASON.get());
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.FALLBACK,
                    FAILURE_REASON.get(),
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    strategy,
                    "no_executable_eco_plan_ae2_fallback"
                );
                LOGGER.debug("ECO planning produced no executable plan; using AE2 crafting calculation");
                diagnosticResult = "ae2_fallback reason=" + FAILURE_REASON.get();
                if (cancellationRequested.get() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("ECO crafting planning was cancelled before AE2 fallback");
                }
                try {
                    long fallbackStarted = System.nanoTime();
                    ICraftingPlan fallbackPlan = ae2Fallback.get();
                    ECOPlanningFailureDiagnostics.logTiming(
                        ECOPlanningFailureDiagnostics.Stage.FALLBACK,
                        snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                        "ae2_fallback", fallbackStarted, "result=success"
                    );
                    diagnosticResult = "ae2_fallback_success reason=" + FAILURE_REASON.get();
                    return fallbackPlan;
                } catch (StackOverflowError overflow) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.FALLBACK,
                        ECOPlannerFallbackReason.PLANNING_FAILURE,
                        snapshot.requestedKey(),
                        snapshot.requestedAmount(),
                        strategy,
                        "ae2_fallback_stack_overflow_returning_missing_plan",
                        overflow
                    );
                    diagnosticResult = "ae2_fallback_stack_overflow_missing_plan";
                    return missingTargetPlan(snapshot);
                }
            } finally {
                ECOPlanningFailureDiagnostics.logTiming(
                    ECOPlanningFailureDiagnostics.Stage.ENTRY,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    strategy,
                    "planning_worker_total",
                    planningStarted,
                    "failureReason=" + FAILURE_REASON.get()
                );
                lease.close();
                ECOPlanningFailureDiagnostics.endRequest(
                    snapshot.diagnosticRequestId(),
                    diagnosticResult
                );
                FAILURE_REASON.remove();
            }
            }
        }) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancellationRequested.set(true);
                // AE2's fallback waits through handlePausing(). Interrupting that worker makes
                // AE2 log an InterruptedException instead of treating this as a normal cancel.
                return super.cancel(false);
            }
        };
        PLANNING_POOL.execute(task);
        return task;
    }

    private static Optional<CraftingPlan> solve(
        ECOAE2PlanningSnapshot snapshot,
        ECOSolveBudget budget,
        long deadlineNanos,
        ECOPlannerNoticeDispatcher.Target noticeTarget
    ) {
        try {
            long solverStarted = System.nanoTime();
            var result = ECOPlanningSolver.solve(snapshot.problem(), budget, deadlineNanos);
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
                snapshot.requestedKey(), snapshot.requestedAmount(), "solver",
                "solver_total", solverStarted,
                "status=" + result.status() + " expandedStates=" + result.expandedStates()
            );
            ECOCyclePlanningDiagnostics cycleDiagnostics = ECOCyclePlanningDiagnostics.from(snapshot, result);
            ECOPlanningFailureDiagnostics.logSolverResult(
                ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
                snapshot.problem(),
                result,
                "graph=target_reachable"
            );
            if (result.status() == ECOHyperflowResult.Status.NO_ROUTE) {
                markFailure(ECOPlannerFallbackReason.SOLVER_NO_ROUTE);
            } else if (result.status() == ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                markFailure(ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
            }
            long assemblerStarted = System.nanoTime();
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result);
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                snapshot.requestedKey(), snapshot.requestedAmount(), "assembler",
                "assembler_total", assemblerStarted, "planPresent=" + plan.isPresent()
            );
            if (!cycleDiagnostics.missingSeeds().isEmpty() || plan.isPresent()) {
                ECOPlannerNoticeDispatcher.sendCycleDiagnostics(noticeTarget, cycleDiagnostics);
            }
            if (plan.isEmpty()) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "resultStatus=" + result.status()
                        + " expandedStates=" + result.expandedStates()
                );
            }
            if (plan.isEmpty()
                && result.status() != ECOHyperflowResult.Status.NO_ROUTE
                && result.status() != ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                markFailure(ECOPlannerFallbackReason.ASSEMBLY_REJECTED);
            }
            return plan;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException | LinkageError failure) {
            markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "assembler",
                "solver_or_assembly_exception",
                failure
            );
            LOGGER.debug("ECO plan assembly failed; using AE2 crafting calculation", failure);
            return Optional.empty();
        }
    }

    private static Optional<CraftingPlan> solveCraftLess(
        ECOAE2PlanningSnapshot snapshot,
        ECOSolveBudget budget,
        long deadlineNanos,
        ECOPlannerNoticeDispatcher.Target noticeTarget
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
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.SOLVER_SELECTION,
                    ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    CalculationStrategy.CRAFT_LESS,
                    "craft_less_binary_search_deadline"
                );
                return Optional.empty();
            }
            long middle = low + ((high - low + 1L) / 2L);
            Optional<CraftingPlan> candidate = calculate(snapshot.forAmount(middle), graph, budget, deadlineNanos);
            if (candidate.isPresent() && !candidate.get().simulation()) {
                low = middle;
                best = candidate.get();
            } else {
                high = middle - 1L;
            }
        }
        if (best == null) {
            markFailure(ECOPlannerFallbackReason.CRAFT_LESS_NO_CRAFTABLE);
        }
        ECOPlannerNoticeDispatcher.sendCycleDiagnostics(noticeTarget, ECOCyclePlanningDiagnostics.EMPTY);
        return Optional.ofNullable(best);
    }

    private static Optional<CraftingPlan> calculate(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        try {
            var result = ECOPlanningSolver.solve(snapshot.problem(), graph, budget, deadlineNanos);
            if (result.status() == ECOHyperflowResult.Status.NO_ROUTE) {
                markFailure(ECOPlannerFallbackReason.SOLVER_NO_ROUTE);
            } else if (result.status() == ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                markFailure(ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED);
            }
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result);
            if (plan.isEmpty()
                && result.status() != ECOHyperflowResult.Status.NO_ROUTE
                && result.status() != ECOHyperflowResult.Status.BUDGET_EXHAUSTED) {
                markFailure(ECOPlannerFallbackReason.ASSEMBLY_REJECTED);
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

    private static void markFailure(ECOPlannerFallbackReason reason) {
        FAILURE_REASON.set(reason);
    }

    private static CraftingPlan missingTargetPlan(ECOAE2PlanningSnapshot snapshot) {
        KeyCounter missing = new KeyCounter();
        missing.add(snapshot.requestedKey(), snapshot.requestedAmount());
        return new CraftingPlan(
            new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
            1L,
            true,
            snapshot.multiplePaths(),
            new KeyCounter(),
            new KeyCounter(),
            missing,
            java.util.Map.of()
        );
    }
}
