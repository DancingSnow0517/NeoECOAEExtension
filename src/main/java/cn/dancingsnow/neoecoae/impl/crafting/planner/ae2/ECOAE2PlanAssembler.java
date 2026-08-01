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
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts a validated ECO result into the complete plan contract consumed by AE2 CPUs. */
public final class ECOAE2PlanAssembler {
    private ECOAE2PlanAssembler() {
    }

    public static Optional<CraftingPlan> assemble(
        ECOAE2PlanningSnapshot snapshot,
        ECOHyperflowResult<IPatternDetails> result
    ) {
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
                "unsupported_solver_status=" + result.status()
                    + " expandedStates=" + result.expandedStates()
            );
            return Optional.empty();
        }

        var problem = snapshot.problem();
        var candidate = result.candidate();
        Map<AEKey, Long> missing = findMissingSources(problem, result);
        addMissingCycleSeed(problem, result, missing);
        Map<AEKey, Long> schedulableInventory = new LinkedHashMap<>(problem.inventory());
        missing.forEach((key, amount) -> schedulableInventory.merge(key, amount, Math::addExact));
        var schedulableProblem = new ECOPlanningProblem<>(
            problem.operations(),
            schedulableInventory,
            problem.requested()
        );
        var schedule = ECOInventoryScheduler.schedule(schedulableProblem, candidate);
        if (!schedule.executable()) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "assembler",
                "scheduler_blockedBy=" + schedule.blockedBy()
                    + " steps=" + schedule.steps().size()
            );
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
                "calculateUsedItems_returned_empty steps=" + schedule.steps().size()
            );
            return Optional.empty();
        }
        if (candidate.executions().isEmpty() && usedItems.get().isEmpty()) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
                ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                "assembler",
                "empty_plan_no_patterns_or_inputs"
            );
            return Optional.empty();
        }
        KeyCounter missingItems = toCounter(missing);
        KeyCounter emittedItems = new KeyCounter();
        long bytes = estimateBytes(snapshot, candidate);
        CraftingPlan plan = new CraftingPlan(
            new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
            bytes,
            !missing.isEmpty(),
            snapshot.multiplePaths(),
            usedItems.get(),
            emittedItems,
            missingItems,
            candidate.executions()
        );
        registerSelectedInputs(plan, snapshot.selectedInputs(), schedule.steps());
        return Optional.of(plan);
    }

    static void registerSelectedInputs(
        CraftingPlan plan,
        Map<IPatternDetails, java.util.List<ECOAE2InputSelection>> selectedInputs,
        java.util.List<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<IPatternDetails>> steps
    ) {
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

    private static Map<AEKey, Long> findMissingSources(
        ECOPlanningProblem<AEKey, IPatternDetails> problem,
        ECOHyperflowResult<IPatternDetails> result
    ) {
        ECOPlanCandidate<IPatternDetails> candidate = result.candidate();
        Map<AEKey, Long> balances = new LinkedHashMap<>(problem.inventory());
        Map<AEKey, Boolean> craftable = new HashMap<>();
        for (var operation : problem.operations()) {
            operation.selectableOutputs().forEach(key -> craftable.put(key, true));
            long count = candidate.executions().getOrDefault(operation.reference(), 0L);
            operation.inputs().forEach((key, amount) -> mergeScaled(balances, key, amount, -count));
            operation.outputs().forEach((key, amount) -> mergeScaled(balances, key, amount, count));
        }
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, Math::addExact));

        Map<AEKey, Long> missing = new LinkedHashMap<>();
        for (var balance : balances.entrySet()) {
            boolean solverClassifiedAsSource = result.status() == ECOHyperflowResult.Status.MISSING_SOURCES
                && candidate.requestedShortfall() == 0L
                && candidate.dependencyShortfall() == 0L;
            if (balance.getValue() < 0
                && (solverClassifiedAsSource || !craftable.containsKey(balance.getKey()))) {
                missing.put(balance.getKey(), Math.negateExact(balance.getValue()));
            }
        }
        return missing;
    }

    private static void addMissingCycleSeed(
        ECOPlanningProblem<AEKey, IPatternDetails> problem,
        ECOHyperflowResult<IPatternDetails> result,
        Map<AEKey, Long> missing
    ) {
        Set<IPatternDetails> starters = result.cycleTrace()
            .map(trace -> trace.missingSeedStarters())
            .orElse(Set.of());
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
        ECOPlanningProblem<AEKey, IPatternDetails> problem,
        ECOPlanCandidate<IPatternDetails> candidate,
        Map<AEKey, Long> missing,
        java.util.List<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<IPatternDetails>> steps
    ) {
        Map<IPatternDetails, ECOPlanningOperation<AEKey, IPatternDetails>> byReference = new HashMap<>();
        problem.operations().forEach(operation -> byReference.put(operation.reference(), operation));
        Map<AEKey, Long> current = new LinkedHashMap<>(problem.inventory());
        Map<AEKey, Long> syntheticRemaining = new LinkedHashMap<>(missing);
        KeyCounter requiredExtract = new KeyCounter();

        try {
            for (var step : steps) {
                var operation = byReference.get(step.operation());
                if (operation == null || step.batches() > candidate.executions().getOrDefault(step.operation(), 0L)) {
                    logUsedItemsFailure(
                        problem,
                        "invalid_scheduled_step operation=" + step.operation()
                            + " batches=" + step.batches()
                            + " planned=" + candidate.executions().getOrDefault(step.operation(), 0L)
                    );
                    return Optional.empty();
                }
                for (var input : operation.inputs().entrySet()) {
                    long needed = Math.multiplyExact(input.getValue(), step.batches());
                    long outputPerBatch = operation.outputs().getOrDefault(input.getKey(), 0L);
                    // A self-sustaining input can be reused between sequential executions in this step.
                    long requiredBeforeStep = outputPerBatch >= input.getValue() ? input.getValue() : needed;
                    long available = current.getOrDefault(input.getKey(), 0L);
                    if (available < requiredBeforeStep) {
                        long supplied = Math.min(
                            requiredBeforeStep - available,
                            syntheticRemaining.getOrDefault(input.getKey(), 0L)
                        );
                        if (supplied > 0) {
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
                                + " inputPerBatch=" + input.getValue()
                                + " outputPerBatch=" + outputPerBatch
                                + " requiredBeforeStep=" + requiredBeforeStep
                                + " totalConsumed=" + needed
                                + " available=" + available
                                + " syntheticRemaining=" + syntheticRemaining.getOrDefault(input.getKey(), 0L)
                        );
                        return Optional.empty();
                    }
                    current.merge(input.getKey(), -needed, Math::addExact);
                    long baseline = problem.inventory().getOrDefault(input.getKey(), 0L);
                    long extracted = Math.min(baseline, Math.max(0, baseline - current.get(input.getKey())));
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

    private static void logUsedItemsFailure(
        ECOPlanningProblem<AEKey, IPatternDetails> problem,
        String context
    ) {
        ECOPlanningFailureDiagnostics.logFailure(
            ECOPlanningFailureDiagnostics.Stage.ASSEMBLER,
            ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
            problem.requested().keySet().stream().findFirst().orElse(null),
            problem.requested().values().stream().findFirst().orElse(0L),
            "used_items",
            context
        );
    }

    private static long estimateBytes(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanCandidate<IPatternDetails> candidate
    ) {
        double bytes = 8.0 * snapshot.requestedAmount()
            / snapshot.requestedKey().getType().getAmountPerByte();
        long graphNodes = 1;
        for (var operation : snapshot.problem().operations()) {
            long count = candidate.executions().getOrDefault(operation.reference(), 0L);
            if (count <= 0) continue;
            bytes += count;
            graphNodes += 1L + snapshot.inputSlotCounts().getOrDefault(
                operation.reference(),
                operation.inputs().size()
            );
            for (var input : operation.inputs().entrySet()) {
                bytes += 8.0 * input.getValue() * count / input.getKey().getType().getAmountPerByte();
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

    private static void mergeScaled(Map<AEKey, Long> balances, AEKey key, long amount, long count) {
        balances.merge(key, Math.multiplyExact(amount, count), Math::addExact);
    }
}
