package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanAssembler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2CraftingPlanCache;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PlanningSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2PatternVariant;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2SnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOCyclePlanningDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOOversizedPlanEstimator;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigInteger;
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

    public static void clearCaches() {
        ECOAE2SnapshotFactory.clearCache();
        ECOAE2CraftingPlanCache.clear();
        ECOPlanningGraph.clearCaches();
        ECOPlanningSolver.clearCaches();
    }

    public static Optional<CraftingPlan> findCachedPlan(
        ECOAE2PlanningSnapshot snapshot,
        CalculationStrategy strategy,
        ECOPlannerNoticeDispatcher.Target noticeTarget
    ) {
        long lookupStarted = System.nanoTime();
        Optional<CraftingPlan> cached = ECOAE2CraftingPlanCache.get(snapshot, strategy);
        cached.ifPresent(plan -> {
            long lookupElapsed = Math.max(1L, System.nanoTime() - lookupStarted);
            ECOPlannerNoticeDispatcher.send(
                noticeTarget,
                ECOPlannerFallbackReason.FAST_PATH,
                lookupElapsed
            );
            ECOPlannerNoticeDispatcher.sendCycleDiagnostics(noticeTarget, ECOCyclePlanningDiagnostics.EMPTY);
            ECOPlannerNoticeDispatcher.sendPlanningMissing(noticeTarget, collectMissingItems(plan));
        });
        return cached;
    }

    public static Future<ICraftingPlan> submit(
        ECOAE2PlanningSnapshot snapshot,
        CalculationStrategy strategy,
        ECOPlanningHostLease lease,
        ECOPlannerNoticeDispatcher.Target noticeTarget,
        Supplier<ICraftingPlan> ae2Fallback
    ) {
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        AtomicBoolean taskStarted = new AtomicBoolean();
        AtomicBoolean requestEnded = new AtomicBoolean();
        FutureTask<ICraftingPlan> task = new FutureTask<>(() -> {
            taskStarted.set(true);
            try (var diagnosticScope = ECOPlanningFailureDiagnostics.bindRequest(
                snapshot.diagnosticRequestId()
            )) {
            ECOPlanningFailureDiagnostics.setDiagnostics(snapshot.diagnostics());
            String diagnosticResult = "started";
            if (cancellationRequested.get()) {
                throw new CancellationException("ECO crafting planning was cancelled before execution");
            }
            FAILURE_REASON.set(ECOPlannerFallbackReason.PLANNING_FAILURE);
            long planningStarted = System.nanoTime();
            ECOPlannerNoticeDispatcher.send(noticeTarget, ECOPlannerFallbackReason.FAST_PATH);
            ECOPlannerNoticeDispatcher.sendCycleDiagnostics(noticeTarget, ECOCyclePlanningDiagnostics.EMPTY);
            ECOPlannerNoticeDispatcher.sendPlanningMissing(noticeTarget, Map.of());
            if (snapshot.dynamicSmithing()) {
                ECOPlannerNoticeDispatcher.send(
                    noticeTarget,
                    ECOPlannerFallbackReason.DYNAMIC_SMITHING,
                    0L,
                    snapshot.diagnostics()
                );
            }
            ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.ENTRY,
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
                PlanningAttempt attempt = PlanningAttempt.empty();
                try {
                    attempt = strategy == CalculationStrategy.CRAFT_LESS
                        ? PlanningAttempt.plan(solveCraftLess(snapshot, budget, deadlineNanos, noticeTarget))
                        : solve(snapshot, budget, deadlineNanos, noticeTarget);
                } catch (CancellationException cancelled) {
                    diagnosticResult = "cancelled";
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
                if (attempt.overflowBytes() != null) {
                    long elapsedNanos = System.nanoTime() - planningStarted;
                    ECOPlanningFailureDiagnostics.logTiming(
                        ECOPlanningFailureDiagnostics.Stage.ENTRY,
                        snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                        "eco_attempt_total", planningStarted,
                        "result=overflow_preview bytes=" + attempt.overflowBytes()
                    );
                    ECOPlannerNoticeDispatcher.sendOverflow(
                        noticeTarget,
                        elapsedNanos,
                        attempt.overflowBytes()
                    );
                    diagnosticResult = "eco_overflow_preview";
                    return missingTargetPlan(snapshot, Long.MAX_VALUE);
                }
                Optional<CraftingPlan> ecoPlan = attempt.plan();
                if (ecoPlan.isPresent()) {
                    ECOAE2CraftingPlanCache.put(snapshot, strategy, ecoPlan.get());
                    boolean simulation = ecoPlan.get().simulation();
                    ECOPlannerNoticeDispatcher.sendPlanningMissing(
                        noticeTarget,
                        collectMissingItems(ecoPlan.get())
                    );
                    ECOPlanningFailureDiagnostics.logTiming(
                        ECOPlanningFailureDiagnostics.Stage.ENTRY,
                        snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                        "eco_attempt_total", planningStarted,
                        simulation ? "result=missing_simulation" : "result=executable_plan"
                    );
                    ECOPlannerNoticeDispatcher.send(
                        noticeTarget,
                        snapshot.dynamicSmithing()
                            ? ECOPlannerFallbackReason.DYNAMIC_SMITHING
                            : ECOPlannerFallbackReason.FAST_PATH,
                        System.nanoTime() - planningStarted,
                        snapshot.dynamicSmithing() ? snapshot.diagnostics() : java.util.List.of()
                    );
                    diagnosticResult = simulation
                        ? "eco_missing_sources_simulation"
                        : "eco_executable_plan";
                    return ecoPlan.get();
                }
                ECOPlanningFailureDiagnostics.logTiming(
                    ECOPlanningFailureDiagnostics.Stage.ENTRY,
                    snapshot.requestedKey(), snapshot.requestedAmount(), strategy,
                    "eco_attempt_total", planningStarted,
                    "result=fallback reason=" + FAILURE_REASON.get()
                );
                if (hasDamageableInput(snapshot)) {
                    ECOPlanningFailureDiagnostics.addDiagnostic(ECOPlannerDiagnostic.DAMAGEABLE_INPUT);
                }
                ECOPlannerNoticeDispatcher.send(
                    noticeTarget,
                    FAILURE_REASON.get(),
                    0L,
                    ECOPlanningFailureDiagnostics.currentDiagnostics()
                );
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
                if (requestEnded.compareAndSet(false, true)) {
                    ECOPlanningFailureDiagnostics.endRequest(
                        snapshot.diagnosticRequestId(),
                        diagnosticResult
                    );
                }
                FAILURE_REASON.remove();
            }
            }
        }) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancellationRequested.set(true);
                // AE2's fallback is registered with TickHandler and may be blocked in
                // CraftingCalculation.handlePausing(). The interrupt is required to make its
                // run() method reach finish(), otherwise TickHandler keeps simulating it forever.
                return super.cancel(mayInterruptIfRunning);
            }

            @Override
            protected void done() {
                // FutureTask does not execute its callable when cancellation wins the race
                // before the worker starts. Release the host lease and diagnostics in that case;
                // a started task owns both until its callable finally block runs.
                if (!taskStarted.get()) {
                    lease.close();
                    if (requestEnded.compareAndSet(false, true)) {
                        ECOPlanningFailureDiagnostics.endRequest(
                            snapshot.diagnosticRequestId(),
                            "cancelled_before_execution"
                        );
                    }
                }
            }
        };
        PLANNING_POOL.execute(task);
        return task;
    }

    private static PlanningAttempt solve(
        ECOAE2PlanningSnapshot snapshot,
        ECOSolveBudget budget,
        long deadlineNanos,
        ECOPlannerNoticeDispatcher.Target noticeTarget
    ) {
        try {
            ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph =
                ECOGraphPruner.targetReachable(snapshot.problem());
            long solverStarted = System.nanoTime();
            var result = ECOPlanningSolver.solve(snapshot.problem(), graph, budget, deadlineNanos);
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
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result, graph);
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                snapshot.requestedKey(), snapshot.requestedAmount(), "assembler",
                "assembler_total", assemblerStarted, "planPresent=" + plan.isPresent()
            );
            if (!cycleDiagnostics.isEmpty() || plan.isPresent()) {
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
            return PlanningAttempt.plan(plan);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (ArithmeticException overflow) {
            Optional<BigInteger> exactBytes = ECOOversizedPlanEstimator.estimateBytes(snapshot);
            if (exactBytes.isPresent()) {
                markFailure(ECOPlannerFallbackReason.OVERFLOW);
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.OVERFLOW,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "overflow_preview",
                    "long_range_exceeded exactBytes=" + exactBytes.get(),
                    overflow
                );
                return PlanningAttempt.overflow(exactBytes.get());
            }
            markFailure(ECOPlannerFallbackReason.PLANNING_FAILURE);
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "assembler",
                "overflow_preview_recalculation_failed",
                overflow
            );
            return PlanningAttempt.empty();
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
            return PlanningAttempt.empty();
        }
    }

    private static Optional<CraftingPlan> solveCraftLess(
        ECOAE2PlanningSnapshot snapshot,
        ECOSolveBudget budget,
        long deadlineNanos,
        ECOPlannerNoticeDispatcher.Target noticeTarget
    ) {
        ECOPlanningGraph<AEKey, ECOAE2PatternVariant> graph = ECOGraphPruner.targetReachable(
            snapshot.problem().operations(), snapshot.problem().requested().keySet()
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
            Optional<CraftingPlan> plan = ECOAE2PlanAssembler.assemble(snapshot, result, graph);
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

    private static Map<AEKey, Long> collectMissingItems(ICraftingPlan plan) {
        Map<AEKey, Long> missing = new LinkedHashMap<>();
        for (var entry : plan.missingItems()) {
            if (entry.getLongValue() > 0L) {
                missing.put(entry.getKey(), entry.getLongValue());
            }
        }
        return Map.copyOf(missing);
    }

    private static void markFailure(ECOPlannerFallbackReason reason) {
        FAILURE_REASON.set(reason);
    }

    private static boolean hasDamageableInput(ECOAE2PlanningSnapshot snapshot) {
        return snapshot.problem().operations().stream()
            .flatMap(operation -> operation.inputs().keySet().stream())
            .filter(AEItemKey.class::isInstance)
            .map(AEItemKey.class::cast)
            .anyMatch(ECOPlanningService::isDamageable);
    }

    private static boolean isDamageable(AEItemKey key) {
        try {
            return key.toStack(1).isDamageableItem();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static CraftingPlan missingTargetPlan(ECOAE2PlanningSnapshot snapshot) {
        return missingTargetPlan(snapshot, Long.MAX_VALUE);
    }

    private static CraftingPlan missingTargetPlan(ECOAE2PlanningSnapshot snapshot, long bytes) {
        KeyCounter missing = new KeyCounter();
        missing.add(snapshot.requestedKey(), snapshot.requestedAmount());
        return new CraftingPlan(
            new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
            bytes,
            true,
            snapshot.multiplePaths(),
            new KeyCounter(),
            new KeyCounter(),
            missing,
            java.util.Map.of()
        );
    }

    private record PlanningAttempt(Optional<CraftingPlan> plan, BigInteger overflowBytes) {
        static PlanningAttempt empty() {
            return new PlanningAttempt(Optional.empty(), null);
        }

        static PlanningAttempt plan(Optional<CraftingPlan> plan) {
            return new PlanningAttempt(plan, null);
        }

        static PlanningAttempt overflow(BigInteger exactBytes) {
            return new PlanningAttempt(Optional.empty(), exactBytes);
        }
    }
}
