package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

/** Integer balance solver for one small cyclic material component. */
public final class ECOOjAlgoCycleSolver {
    private static final int MAX_COMPONENT_MATERIALS = 32;
    private static final int MAX_COMPONENT_OPERATIONS = 64;
    private static final long MAX_SOLVE_MILLIS = 250L;

    private ECOOjAlgoCycleSolver() {
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        Set<K> component = requestedCycle(problem, graph);
        if (component == null || component.size() > MAX_COMPONENT_MATERIALS) {
            return Optional.empty();
        }

        List<ECOPlanningOperation<K, R>> operations = graph.operations().stream()
            .filter(operation -> operation.selectableOutputs().stream().anyMatch(component::contains))
            .toList();
        if (operations.isEmpty() || operations.size() > MAX_COMPONENT_OPERATIONS) {
            return Optional.empty();
        }

        if (hasUnmetRequest(problem) && isClosedUnseededComponent(problem, component, operations)) {
            long requested = ECOPlannerMath.saturatedSum(problem.requested().values());
            return Optional.of(new ECOHyperflowResult<>(
                ECOHyperflowResult.Status.NO_ROUTE,
                new ECOPlanCandidate<>(Map.of(), requested, 0L, 0L, 0L),
                0L
            ));
        }

        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }

        ExpressionsBasedModel model = new ExpressionsBasedModel();
        Map<ECOPlanningOperation<K, R>, Variable> variables = new LinkedHashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            Variable variable = model.addVariable("operation_" + index)
                .integer(true)
                .lower(BigDecimal.ZERO)
                .weight(BigDecimal.ONE);
            variables.put(operations.get(index), variable);
        }

        Set<K> constrainedMaterials = new LinkedHashSet<>(component);
        operations.forEach(operation -> {
            constrainedMaterials.addAll(operation.inputs().keySet());
            constrainedMaterials.addAll(operation.outputs().keySet());
        });
        constrainedMaterials.addAll(problem.requested().keySet());

        int materialIndex = 0;
        for (K material : constrainedMaterials) {
            long required = Math.subtractExact(
                problem.requested().getOrDefault(material, 0L),
                problem.inventory().getOrDefault(material, 0L)
            );
            Expression balance = model.addExpression("material_" + materialIndex++).lower(required);
            for (var entry : variables.entrySet()) {
                long coefficient = Math.subtractExact(
                    entry.getKey().outputAmount(material),
                    entry.getKey().inputAmount(material)
                );
                if (coefficient != 0L) {
                    balance.set(entry.getValue(), coefficient);
                }
            }
        }

        model.options.time_abort = remainingMillis(deadlineNanos);
        Optimisation.Result solved = model.minimise();
        if (!solved.getState().isFeasible() || ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }

        Map<R, Long> executions = new LinkedHashMap<>();
        for (int index = 0; index < operations.size(); index++) {
            long count;
            try {
                count = solved.get(index).setScale(0, RoundingMode.HALF_UP).longValueExact();
            } catch (ArithmeticException invalidCount) {
                return Optional.empty();
            }
            if (count > 0L) {
                executions.put(operations.get(index).reference(), count);
            }
        }

        ECOPlanCandidate<R> candidate = candidate(problem, operations, executions);
        if (candidate == null) {
            return Optional.empty();
        }
        ECOHyperflowResult<R> result = new ECOHyperflowResult<>(
            candidate.requestedShortfall() == 0L && candidate.dependencyShortfall() == 0L
                ? candidate.sourceShortfall() == 0L
                    ? ECOHyperflowResult.Status.COMPLETE
                    : ECOHyperflowResult.Status.MISSING_SOURCES
                : ECOHyperflowResult.Status.NO_ROUTE,
            candidate,
            1L
        );
        if (result.status() != ECOHyperflowResult.Status.COMPLETE
            || !ECOInventoryScheduler.schedule(problem, candidate).executable()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private static <K, R> Set<K> requestedCycle(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        for (Set<K> component : ECOStrongComponents.find(graph)) {
            if (!component.containsAll(problem.requested().keySet()) || !isCyclic(component, graph)) {
                continue;
            }
            return component;
        }
        return null;
    }

    private static <K, R> boolean isCyclic(Set<K> component, ECOPlanningGraph<K, R> graph) {
        if (component.size() > 1) {
            return true;
        }
        K material = component.iterator().next();
        return graph.operations().stream().anyMatch(operation ->
            operation.inputs().containsKey(material) && operation.outputs().containsKey(material));
    }

    private static <K, R> boolean isClosedUnseededComponent(
        ECOPlanningProblem<K, R> problem,
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations
    ) {
        boolean closed = operations.stream()
            .allMatch(operation -> component.containsAll(operation.inputs().keySet()));
        if (!closed) {
            return false;
        }
        return operations.stream().noneMatch(operation -> canStart(operation, problem.inventory()));
    }

    private static <K, R> boolean hasUnmetRequest(ECOPlanningProblem<K, R> problem) {
        return problem.requested().entrySet().stream().anyMatch(request ->
            problem.inventory().getOrDefault(request.getKey(), 0L) < request.getValue());
    }

    private static <K, R> boolean canStart(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory
    ) {
        return operation.inputs().entrySet().stream()
            .allMatch(input -> inventory.getOrDefault(input.getKey(), 0L) >= input.getValue());
    }

    private static long remainingMillis(long deadlineNanos) {
        if (deadlineNanos == Long.MAX_VALUE) {
            return MAX_SOLVE_MILLIS;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return 1L;
        }
        long remainingMillis = Math.max(1L, remainingNanos / 1_000_000L);
        return Math.min(MAX_SOLVE_MILLIS, remainingMillis);
    }

    private static <K, R> ECOPlanCandidate<R> candidate(
        ECOPlanningProblem<K, R> problem,
        List<ECOPlanningOperation<K, R>> operations,
        Map<R, Long> executions
    ) {
        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        try {
            for (ECOPlanningOperation<K, R> operation : operations) {
                long count = executions.getOrDefault(operation.reference(), 0L);
                for (var input : operation.inputs().entrySet()) {
                    mergeScaled(balances, input.getKey(), input.getValue(), -count);
                }
                for (var output : operation.outputs().entrySet()) {
                    mergeScaled(balances, output.getKey(), output.getValue(), count);
                }
            }
            problem.requested().forEach((material, amount) ->
                balances.merge(material, -amount, Math::addExact));
        } catch (ArithmeticException overflow) {
            return null;
        }

        Set<K> expandable = new LinkedHashSet<>();
        operations.forEach(operation -> expandable.addAll(operation.selectableOutputs()));
        return ECOPlannerMath.buildResult(
            balances,
            executions,
            problem.requested(),
            expandable,
            1L
        ).candidate();
    }

    private static <K> void mergeScaled(
        Map<K, Long> balances,
        K material,
        long amount,
        long count
    ) {
        balances.merge(material, Math.multiplyExact(amount, count), Math::addExact);
    }
}
