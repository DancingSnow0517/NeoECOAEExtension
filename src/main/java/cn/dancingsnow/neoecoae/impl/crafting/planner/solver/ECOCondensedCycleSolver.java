package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.HashSet;
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

/** Demand propagation over the SCC condensation DAG with local integer solves for cyclic components. */
public final class ECOCondensedCycleSolver {
    private static final int MAX_COMPONENT_MATERIALS = 32;
    private static final int MAX_COMPONENT_OPERATIONS = 64;
    private static final long MAX_COMPONENT_SOLVE_MILLIS = 2_000L;
    private static final BigDecimal EXTERNAL_INPUT_WEIGHT = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE);
    private static final BigDecimal BOUNDARY_DEFICIT_WEIGHT = BigDecimal.TEN.pow(40);

    private ECOCondensedCycleSolver() {
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        Components<K> components = Components.build(graph);
        if (!components.hasCycle()) {
            return Optional.empty();
        }

        Map<K, Long> balances;
        try {
            balances = ECOPlannerMath.initialBalances(problem);
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        Map<R, Long> executions = new LinkedHashMap<>();
        Set<R> cycleOperations = new LinkedHashSet<>();
        Set<R> missingSeedStarters = new LinkedHashSet<>();
        Map<K, Long> missingSeedAmounts = new LinkedHashMap<>();
        Set<K> terminalBoundaryDeficits = new LinkedHashSet<>();
        Map<K, Long> boundaryDeficitAmounts = new LinkedHashMap<>();
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        problem.requested().keySet().forEach(key -> enqueue(key, balances, graph, queue, queued));

        long expansions = 0L;
        long maxExpansions = Math.max(64L, graph.materials().size() * 4L + graph.operations().size() * 2L);
        try {
            while (!queue.isEmpty()) {
                if (ECOSolveBudget.shouldStop(deadlineNanos) || ++expansions > maxExpansions) {
                    logFailure(problem, "propagation_budget expansions=" + expansions + " queue=" + queue.size());
                    return Optional.empty();
                }
                K material = queue.removeFirst();
                queued.remove(material);
                if (balances.getOrDefault(material, 0L) >= 0L) {
                    continue;
                }

                Set<K> component = components.of(material);
                if (component == null || !components.isCyclic(component)) {
                    if (!expandAcyclic(material, balances, executions, graph, queue, queued)) {
                        logFailure(problem, "no_acyclic_producer material=" + material
                            + " producerCount=" + graph.producersOf(material).size());
                        return Optional.empty();
                    }
                    continue;
                }

                List<ECOPlanningOperation<K, R>> localOperations = graph.operations().stream()
                    .filter(operation -> operation.selectableOutputs().stream().anyMatch(component::contains))
                    .toList();
                if (component.size() > MAX_COMPONENT_MATERIALS
                    || localOperations.isEmpty()
                    || localOperations.size() > MAX_COMPONENT_OPERATIONS) {
                    logFailure(problem, "cycle_component_limit materials=" + component.size()
                        + " operations=" + localOperations.size());
                    return Optional.empty();
                }

                LocalSolution<K, R> local = solveProductiveSelfCycle(
                    component, localOperations, material, balances, problem.inventory());
                if (local == null) {
                    local = solveComponent(
                        component, localOperations, material, balances, problem.inventory(), deadlineNanos);
                }
                if (local == null) {
                    logFailure(problem, "local_milp_no_result materials=" + component.size()
                        + " operations=" + localOperations.size()
                        + " deficitMaterial=" + material
                        + " deficit=" + Math.negateExact(balances.get(material)));
                    return Optional.empty();
                }
                local.executions().forEach((reference, count) ->
                    executions.merge(reference, count, Math::addExact));
                cycleOperations.addAll(local.operationReferences());
                if (local.missingSeedStarter() != null) {
                    missingSeedStarters.add(local.missingSeedStarter().reference());
                }
                local.missingSeedAmounts().forEach((key, amount) ->
                    missingSeedAmounts.merge(key, amount, Math::max));
                apply(localOperations, local.executions(), balances);
                terminalBoundaryDeficits.addAll(local.boundaryDeficits().keySet());
                local.boundaryDeficits().forEach((key, amount) ->
                    boundaryDeficitAmounts.merge(key, amount, Math::addExact));
                for (var operation : localOperations) {
                    operation.inputs().keySet().stream()
                        .filter(key -> !terminalBoundaryDeficits.contains(key))
                        .forEach(key -> enqueue(key, balances, graph, queue, queued));
                }
            }
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }

        Set<K> expandable = new LinkedHashSet<>();
        graph.operations().forEach(operation -> expandable.addAll(operation.selectableOutputs()));
        expandable.removeAll(terminalBoundaryDeficits);
        ECOHyperflowResult<R> built = ECOPlannerMath.buildResult(
            balances, executions, problem.requested(), expandable, graph.materials(), expansions);
        ECOPlanCandidate<R> candidate = built.candidate();
        var originalSchedule = ECOInventoryScheduler.schedule(problem, candidate);
        Set<R> effectiveMissingSeedStarters = originalSchedule.executable()
            ? Set.of()
            : Set.copyOf(missingSeedStarters);
        ECOHyperflowResult.Status status = built.status();
        if (!effectiveMissingSeedStarters.isEmpty() && status == ECOHyperflowResult.Status.COMPLETE) {
            status = ECOHyperflowResult.Status.MISSING_SOURCES;
        }
        if (status != ECOHyperflowResult.Status.COMPLETE
            && status != ECOHyperflowResult.Status.MISSING_SOURCES) {
            logFailure(problem, "unresolved_balances status=" + status
                + " requestedShortfall=" + built.candidate().requestedShortfall()
                + " dependencyShortfall=" + built.candidate().dependencyShortfall()
                + " sourceShortfall=" + built.candidate().sourceShortfall());
            return Optional.empty();
        }

        Map<K, Long> schedulingSources = new LinkedHashMap<>(boundaryDeficitAmounts);
        balances.forEach((key, balance) -> {
            if (balance < 0L && !expandable.contains(key)) {
                schedulingSources.merge(key, ECOPlannerMath.saturatedNegate(balance), Math::max);
            }
        });
        if (!effectiveMissingSeedStarters.isEmpty()) {
            missingSeedAmounts.forEach((key, amount) -> schedulingSources.merge(key, amount, Math::max));
        }
        ECOPlanningProblem<K, R> schedulableProblem = withSyntheticInventory(problem, schedulingSources);
        var schedule = originalSchedule.executable()
            ? originalSchedule
            : ECOInventoryScheduler.schedule(schedulableProblem, candidate);
        if (!schedule.executable()) {
            logFailure(problem, "scheduler_blocked blockedBy=" + schedule.blockedBy()
                + " steps=" + schedule.steps().size());
            return Optional.empty();
        }
        return Optional.of(new ECOHyperflowResult<>(
            status,
            candidate,
            expansions,
            Optional.of(new ECOCycleTrace<>(cycleOperations, effectiveMissingSeedStarters))
        ));
    }

    private static <K, R> boolean expandAcyclic(
        K material,
        Map<K, Long> balances,
        Map<R, Long> executions,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> queue,
        Set<K> queued
    ) {
        List<ECOPlanningOperation<K, R>> producers = graph.producersOf(material).stream()
            .filter(operation -> ECOPlannerMath.positiveNet(operation, material) > 0L)
            .toList();
        if (producers.isEmpty()) {
            return true;
        }
        long missing = Math.negateExact(balances.get(material));
        ECOPlanningOperation<K, R> operation = chooseAcyclicProducer(material, missing, producers, balances);
        if (operation == null) {
            return false;
        }
        long batches = ECOPlannerMath.ceilDiv(missing, ECOPlannerMath.positiveNet(operation, material));
        executions.merge(operation.reference(), batches, Math::addExact);
        apply(operation, batches, balances);
        operation.inputs().keySet().forEach(key -> enqueue(key, balances, graph, queue, queued));
        return true;
    }

    private static <K, R> ECOPlanningOperation<K, R> chooseAcyclicProducer(
        K material,
        long deficit,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances
    ) {
        ECOPlanningOperation<K, R> best = null;
        long bestMissingInputs = Long.MAX_VALUE;
        long bestBatches = Long.MAX_VALUE;
        int bestInputKinds = Integer.MAX_VALUE;
        for (var producer : producers) {
            long net = ECOPlannerMath.positiveNet(producer, material);
            if (net <= 0L) {
                continue;
            }
            long batches = ECOPlannerMath.ceilDiv(deficit, net);
            long missingInputs = 0L;
            for (var input : producer.inputs().entrySet()) {
                long required = ECOPlannerMath.saturatedMultiply(input.getValue(), batches);
                long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
                missingInputs = ECOPlannerMath.saturatedAdd(
                    missingInputs, Math.max(0L, required - Math.min(required, available)));
            }
            if (best == null
                || missingInputs < bestMissingInputs
                || (missingInputs == bestMissingInputs && batches < bestBatches)
                || (missingInputs == bestMissingInputs && batches == bestBatches
                    && producer.inputs().size() < bestInputKinds)) {
                best = producer;
                bestMissingInputs = missingInputs;
                bestBatches = batches;
                bestInputKinds = producer.inputs().size();
            }
        }
        return best;
    }

    private static <K, R> LocalSolution<K, R> solveComponent(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory,
        long deadlineNanos
    ) {
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return null;
        }
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        Map<ECOPlanningOperation<K, R>, Variable> variables = new LinkedHashMap<>();
        Map<K, Variable> boundaryDeficits = new LinkedHashMap<>();
        for (int i = 0; i < operations.size(); i++) {
            long externalInput = operations.get(i).inputs().entrySet().stream()
                .filter(entry -> !component.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(0L, ECOPlannerMath::saturatedAdd);
            BigDecimal weight = BigDecimal.ONE.add(
                EXTERNAL_INPUT_WEIGHT.multiply(BigDecimal.valueOf(externalInput)));
            variables.put(operations.get(i), model.addVariable("operation_" + i)
                .integer(true).lower(BigDecimal.ZERO).weight(weight));
        }
        int materialIndex = 0;
        for (K material : component) {
            BigDecimal minimumNetChange = BigDecimal.valueOf(balances.getOrDefault(material, 0L)).negate();
            Expression expression = model.addExpression("material_" + materialIndex++).lower(minimumNetChange);
            for (var entry : variables.entrySet()) {
                long coefficient = Math.subtractExact(
                    entry.getKey().outputAmount(material), entry.getKey().inputAmount(material));
                if (coefficient != 0L) {
                    expression.set(entry.getValue(), coefficient);
                }
            }
            if (!material.equals(deficientMaterial)) {
                Variable deficit = model.addVariable("boundary_deficit_" + materialIndex)
                    .integer(true).lower(BigDecimal.ZERO).weight(BOUNDARY_DEFICIT_WEIGHT);
                boundaryDeficits.put(material, deficit);
                expression.set(deficit, BigDecimal.ONE);
            }
        }
        for (K material : component) {
            long available = Math.max(0L, initialInventory.getOrDefault(material, 0L));
            boolean needsSeed = operations.stream().anyMatch(operation ->
                operation.inputAmount(material) > 0L
                    && operation.outputAmount(material) > operation.inputAmount(material)
                    && available < operation.inputAmount(material));
            if (!needsSeed) {
                continue;
            }
            List<ECOPlanningOperation<K, R>> inboundStarters = operations.stream()
                .filter(operation -> operation.inputAmount(material) == 0L)
                .filter(operation -> ECOPlannerMath.positiveNet(operation, material) > 0L)
                .toList();
            if (!inboundStarters.isEmpty()) {
                Expression bootstrap = model.addExpression("external_bootstrap_" + material).lower(BigDecimal.ONE);
                inboundStarters.forEach(operation -> bootstrap.set(variables.get(operation), BigDecimal.ONE));
            }
        }
        model.options.time_abort = remainingMillis(deadlineNanos);
        Optimisation.Result solved = model.minimise();
        if (!solved.getState().isFeasible() || ECOSolveBudget.shouldStop(deadlineNanos)) {
            return null;
        }
        Map<R, Long> executions = new LinkedHashMap<>();
        for (int i = 0; i < operations.size(); i++) {
            long count;
            try {
                count = solved.get(i).setScale(0, RoundingMode.HALF_UP).longValueExact();
            } catch (ArithmeticException invalid) {
                return null;
            }
            if (count > 0L) {
                executions.put(operations.get(i).reference(), count);
            }
        }
        ECOPlanningOperation<K, R> starter = operations.stream()
            .filter(operation -> executions.containsKey(operation.reference()))
            .anyMatch(operation -> canStart(operation, initialInventory))
                ? null
                : preferredStarter(component, operations, executions, deficientMaterial, initialInventory);
        Map<K, Long> missingSeedAmounts = new LinkedHashMap<>();
        if (starter != null) {
            starter.inputs().forEach((key, amount) -> {
                if (!component.contains(key)) return;
                long missing = Math.max(0L, amount - initialInventory.getOrDefault(key, 0L));
                if (missing > 0L) missingSeedAmounts.put(key, missing);
            });
        }
        Set<R> references = new LinkedHashSet<>();
        operations.forEach(operation -> references.add(operation.reference()));
        Map<K, Long> usedBoundaryDeficits = new LinkedHashMap<>();
        for (var entry : boundaryDeficits.entrySet()) {
            int index = model.indexOf(entry.getValue());
            if (index >= 0 && solved.get(index).signum() > 0) {
                try {
                    usedBoundaryDeficits.put(
                        entry.getKey(), solved.get(index).setScale(0, RoundingMode.CEILING).longValueExact());
                } catch (ArithmeticException invalid) {
                    return null;
                }
            }
        }
        return new LocalSolution<>(executions, Set.copyOf(references), starter,
            Map.copyOf(missingSeedAmounts), Map.copyOf(usedBoundaryDeficits));
    }

    /** Handles A + external inputs -> nA without paying the MILP setup cost. */
    private static <K, R> LocalSolution<K, R> solveProductiveSelfCycle(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory
    ) {
        if (component.size() != 1) {
            return null;
        }
        // If another operation can create the seed without consuming it, the
        // self-cycle shortcut must leave that upstream route to the SCC model.
        // Otherwise the shortcut reports a source deficit before the seed
        // producer has a chance to be scheduled.
        if (operations.stream().anyMatch(operation ->
            operation.inputAmount(deficientMaterial) == 0L
                && ECOPlannerMath.positiveNet(operation, deficientMaterial) > 0L)) {
            return null;
        }
        List<ECOPlanningOperation<K, R>> producers = operations.stream()
            .filter(operation -> operation.inputAmount(deficientMaterial) > 0L)
            .filter(operation -> ECOPlannerMath.positiveNet(operation, deficientMaterial) > 0L)
            .toList();
        if (producers.size() != 1) {
            return null;
        }

        ECOPlanningOperation<K, R> operation = producers.getFirst();
        long deficit = ECOPlannerMath.saturatedNegate(balances.getOrDefault(deficientMaterial, 0L));
        long net = ECOPlannerMath.positiveNet(operation, deficientMaterial);
        if (deficit <= 0L || net <= 0L) {
            return null;
        }
        long batches = ECOPlannerMath.ceilDiv(deficit, net);
        Map<K, Long> missingSeedAmounts = new LinkedHashMap<>();
        long seedMissing = Math.max(0L,
            operation.inputAmount(deficientMaterial) - initialInventory.getOrDefault(deficientMaterial, 0L));
        if (seedMissing > 0L) {
            missingSeedAmounts.put(deficientMaterial, seedMissing);
        }
        return new LocalSolution<>(
            Map.of(operation.reference(), batches),
            Set.of(operation.reference()),
            seedMissing > 0L ? operation : null,
            Map.copyOf(missingSeedAmounts),
            Map.of()
        );
    }

    private static <K, R> ECOPlanningOperation<K, R> preferredStarter(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        Map<R, Long> executions,
        K deficientMaterial,
        Map<K, Long> inventory
    ) {
        return operations.stream()
            .filter(operation -> executions.containsKey(operation.reference()))
            .filter(operation -> operation.inputs().keySet().stream().anyMatch(component::contains))
            .filter(operation -> component.stream().anyMatch(material ->
                ECOPlannerMath.positiveNet(operation, material) > 0L))
            .min((left, right) -> {
                boolean leftConsumesDeficit = left.inputs().containsKey(deficientMaterial);
                boolean rightConsumesDeficit = right.inputs().containsKey(deficientMaterial);
                if (leftConsumesDeficit != rightConsumesDeficit) {
                    return leftConsumesDeficit ? -1 : 1;
                }
                return Long.compare(
                    missingInputTotal(left, inventory), missingInputTotal(right, inventory));
            })
            .orElse(null);
    }

    private static <K, R> long missingInputTotal(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory
    ) {
        long total = 0L;
        for (var input : operation.inputs().entrySet()) {
            long available = inventory.getOrDefault(input.getKey(), 0L);
            total = ECOPlannerMath.saturatedAdd(total, Math.max(0L, input.getValue() - available));
        }
        return total;
    }

    private static <K, R> boolean canStart(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory
    ) {
        return operation.inputs().entrySet().stream().allMatch(input ->
            inventory.getOrDefault(input.getKey(), 0L) >= input.getValue());
    }

    private static <K, R> ECOPlanningProblem<K, R> withSyntheticInventory(
        ECOPlanningProblem<K, R> problem,
        Map<K, Long> deficits
    ) {
        if (deficits.isEmpty()) {
            return problem;
        }
        Map<K, Long> inventory = new LinkedHashMap<>(problem.inventory());
        deficits.forEach((key, amount) -> inventory.merge(key, amount, Math::addExact));
        return new ECOPlanningProblem<>(problem.operations(), inventory, problem.requested());
    }

    private static <K, R> void enqueue(
        K material,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> queue,
        Set<K> queued
    ) {
        if (balances.getOrDefault(material, 0L) < 0L
            && !graph.producersOf(material).isEmpty()
            && queued.add(material)) {
            queue.addLast(material);
        }
    }

    private static <K, R> void apply(
        List<ECOPlanningOperation<K, R>> operations,
        Map<R, Long> executions,
        Map<K, Long> balances
    ) {
        operations.forEach(operation -> apply(
            operation, executions.getOrDefault(operation.reference(), 0L), balances));
    }

    private static <K, R> void apply(
        ECOPlanningOperation<K, R> operation,
        long count,
        Map<K, Long> balances
    ) {
        if (count <= 0L) return;
        operation.inputs().forEach((key, amount) ->
            balances.merge(key, Math.multiplyExact(amount, -count), Math::addExact));
        operation.outputs().forEach((key, amount) ->
            balances.merge(key, Math.multiplyExact(amount, count), Math::addExact));
    }

    private static long remainingMillis(long deadlineNanos) {
        if (deadlineNanos == Long.MAX_VALUE) return MAX_COMPONENT_SOLVE_MILLIS;
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) return 1L;
        return Math.min(MAX_COMPONENT_SOLVE_MILLIS, Math.max(1L, remaining / 1_000_000L));
    }

    private static <K, R> void logFailure(ECOPlanningProblem<K, R> problem, String context) {
        ECOPlanningFailureDiagnostics.logFailure(
            ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
            ECOPlannerFallbackReason.SOLVER_NO_ROUTE,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "scc_ojalgo",
            context
        );
    }

    private record LocalSolution<K, R>(
        Map<R, Long> executions,
        Set<R> operationReferences,
        ECOPlanningOperation<K, R> missingSeedStarter,
        Map<K, Long> missingSeedAmounts,
        Map<K, Long> boundaryDeficits
    ) {
    }

    private record Components<K>(Map<K, Set<K>> byMaterial, Set<Set<K>> cyclic) {
        private static <K, R> Components<K> build(ECOPlanningGraph<K, R> graph) {
            Map<K, Set<K>> byMaterial = new LinkedHashMap<>();
            Set<Set<K>> cyclic = new HashSet<>();
            for (Set<K> component : ECOStrongComponents.find(graph)) {
                component.forEach(material -> byMaterial.put(material, component));
                if (component.size() > 1 || graph.operations().stream().anyMatch(operation ->
                    component.stream().anyMatch(material -> operation.inputs().containsKey(material)
                        && operation.outputs().containsKey(material)))) {
                    cyclic.add(component);
                }
            }
            return new Components<>(Map.copyOf(byMaterial), Set.copyOf(cyclic));
        }

        private Set<K> of(K material) {
            return byMaterial.get(material);
        }

        private boolean isCyclic(Set<K> component) {
            return cyclic.contains(component);
        }

        private boolean hasCycle() {
            return !cyclic.isEmpty();
        }
    }
}
