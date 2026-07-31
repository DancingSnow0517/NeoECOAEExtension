package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerInputPolicy;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerNoticeDispatcher;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningDiagnostics;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures the immutable AE2 input view consumed by the ECO planning worker. */
public final class ECOAE2SnapshotFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_MATERIALS = 16_384;
    private static final int MAX_OPERATIONS = 65_536;
    private static final int MAX_INPUT_SLOTS_PER_PATTERN = 256;
    private static final int MAX_OUTPUTS_PER_PATTERN = 256;
    private static final int MAX_VARIANTS_PER_PATTERN = 1_024;
    private static final long MAX_OPERATION_TERMS = 1_000_000L;
    private static final long NO_GENERATION = Long.MIN_VALUE;

    private ECOAE2SnapshotFactory() {
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy
    ) {
        return captureDetailed(grid, requester, requestedKey, requestedAmount, strategy, NO_GENERATION, null).snapshot();
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration
    ) {
        return captureDetailed(
            grid, requester, requestedKey, requestedAmount, strategy, craftableGeneration, null
        ).snapshot();
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration,
        Level level
    ) {
        return captureDetailed(
            grid, requester, requestedKey, requestedAmount, strategy, craftableGeneration, level
        ).snapshot();
    }

    public static ECOAE2SnapshotCapture captureDetailed(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration,
        Level level
    ) {
        ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_STARTED);
        if (requestedAmount <= 0L
            || (strategy != CalculationStrategy.REPORT_MISSING_ITEMS
                && strategy != CalculationStrategy.CRAFT_LESS)) {
            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.UNSUPPORTED_REQUEST);
            return ECOAE2SnapshotCapture.rejected(
                ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                "unsupported request amount or calculation strategy"
            );
        }

        try {
            ECOPlannerNoticeDispatcher.Target noticeTarget = ECOPlannerNoticeDispatcher.targetFor(requester);
            Map<AEKey, Long> inventory = copyInventory(grid, requester);
            ICraftingService craftingService = grid.getCraftingService();

            // craftableGeneration is an AE2 processed-tick marker, not a provider revision. Rebuilding
            // avoids reusing a graph across provider changes that happen inside the same server tick.
            PatternGraph graph = buildGraph(craftingService, requestedKey, inventory, level);
            List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = materialize(graph);
            Map<ECOAE2PatternVariant, Integer> inputSlotCounts = new LinkedHashMap<>();
            for (var operation : operations) {
                inputSlotCounts.put(operation.reference(), operation.reference().selectedInputs().size());
            }

            // AE2's CraftingCalculation calls CraftingSimulationState.ignore(finalOutput), so existing
            // copies of the root output cannot satisfy the request or bootstrap a root self-cycle.
            inventory.remove(requestedKey);
            retainRelevantInventory(inventory, operations, requestedKey);

            var problem = new ECOPlanningProblem<>(operations, inventory, Map.of(requestedKey, requestedAmount));
            ECOAE2PlanningSnapshot snapshot = new ECOAE2PlanningSnapshot(
                problem,
                requestedKey,
                requestedAmount,
                graph.multiplePaths(),
                inputSlotCounts,
                graph.emittableKeys(),
                noticeTarget
            );
            LOGGER.debug(
                "Captured ECO planning snapshot for {} x{}: generation={}, patterns={}, variants={}, "
                    + "materials={}, inventoryKeys={}, emitters={}",
                requestedKey,
                requestedAmount,
                craftableGeneration,
                graph.patterns().size(),
                operations.size(),
                graph.materialCount(),
                inventory.size(),
                graph.emittableKeys().size()
            );
            return ECOAE2SnapshotCapture.accepted(snapshot);
        } catch (SnapshotRejection rejection) {
            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_REJECTED);
            if (rejection.reason() == ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE) {
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.PATTERN_INCOMPATIBLE);
            } else if (rejection.reason() == ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED) {
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_LIMIT_EXCEEDED);
            }
            LOGGER.debug(
                "Rejected ECO planning snapshot for {} x{}: reason={}, detail={}",
                requestedKey,
                requestedAmount,
                rejection.reason(),
                rejection.getMessage()
            );
            return ECOAE2SnapshotCapture.rejected(rejection.reason(), rejection.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_REJECTED);
            LOGGER.debug(
                "ECO AE2 snapshot capture failed for {} x{}; the caller will use AE2 crafting calculation",
                requestedKey,
                requestedAmount,
                failure
            );
            return ECOAE2SnapshotCapture.rejected(
                ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage())
            );
        }
    }

    private static PatternGraph buildGraph(
        ICraftingService craftingService,
        AEKey requestedKey,
        Map<AEKey, Long> inventory,
        Level level
    ) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        Set<AEKey> visitedMaterials = new LinkedHashSet<>();
        Map<AEItemKey, CapturedPattern> canonicalPatterns = new LinkedHashMap<>();
        List<CapturedPattern> patterns = new ArrayList<>();
        Set<AEKey> emittableKeys = new LinkedHashSet<>();
        boolean multiplePaths = false;
        long operationCount = 0L;
        long operationTermCount = 0L;
        pending.add(requestedKey);

        while (!pending.isEmpty()) {
            AEKey material = pending.removeFirst();
            if (!visitedMaterials.add(material)) {
                continue;
            }
            if (visitedMaterials.size() > MAX_MATERIALS) {
                throw reject(ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED, "material limit exceeded");
            }
            if (craftingService.canEmitFor(material)) {
                emittableKeys.add(material);
                continue;
            }

            Set<AEItemKey> logicalProducerIdentities = new LinkedHashSet<>();
            for (IPatternDetails details : new ArrayList<>(craftingService.getCraftingFor(material))) {
                if (details == null) {
                    throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "null pattern details");
                }
                AEItemKey definition = null;
                CapturedPattern captured;
                try {
                    definition = details.getDefinition();
                    if (definition == null) {
                        throw reject(
                            ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                            details.getClass().getName() + " returned a null definition"
                        );
                    }
                    captured = capturePattern(details, craftingService, inventory, level);
                } catch (SnapshotRejection rejection) {
                    throw reject(
                        rejection.reason(),
                        "patternClass=" + details.getClass().getName()
                            + ", definition=" + definition + ", producerFor=" + material
                            + ": " + rejection.getMessage()
                    );
                } catch (RuntimeException | LinkageError failure) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "patternClass=" + details.getClass().getName()
                            + ", definition=" + definition + ", producerFor=" + material
                            + " metadata failed: "
                            + failure.getClass().getSimpleName()
                    );
                }
                logicalProducerIdentities.add(definition);

                CapturedPattern canonical = canonicalPatterns.get(definition);
                if (canonical != null) {
                    if (!canonical.shape().equals(captured.shape())) {
                        throw reject(
                            ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                            "one pattern definition has multiple logical shapes: " + definition
                        );
                    }
                    continue;
                }

                long variants = variantCount(captured.selections());
                if (variants > MAX_VARIANTS_PER_PATTERN
                    || operationCount > MAX_OPERATIONS - variants) {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.VARIANT_LIMIT_REJECTED);
                    throw reject(
                        ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                        "input alternatives exceed ECO's immutable variant limit for " + definition
                    );
                }
                long addedTerms = operationTermCount(captured, variants);
                if (addedTerms > MAX_OPERATION_TERMS - operationTermCount) {
                    throw reject(
                        ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                        "operation term limit exceeded for " + definition
                    );
                }
                operationCount += variants;
                operationTermCount += addedTerms;
                canonicalPatterns.put(definition, captured);
                patterns.add(captured);
                multiplePaths |= variants > 1L;
                for (InputShape slot : captured.shape().inputs()) {
                    for (GenericStack choice : slot.choices()) {
                        pending.addLast(choice.what());
                    }
                }
            }
            multiplePaths |= logicalProducerIdentities.size() > 1;
        }
        return new PatternGraph(patterns, emittableKeys, multiplePaths, visitedMaterials.size());
    }

    private static CapturedPattern capturePattern(
        IPatternDetails details,
        ICraftingService craftingService,
        Map<AEKey, Long> inventory,
        Level level
    ) {
        GenericStack primaryOutput = details.getPrimaryOutput();
        List<GenericStack> rawOutputs = details.getOutputs();
        if (primaryOutput == null || primaryOutput.amount() <= 0L
            || rawOutputs == null || rawOutputs.isEmpty()) {
            throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "pattern has no primary output");
        }
        if (rawOutputs.size() > MAX_OUTPUTS_PER_PATTERN) {
            throw reject(ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED, "pattern output limit exceeded");
        }
        for (GenericStack output : rawOutputs) {
            if (output == null || output.amount() <= 0L) {
                throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "pattern has an invalid output");
            }
        }
        List<GenericStack> outputs = List.copyOf(rawOutputs);
        boolean containsPrimary = outputs.stream().anyMatch(output -> output.what().equals(primaryOutput.what()));
        if (!containsPrimary) {
            throw reject(
                ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                "primary output is not included in pattern outputs"
            );
        }

        IPatternDetails.IInput[] patternInputs = details.getInputs();
        if (patternInputs == null) {
            throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "pattern returned null inputs");
        }
        if (patternInputs.length > MAX_INPUT_SLOTS_PER_PATTERN) {
            throw reject(ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED, "pattern input limit exceeded");
        }
        var assessment = ECOAE2PatternCompatibility.assess(details, patternInputs, craftingService, level);
        if (!assessment.compatible()) {
            throw reject(
                ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                assessment.rejection()
            );
        }
        List<List<GenericStack>> choices = orderedChoices(
            details,
            patternInputs,
            assessment.inputSemantics(),
            assessment.includeFuzzyInventory(),
            assessment.requireUnitMultiplierForAlternatives(),
            inventory,
            craftingService,
            level
        );
        List<List<ECOAE2InputSelection>> selections = inputSelections(
            details, patternInputs, choices, assessment.inputSemantics()
        );
        PatternShape shape = patternShape(
            details.getClass(), assessment.inputSemantics(), primaryOutput, outputs, patternInputs, choices
        );
        return new CapturedPattern(details, selections, shape);
    }

    private static PatternShape patternShape(
        Class<?> implementation,
        IECOPlannerCompatiblePattern.InputSemantics inputSemantics,
        GenericStack primaryOutput,
        List<GenericStack> outputs,
        IPatternDetails.IInput[] patternInputs,
        List<List<GenericStack>> choices
    ) {
        List<InputShape> inputs = new ArrayList<>();
        for (int slot = 0; slot < patternInputs.length; slot++) {
            IPatternDetails.IInput input = patternInputs[slot];
            List<Optional<AEKey>> remaining = new ArrayList<>();
            for (GenericStack choice : choices.get(slot)) {
                remaining.add(Optional.ofNullable(input.getRemainingKey(choice.what())));
            }
            inputs.add(new InputShape(input.getMultiplier(), choices.get(slot), remaining));
        }
        return new PatternShape(implementation, inputSemantics, primaryOutput, outputs, inputs);
    }

    private static long variantCount(List<List<ECOAE2InputSelection>> choices) {
        long count = 1L;
        for (List<ECOAE2InputSelection> slot : choices) {
            try {
                count = Math.multiplyExact(count, slot.size());
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
            if (count > MAX_VARIANTS_PER_PATTERN) {
                return count;
            }
        }
        return count;
    }

    private static long operationTermCount(CapturedPattern captured, long variants) {
        try {
            long total = Math.multiplyExact(variants, captured.shape().outputs().size());
            for (List<ECOAE2InputSelection> slot : captured.selections()) {
                long alternatives = 0L;
                for (ECOAE2InputSelection selection : slot) {
                    alternatives = Math.addExact(alternatives, selection.alternatives().size());
                }
                long appearances = Math.multiplyExact(variants / slot.size(), alternatives);
                // One input term plus at most one remaining-item term per selected alternative.
                total = Math.addExact(total, Math.multiplyExact(appearances, 2L));
            }
            return total;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static List<List<ECOAE2InputSelection>> inputSelections(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        List<List<GenericStack>> choices,
        IECOPlannerCompatiblePattern.InputSemantics semantics
    ) {
        List<List<ECOAE2InputSelection>> result = new ArrayList<>(inputs.length);
        long totalVariants = 1L;
        for (int slot = 0; slot < inputs.length; slot++) {
            long multiplier = inputs[slot].getMultiplier();
            List<GenericStack> slotChoices = choices.get(slot);
            long slotVariants = semantics == IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                ? compositionCount(multiplier, slotChoices.size())
                : slotChoices.size();
            if (slotVariants > MAX_VARIANTS_PER_PATTERN
                || totalVariants > MAX_VARIANTS_PER_PATTERN / slotVariants) {
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.VARIANT_LIMIT_REJECTED);
                throw reject(
                    ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                    "input selections exceed ECO's variant limit for "
                        + details.getDefinition() + " at slot " + slot
                        + " (multiplier=" + multiplier + ", alternatives=" + slotChoices.size() + ")"
                        + ", semantics=" + semantics
                );
            }
            totalVariants *= slotVariants;
            result.add(semantics == IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                ? mixedSelections(slotChoices, multiplier)
                : homogeneousSelections(slotChoices, multiplier));
        }
        return List.copyOf(result);
    }

    private static long compositionCount(long multiplier, int alternatives) {
        if (alternatives <= 1) {
            return 1L;
        }
        long selected = Math.min(multiplier, alternatives - 1L);
        if (selected > MAX_VARIANTS_PER_PATTERN) {
            return MAX_VARIANTS_PER_PATTERN + 1L;
        }
        BigInteger n = BigInteger.valueOf(multiplier).add(BigInteger.valueOf(alternatives - 1L));
        BigInteger result = BigInteger.ONE;
        for (long index = 1L; index <= selected; index++) {
            result = result.multiply(n.subtract(BigInteger.valueOf(selected - index)))
                .divide(BigInteger.valueOf(index));
            if (result.compareTo(BigInteger.valueOf(MAX_VARIANTS_PER_PATTERN)) > 0) {
                return MAX_VARIANTS_PER_PATTERN + 1L;
            }
        }
        return result.longValueExact();
    }

    private static List<ECOAE2InputSelection> homogeneousSelections(
        List<GenericStack> choices,
        long multiplier
    ) {
        return choices.stream()
            .map(choice -> ECOAE2InputSelection.single(choice, multiplier))
            .toList();
    }

    private static List<ECOAE2InputSelection> mixedSelections(
        List<GenericStack> choices,
        long multiplier
    ) {
        if (choices.size() == 1) {
            return List.of(ECOAE2InputSelection.single(choices.getFirst(), multiplier));
        }
        List<ECOAE2InputSelection> result = new ArrayList<>();
        enumerateMixedSelections(choices, 0, multiplier, new ArrayList<>(), result);
        return List.copyOf(result);
    }

    private static void enumerateMixedSelections(
        List<GenericStack> choices,
        int choiceIndex,
        long remaining,
        List<ECOAE2InputSelection.Alternative> selected,
        List<ECOAE2InputSelection> result
    ) {
        if (choiceIndex == choices.size() - 1) {
            if (remaining > 0L) {
                selected.add(new ECOAE2InputSelection.Alternative(choices.get(choiceIndex), remaining));
            }
            result.add(new ECOAE2InputSelection(selected));
            if (remaining > 0L) {
                selected.removeLast();
            }
            return;
        }

        for (long units = remaining; ; units--) {
            if (units > 0L) {
                selected.add(new ECOAE2InputSelection.Alternative(choices.get(choiceIndex), units));
            }
            enumerateMixedSelections(choices, choiceIndex + 1, remaining - units, selected, result);
            if (units > 0L) {
                selected.removeLast();
            }
            if (units == 0L) {
                break;
            }
        }
    }

    private static List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> materialize(PatternGraph graph) {
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = new ArrayList<>();
        for (CapturedPattern captured : graph.patterns()) {
            expandVariants(captured, 0, new ArrayList<>(), operations);
        }
        return List.copyOf(operations);
    }

    private static List<List<GenericStack>> orderedChoices(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        IECOPlannerCompatiblePattern.InputSemantics semantics,
        boolean includeFuzzyInventory,
        boolean requireUnitMultiplierForAlternatives,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level
    ) {
        List<List<GenericStack>> result = new ArrayList<>();
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            if (input == null) {
                throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "pattern has a null input slot");
            }
            if (input.getMultiplier() <= 0L) {
                throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "input multiplier is not positive");
            }
            List<GenericStack> choices = semantics == IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY
                ? canonicalChoice(input, level)
                : choicesForSlot(
                    details, slot, input, includeFuzzyInventory, inventory, craftingService, level
                );
            if (requireUnitMultiplierForAlternatives
                && input.getMultiplier() > 1L
                && choices.size() > 1) {
                throw reject(
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    "undeclared mixed alternatives for " + details.getDefinition()
                        + " at slot " + slot + " (multiplier=" + input.getMultiplier()
                        + ", alternatives=" + choices.size() + ")"
                );
            }
            choices.sort((left, right) -> compareInputChoices(left, right, input, inventory, craftingService));
            result.add(List.copyOf(choices));
        }
        return List.copyOf(result);
    }

    private static List<GenericStack> canonicalChoice(IPatternDetails.IInput input, Level level) {
        GenericStack[] possible = input.getPossibleInputs();
        if (possible.length == 0 || possible[0] == null || possible[0].amount() <= 0L) {
            throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "canonical input is missing");
        }
        try {
            if (!input.isValid(possible[0].what(), level)) {
                throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "canonical input is not valid");
            }
        } catch (SnapshotRejection rejection) {
            throw rejection;
        } catch (RuntimeException | LinkageError failure) {
            throw reject(
                ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                "canonical input validation failed: " + failure.getClass().getSimpleName()
            );
        }
        return new ArrayList<>(List.of(possible[0]));
    }

    private static List<GenericStack> choicesForSlot(
        IPatternDetails details,
        int slot,
        IPatternDetails.IInput input,
        boolean includeFuzzyInventory,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level
    ) {
        Map<AEKey, Long> choices = new LinkedHashMap<>();
        for (GenericStack candidate : input.getPossibleInputs()) {
            if (candidate != null && candidate.amount() > 0L && isValid(input, candidate.what(), level)) {
                putChoice(choices, candidate.what(), candidate.amount());
            }
        }
        if (choices.isEmpty()) {
            throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "input has no valid alternatives");
        }
        IECOPlannerInputPolicy.MatchMode mode = details instanceof IECOPlannerInputPolicy policy
            ? policy.getPlannerInputMatchMode(slot, input)
            : IECOPlannerInputPolicy.MatchMode.STRICT;
        if (includeFuzzyInventory) {
            addFuzzyCraftableVariant(choices, input, craftingService, level);
            addFuzzyInventoryVariants(choices, input, inventory, level);
        } else if (mode == IECOPlannerInputPolicy.MatchMode.ITEM_ONLY) {
            addFuzzyCraftableVariant(choices, input, craftingService, level);
            addItemOnlyInventoryVariants(choices, input, inventory, level);
        }
        List<GenericStack> result = new ArrayList<>(choices.size());
        choices.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return result;
    }

    private static boolean isValid(IPatternDetails.IInput input, AEKey key, Level level) {
        try {
            return input.isValid(key, level);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static void putChoice(Map<AEKey, Long> choices, AEKey key, long amount) {
        Long existing = choices.putIfAbsent(key, amount);
        if (existing != null && existing.longValue() != amount) {
            throw reject(
                ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                "one input key has conflicting template amounts"
            );
        }
    }

    private static void addItemOnlyInventoryVariants(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level
    ) {
        Map<net.minecraft.world.item.Item, Long> allowedItems = new LinkedHashMap<>();
        choices.forEach((key, amount) -> {
            if (key instanceof AEItemKey itemKey) {
                allowedItems.putIfAbsent(itemKey.getItem(), amount);
            }
        });
        if (allowedItems.isEmpty()) {
            return;
        }
        for (AEKey key : inventory.keySet()) {
            if (key instanceof AEItemKey itemKey) {
                Long amount = allowedItems.get(itemKey.getItem());
                if (amount != null && isValid(input, key, level)) {
                    putChoice(choices, key, amount);
                }
            }
        }
    }

    private static void addFuzzyInventoryVariants(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level
    ) {
        List<Map.Entry<AEKey, Long>> templates = List.copyOf(choices.entrySet());
        for (AEKey key : inventory.keySet()) {
            for (var template : templates) {
                if (isFuzzyMatch(template.getKey(), key) && isValid(input, key, level)) {
                    putChoice(choices, key, template.getValue());
                    break;
                }
            }
        }
    }

    private static void addFuzzyCraftableVariant(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        ICraftingService craftingService,
        Level level
    ) {
        long acceptableAmount = choices.values().iterator().next();
        AEKey canonical = choices.keySet().iterator().next();
        if (!craftingService.getCraftingFor(canonical).isEmpty()) {
            return;
        }
        for (var template : List.copyOf(choices.entrySet())) {
            if (template.getValue() != acceptableAmount) {
                continue;
            }
            AEKey craftable = craftingService.getFuzzyCraftable(
                template.getKey(), candidate -> isValid(input, candidate, level)
            );
            if (craftable != null) {
                putChoice(choices, craftable, template.getValue());
                return;
            }
        }
    }

    private static boolean isFuzzyMatch(AEKey template, AEKey candidate) {
        try {
            return template.fuzzyEquals(candidate, FuzzyMode.IGNORE_ALL);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static int compareInputChoices(
        GenericStack left,
        GenericStack right,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        int rank = Integer.compare(
            inputRank(right, input, inventory, craftingService),
            inputRank(left, input, inventory, craftingService)
        );
        if (rank != 0) {
            return rank;
        }
        return Long.compare(
            inventory.getOrDefault(right.what(), 0L),
            inventory.getOrDefault(left.what(), 0L)
        );
    }

    private static int inputRank(
        GenericStack candidate,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        long required;
        try {
            required = Math.multiplyExact(candidate.amount(), input.getMultiplier());
        } catch (ArithmeticException ignored) {
            required = Long.MAX_VALUE;
        }
        long available = inventory.getOrDefault(candidate.what(), 0L);
        if (available >= required) {
            return 2;
        }
        return craftingService.getCraftingFor(candidate.what()).isEmpty() ? 0 : 1;
    }

    private static void expandVariants(
        CapturedPattern captured,
        int slot,
        List<ECOAE2InputSelection> selected,
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> target
    ) {
        List<List<ECOAE2InputSelection>> choices = captured.selections();
        if (slot == choices.size()) {
            ECOAE2PatternVariant variant = new ECOAE2PatternVariant(captured.details(), target.size(), selected);
            target.add(operationFor(variant, captured.shape()));
            return;
        }
        for (ECOAE2InputSelection choice : choices.get(slot)) {
            selected.add(choice);
            expandVariants(captured, slot + 1, selected, target);
            selected.removeLast();
        }
    }

    private static ECOPlanningOperation<AEKey, ECOAE2PatternVariant> operationFor(
        ECOAE2PatternVariant variant,
        PatternShape shape
    ) {
        Map<AEKey, Long> inputs = new LinkedHashMap<>();
        List<ECOAE2InputSelection> selectedInputs = variant.selectedInputs();
        if (shape.inputs().size() != selectedInputs.size()) {
            throw reject(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, "pattern input shape changed during capture");
        }
        for (int slot = 0; slot < shape.inputs().size(); slot++) {
            for (ECOAE2InputSelection.Alternative alternative : selectedInputs.get(slot).alternatives()) {
                long amount = Math.multiplyExact(
                    alternative.template().amount(), alternative.multiplier()
                );
                inputs.merge(alternative.template().what(), amount, Math::addExact);
            }
        }

        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        for (GenericStack output : shape.outputs()) {
            outputs.merge(output.what(), output.amount(), Math::addExact);
        }
        for (int slot = 0; slot < shape.inputs().size(); slot++) {
            InputShape input = shape.inputs().get(slot);
            for (ECOAE2InputSelection.Alternative alternative : selectedInputs.get(slot).alternatives()) {
                int choice = input.choices().indexOf(alternative.template());
                if (choice < 0) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "captured input choice was lost"
                    );
                }
                input.remainingKeys().get(choice).ifPresent(remainingKey -> outputs.merge(
                    remainingKey, alternative.multiplier(), Math::addExact
                ));
            }
        }
        return new ECOPlanningOperation<>(variant, inputs, outputs, Set.of(shape.primaryOutput().what()));
    }

    private static Map<AEKey, Long> copyInventory(IGrid grid, ICraftingSimulationRequester requester) {
        KeyCounter source;
        var actionSource = requester.getActionSource();
        if (actionSource != null && actionSource.player().isPresent()) {
            source = grid.getStorageService().getInventory().getAvailableStacks();
        } else {
            source = grid.getStorageService().getCachedInventory();
        }
        Map<AEKey, Long> inventory = new LinkedHashMap<>();
        for (var entry : source) {
            if (entry.getLongValue() > 0L) {
                inventory.put(entry.getKey(), entry.getLongValue());
            }
        }
        return inventory;
    }

    private static void retainRelevantInventory(
        Map<AEKey, Long> inventory,
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations,
        AEKey requestedKey
    ) {
        Set<AEKey> relevant = new HashSet<>();
        relevant.add(requestedKey);
        for (var operation : operations) {
            relevant.addAll(operation.inputs().keySet());
            relevant.addAll(operation.outputs().keySet());
        }
        inventory.keySet().removeIf(key -> !relevant.contains(key));
    }

    private static SnapshotRejection reject(ECOPlannerFallbackReason reason, String detail) {
        return new SnapshotRejection(reason, detail);
    }

    private record CapturedPattern(
        IPatternDetails details,
        List<List<ECOAE2InputSelection>> selections,
        PatternShape shape
    ) {
        private CapturedPattern {
            selections = selections.stream().map(List::copyOf).toList();
        }
    }

    private record PatternShape(
        Class<?> implementation,
        IECOPlannerCompatiblePattern.InputSemantics inputSemantics,
        GenericStack primaryOutput,
        List<GenericStack> outputs,
        List<InputShape> inputs
    ) {
        private PatternShape {
            outputs = List.copyOf(outputs);
            inputs = List.copyOf(inputs);
        }
    }

    private record InputShape(
        long multiplier,
        List<GenericStack> choices,
        List<Optional<AEKey>> remainingKeys
    ) {
        private InputShape {
            choices = List.copyOf(choices);
            remainingKeys = List.copyOf(remainingKeys);
        }
    }

    private record PatternGraph(
        List<CapturedPattern> patterns,
        Set<AEKey> emittableKeys,
        boolean multiplePaths,
        int materialCount
    ) {
        private PatternGraph {
            patterns = List.copyOf(patterns);
            emittableKeys = Set.copyOf(emittableKeys);
        }
    }

    private static final class SnapshotRejection extends RuntimeException {
        private final ECOPlannerFallbackReason reason;

        private SnapshotRejection(ECOPlannerFallbackReason reason, String detail) {
            super(detail);
            this.reason = reason;
        }

        private ECOPlannerFallbackReason reason() {
            return reason;
        }
    }
}
