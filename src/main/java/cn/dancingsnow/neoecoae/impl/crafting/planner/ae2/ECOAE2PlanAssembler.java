package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a validated ECO result into the complete plan contract consumed by AE2 CPUs. */
public final class ECOAE2PlanAssembler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private ECOAE2PlanAssembler() {
    }

    public static Optional<CraftingPlan> assemble(
        ECOAE2PlanningSnapshot snapshot,
        ECOHyperflowResult<ECOAE2PatternVariant> result
    ) {
        return assemble(snapshot, result, Long.MAX_VALUE);
    }

    public static Optional<CraftingPlan> assemble(
        ECOAE2PlanningSnapshot snapshot,
        ECOHyperflowResult<ECOAE2PatternVariant> result,
        long deadlineNanos
    ) {
        if (result.status() != ECOHyperflowResult.Status.COMPLETE
            && result.status() != ECOHyperflowResult.Status.MISSING_SOURCES) {
            return Optional.empty();
        }

        var problem = snapshot.problem();
        var candidate = result.candidate();
        var schedule = ECOInventoryScheduler.scheduleWithSyntheticSources(
            problem, candidate, deadlineNanos, 50_000L
        );
        if (!schedule.executable()) {
            LOGGER.debug(
                "ECO assembly scheduling rejected {} x{} after {} states: exhausted={}, blockedBy={}",
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                schedule.expandedStates(),
                schedule.budgetExhausted(),
                schedule.blockedBy()
            );
            return Optional.empty();
        }
        Map<AEKey, Long> sourceDeficits = schedule.syntheticSources();
        SourceRequirements requirements = splitSourceRequirements(sourceDeficits, snapshot.emittableKeys());

        Optional<KeyCounter> usedItems = calculateUsedItems(
            problem, candidate, sourceDeficits, schedule.steps(), deadlineNanos
        );
        if (usedItems.isEmpty()) {
            return Optional.empty();
        }
        KeyCounter missingItems = toCounter(requirements.missing());
        KeyCounter emittedItems = toCounter(requirements.emitted());
        long bytes = estimateBytes(snapshot, candidate);
        Map<IPatternDetails, Long> patternExecutions = aggregatePatternExecutions(candidate);
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }
        CraftingPlan plan = new CraftingPlan(
            new GenericStack(snapshot.requestedKey(), snapshot.requestedAmount()),
            bytes,
            !requirements.missing().isEmpty(),
            snapshot.multiplePaths(),
            usedItems.get(),
            emittedItems,
            missingItems,
            patternExecutions
        );
        ECOPlannedInputs.register(plan, schedule.steps());
        return Optional.of(plan);
    }

    /** Splits uncraftable source deficits by AE2's direct-emitter semantics. */
    private static SourceRequirements splitSourceRequirements(
        Map<AEKey, Long> sourceDeficits,
        Set<AEKey> emittableKeys
    ) {
        Map<AEKey, Long> emitted = new LinkedHashMap<>();
        Map<AEKey, Long> missing = new LinkedHashMap<>();
        sourceDeficits.forEach((key, amount) -> {
            if (emittableKeys.contains(key)) {
                emitted.put(key, amount);
            } else {
                missing.put(key, amount);
            }
        });
        return new SourceRequirements(Map.copyOf(emitted), Map.copyOf(missing));
    }

    private record SourceRequirements(Map<AEKey, Long> emitted, Map<AEKey, Long> missing) {
    }

    private static Optional<KeyCounter> calculateUsedItems(
        ECOPlanningProblem<AEKey, ECOAE2PatternVariant> problem,
        ECOPlanCandidate<ECOAE2PatternVariant> candidate,
        Map<AEKey, Long> sourceDeficits,
        java.util.List<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<ECOAE2PatternVariant>> steps,
        long deadlineNanos
    ) {
        Map<ECOAE2PatternVariant, ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> byReference = new HashMap<>();
        problem.operations().forEach(operation -> byReference.put(operation.reference(), operation));
        Map<AEKey, Long> current = new LinkedHashMap<>(problem.inventory());
        Map<AEKey, Long> syntheticRemaining = new LinkedHashMap<>(sourceDeficits);
        KeyCounter requiredExtract = new KeyCounter();

        try {
            for (var step : steps) {
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    return Optional.empty();
                }
                var operation = byReference.get(step.operation());
                if (operation == null || step.batches() > candidate.executions().getOrDefault(step.operation(), 0L)) {
                    return Optional.empty();
                }
                Set<AEKey> touched = new java.util.LinkedHashSet<>(operation.inputs().keySet());
                touched.addAll(operation.outputs().keySet());
                for (var input : operation.inputs().entrySet()) {
                    long needed = minimumInventory(
                        input.getValue(),
                        operation.outputs().getOrDefault(input.getKey(), 0L),
                        step.batches()
                    );
                    long available = current.getOrDefault(input.getKey(), 0L);
                    if (available < needed) {
                        long supplied = Math.min(needed - available, syntheticRemaining.getOrDefault(input.getKey(), 0L));
                        if (supplied > 0) {
                            current.merge(input.getKey(), supplied, Math::addExact);
                            syntheticRemaining.merge(input.getKey(), -supplied, Math::addExact);
                            available += supplied;
                        }
                    }
                    if (available < needed) {
                        return Optional.empty();
                    }
                    long baseline = problem.inventory().getOrDefault(input.getKey(), 0L);
                    long lowPoint = available - needed;
                    long extracted = Math.min(baseline, Math.max(0L, baseline - lowPoint));
                    if (extracted > requiredExtract.get(input.getKey())) {
                        requiredExtract.set(input.getKey(), extracted);
                    }
                }
                for (AEKey key : touched) {
                    long net = Math.subtractExact(
                        operation.outputs().getOrDefault(key, 0L),
                        operation.inputs().getOrDefault(key, 0L)
                    );
                    current.merge(key, Math.multiplyExact(net, step.batches()), Math::addExact);
                }
            }
            return Optional.of(requiredExtract);
        } catch (ArithmeticException ignored) {
            return Optional.empty();
        }
    }

    private static long estimateBytes(
        ECOAE2PlanningSnapshot snapshot,
        ECOPlanCandidate<ECOAE2PatternVariant> candidate
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

    private static Map<IPatternDetails, Long> aggregatePatternExecutions(
        ECOPlanCandidate<ECOAE2PatternVariant> candidate
    ) {
        Map<IPatternDetails, Long> result = new LinkedHashMap<>();
        candidate.executions().forEach((variant, count) -> result.merge(
            variant.pattern(), count, Math::addExact));
        return Map.copyOf(result);
    }

    private static KeyCounter toCounter(Map<AEKey, Long> amounts) {
        KeyCounter result = new KeyCounter();
        amounts.forEach(result::add);
        return result;
    }

    private static long minimumInventory(long input, long output, long batches) {
        if (output >= input) {
            return input;
        }
        return Math.addExact(input, Math.multiplyExact(batches - 1L, input - output));
    }

}
