package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private static final BigDecimal INTEGER_TOLERANCE = new BigDecimal("0.000001");
    // Avoid an effectively infinite ojAlgo variable domain. Its integer presolver becomes
    // numerically unstable when a small recipe constraint is paired with Long.MAX_VALUE.
    private static final long MAX_MILP_VARIABLE_BOUND = 1_000_000_000L;
    private static final long MAX_COMPONENT_SOLVE_MILLIS_HARD = 10_000L;
    private static final Object CYCLE_ANALYSIS_LOCK = new Object();
    private static final LinkedHashMap<CycleAnalysisKey,
        Optional<DeterministicCycleAnalysis<?, ?>>> DETERMINISTIC_ANALYSIS_CACHE =
        new LinkedHashMap<>(16, 0.75F, true);

    private ECOCondensedCycleSolver() {
    }

    static void clearCache() {
        synchronized (CYCLE_ANALYSIS_LOCK) {
            DETERMINISTIC_ANALYSIS_CACHE.clear();
        }
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        var topology = graph.topology(problem.unlimitedInventory());
        if (!topology.hasCycle()) {
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

                var component = topology.componentOf(material);
                if (component == null || !topology.isCyclic(component)) {
                    if (!expandAcyclic(material, balances, executions, graph, queue, queued)) {
                        logFailure(problem, "no_acyclic_producer material=" + material
                            + " producerCount=" + graph.producersOf(material).size());
                        return Optional.empty();
                    }
                    continue;
                }

                Set<K> componentMaterials = component.materials();
                List<ECOPlanningOperation<K, R>> localOperations = topology.localOperationsOf(component);
                int maxComponentMaterials = Math.clamp(
                    NEConfig.ecoPlannerMaxComponentMaterials,
                    1,
                    NEConfig.ECO_PLANNER_COMPONENT_MATERIALS_HARD_MAX
                );
                int maxComponentOperations = Math.clamp(
                    NEConfig.ecoPlannerMaxComponentOperations,
                    1,
                    NEConfig.ECO_PLANNER_COMPONENT_OPERATIONS_HARD_MAX
                );
                if (componentMaterials.size() > maxComponentMaterials
                    || localOperations.isEmpty()
                    || localOperations.size() > maxComponentOperations) {
                    if (componentMaterials.size() > maxComponentMaterials
                        || localOperations.size() > maxComponentOperations) {
                        ECOPlanningFailureDiagnostics.addDiagnostic(ECOPlannerDiagnostic.CYCLE_SIZE_LIMIT);
                        ECOPlanningFailureDiagnostics.logDetail(
                            ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                            "cycle_component_limit materials=" + componentMaterials.size()
                                + " operations=" + localOperations.size()
                        );
                    }
                    logFailure(problem, "cycle_component_limit materials=" + componentMaterials.size()
                        + " operations=" + localOperations.size());
                    return Optional.empty();
                }

                LocalSolution<K, R> local = solveSimpleUnitGrowthRing(
                    componentMaterials, localOperations, material, balances, problem.inventory());
                if (local == null) {
                    local = solveProductiveSelfCycle(
                        componentMaterials, localOperations, material, balances, problem.inventory());
                }
                if (local == null) {
                    local = solveDeterministicCycle(
                        componentMaterials,
                        localOperations,
                        material,
                        balances,
                        problem.unlimitedInventory(),
                        graph.recipeBindingVersion()
                    );
                }
                if (local == null) {
                    local = solveComponent(
                        componentMaterials,
                        localOperations,
                        material,
                        balances,
                        problem.inventory(),
                        problem.unlimitedInventory(),
                        deadlineNanos
                    );
                }
                if (local == null) {
                    logFailure(problem, "local_milp_no_result materials=" + componentMaterials.size()
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
                // The SCC was solved as one coupled system. Remove any other queued members
                // before propagating only its external inputs, so the same ring cannot be
                // solved again and have its execution counts added a second time.
                queue.removeIf(componentMaterials::contains);
                queued.removeAll(componentMaterials);
                for (var operation : localOperations) {
                    operation.inputs().keySet().stream()
                        .filter(key -> !componentMaterials.contains(key))
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
            balances,
            executions,
            problem.requested(),
            ECOPlannerMath.findStartableMaterials(graph, expandable, balances, problem.requested()),
            graph.materials(),
            expansions
        );
        ECOPlanCandidate<R> candidate = built.candidate();
        var originalSchedule = ECOInventoryScheduler.schedule(problem, candidate, graph);
        Set<R> effectiveMissingSeedStarters = originalSchedule.executable()
            ? Set.of()
            : Set.copyOf(missingSeedStarters);
        if (!originalSchedule.executable() && effectiveMissingSeedStarters.isEmpty()) {
            inferMissingCycleSeeds(
                problem,
                topology,
                candidate,
                cycleOperations,
                missingSeedStarters,
                missingSeedAmounts
            );
            effectiveMissingSeedStarters = Set.copyOf(missingSeedStarters);
        }
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
            : ECOInventoryScheduler.schedule(schedulableProblem, candidate, graph);
        if (!schedule.executable()) {
            logFailure(problem, "scheduler_blocked blockedBy=" + schedule.blockedBy()
                + " steps=" + schedule.steps().size());
            return Optional.empty();
        }
        return Optional.of(new ECOHyperflowResult<>(
            status,
            candidate,
            expansions,
            Optional.of(new ECOCycleTrace<>(
                cycleOperations,
                effectiveMissingSeedStarters,
                effectiveMissingSeedStarters.isEmpty() ? Map.of() : missingSeedAmounts
            ))
        ));
    }

    /**
     * A coupled cycle can balance every material algebraically while still having no first
     * executable operation. Convert that zero-seed case into an explicit missing-source result.
     */
    private static <K, R> void inferMissingCycleSeeds(
        ECOPlanningProblem<K, R> problem,
        ECOStrongComponents.Topology<K, R> topology,
        ECOPlanCandidate<R> candidate,
        Set<R> cycleOperations,
        Set<R> missingSeedStarters,
        Map<K, Long> missingSeedAmounts
    ) {
        for (var component : topology.cyclicComponents()) {
            if (component.materials().size() < 2) {
                continue;
            }
            List<ECOPlanningOperation<K, R>> active = topology.localOperationsOf(component).stream()
                .filter(operation -> cycleOperations.contains(operation.reference()))
                .filter(operation -> candidate.executions().getOrDefault(operation.reference(), 0L) > 0L)
                .toList();
            if (active.isEmpty() || active.stream().anyMatch(operation -> canStart(operation, problem.inventory()))) {
                continue;
            }
            ECOPlanningOperation<K, R> starter = active.stream()
                .min(java.util.Comparator.comparingLong(operation ->
                    missingInputTotal(operation, problem.inventory())))
                .orElse(null);
            if (starter == null) {
                continue;
            }
            missingSeedStarters.add(starter.reference());
            starter.inputs().forEach((key, amount) -> {
                if (problem.isUnlimited(key)) {
                    return;
                }
                long missing = Math.max(0L, amount - problem.inventory().getOrDefault(key, 0L));
                if (missing > 0L) {
                    missingSeedAmounts.merge(key, missing, Math::max);
                }
            });
            ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                "cycle_seed_inferred componentMaterials=" + component.materials().size()
                    + " starter=" + starter.reference()
                    + " missing=" + missingSeedAmounts
            );
        }
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
        Set<K> unlimited,
        long deadlineNanos
    ) {
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return null;
        }
        ComponentLowerBounds lowerBounds = componentLowerBounds(
            component, operations, deficientMaterial, balances, unlimited);
        if (lowerBounds == null) {
            return null;
        }
        LocalSolution finiteIncumbent = tryFiniteCandidate(
            component, operations, deficientMaterial, balances, initialInventory, unlimited);
        if (finiteIncumbent != null && hasUniqueProducerPath(component, operations)) {
            return finiteIncumbent;
        }

        BigInteger incumbentExternalUpper = finiteIncumbent == null
            ? null
            : externalObjectiveCostByReference(
                operations, finiteIncumbent.executions(), component, unlimited
            );
        BigInteger incumbentBoundaryUpper = finiteIncumbent == null
            ? null
            : boundaryObjectiveCost(finiteIncumbent.boundaryDeficits());
        ComponentModel<K, R> model = buildComponentModel(
            component,
            operations,
            deficientMaterial,
            balances,
            initialInventory,
            unlimited,
            lowerBounds,
            false,
            true,
            null,
            incumbentBoundaryUpper
        );
        Optimisation.Result solved = solveModel(model, deadlineNanos);
        if (!solved.getState().isFeasible() || ECOSolveBudget.shouldStop(deadlineNanos)) {
            return finiteIncumbent;
        }
        Map<ECOPlanningOperation<K, R>, BigInteger> exactCounts = exactCounts(model, solved);
        if (exactCounts == null) {
            return finiteIncumbent;
        }

        Map<K, BigInteger> exactBoundaryDeficits = exactBoundaryDeficits(model, solved);
        if (exactBoundaryDeficits == null) {
            return finiteIncumbent;
        }
        BigInteger optimalBoundary = boundaryObjectiveCostBigInteger(exactBoundaryDeficits);
        // The first pass makes the plan schedulable with the smallest boundary deficit. The
        // second pass then minimizes external input and operation count at that fixed boundary
        // level, without a Big-M coefficient coupling the two priorities.
        BigInteger externalUpper = incumbentBoundaryUpper != null
            && incumbentBoundaryUpper.equals(optimalBoundary)
            ? incumbentExternalUpper
            : null;
        if (!ECOSolveBudget.shouldStop(deadlineNanos)) {
            ComponentModel<K, R> boundaryModel = buildComponentModel(
                component,
                operations,
                deficientMaterial,
                balances,
                initialInventory,
                unlimited,
                lowerBounds,
                true,
                false,
                externalUpper,
                optimalBoundary
            );
            Optimisation.Result boundarySolved = solveModel(boundaryModel, deadlineNanos);
            if (boundarySolved.getState().isFeasible()
                && !ECOSolveBudget.shouldStop(deadlineNanos)) {
                Map<ECOPlanningOperation<K, R>, BigInteger> boundaryCounts = exactCounts(
                    boundaryModel, boundarySolved
                );
                if (boundaryCounts != null) {
                    model = boundaryModel;
                    solved = boundarySolved;
                    exactCounts = boundaryCounts;
                    exactBoundaryDeficits = exactBoundaryDeficits(boundaryModel, boundarySolved);
                }
            }
        }

        if (exactBoundaryDeficits == null) {
            return finiteIncumbent;
        }
        if (!satisfiesExactComponentConstraints(
            component, operations, exactCounts, exactBoundaryDeficits,
            deficientMaterial, balances, initialInventory
        )) {
            return finiteIncumbent;
        }
        Map<R, Long> executions = new LinkedHashMap<>();
        for (var operation : operations) {
            long count;
            try {
                count = exactCounts.get(operation).longValueExact();
            } catch (ArithmeticException invalid) {
                return null;
            }
            if (count > 0L) {
                executions.put(operation.reference(), count);
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
        for (var entry : exactBoundaryDeficits.entrySet()) {
            if (entry.getValue().signum() > 0) {
                try {
                    usedBoundaryDeficits.put(entry.getKey(), entry.getValue().longValueExact());
                } catch (ArithmeticException invalid) {
                    return null;
                }
            }
        }
        return new LocalSolution<>(executions, Set.copyOf(references), starter,
            Map.copyOf(missingSeedAmounts), Map.copyOf(usedBoundaryDeficits));
    }

    private static <K, R> ComponentModel<K, R> buildComponentModel(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory,
        Set<K> unlimited,
        ComponentLowerBounds lowerBounds,
        boolean optimizeExternal,
        boolean optimizeBoundary,
        BigInteger externalUpper,
        BigInteger boundaryUpper
    ) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        Map<ECOPlanningOperation<K, R>, Variable> variables = new LinkedHashMap<>();
        Map<K, Variable> boundaryDeficits = new LinkedHashMap<>();
        Expression externalObjective = model.addExpression("objective_external");
        if (optimizeExternal) {
            externalObjective.weight(BigDecimal.ONE);
        }
        Expression externalLower = lowerBounds.externalInputLowerBound() > 0L
            ? model.addExpression("external_lower").lower(
                BigDecimal.valueOf(lowerBounds.externalInputLowerBound()))
            : null;
        Expression externalLimit = externalUpper == null
            ? null
            : model.addExpression("external_limit").upper(new BigDecimal(externalUpper));
        Expression boundaryObjective = model.addExpression("objective_boundary");
        if (optimizeBoundary) {
            boundaryObjective.weight(BigDecimal.ONE);
        }
        Expression boundaryLimit = boundaryUpper == null
            ? null
            : model.addExpression("boundary_limit").upper(new BigDecimal(boundaryUpper));
        Map<ECOPlanningOperation<K, R>, Long> operationUpperBounds = new LinkedHashMap<>();
        for (int i = 0; i < operations.size(); i++) {
            ECOPlanningOperation<K, R> operation = operations.get(i);
            long externalInput = operation.inputs().entrySet().stream()
                .filter(entry -> !component.contains(entry.getKey()))
                .filter(entry -> !unlimited.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(0L, ECOPlannerMath::saturatedAdd);
            long operationUpper = modelOperationUpper(externalUpper, externalInput);
            Variable variable = model.addVariable("operation_" + i)
                .integer(true)
                .lower(BigDecimal.ZERO)
                .upper(BigDecimal.valueOf(operationUpper));
            variables.put(operation, variable);
            operationUpperBounds.put(operation, operationUpper);
            BigDecimal externalCoefficient = BigDecimal.valueOf(
                ECOPlannerMath.saturatedAdd(externalInput, 1L));
            externalObjective.set(variable, externalCoefficient);
            if (externalLimit != null) {
                externalLimit.set(variable, externalCoefficient);
            }
            if (externalLower != null && externalInput > 0L) {
                externalLower.set(variable, BigDecimal.valueOf(externalInput));
            }
        }
        Expression operationCount = model.addExpression("operation_count_lower");
        for (Variable variable : variables.values()) {
            operationCount.set(variable, BigDecimal.ONE);
        }
        operationCount.lower(BigDecimal.valueOf(lowerBounds.minimumOperationBatchesLowerBound()));
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
                    .integer(true)
                    .lower(BigDecimal.ZERO)
                    .upper(BigDecimal.valueOf(modelBoundaryUpper(
                        material, balances, operations, operationUpperBounds)));
                boundaryDeficits.put(material, deficit);
                boundaryObjective.set(deficit, BigDecimal.ONE);
                if (boundaryLimit != null) {
                    boundaryLimit.set(deficit, BigDecimal.ONE);
                }
                expression.set(deficit, BigDecimal.ONE);
            }
        }
        Expression boundaryLower = model.addExpression("boundary_lower");
        for (Variable deficit : boundaryDeficits.values()) {
            boundaryLower.set(deficit, BigDecimal.ONE);
        }
        boundaryLower.lower(BigDecimal.valueOf(lowerBounds.internalMaterialDeficitLowerBound()));
        for (K material : component) {
            long available = Math.max(0L, initialInventory.getOrDefault(material, 0L));
            boolean needsSeed = operations.stream().anyMatch(operation ->
                operation.inputAmount(material) > 0L
                    && operation.outputAmount(material) >= operation.inputAmount(material)
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
        return new ComponentModel<>(model, variables, boundaryDeficits);
    }

    private static <K> BigInteger boundaryObjectiveCost(Map<K, Long> deficits) {
        BigInteger total = BigInteger.ZERO;
        for (long deficit : deficits.values()) {
            if (deficit > 0L) {
                total = total.add(BigInteger.valueOf(deficit));
            }
        }
        return total;
    }

    private static <K> BigInteger boundaryObjectiveCostBigInteger(
        Map<K, BigInteger> deficits
    ) {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger deficit : deficits.values()) {
            if (deficit.signum() > 0) {
                total = total.add(deficit);
            }
        }
        return total;
    }

    private static <K, R> Map<K, BigInteger> exactBoundaryDeficits(
        ComponentModel<K, R> model,
        Optimisation.Result solved
    ) {
        Map<K, BigInteger> exact = new LinkedHashMap<>();
        for (var entry : model.boundaryDeficits().entrySet()) {
            BigInteger deficit = exactInteger(solved.get(model.model().indexOf(entry.getValue())));
            if (deficit == null || deficit.signum() < 0) {
                return null;
            }
            exact.put(entry.getKey(), deficit);
        }
        return exact;
    }

    private static <K, R> long modelOperationUpper(
        BigInteger externalUpper,
        long externalInput
    ) {
        BigInteger upper = externalUpper == null
            ? BigInteger.valueOf(MAX_MILP_VARIABLE_BOUND)
            : externalUpper.divide(BigInteger.valueOf(
                ECOPlannerMath.saturatedAdd(externalInput, 1L)));
        return upper.max(BigInteger.ZERO)
            .min(BigInteger.valueOf(MAX_MILP_VARIABLE_BOUND))
            .longValue();
    }

    private static <K, R> long modelBoundaryUpper(
        K material,
        Map<K, Long> balances,
        List<ECOPlanningOperation<K, R>> operations,
        Map<ECOPlanningOperation<K, R>, Long> operationUpperBounds
    ) {
        BigInteger upper = BigInteger.valueOf(Math.max(
            0L, ECOPlannerMath.saturatedNegate(balances.getOrDefault(material, 0L))));
        for (var operation : operations) {
            long batches = operationUpperBounds.getOrDefault(operation, 0L);
            if (batches == 0L || operation.inputAmount(material) == 0L) {
                continue;
            }
            upper = upper.add(BigInteger.valueOf(operation.inputAmount(material))
                .multiply(BigInteger.valueOf(batches)));
        }
        return upper.min(BigInteger.valueOf(MAX_MILP_VARIABLE_BOUND)).longValue();
    }

    private static <K, R> Optimisation.Result solveModel(
        ComponentModel<K, R> model,
        long deadlineNanos
    ) {
        model.model().options.time_abort = remainingMillis(deadlineNanos);
        return model.model().minimise();
    }

    private static <K, R> Map<ECOPlanningOperation<K, R>, BigInteger> exactCounts(
        ComponentModel<K, R> model,
        Optimisation.Result solved
    ) {
        Map<ECOPlanningOperation<K, R>, BigInteger> exactCounts = new LinkedHashMap<>();
        for (var entry : model.variables().entrySet()) {
            BigInteger count = exactInteger(solved.get(model.model().indexOf(entry.getValue())));
            if (count == null || count.signum() < 0) {
                return null;
            }
            exactCounts.put(entry.getKey(), count);
        }
        return Map.copyOf(exactCounts);
    }

    private static <K, R> BigInteger externalObjectiveCostByReference(
        List<ECOPlanningOperation<K, R>> operations,
        Map<R, Long> executions,
        Set<K> component,
        Set<K> unlimited
    ) {
        BigInteger total = BigInteger.ZERO;
        for (var operation : operations) {
            long batches = executions.getOrDefault(operation.reference(), 0L);
            if (batches <= 0L) {
                continue;
            }
            long externalInput = operation.inputs().entrySet().stream()
                .filter(entry -> !component.contains(entry.getKey()))
                .filter(entry -> !unlimited.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(0L, ECOPlannerMath::saturatedAdd);
            BigInteger coefficient = BigInteger.valueOf(externalInput).add(BigInteger.ONE);
            total = total.add(coefficient.multiply(BigInteger.valueOf(batches)));
        }
        return total;
    }

    /** Computes sound, one-sided bounds before allocating an ojAlgo model. */
    private static <K, R> ComponentLowerBounds componentLowerBounds(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Set<K> unlimited
    ) {
        long targetDeficit = Math.max(0L,
            ECOPlannerMath.saturatedNegate(balances.getOrDefault(deficientMaterial, 0L)));
        long minimumBatches = Long.MAX_VALUE;
        long externalInputLowerBound = Long.MAX_VALUE;
        for (var operation : operations) {
            long net = ECOPlannerMath.positiveNet(operation, deficientMaterial);
            if (net <= 0L) {
                continue;
            }
            long batches = ECOPlannerMath.ceilDiv(Math.max(1L, targetDeficit), net);
            minimumBatches = Math.min(minimumBatches, batches);
            long external = operation.inputs().entrySet().stream()
                .filter(entry -> !component.contains(entry.getKey()))
                .filter(entry -> !unlimited.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(0L, ECOPlannerMath::saturatedAdd);
            externalInputLowerBound = Math.min(
                externalInputLowerBound,
                ECOPlannerMath.saturatedMultiply(external, batches)
            );
        }
        if (minimumBatches == Long.MAX_VALUE) {
            return null;
        }
        long internalDeficitLowerBound = 0L;
        for (K material : component) {
            if (material.equals(deficientMaterial)) {
                continue;
            }
            long balance = balances.getOrDefault(material, 0L);
            boolean hasPositiveProducer = operations.stream().anyMatch(operation ->
                ECOPlannerMath.positiveNet(operation, material) > 0L);
            if (balance < 0L && !hasPositiveProducer) {
                internalDeficitLowerBound = ECOPlannerMath.saturatedAdd(
                    internalDeficitLowerBound,
                    ECOPlannerMath.saturatedNegate(balance)
                );
            }
        }
        return new ComponentLowerBounds(
            targetDeficit,
            externalInputLowerBound == Long.MAX_VALUE ? 0L : externalInputLowerBound,
            internalDeficitLowerBound,
            minimumBatches
        );
    }

    /**
     * Tries finite producer choices before the general integer model. The unique-producer
     * candidate is exact for that route; the least-external-input candidate is retained as an
     * incumbent when variants remain and is used if the bounded model cannot finish.
     */
    private static <K, R> LocalSolution<K, R> tryFiniteCandidate(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory,
        Set<K> unlimited
    ) {
        LocalSolution unique = greedyCandidate(
            component, operations, deficientMaterial, balances, initialInventory, unlimited, true);
        if (unique != null) {
            return unique;
        }
        return greedyCandidate(
            component, operations, deficientMaterial, balances, initialInventory, unlimited, false);
    }

    private static <K, R> boolean hasUniqueProducerPath(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations
    ) {
        return component.stream().allMatch(material -> operations.stream()
            .filter(operation -> operation.selectableOutputs().contains(material))
            .filter(operation -> ECOPlannerMath.positiveNet(operation, material) > 0L)
            .count() == 1L);
    }

    private static <K, R> LocalSolution<K, R> greedyCandidate(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory,
        Set<K> unlimited,
        boolean requireUniqueProducer
    ) {
        Map<K, Long> working = new LinkedHashMap<>(balances);
        Map<ECOPlanningOperation<K, R>, Long> counts = new LinkedHashMap<>();
        int maximumRounds = Math.max(8, component.size() * 4 + operations.size() * 2);
        for (int round = 0; round < maximumRounds; round++) {
            K deficiency = component.stream()
                .filter(material -> working.getOrDefault(material, 0L) < 0L)
                .max((left, right) -> Long.compare(
                    ECOPlannerMath.saturatedNegate(working.getOrDefault(left, 0L)),
                    ECOPlannerMath.saturatedNegate(working.getOrDefault(right, 0L))))
                .orElse(null);
            if (deficiency == null) {
                break;
            }
            List<ECOPlanningOperation<K, R>> producers = operations.stream()
                .filter(operation -> operation.selectableOutputs().contains(deficiency))
                .filter(operation -> ECOPlannerMath.positiveNet(operation, deficiency) > 0L)
                .toList();
            if (producers.isEmpty() || requireUniqueProducer && producers.size() != 1) {
                return null;
            }
            ECOPlanningOperation<K, R> selected = producers.stream()
                .min((left, right) -> {
                    int external = Long.compare(externalInput(left, component), externalInput(right, component));
                    if (external != 0) {
                        return external;
                    }
                    return Long.compare(
                        -ECOPlannerMath.positiveNet(right, deficiency),
                        -ECOPlannerMath.positiveNet(left, deficiency)
                    );
                })
                .orElse(null);
            if (selected == null) {
                return null;
            }
            long missing = ECOPlannerMath.saturatedNegate(working.getOrDefault(deficiency, 0L));
            long net = ECOPlannerMath.positiveNet(selected, deficiency);
            long batches = ECOPlannerMath.ceilDiv(missing, net);
            if (batches <= 0L) {
                return null;
            }
            try {
                counts.merge(selected, batches, Math::addExact);
                selected.inputs().forEach((material, amount) -> {
                    if (!unlimited.contains(material)) {
                        working.merge(material, Math.multiplyExact(amount, -batches), Math::addExact);
                    }
                });
                selected.outputs().forEach((material, amount) -> {
                    if (!unlimited.contains(material)) {
                        working.merge(material, Math.multiplyExact(amount, batches), Math::addExact);
                    }
                });
            } catch (ArithmeticException overflow) {
                return null;
            }
        }
        if (component.stream().anyMatch(material ->
            material.equals(deficientMaterial) && working.getOrDefault(material, 0L) < 0L)) {
            return null;
        }

        Map<ECOPlanningOperation<K, R>, BigInteger> exactCounts = new LinkedHashMap<>();
        operations.forEach(operation -> exactCounts.put(
            operation, BigInteger.valueOf(counts.getOrDefault(operation, 0L))));
        Map<K, BigInteger> boundaryDeficits = new LinkedHashMap<>();
        component.forEach(material -> {
            if (!material.equals(deficientMaterial)) {
                long missing = Math.max(0L, -working.getOrDefault(material, 0L));
                if (missing > 0L) {
                    boundaryDeficits.put(material, BigInteger.valueOf(missing));
                }
            }
        });
        if (!satisfiesExactComponentConstraints(
            component,
            operations,
            exactCounts,
            boundaryDeficits,
            deficientMaterial,
            balances,
            initialInventory
        )) {
            return null;
        }

        Map<R, Long> executions = new LinkedHashMap<>();
        counts.forEach((operation, count) -> {
            if (count > 0L) {
                executions.put(operation.reference(), count);
            }
        });
        ECOPlanningOperation<K, R> starter = executions.keySet().stream()
            .map(reference -> operations.stream()
                .filter(operation -> operation.reference().equals(reference))
                .findFirst().orElse(null))
            .filter(operation -> operation != null && !canStart(operation, initialInventory))
            .findFirst()
            .orElse(null);
        Map<K, Long> missingSeeds = new LinkedHashMap<>();
        if (starter != null) {
            starter.inputs().forEach((material, amount) -> {
                long missing = Math.max(0L, amount - initialInventory.getOrDefault(material, 0L));
                if (missing > 0L && component.contains(material)) {
                    missingSeeds.put(material, missing);
                }
            });
        }
        Set<R> references = new LinkedHashSet<>();
        operations.forEach(operation -> references.add(operation.reference()));
        Map<K, Long> usedBoundaryDeficits = new LinkedHashMap<>();
        boundaryDeficits.forEach((material, amount) -> {
            try {
                usedBoundaryDeficits.put(material, amount.longValueExact());
            } catch (ArithmeticException ignored) {
                // The candidate is outside the long planning contract.
            }
        });
        return new LocalSolution<>(
            Map.copyOf(executions),
            Set.copyOf(references),
            starter,
            Map.copyOf(missingSeeds),
            Map.copyOf(usedBoundaryDeficits)
        );
    }

    private static <K, R> long externalInput(
        ECOPlanningOperation<K, R> operation,
        Set<K> component
    ) {
        return operation.inputs().entrySet().stream()
            .filter(entry -> !component.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .reduce(0L, ECOPlannerMath::saturatedAdd);
    }

    private record ComponentLowerBounds(
        long targetOutputLowerBound,
        long externalInputLowerBound,
        long internalMaterialDeficitLowerBound,
        long minimumOperationBatchesLowerBound
    ) {
    }

    private record ComponentModel<K, R>(
        ExpressionsBasedModel model,
        Map<ECOPlanningOperation<K, R>, Variable> variables,
        Map<K, Variable> boundaryDeficits
    ) {
    }

    private static BigInteger exactInteger(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal nearest = value.setScale(0, RoundingMode.HALF_EVEN);
        return value.subtract(nearest).abs().compareTo(INTEGER_TOLERANCE) <= 0
            ? nearest.toBigIntegerExact()
            : null;
    }

    private static <K, R> boolean satisfiesExactComponentConstraints(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        Map<ECOPlanningOperation<K, R>, BigInteger> counts,
        Map<K, BigInteger> boundaryDeficits,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory
    ) {
        for (K material : component) {
            BigInteger net = BigInteger.ZERO;
            for (var operation : operations) {
                long coefficient = Math.subtractExact(
                    operation.outputAmount(material), operation.inputAmount(material));
                if (coefficient != 0L) {
                    net = net.add(counts.get(operation).multiply(BigInteger.valueOf(coefficient)));
                }
            }
            if (!material.equals(deficientMaterial)) {
                net = net.add(boundaryDeficits.getOrDefault(material, BigInteger.ZERO));
            }
            BigInteger minimum = BigInteger.valueOf(balances.getOrDefault(material, 0L)).negate();
            if (net.compareTo(minimum) < 0) {
                return false;
            }
        }

        for (K material : component) {
            long available = Math.max(0L, initialInventory.getOrDefault(material, 0L));
            boolean needsSeed = operations.stream().anyMatch(operation ->
                operation.inputAmount(material) > 0L
                    && operation.outputAmount(material) >= operation.inputAmount(material)
                    && available < operation.inputAmount(material));
            if (!needsSeed) {
                continue;
            }
            BigInteger starters = operations.stream()
                .filter(operation -> operation.inputAmount(material) == 0L)
                .filter(operation -> ECOPlannerMath.positiveNet(operation, material) > 0L)
                .map(counts::get)
                .reduce(BigInteger.ZERO, BigInteger::add);
            boolean hasInboundStarter = operations.stream().anyMatch(operation ->
                operation.inputAmount(material) == 0L
                    && ECOPlannerMath.positiveNet(operation, material) > 0L);
            if (hasInboundStarter && starters.signum() <= 0) {
                return false;
            }
        }
        return true;
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

    /**
     * Solves a deterministic multi-step cycle by closed-form net change and prefix requirements.
     * A cycle is accepted only when every internal material has exactly one producer and one
     * consumer, so there is no recipe variant branch for this shortcut to choose between.
     */
    private static <K, R> LocalSolution<K, R> solveDeterministicCycle(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Set<K> unlimited,
        long recipeBindingVersion
    ) {
        if (component.size() < 2 || operations.size() != component.size()) {
            return null;
        }

        DeterministicCycleAnalysis<K, R> analysis = deterministicCycleAnalysis(
            component, operations, deficientMaterial, recipeBindingVersion
        );
        if (analysis == null) {
            return null;
        }
        List<ECOPlanningOperation<K, R>> baseOrder = analysis.order();
        Map<K, BigInteger> cycleNet = analysis.netChange();

        BigInteger repetitions = BigInteger.ONE;
        for (K material : component) {
            long balance = balances.getOrDefault(material, 0L);
            if (balance >= 0L) {
                continue;
            }
            BigInteger deficit = BigInteger.valueOf(balance).negate();
            BigInteger net = cycleNet.getOrDefault(material, BigInteger.ZERO);
            if (net.signum() <= 0) {
                return null;
            }
            repetitions = repetitions.max(ceilDivide(deficit, net));
        }
        if (repetitions.signum() <= 0 || repetitions.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }

        // The cycle is circular. Try each rotation and retain the one with the smallest
        // upfront seed while preserving the same aggregate operation counts.
        Map<K, BigInteger> bestRequired = null;
        List<ECOPlanningOperation<K, R>> bestOrder = null;
        for (int shift = 0; shift < baseOrder.size(); shift++) {
            List<ECOPlanningOperation<K, R>> order = rotate(baseOrder, shift);
            Map<K, BigInteger> required = repeatedPrefixRequirements(order, cycleNet, repetitions);
            if (required.entrySet().stream().anyMatch(entry ->
                !unlimited.contains(entry.getKey())
                    && !component.contains(entry.getKey())
                    && entry.getValue().compareTo(BigInteger.valueOf(
                        Math.max(0L, balances.getOrDefault(entry.getKey(), 0L)))) > 0)) {
                continue;
            }
            if (bestRequired == null || compareSeed(required, bestRequired, balances, component, unlimited) < 0) {
                bestRequired = required;
                bestOrder = order;
            }
        }
        if (bestRequired == null || bestOrder == null) {
            return null;
        }

        Map<K, Long> missingSeeds = new LinkedHashMap<>();
        for (K material : component) {
            if (unlimited.contains(material)) {
                continue;
            }
            BigInteger available = BigInteger.valueOf(
                Math.max(0L, balances.getOrDefault(material, 0L)));
            BigInteger missing = bestRequired.getOrDefault(material, BigInteger.ZERO)
                .subtract(available).max(BigInteger.ZERO);
            if (missing.signum() > 0) {
                try {
                    missingSeeds.put(material, missing.longValueExact());
                } catch (ArithmeticException overflow) {
                    return null;
                }
            }
        }

        long batchCount;
        try {
            batchCount = repetitions.longValueExact();
        } catch (ArithmeticException overflow) {
            return null;
        }
        Map<R, Long> executions = new LinkedHashMap<>();
        bestOrder.forEach(operation -> executions.put(operation.reference(), batchCount));
        ECOPlanningOperation<K, R> starter = missingSeeds.isEmpty()
            ? null
            : bestOrder.stream().filter(operation -> operation.inputs().keySet().stream()
                .anyMatch(missingSeeds::containsKey)).findFirst().orElse(bestOrder.getFirst());
        return new LocalSolution<>(
            Map.copyOf(executions),
            Set.copyOf(executions.keySet()),
            starter,
            Map.copyOf(missingSeeds),
            Map.of()
        );
    }

    /** Builds the balance/order part once; inventory-dependent counts are calculated per request. */
    private static <K, R> DeterministicCycleAnalysis<K, R> deterministicCycleAnalysis(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        long recipeBindingVersion
    ) {
        CycleAnalysisKey key = new CycleAnalysisKey(
            recipeBindingVersion,
            Set.copyOf(component),
            operations.stream().map(ECOPlanningOperation::reference).collect(java.util.stream.Collectors.toUnmodifiableSet()),
            deficientMaterial
        );
        synchronized (CYCLE_ANALYSIS_LOCK) {
            Optional<DeterministicCycleAnalysis<?, ?>> cached = DETERMINISTIC_ANALYSIS_CACHE.get(key);
            if (cached != null) {
                return castAnalysis(cached.orElse(null));
            }
            Map<K, ECOPlanningOperation<K, R>> producers = new LinkedHashMap<>();
            Map<K, ECOPlanningOperation<K, R>> consumers = new LinkedHashMap<>();
            Map<ECOPlanningOperation<K, R>, K> outputByOperation = new LinkedHashMap<>();
            for (var operation : operations) {
                if (!operation.stateTransitionInputs().isEmpty()) {
                    DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                    trimCycleAnalysisCacheLocked();
                    return null;
                }
                List<K> inputs = operation.inputs().keySet().stream()
                    .filter(component::contains)
                    .toList();
                List<K> outputs = operation.outputs().keySet().stream()
                    .filter(component::contains)
                    .toList();
                if (inputs.size() != 1 || outputs.size() != 1) {
                    DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                    trimCycleAnalysisCacheLocked();
                    return null;
                }
                K input = inputs.getFirst();
                K output = outputs.getFirst();
                if (!operation.selectableOutputs().contains(output)) {
                    DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                    trimCycleAnalysisCacheLocked();
                    return null;
                }
                if (consumers.put(input, operation) != null
                    || producers.put(output, operation) != null) {
                    DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                    trimCycleAnalysisCacheLocked();
                    return null;
                }
                outputByOperation.put(operation, output);
            }
            if (producers.size() != component.size() || consumers.size() != component.size()
                || !producers.containsKey(deficientMaterial)
                || !component.stream().allMatch(material ->
                    producers.containsKey(material) && consumers.containsKey(material))) {
                DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                trimCycleAnalysisCacheLocked();
                return null;
            }

            List<ECOPlanningOperation<K, R>> baseOrder = new ArrayList<>(operations.size());
            ECOPlanningOperation<K, R> current = producers.get(deficientMaterial);
            for (int index = 0; index < operations.size(); index++) {
                if (current == null || baseOrder.contains(current)) {
                    DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                    trimCycleAnalysisCacheLocked();
                    return null;
                }
                baseOrder.add(current);
                current = consumers.get(outputByOperation.get(current));
            }
            if (current != producers.get(deficientMaterial) || baseOrder.size() != operations.size()) {
                DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.empty());
                trimCycleAnalysisCacheLocked();
                return null;
            }
            DeterministicCycleAnalysis<K, R> analysis = new DeterministicCycleAnalysis<>(
                List.copyOf(baseOrder), Map.copyOf(cycleNet(baseOrder))
            );
            DETERMINISTIC_ANALYSIS_CACHE.put(key, Optional.of(analysis));
            trimCycleAnalysisCacheLocked();
            return analysis;
        }
    }

    private static void trimCycleAnalysisCacheLocked() {
        int limit = Math.clamp(
            NEConfig.ecoPlannerCycleCacheSize,
            16,
            NEConfig.ECO_PLANNER_CACHE_HARD_MAX
        );
        while (DETERMINISTIC_ANALYSIS_CACHE.size() > limit) {
            DETERMINISTIC_ANALYSIS_CACHE.remove(DETERMINISTIC_ANALYSIS_CACHE.keySet().iterator().next());
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, R> DeterministicCycleAnalysis<K, R> castAnalysis(
        DeterministicCycleAnalysis<?, ?> analysis
    ) {
        return (DeterministicCycleAnalysis<K, R>) analysis;
    }

    private record CycleAnalysisKey(
        long recipeBindingVersion,
        Set<?> componentMaterials,
        Set<?> operationReferences,
        Object deficientMaterial
    ) {
    }

    private record DeterministicCycleAnalysis<K, R>(
        List<ECOPlanningOperation<K, R>> order,
        Map<K, BigInteger> netChange
    ) {
    }


    private static <K, R> Map<K, BigInteger> cycleNet(
        List<ECOPlanningOperation<K, R>> order
    ) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        for (var operation : order) {
            operation.inputs().forEach((material, amount) -> result.merge(
                material, BigInteger.valueOf(amount).negate(), BigInteger::add));
            operation.outputs().forEach((material, amount) -> result.merge(
                material, BigInteger.valueOf(amount), BigInteger::add));
        }
        return result;
    }

    private static <K, R> Map<K, BigInteger> repeatedPrefixRequirements(
        List<ECOPlanningOperation<K, R>> order,
        Map<K, BigInteger> cycleNet,
        BigInteger repetitions
    ) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        Map<K, BigInteger> prefix = new LinkedHashMap<>();
        for (var operation : order) {
            operation.inputs().forEach((material, amount) -> {
                BigInteger cycleChange = cycleNet.getOrDefault(material, BigInteger.ZERO);
                BigInteger cycleOffset = cycleChange.signum() < 0
                    ? cycleChange.multiply(repetitions.subtract(BigInteger.ONE))
                    : BigInteger.ZERO;
                BigInteger availableBefore = cycleOffset.add(prefix.getOrDefault(material, BigInteger.ZERO));
                BigInteger required = BigInteger.valueOf(amount).subtract(availableBefore);
                result.merge(material, required.max(BigInteger.ZERO), BigInteger::max);
            });
            operation.inputs().forEach((material, amount) -> prefix.merge(
                material, BigInteger.valueOf(amount).negate(), BigInteger::add));
            operation.outputs().forEach((material, amount) -> prefix.merge(
                material, BigInteger.valueOf(amount), BigInteger::add));
        }
        return result;
    }

    private static <K, R> List<ECOPlanningOperation<K, R>> rotate(
        List<ECOPlanningOperation<K, R>> source,
        int offset
    ) {
        List<ECOPlanningOperation<K, R>> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            result.add(source.get((index + offset) % source.size()));
        }
        return result;
    }

    private static <K> int compareSeed(
        Map<K, BigInteger> left,
        Map<K, BigInteger> right,
        Map<K, Long> balances,
        Set<K> component,
        Set<K> unlimited
    ) {
        BigInteger leftMissing = seedTotal(left, balances, component, unlimited);
        BigInteger rightMissing = seedTotal(right, balances, component, unlimited);
        return leftMissing.compareTo(rightMissing);
    }

    private static <K> BigInteger seedTotal(
        Map<K, BigInteger> required,
        Map<K, Long> balances,
        Set<K> component,
        Set<K> unlimited
    ) {
        BigInteger total = BigInteger.ZERO;
        for (var entry : required.entrySet()) {
            if (unlimited.contains(entry.getKey())) {
                continue;
            }
            BigInteger available = BigInteger.valueOf(
                Math.max(0L, balances.getOrDefault(entry.getKey(), 0L)));
            if (component.contains(entry.getKey())) {
                total = total.add(entry.getValue().subtract(available).max(BigInteger.ZERO));
            }
        }
        return total;
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    /**
     * Solves the common unit-catalyst ring without invoking ojAlgo. Every operation must
     * consume exactly one ring material, produce exactly one ring material, and the ring must
     * have one positive-growth edge. All operations then run the same number of batches.
     */
    private static <K, R> LocalSolution<K, R> solveSimpleUnitGrowthRing(
        Set<K> component,
        List<ECOPlanningOperation<K, R>> operations,
        K deficientMaterial,
        Map<K, Long> balances,
        Map<K, Long> initialInventory
    ) {
        if (component.size() < 2 || operations.size() != component.size()) {
            return null;
        }
        Map<K, ECOPlanningOperation<K, R>> consumers = new LinkedHashMap<>();
        Map<K, ECOPlanningOperation<K, R>> producers = new LinkedHashMap<>();
        ECOPlanningOperation<K, R> growth = null;
        long growthNet = 0L;
        for (var operation : operations) {
            Set<K> componentInputs = operation.inputs().keySet().stream()
                .filter(component::contains)
                .collect(java.util.stream.Collectors.toSet());
            Set<K> componentOutputs = operation.outputs().keySet().stream()
                .filter(component::contains)
                .collect(java.util.stream.Collectors.toSet());
            if (componentInputs.size() != 1 || componentOutputs.size() != 1
                || operation.inputs().size() != 1 || operation.outputs().size() != 1) {
                return null;
            }
            K input = componentInputs.iterator().next();
            K output = componentOutputs.iterator().next();
            if (operation.inputAmount(input) != 1L || operation.outputAmount(output) < 1L
                || consumers.put(input, operation) != null
                || producers.put(output, operation) != null) {
                return null;
            }
            long net = operation.outputAmount(output) - 1L;
            if (net > 0L) {
                if (growth != null) {
                    return null;
                }
                growth = operation;
                growthNet = net;
            } else if (net < 0L) {
                return null;
            }
        }
        if (growth == null || consumers.size() != component.size() || producers.size() != component.size()
            || !consumers.containsKey(deficientMaterial)) {
            return null;
        }

        java.math.BigInteger batches = java.math.BigInteger.ZERO;
        for (K material : component) {
            long balance = balances.getOrDefault(material, 0L);
            if (balance >= 0L) {
                continue;
            }
            if (!material.equals(growth.outputs().keySet().iterator().next())) {
                return null;
            }
            java.math.BigInteger deficit = java.math.BigInteger.valueOf(balance).negate();
            java.math.BigInteger required = deficit.add(java.math.BigInteger.valueOf(growthNet - 1L))
                .divide(java.math.BigInteger.valueOf(growthNet));
            batches = batches.max(required);
        }
        if (batches.signum() <= 0 || batches.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }
        long batchCount = batches.longValueExact();
        Map<R, Long> executions = new LinkedHashMap<>();
        operations.forEach(operation -> executions.put(operation.reference(), batchCount));
        ECOPlanningOperation<K, R> starter = operations.stream()
            .anyMatch(operation -> canStart(operation, initialInventory))
                ? null
                : operations.getFirst();
        Map<K, Long> missingSeeds = new LinkedHashMap<>();
        if (starter != null) {
            starter.inputs().forEach((key, amount) -> {
                long missing = Math.max(0L, amount - initialInventory.getOrDefault(key, 0L));
                if (missing > 0L) {
                    missingSeeds.put(key, missing);
                }
            });
        }
        Set<R> references = new LinkedHashSet<>(executions.keySet());
        return new LocalSolution<>(
            Map.copyOf(executions), references, starter, Map.copyOf(missingSeeds), Map.of());
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
        deficits.forEach((key, amount) -> {
            if (!problem.isUnlimited(key)) {
                inventory.merge(key, amount, Math::addExact);
            }
        });
        return new ECOPlanningProblem<>(
            problem.operations(), inventory, problem.requested(), problem.unlimitedInventory()
        );
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
        long configured = Math.clamp(
            (long) NEConfig.ecoPlannerComponentSolveMillis,
            1L,
            MAX_COMPONENT_SOLVE_MILLIS_HARD
        );
        if (deadlineNanos == Long.MAX_VALUE) return configured;
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) return 1L;
        return Math.min(configured, Math.max(1L, remaining / 1_000_000L));
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

}
