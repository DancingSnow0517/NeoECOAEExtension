package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts a validated ECO result into the complete plan contract consumed by AE2 CPUs. */
public final class ECOAE2PlanAssembler {
    private ECOAE2PlanAssembler() {}

    public static Optional<CraftingPlan> assemble(
            ECOAE2PlanningSnapshot snapshot, ECOHyperflowResult<ECOAE2PatternVariant> result) {
        if (result.status() != ECOHyperflowResult.Status.COMPLETE
                && result.status() != ECOHyperflowResult.Status.MISSING_SOURCES) {
            ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    result.status() == ECOHyperflowResult.Status.BUDGET_EXHAUSTED
                            ? ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED
                            : ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "unsupported_solver_status=" + result.status() + " expandedStates=" + result.expandedStates());
            return Optional.empty();
        }
        if ((snapshot.truncatedStateExpansion() || snapshot.excludedDynamicPaths())
                && result.status() != ECOHyperflowResult.Status.COMPLETE) {
            ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    (snapshot.truncatedStateExpansion()
                                    ? "truncated_state_inconclusive"
                                    : "excluded_dynamic_path_inconclusive")
                            + " status=" + result.status());
            return Optional.empty();
        }

        var problem = snapshot.problem();
        var candidate = result.candidate();
        Map<AEKey, Long> missing = findMissingSources(problem, result);
        addMissingCycleSeed(problem, result, missing);
        Map<AEKey, Long> negativeBalances = findNegativeBalances(problem, candidate);
        boolean simulation = result.status() == ECOHyperflowResult.Status.MISSING_SOURCES;
        if (simulation || !missing.isEmpty() || !negativeBalances.isEmpty()) {
            ECOPlanningFailureDiagnostics.logDetail(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    "material_shortfall status=" + result.status()
                            + " missing=" + ECOPlanningFailureDiagnostics.describeMap(missing)
                            + " available="
                            + ECOPlanningFailureDiagnostics.describeMap(
                                    availableFor(problem.inventory(), missing.keySet(), negativeBalances.keySet()))
                            + " negativeBalances="
                            + ECOPlanningFailureDiagnostics.describeMap(negativeBalances));
        }
        if (simulation && (missing.isEmpty() || !coversNegativeBalances(missing, negativeBalances))) {
            logInvariantFailure(
                    snapshot,
                    "missing_sources_without_complete_deficit_report missing=" + missing + " negativeBalances="
                            + negativeBalances);
            return Optional.empty();
        }
        if (!simulation && (!missing.isEmpty() || !negativeBalances.isEmpty())) {
            logInvariantFailure(
                    snapshot,
                    "non_simulation_with_negative_balance missing=" + missing + " negativeBalances="
                            + negativeBalances);
            return Optional.empty();
        }
        if (ECOPlanningFailureDiagnostics.canLogDetail(ECOPlanningFailureDiagnostics.Stage.ASSEMBLER)) {
            ECOPlanningFailureDiagnostics.logDetail(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    "assembler_candidate status=" + result.status()
                            + " executions=" + ECOPlanningFailureDiagnostics.describeMap(candidate.executions())
                            + " missing=" + ECOPlanningFailureDiagnostics.describeMap(missing)
                            + " negativeBalances=" + ECOPlanningFailureDiagnostics.describeMap(negativeBalances)
                            + " simulation=" + simulation
                            + " truncatedStateExpansion=" + snapshot.truncatedStateExpansion()
                            + " excludedDynamicPaths=" + snapshot.excludedDynamicPaths());
        }
        if (result.status() == ECOHyperflowResult.Status.MISSING_SOURCES && hasSaturatedCounts(candidate)) {
            CraftingPlan plan = missingOnlyPlan(snapshot, missing);
            ECOPlanningFailureDiagnostics.logTrace(
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "saturated_missing_report missingKeys=" + missing.size());
            return Optional.of(plan);
        }
        Map<AEKey, Long> schedulableInventory = new LinkedHashMap<>(problem.inventory());
        if (simulation && !snapshot.truncatedStateExpansion() && !snapshot.excludedDynamicPaths()) {
            missing.forEach((key, amount) -> schedulableInventory.merge(key, amount, Math::addExact));
        }
        var schedulableProblem =
                new ECOPlanningProblem<>(problem.operations(), schedulableInventory, problem.requested());
        long schedulerStarted = System.nanoTime();
        var schedule = ECOInventoryScheduler.schedule(schedulableProblem, candidate);
        ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.SCHEDULER,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "scheduler",
                simulation ? "synthetic_missing_schedule" : "real_inventory_schedule",
                schedulerStarted,
                "schedulable=" + schedule.executable()
                        + " simulation=" + simulation
                        + " steps=" + schedule.steps().size());
        if (!schedule.executable()) {
            // A missing-source result is a report, not an executable plan. The scheduler is
            // still required as a consistency check, but a large deficient graph may not have a
            // complete topological schedule after synthetic source injection. Do not turn that
            // report into an AE2 fallback or expose a partially executable plan.
            if (simulation) {
                ECOPlanningFailureDiagnostics.logTrace(
                        snapshot.requestedKey(),
                        snapshot.requestedAmount(),
                        "assembler",
                        "simulation_schedule_incomplete_missing_only blockedBy=" + schedule.blockedBy() + " steps="
                                + schedule.steps().size());
                return Optional.of(missingOnlyPlan(snapshot, missing));
            }
            ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "scheduler_blockedBy=" + schedule.blockedBy() + " steps="
                            + schedule.steps().size());
            return Optional.empty();
        }

        Optional<KeyCounter> usedItems = calculateUsedItems(problem, candidate, missing, schedule.steps());
        if (usedItems.isEmpty()) {
            ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "calculateUsedItems_returned_empty steps="
                            + schedule.steps().size());
            return Optional.empty();
        }
        if (candidate.executions().isEmpty() && usedItems.get().isEmpty()) {
            ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                    snapshot.requestedKey(),
                    snapshot.requestedAmount(),
                    "assembler",
                    "empty_plan_no_patterns_or_inputs");
            return Optional.empty();
        }
        KeyCounter missingItems = toCounter(missing);
        KeyCounter emittedItems = new KeyCounter();
        long bytes = estimateBytes(snapshot, candidate);
        CraftingPlan plan = new CraftingPlan(
                new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
                bytes,
                simulation,
                snapshot.multiplePaths(),
                usedItems.get(),
                emittedItems,
                missingItems,
                aggregatePatternExecutions(candidate));
        if (!simulation) {
            ECOPlannedInputs.register(plan, schedule.steps());
        }
        if (ECOPlanningFailureDiagnostics.canLogDetail(ECOPlanningFailureDiagnostics.Stage.ASSEMBLER)) {
            ECOPlanningFailureDiagnostics.logDetail(
                    ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                    (simulation ? "assembler_missing_simulation" : "assembler_executable_plan")
                            + " bytes=" + bytes
                            + " simulation=" + simulation
                            + " patternTimes="
                            + ECOPlanningFailureDiagnostics.describeMap(aggregatePatternExecutions(candidate))
                            + " usedItems=" + usedItems.get()
                            + " scheduledSteps=" + schedule.steps().size());
        }
        return Optional.of(plan);
    }

    /** Compatibility helper retained for the old input-selection execution tests. */
    static void registerSelectedInputs(
            CraftingPlan plan,
            Map<IPatternDetails, List<ECOAE2InputSelection>> selectedInputs,
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<IPatternDetails>> steps) {
        var selectedSteps = steps.stream()
                .filter(step -> selectedInputs.containsKey(step.operation()))
                .map(step -> new cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<>(
                        new ECOAE2PatternVariant(step.operation(), 0, selectedInputs.get(step.operation())),
                        step.batches()))
                .toList();
        if (!selectedSteps.isEmpty()) {
            ECOPlannedInputs.register(plan, selectedSteps);
        }
    }

    private static Map<IPatternDetails, Long> aggregatePatternExecutions(
            ECOPlanCandidate<ECOAE2PatternVariant> candidate) {
        Map<IPatternDetails, Long> result = new LinkedHashMap<>();
        candidate.executions().forEach((variant, count) -> result.merge(variant.pattern(), count, Math::addExact));
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> findMissingSources(
            ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem, ECOHyperflowResult<ECOAE2PatternVariant> result) {
        ECOPlanCandidate<ECOAE2PatternVariant> candidate = result.candidate();
        Map<AEKey, Long> balances = new LinkedHashMap<>(problem.inventory());
        Map<AEKey, Boolean> craftable = new HashMap<>();
        for (var operation : problem.operations()) {
            operation.selectableOutputs().forEach(key -> craftable.put(key, true));
            long count = candidate.executions().getOrDefault(operation.reference(), 0L);
            operation.inputs().forEach((key, amount) -> mergeScaledSaturated(balances, key, amount, -count));
            operation.outputs().forEach((key, amount) -> mergeScaledSaturated(balances, key, amount, count));
        }
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, ECOAE2PlanAssembler::saturatedAdd));

        Map<AEKey, Long> missing = new LinkedHashMap<>();
        for (var balance : balances.entrySet()) {
            boolean solverClassifiedAsSource = result.status() == ECOHyperflowResult.Status.MISSING_SOURCES
                    && candidate.requestedShortfall() == 0L
                    && candidate.dependencyShortfall() == 0L;
            if (balance.getValue() < 0 && (solverClassifiedAsSource || !craftable.containsKey(balance.getKey()))) {
                missing.put(balance.getKey(), saturatedNegate(balance.getValue()));
            }
        }
        return missing;
    }

    private static Map<AEKey, Long> findNegativeBalances(
            ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem, ECOPlanCandidate<ECOAE2PatternVariant> candidate) {
        Map<AEKey, Long> balances = new LinkedHashMap<>(problem.inventory());
        for (var operation : problem.operations()) {
            long count = candidate.executions().getOrDefault(operation.reference(), 0L);
            operation.inputs().forEach((key, amount) -> mergeScaledSaturated(balances, key, amount, -count));
            operation.outputs().forEach((key, amount) -> mergeScaledSaturated(balances, key, amount, count));
        }
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, ECOAE2PlanAssembler::saturatedAdd));
        Map<AEKey, Long> negative = new LinkedHashMap<>();
        balances.forEach((key, amount) -> {
            if (amount < 0L) {
                negative.put(key, saturatedNegate(amount));
            }
        });
        return negative;
    }

    private static boolean coversNegativeBalances(Map<AEKey, Long> missing, Map<AEKey, Long> negativeBalances) {
        return negativeBalances.entrySet().stream()
                .allMatch(entry -> missing.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    private static void logInvariantFailure(ECOAE2PlanningSnapshot snapshot, String context) {
        ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "assembler_invariant",
                context);
    }

    private static Map<AEKey, Long> availableFor(
            Map<AEKey, Long> inventory, Set<AEKey> missingKeys, Set<AEKey> negativeBalanceKeys) {
        Map<AEKey, Long> available = new LinkedHashMap<>();
        missingKeys.forEach(key -> available.put(key, inventory.getOrDefault(key, 0L)));
        negativeBalanceKeys.forEach(key -> available.putIfAbsent(key, inventory.getOrDefault(key, 0L)));
        return available;
    }

    private static void addMissingCycleSeed(
            ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
            ECOHyperflowResult<ECOAE2PatternVariant> result,
            Map<AEKey, Long> missing) {
        Set<ECOAE2PatternVariant> starters =
                result.cycleTrace().map(trace -> trace.missingSeedStarters()).orElse(Set.of());
        if (starters.isEmpty()) {
            return;
        }
        problem.operations().stream()
                .filter(operation -> starters.contains(operation.reference()))
                .forEach(operation -> operation.inputs().forEach((key, amount) -> {
                    long deficit = Math.max(0L, amount - problem.inventory().getOrDefault(key, 0L));
                    if (deficit > 0L) {
                        missing.merge(key, deficit, Math::max);
                    }
                }));
    }

    private static Optional<KeyCounter> calculateUsedItems(
            ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
            ECOPlanCandidate<ECOAE2PatternVariant> candidate,
            Map<AEKey, Long> missing,
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<ECOAE2PatternVariant>> steps) {
        Map<ECOAE2PatternVariant, ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> byReference = new HashMap<>();
        problem.operations().forEach(operation -> byReference.put(operation.reference(), operation));
        Map<AEKey, Long> current = new LinkedHashMap<>(problem.inventory());
        Map<AEKey, Long> syntheticRemaining = new LinkedHashMap<>(missing);
        KeyCounter requiredExtract = new KeyCounter();

        try {
            for (var step : steps) {
                var operation = byReference.get(step.operation());
                if (operation == null || step.batches() > candidate.executions().getOrDefault(step.operation(), 0L)) {
                    logUsedItemsFailure(problem, "invalid_scheduled_step operation=" + step.operation());
                    return Optional.empty();
                }
                for (var input : operation.inputs().entrySet()) {
                    long needed = Math.multiplyExact(input.getValue(), step.batches());
                    long outputPerBatch = operation.outputs().getOrDefault(input.getKey(), 0L);
                    // Exact self-returning catalysts can be reused within a consecutive step.
                    long requiredBeforeStep = !operation.stateTransitionInputs().contains(input.getKey())
                                    && outputPerBatch >= input.getValue()
                            ? input.getValue()
                            : needed;
                    long available = current.getOrDefault(input.getKey(), 0L);
                    if (available < requiredBeforeStep) {
                        long supplied = Math.min(
                                requiredBeforeStep - available, syntheticRemaining.getOrDefault(input.getKey(), 0L));
                        if (supplied > 0L) {
                            current.merge(input.getKey(), supplied, Math::addExact);
                            syntheticRemaining.merge(input.getKey(), -supplied, Math::addExact);
                            available += supplied;
                        }
                    }
                    if (available < requiredBeforeStep) {
                        logUsedItemsFailure(
                                problem,
                                "insufficient_step_input operation=" + step.operation()
                                        + " material=" + input.getKey()
                                        + " batches=" + step.batches()
                                        + " requiredBeforeStep=" + requiredBeforeStep);
                        return Optional.empty();
                    }
                    current.merge(input.getKey(), -needed, Math::addExact);
                    long baseline = problem.inventory().getOrDefault(input.getKey(), 0L);
                    long extracted =
                            Math.min(baseline, Math.max(0L, baseline - current.getOrDefault(input.getKey(), 0L)));
                    if (extracted > requiredExtract.get(input.getKey())) {
                        requiredExtract.set(input.getKey(), extracted);
                    }
                }
                for (var output : operation.outputs().entrySet()) {
                    long produced = Math.multiplyExact(output.getValue(), step.batches());
                    current.merge(output.getKey(), produced, Math::addExact);
                }
            }
            return Optional.of(requiredExtract);
        } catch (ArithmeticException overflow) {
            logUsedItemsFailure(problem, "arithmetic_overflow exception=" + overflow.getMessage());
            return Optional.empty();
        }
    }

    private static void logUsedItemsFailure(ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem, String context) {
        ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "used_items",
                context);
    }

    private static long estimateBytes(
            ECOAE2PlanningSnapshot snapshot, ECOPlanCandidate<ECOAE2PatternVariant> candidate) {
        double bytes = 8.0
                * snapshot.requestedAmount()
                / snapshot.requestedKey().getType().getAmountPerByte();
        long graphNodes = 1L;
        for (var operation : snapshot.problem().operations()) {
            long count = candidate.executions().getOrDefault(operation.reference(), 0L);
            if (count <= 0L) {
                continue;
            }
            bytes += count;
            graphNodes += 1L
                    + snapshot.inputSlotCounts()
                            .getOrDefault(
                                    operation.reference(),
                                    operation.reference().selectedInputs().size());
            for (var input : operation.inputs().entrySet()) {
                bytes += 8.0
                        * input.getValue()
                        * count
                        / input.getKey().getType().getAmountPerByte();
            }
        }
        bytes += graphNodes * 8.0;
        if (!Double.isFinite(bytes) || bytes >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(bytes));
    }

    private static KeyCounter toCounter(Map<AEKey, Long> amounts) {
        KeyCounter result = new KeyCounter();
        amounts.forEach(result::add);
        return result;
    }

    private static boolean hasSaturatedCounts(ECOPlanCandidate<ECOAE2PatternVariant> candidate) {
        return candidate.sourceShortfall() == Long.MAX_VALUE
                || candidate.dependencyShortfall() == Long.MAX_VALUE
                || candidate.requestedShortfall() == Long.MAX_VALUE
                || candidate.executions().containsValue(Long.MAX_VALUE);
    }

    private static CraftingPlan missingOnlyPlan(ECOAE2PlanningSnapshot snapshot, Map<AEKey, Long> missing) {
        return new CraftingPlan(
                new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
                1L,
                true,
                snapshot.multiplePaths(),
                new KeyCounter(),
                new KeyCounter(),
                toCounter(missing),
                Map.of());
    }

    private static void mergeScaled(Map<AEKey, Long> balances, AEKey key, long amount, long count) {
        balances.merge(key, Math.multiplyExact(amount, count), Math::addExact);
    }

    private static void mergeScaledSaturated(Map<AEKey, Long> balances, AEKey key, long amount, long count) {
        balances.merge(key, saturatedMultiply(amount, count), ECOAE2PlanAssembler::saturatedAdd);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return (left < 0L) ^ (right < 0L) ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long saturatedNegate(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : -value;
    }
}
