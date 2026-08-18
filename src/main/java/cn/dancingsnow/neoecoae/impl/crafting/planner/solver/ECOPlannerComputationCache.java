package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Bounded planner result cache with request coalescing for concurrent callers. */
final class ECOPlannerComputationCache {
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<PlanningKey, ECOHyperflowResult<?>> RESULTS =
        new LinkedHashMap<>(16, 0.75F, true);
    private static final ConcurrentHashMap<PlanningKey, CompletableFuture<ECOHyperflowResult<?>>> IN_FLIGHT =
        new ConcurrentHashMap<>();
    private static long cacheGeneration;

    private ECOPlannerComputationCache() {
    }

    static <K, R> ECOHyperflowResult<R> getOrCompute(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget,
        long deadlineNanos,
        Supplier<ECOHyperflowResult<R>> computation
    ) {
        PlanningKey key;
        synchronized (LOCK) {
            key = PlanningKey.of(problem, graph, budget, cacheGeneration);
            ECOHyperflowResult<?> cached = RESULTS.get(key);
            if (cached != null) {
                return cast(cached);
            }
        }

        CompletableFuture<ECOHyperflowResult<?>> created = new CompletableFuture<>();
        CompletableFuture<ECOHyperflowResult<?>> running = IN_FLIGHT.putIfAbsent(key, created);
        if (running == null) {
            try {
                ECOHyperflowResult<R> result = computation.get();
                if (result.status() == ECOHyperflowResult.Status.COMPLETE) {
                    synchronized (LOCK) {
                        if (key.cacheGeneration() == cacheGeneration) {
                            RESULTS.put(key, result);
                            trimResultsLocked();
                        }
                    }
                }
                created.complete(result);
                return result;
            } catch (RuntimeException | Error failure) {
                created.completeExceptionally(failure);
                throw failure;
            } finally {
                IN_FLIGHT.remove(key, created);
            }
        }

        try {
            long remaining = deadlineNanos == Long.MAX_VALUE
                ? 30_000L
                : Math.max(1L, deadlineNanos - System.nanoTime());
            return cast(running.get(remaining, TimeUnit.NANOSECONDS));
        } catch (TimeoutException timeout) {
            // Do not start a second solve after the shared computation has been admitted. The
            // caller can safely report budget exhaustion while the owner continues publishing.
            return budgetExhausted(problem);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return budgetExhausted(problem);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Cached ECO planner computation failed", cause);
        }
    }

    static void clear() {
        synchronized (LOCK) {
            RESULTS.clear();
            cacheGeneration++;
        }
    }

    private static void trimResultsLocked() {
        int limit = Math.clamp(
            NEConfig.ecoPlannerResultCacheSize,
            16,
            NEConfig.ECO_PLANNER_CACHE_HARD_MAX
        );
        while (RESULTS.size() > limit) {
            RESULTS.remove(RESULTS.keySet().iterator().next());
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, R> ECOHyperflowResult<R> cast(ECOHyperflowResult<?> value) {
        return (ECOHyperflowResult<R>) value;
    }

    private static <K, R> ECOHyperflowResult<R> budgetExhausted(ECOPlanningProblem<K, R> problem) {
        return new ECOHyperflowResult<>(
            ECOHyperflowResult.Status.BUDGET_EXHAUSTED,
            new ECOPlanCandidate<>(
                Map.of(),
                ECOPlannerMath.saturatedSum(problem.requested().values()),
                0L,
                0L,
                0L
            ),
            0L
        );
    }

    private record PlanningKey(
        long cacheGeneration,
        long graphRevision,
        long recipeBindingVersion,
        List<?> operations,
        Object requested,
        Object inventory,
        Object unlimitedInventory,
        long maxExpandedStates,
        int maxDepth,
        int extraBatchChoices,
        long maxDurationNanos,
        int componentMaterialLimit,
        int componentOperationLimit,
        int plannerBudgetVersion,
        int componentSolveMillis
    ) {
        private static <K, R> PlanningKey of(
            ECOPlanningProblem<K, R> problem,
            ECOPlanningGraph<K, R> graph,
            ECOSolveBudget budget,
            long cacheGeneration
        ) {
            return new PlanningKey(
                cacheGeneration,
                graph.revision(),
                graph.recipeBindingVersion(),
                graph.operations(),
                problem.requested(),
                problem.inventory(),
                problem.unlimitedInventory(),
                budget.maxExpandedStates(),
                budget.maxDepth(),
                budget.extraBatchChoices(),
                budget.maxDurationNanos(),
                NEConfig.ecoPlannerMaxComponentMaterials,
                NEConfig.ecoPlannerMaxComponentOperations,
                NEConfig.ECO_PLANNER_BUDGET_VERSION,
                NEConfig.ecoPlannerComponentSolveMillis
            );
        }
    }
}
