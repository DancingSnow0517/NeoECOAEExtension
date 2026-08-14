package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerInputPolicy;
import cn.dancingsnow.neoecoae.compat.ae2.AE2LTOverloadPatternCompatibility;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Turns one immutable AE2 pattern description into exact ECO operations.
 *
 * <p>This class deliberately does not solve anything. It captures all provider callbacks once,
 * expands only finite choices, and leaves graph construction, scheduling and cycle handling to
 * the existing ECO planner.</p>
 */
final class ECOAE2PatternMaterializer {
    static final int MAX_VARIANTS_PER_PATTERN = 256;
    /**
     * A state transition is a graph edge, not another independent recipe input.
     * Bound its reachable nodes independently so unrelated alternative slots do
     * not prematurely truncate the state graph through a Cartesian-product cap.
     */
    private static final int MAX_STATE_TRANSITION_STATES_PER_SLOT = 256;
    private static final int MAX_STATEFUL_VARIANTS_PER_PATTERN =
        MAX_VARIANTS_PER_PATTERN * MAX_STATE_TRANSITION_STATES_PER_SLOT;

    private ECOAE2PatternMaterializer() {
    }

    static PatternExpansion expand(
        IPatternDetails details,
        ECOAE2PatternCompatibility.Assessment assessment,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level
    ) {
        return expand(details, assessment, inventory, craftingService, level, Set.of());
    }

    static PatternExpansion expand(
        IPatternDetails details,
        ECOAE2PatternCompatibility.Assessment assessment,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(assessment, "assessment");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(craftingService, "craftingService");
        fuzzyItemIds = Set.copyOf(Objects.requireNonNull(fuzzyItemIds, "fuzzyItemIds"));
        if (!assessment.compatible()) {
            throw reject(assessment.rejection());
        }

        IPatternDetails.IInput[] inputs = readInputs(details);
        if (inputs.length > MAX_VARIANTS_PER_PATTERN) {
            throw limit("input_slot_limit");
        }
        List<GenericStack> fixedOutputs = readOutputs(details);
        Set<AEKey> dependencyKeys = new LinkedHashSet<>();
        fixedOutputs.forEach(stack -> dependencyKeys.add(stack.what()));

        List<SlotMaterialization> slots = new ArrayList<>(inputs.length);
        boolean inventoryDependent = false;
        boolean stateful = false;
        int statefulSlots = 0;
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            if (input == null) {
                throw reject("null_input_slot slot=" + slot);
            }
            if (assessment.normalizeSubstitutionInputs()) {
                input = ECOAE2PatternCompatibility.normalizeSubstitutionInput(details, slot, input);
            }
            long multiplier = readMultiplier(input, slot);
            IECOPlannerInputPolicy.MatchMode matchMode = readMatchMode(details, slot, input);
            boolean configuredFuzzyTemplate = hasConfiguredFuzzyTemplate(input, fuzzyItemIds);
            IECOPlannerCompatiblePattern.InputSemantics inputSemantics = configuredFuzzyTemplate
                ? IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                : assessment.inputSemantics();
            List<Candidate> candidates = baseCandidates(
                details,
                input,
                assessment,
                matchMode,
                inventory,
                craftingService,
                level,
                slot,
                fuzzyItemIds
            );
            if (matchMode == IECOPlannerInputPolicy.MatchMode.ITEM_ONLY
                || assessment.includeFuzzyInventory()
                || configuredFuzzyTemplate) {
                inventoryDependent = true;
            }

            if (matchMode != IECOPlannerInputPolicy.MatchMode.ITEM_ONLY
                && !(details instanceof IECOPlannerCompatiblePattern)
                && !ECOAE2PatternCompatibility.isKnownBuiltIn(details)
                && assessment.inputSemantics()
                    == IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY) {
                rejectUndeclaredInventoryVariants(details, input, inventory, level, slot, fuzzyItemIds);
            }

            Set<AEKey> visited = new LinkedHashSet<>();
            for (Candidate candidate : candidates) {
                visited.add(candidate.template().what());
                dependencyKeys.add(candidate.template().what());
                candidate.remainingKey().ifPresent(dependencyKeys::add);
                if (candidate.hasStateTransition()) {
                    stateful = true;
                    if (!assessment.stateExpansionAllowed()) {
                        throw reject("undeclared_dynamic_input slot=" + slot);
                    }
                    if (candidate.template().amount() != 1L || multiplier != 1L) {
                        throw reject(
                            "state_transition_multiplier slot=" + slot
                                + " templateAmount=" + candidate.template().amount()
                                + " multiplier=" + multiplier
                        );
                    }
                }
            }
            if (candidates.stream().anyMatch(Candidate::hasStateTransition)) {
                statefulSlots++;
            }
            slots.add(new SlotMaterialization(input, multiplier, inputSemantics, candidates, visited));
            if (ECOPlanningFailureDiagnostics.canLogDetail(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION
            )) {
                ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
                "slot_base patternClass=" + details.getClass().getName()
                    + " slot=" + slot
                    + " matchMode=" + matchMode
                    + " inputSemantics=" + inputSemantics
                    + " multiplier=" + multiplier
                    + " candidates=" + describeCandidates(candidates)
                );
            }
        }

        if (hasStateChangingOutput(slots, fixedOutputs)) {
            throw reject("stateful_output");
        }

        int variantLimit = stateful ? MAX_STATEFUL_VARIANTS_PER_PATTERN : MAX_VARIANTS_PER_PATTERN;
        long baseCombinationCount = 1L;
        boolean useBoundedMixableBasis = false;
        for (SlotMaterialization slot : slots) {
            baseCombinationCount = saturatedMultiply(
                baseCombinationCount,
                selectionCount(slot.candidates().size(), slot.multiplier(), slot.inputSemantics())
            );
            if (baseCombinationCount > MAX_VARIANTS_PER_PATTERN
                && (useBoundedMixableBasis
                    || slot.inputSemantics()
                        == IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES)) {
                // Preserve the compact basis for freely mixable inputs. Stateful slots are
                // expanded as graph nodes below; multiplying those nodes by every allocation
                // of unrelated consumables is not part of the state model.
                useBoundedMixableBasis = true;
            } else if (baseCombinationCount > variantLimit) {
                throw limit("variant_limit ordinary_combinations=" + baseCombinationCount);
            }
        }
        if (useBoundedMixableBasis && ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
                "bounded_mixable_basis theoreticalCombinations=" + baseCombinationCount
                    + " slots=" + slots.size()
            );
        }

        boolean truncated = false;
        if (statefulSlots > 1) {
            throw reject("multiple_stateful_slots");
        }
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            SlotMaterialization slot = slots.get(slotIndex);
            if (slot.candidates().stream().noneMatch(Candidate::hasStateTransition)) {
                continue;
            }
            if (slot.multiplier() != 1L) {
                throw reject(
                    "state_transition_multiplier slot=" + slotIndex
                        + " multiplier=" + slot.multiplier()
                );
            }

            ArrayDeque<Candidate> pending = new ArrayDeque<>(slot.candidates());
            while (!pending.isEmpty()) {
                Candidate current = pending.removeFirst();
                if (!current.hasStateTransition()) {
                    continue;
                }
                Optional<AEKey> next = current.remainingKey();
                if (next.isEmpty() || slot.visited().contains(next.get())) {
                    continue;
                }
                if (slot.candidates().size() >= MAX_STATE_TRANSITION_STATES_PER_SLOT) {
                    truncated = true;
                    break;
                }
                AEKey nextKey = next.get();
                Candidate nextCandidate = captureCandidate(
                    slot.input(), new GenericStack(nextKey, 1L), level, slotIndex
                );
                slot.candidates().add(nextCandidate);
                slot.visited().add(nextKey);
                dependencyKeys.add(nextKey);
                nextCandidate.remainingKey().ifPresent(dependencyKeys::add);
                pending.addLast(nextCandidate);
            }
            if (ECOPlanningFailureDiagnostics.canLogDetail(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION
            )) {
                ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
                "slot_state_closure patternClass=" + details.getClass().getName()
                    + " slot=" + slotIndex
                    + " candidateCount=" + slot.candidates().size()
                    + " truncated=" + truncated
                    + " candidates=" + describeCandidates(slot.candidates())
                );
            }
        }

        List<List<ECOAE2InputSelection>> selections = new ArrayList<>(slots.size());
        for (SlotMaterialization slot : slots) {
            if (assessment.requireUnitMultiplierForAlternatives()
                && slot.multiplier() > 1L
                && slot.candidates().size() > 1) {
                throw reject("alternative_unit_multiplier slot=" + selections.size());
            }
            selections.add(selectionChoices(
                slot.candidates(),
                slot.multiplier(),
                slot.inputSemantics(),
                inventory,
                useBoundedMixableBasis
            ));
        }

        long variantCount = 1L;
        for (List<ECOAE2InputSelection> slot : selections) {
            variantCount = saturatedMultiply(variantCount, slot.size());
            if (variantCount > variantLimit) {
                if (stateful) {
                    truncated = true;
                    break;
                }
                throw limit("variant_limit materialized_combinations=" + variantCount);
            }
        }
        if (variantCount > variantLimit && !stateful) {
            throw limit("variant_limit materialized_combinations=" + variantCount);
        }

        Map<AEKey, Long> fixedOutputAmounts = new LinkedHashMap<>();
        for (GenericStack output : fixedOutputs) {
            mergeAmount(fixedOutputAmounts, output.what(), output.amount(), "output");
        }
        Map<AEKey, Candidate> candidatesByKey = new LinkedHashMap<>();
        for (SlotMaterialization slot : slots) {
            for (Candidate candidate : slot.candidates()) {
                Candidate previous = candidatesByKey.putIfAbsent(candidate.template().what(), candidate);
                if (previous != null && !previous.equals(candidate)) {
                    throw reject("candidate_capture_conflict key=" + candidate.template().what());
                }
            }
        }

        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = new ArrayList<>();
        enumerateVariants(
            details,
            selections,
            0,
            new ArrayList<>(),
            candidatesByKey,
            fixedOutputAmounts,
            slots,
            operations,
            variantLimit
        );
        if (operations.size() > variantLimit) {
            if (!stateful) {
                throw limit("variant_limit materialized_operations=" + operations.size());
            }
            truncated = true;
            operations = new ArrayList<>(operations.subList(0, variantLimit));
        }
        if (ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
            ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
            "pattern_expansion patternClass=" + details.getClass().getName()
                + " slots=" + slots.size()
                + " variants=" + operations.size()
                + " inventoryDependent=" + inventoryDependent
                + " stateful=" + stateful
                + " truncated=" + truncated
                + " sampleOperations=" + describeOperations(operations)
            );
        }
        return new PatternExpansion(
            List.copyOf(operations),
            Set.copyOf(dependencyKeys),
            inventoryDependent,
            stateful,
            truncated,
            stateCapacityTemplates(slots)
        );
    }

    private static IPatternDetails.IInput[] readInputs(IPatternDetails details) {
        try {
            IPatternDetails.IInput[] inputs = details.getInputs();
            if (inputs == null) {
                throw reject("pattern_returned_null_inputs");
            }
            return inputs.clone();
        } catch (PatternRejection rejection) {
            throw rejection;
        } catch (RuntimeException | LinkageError failure) {
            throw reject("input_read_exception=" + failure.getClass().getSimpleName(), failure);
        }
    }

    private static List<GenericStack> readOutputs(IPatternDetails details) {
        try {
            GenericStack primary = details.getPrimaryOutput();
            List<GenericStack> outputs = details.getOutputs();
            if (primary == null || primary.amount() <= 0L || outputs == null || outputs.isEmpty()) {
                throw reject("missing_primary_or_outputs");
            }
            List<GenericStack> copy = new ArrayList<>(outputs.size());
            boolean containsPrimary = false;
            for (GenericStack output : outputs) {
                if (output == null || output.amount() <= 0L) {
                    throw reject("invalid_output");
                }
                copy.add(output);
                containsPrimary |= output.what().equals(primary.what());
            }
            if (!containsPrimary) {
                throw reject("primary_output_not_in_outputs");
            }
            return List.copyOf(copy);
        } catch (PatternRejection rejection) {
            throw rejection;
        } catch (RuntimeException | LinkageError failure) {
            throw reject("output_read_exception=" + failure.getClass().getSimpleName(), failure);
        }
    }

    private static long readMultiplier(IPatternDetails.IInput input, int slot) {
        try {
            long multiplier = input.getMultiplier();
            if (multiplier <= 0L) {
                throw reject("invalid_input_multiplier slot=" + slot + " value=" + multiplier);
            }
            return multiplier;
        } catch (PatternRejection rejection) {
            throw rejection;
        } catch (RuntimeException | LinkageError failure) {
            throw reject("multiplier_read_exception slot=" + slot, failure);
        }
    }

    private static IECOPlannerInputPolicy.MatchMode readMatchMode(
        IPatternDetails details,
        int slot,
        IPatternDetails.IInput input
    ) {
        if (details instanceof IECOPlannerInputPolicy policy) {
            try {
                return Objects.requireNonNull(
                    policy.getPlannerInputMatchMode(slot, input),
                    "planner input match mode"
                );
            } catch (RuntimeException | LinkageError failure) {
                throw reject("input_policy_exception slot=" + slot, failure);
            }
        }
        return AE2LTOverloadPatternCompatibility.ignoresComponents(details, slot)
            ? IECOPlannerInputPolicy.MatchMode.ITEM_ONLY
            : IECOPlannerInputPolicy.MatchMode.STRICT;
    }

    private static List<Candidate> baseCandidates(
        IPatternDetails details,
        IPatternDetails.IInput input,
        ECOAE2PatternCompatibility.Assessment assessment,
        IECOPlannerInputPolicy.MatchMode matchMode,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level,
        int slot,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        GenericStack[] possible;
        try {
            possible = input.getPossibleInputs();
        } catch (RuntimeException | LinkageError failure) {
            throw reject("possible_inputs_exception slot=" + slot, failure);
        }
        if (possible == null) {
            throw reject("null_possible_inputs slot=" + slot);
        }

        Map<AEKey, Long> choices = new LinkedHashMap<>();
        boolean canonicalOnly = assessment.inputSemantics()
            == IECOPlannerCompatiblePattern.InputSemantics.CANONICAL_ONLY;
        for (GenericStack candidate : possible) {
            if (candidate == null || candidate.amount() <= 0L) {
                continue;
            }
            if (!isValid(input, candidate.what(), level, slot)) {
                if (canonicalOnly) {
                    continue;
                }
                continue;
            }
            if (canonicalOnly) {
                putChoice(choices, candidate.what(), candidate.amount());
                break;
            }
            putChoice(choices, candidate.what(), candidate.amount());
        }
        if (choices.isEmpty()) {
            throw reject("input_has_no_valid_choice slot=" + slot);
        }

        if (matchMode == IECOPlannerInputPolicy.MatchMode.ITEM_ONLY) {
            addItemOnlyInventoryVariants(choices, input, inventory, level, slot);
        } else if (hasConfiguredFuzzyTemplate(input, fuzzyItemIds)) {
            addConfiguredFuzzyInventoryVariants(choices, inventory, fuzzyItemIds);
        } else if (assessment.includeFuzzyInventory()) {
            addFuzzyCraftableVariants(choices, input, craftingService, level, slot);
            addFuzzyInventoryVariants(choices, input, inventory, level, slot);
        }

        List<Candidate> result = new ArrayList<>(choices.size());
        for (var entry : choices.entrySet()) {
            result.add(captureCandidate(
                input,
                new GenericStack(entry.getKey(), entry.getValue()),
                level,
                slot
            ));
        }
        return result;
    }

    private static void rejectUndeclaredInventoryVariants(
        IPatternDetails details,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level,
        int slot,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        GenericStack[] possible;
        try {
            possible = input.getPossibleInputs();
        } catch (RuntimeException | LinkageError failure) {
            throw reject("possible_inputs_exception slot=" + slot, failure);
        }
        GenericStack canonical = null;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.amount() > 0L
                && isValid(input, candidate.what(), level, slot)) {
                canonical = candidate;
                break;
            }
        }
        if (canonical == null) {
            return;
        }
        for (AEKey key : inventory.keySet()) {
            if (key.equals(canonical.what())
                || !Objects.equals(key.getPrimaryKey(), canonical.what().getPrimaryKey())) {
                continue;
            }
            if (isConfiguredFuzzyItem(canonical.what(), fuzzyItemIds)) {
                continue;
            }
            if (isValid(input, key, level, slot)) {
                throw reject(
                    "undeclared_dynamic_input pattern=" + details.getClass().getName()
                        + " slot=" + slot
                        + " key=" + key
                );
            }
        }
    }

    private static void addItemOnlyInventoryVariants(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level,
        int slot
    ) {
        Map<Object, Long> itemAmounts = new LinkedHashMap<>();
        for (var entry : choices.entrySet()) {
            Object identity = entry.getKey().getPrimaryKey();
            Long previous = itemAmounts.putIfAbsent(identity, entry.getValue());
            if (previous != null && previous.longValue() != entry.getValue()) {
                throw reject("candidate_template_amount_conflict slot=" + slot);
            }
        }
        for (AEKey key : inventory.keySet()) {
            Long amount = itemAmounts.get(key.getPrimaryKey());
            if (amount != null && isValid(input, key, level, slot)) {
                putChoice(choices, key, amount);
            }
        }
    }

    private static void addFuzzyInventoryVariants(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level,
        int slot
    ) {
        List<Map.Entry<AEKey, Long>> templates = List.copyOf(choices.entrySet());
        for (AEKey key : inventory.keySet()) {
            for (var template : templates) {
                if (fuzzyEquals(template.getKey(), key, slot)
                    && isValid(input, key, level, slot)) {
                    putChoice(choices, key, template.getValue());
                    break;
                }
            }
        }
    }

    private static void addFuzzyCraftableVariants(
        Map<AEKey, Long> choices,
        IPatternDetails.IInput input,
        ICraftingService craftingService,
        Level level,
        int slot
    ) {
        for (var template : List.copyOf(choices.entrySet())) {
            AEKey craftable;
            try {
                craftable = craftingService.getFuzzyCraftable(
                    template.getKey(), candidate -> isValid(input, candidate, level, slot)
                );
            } catch (RuntimeException | LinkageError failure) {
                throw reject("fuzzy_craftable_exception slot=" + slot, failure);
            }
            if (craftable != null && isValid(input, craftable, level, slot)) {
                putChoice(choices, craftable, template.getValue());
            }
        }
    }

    private static boolean fuzzyEquals(AEKey template, AEKey candidate, int slot) {
        try {
            return template.fuzzyEquals(candidate, FuzzyMode.IGNORE_ALL);
        } catch (RuntimeException | LinkageError failure) {
            throw reject("fuzzy_match_exception slot=" + slot, failure);
        }
    }

    private static Candidate captureCandidate(
        IPatternDetails.IInput input,
        GenericStack template,
        Level level,
        int slot
    ) {
        if (template == null || template.amount() <= 0L) {
            throw reject("invalid_candidate slot=" + slot);
        }
        AEKey remaining;
        try {
            remaining = input.getRemainingKey(template.what());
        } catch (RuntimeException | LinkageError failure) {
            throw reject("remaining_key_exception slot=" + slot, failure);
        }
        Optional<AEKey> remainingKey = remaining == null ? Optional.empty() : Optional.of(remaining);
        boolean stateTransition = remainingKey.isPresent()
            && !remainingKey.get().equals(template.what())
            && isValid(input, remainingKey.get(), level, slot);
        return new Candidate(template, remainingKey, stateTransition);
    }

    private static boolean isValid(
        IPatternDetails.IInput input,
        AEKey key,
        Level level,
        int slot
    ) {
        try {
            return input.isValid(key, level);
        } catch (RuntimeException | LinkageError failure) {
            throw reject("input_validation_exception slot=" + slot + " key=" + key, failure);
        }
    }

    private static void putChoice(Map<AEKey, Long> choices, AEKey key, long amount) {
        if (key == null) {
            throw reject("candidate_key_null");
        }
        Long previous = choices.putIfAbsent(key, amount);
        if (previous != null && previous.longValue() != amount) {
            throw reject("candidate_template_amount_conflict key=" + key);
        }
    }

    private static long selectionCount(
        long candidateCount,
        long multiplier,
        IECOPlannerCompatiblePattern.InputSemantics semantics
    ) {
        if (candidateCount <= 0L) {
            return 0L;
        }
        if (semantics != IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES) {
            return candidateCount;
        }
        long result = 1L;
        for (long i = 1L; i <= candidateCount - 1L; i++) {
            long numerator;
            try {
                numerator = Math.addExact(multiplier, i);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
            long product = saturatedMultiply(result, numerator);
            if (product == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            result = product;
            result = result / i;
            if (result > MAX_VARIANTS_PER_PATTERN) {
                return result;
            }
        }
        return result;
    }

    private static List<ECOAE2InputSelection> selectionChoices(
        List<Candidate> candidates,
        long multiplier,
        IECOPlannerCompatiblePattern.InputSemantics semantics,
        Map<AEKey, Long> inventory,
        boolean useBoundedMixableBasis
    ) {
        if (semantics != IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES) {
            return candidates.stream()
                .map(candidate -> ECOAE2InputSelection.single(candidate.template(), multiplier))
                .toList();
        }
        if (useBoundedMixableBasis) {
            return boundedMixableBasis(candidates, multiplier, inventory);
        }
        List<GenericStack> templates = candidates.stream().map(Candidate::template).toList();
        List<ECOAE2InputSelection> result = new ArrayList<>();
        enumerateMixedSelections(templates, 0, multiplier, new ArrayList<>(), result);
        return List.copyOf(result);
    }

    /**
     * A bounded exact basis for a mixable slot whose theoretical integer allocations exceed the
     * variant limit. Uniform selections retain every source/craftable route. Additional selections
     * pack the current inventory remainders, which is the only case that requires mixing within a
     * single execution; full groups are already executable through the uniform selections.
     */
    private static List<ECOAE2InputSelection> boundedMixableBasis(
        List<Candidate> candidates,
        long multiplier,
        Map<AEKey, Long> inventory
    ) {
        Set<ECOAE2InputSelection> result = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            result.add(ECOAE2InputSelection.single(candidate.template(), multiplier));
        }

        List<ECOAE2InputSelection.Alternative> packed = new ArrayList<>();
        long needed = multiplier;
        for (Candidate candidate : candidates) {
            GenericStack template = candidate.template();
            long availableUnits = inventory.getOrDefault(template.what(), 0L) / template.amount();
            long remainder = availableUnits % multiplier;
            while (remainder > 0L) {
                long selected = Math.min(remainder, needed);
                packed.add(new ECOAE2InputSelection.Alternative(template, selected));
                remainder -= selected;
                needed -= selected;
                if (needed == 0L) {
                    result.add(new ECOAE2InputSelection(packed));
                    packed = new ArrayList<>();
                    needed = multiplier;
                }
            }
        }
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

    private static void enumerateVariants(
        IPatternDetails details,
        List<List<ECOAE2InputSelection>> selections,
        int slot,
        List<ECOAE2InputSelection> selected,
        Map<AEKey, Candidate> candidatesByKey,
        Map<AEKey, Long> fixedOutputs,
        List<SlotMaterialization> slots,
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations,
        int variantLimit
    ) {
        if (operations.size() >= variantLimit) {
            return;
        }
        if (slot == selections.size()) {
            ECOAE2PatternVariant variant = new ECOAE2PatternVariant(
                details,
                operations.size(),
                selected
            );
            operations.add(operationFor(variant, candidatesByKey, fixedOutputs, slots));
            return;
        }
        for (ECOAE2InputSelection choice : selections.get(slot)) {
            selected.add(choice);
            enumerateVariants(
                details,
                selections,
                slot + 1,
                selected,
                candidatesByKey,
                fixedOutputs,
                slots,
                operations,
                variantLimit
            );
            selected.removeLast();
            if (operations.size() >= variantLimit) {
                return;
            }
        }
    }

    private static ECOPlanningOperation<AEKey, ECOAE2PatternVariant> operationFor(
        ECOAE2PatternVariant variant,
        Map<AEKey, Candidate> candidatesByKey,
        Map<AEKey, Long> fixedOutputs,
        List<SlotMaterialization> slots
    ) {
        try {
            Map<AEKey, Long> inputs = new LinkedHashMap<>();
            Map<AEKey, Long> outputs = new LinkedHashMap<>(fixedOutputs);
            Set<AEKey> stateTransitions = new LinkedHashSet<>();
            for (int slot = 0; slot < slots.size(); slot++) {
                ECOAE2InputSelection selection = variant.selectedInputs().get(slot);
                for (ECOAE2InputSelection.Alternative alternative : selection.alternatives()) {
                    GenericStack template = alternative.template();
                    long amount = Math.multiplyExact(template.amount(), alternative.multiplier());
                    inputs.merge(template.what(), amount, Math::addExact);
                    Candidate candidate = candidatesByKey.get(template.what());
                    if (candidate == null) {
                        throw reject("candidate_capture_missing key=" + template.what());
                    }
                    candidate.remainingKey().ifPresent(remaining -> {
                        outputs.merge(remaining, alternative.multiplier(), Math::addExact);
                        if (candidate.hasStateTransition()) {
                            stateTransitions.add(template.what());
                        }
                    });
                }
            }
            return new ECOPlanningOperation<>(variant, inputs, outputs, outputs.keySet(), stateTransitions);
        } catch (PatternRejection rejection) {
            throw rejection;
        } catch (ArithmeticException overflow) {
            throw reject("operation_amount_overflow", overflow);
        }
    }

    private static void mergeAmount(Map<AEKey, Long> amounts, AEKey key, long amount, String kind) {
        try {
            amounts.merge(key, amount, Math::addExact);
        } catch (ArithmeticException overflow) {
            throw reject(kind + "_amount_overflow key=" + key, overflow);
        }
    }

    /**
     * An interface-configured fuzzy item deliberately ignores all data components. This is limited
     * to keys whose item id is present in the computation interface, so ordinary item inputs stay
     * exact and no provider-specific state leaks into unrelated patterns.
     */
    private static void addConfiguredFuzzyInventoryVariants(
        Map<AEKey, Long> choices,
        Map<AEKey, Long> inventory,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        Map<ResourceLocation, Long> itemAmounts = new LinkedHashMap<>();
        for (var entry : choices.entrySet()) {
            if (!isConfiguredFuzzyItem(entry.getKey(), fuzzyItemIds)) {
                continue;
            }
            Long previous = itemAmounts.putIfAbsent(entry.getKey().getId(), entry.getValue());
            if (previous != null && previous.longValue() != entry.getValue()) {
                throw reject("candidate_template_amount_conflict key=" + entry.getKey());
            }
        }
        for (AEKey key : inventory.keySet()) {
            Long amount = key.getType().equals(AEKeyType.items()) ? itemAmounts.get(key.getId()) : null;
            if (amount != null) {
                putChoice(choices, key, amount);
            }
        }
    }

    private static boolean hasConfiguredFuzzyTemplate(
        IPatternDetails.IInput input,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        if (fuzzyItemIds.isEmpty()) {
            return false;
        }
        GenericStack[] possible;
        try {
            possible = input.getPossibleInputs();
        } catch (RuntimeException | LinkageError failure) {
            throw reject("possible_inputs_exception", failure);
        }
        if (possible == null) {
            throw reject("null_possible_inputs");
        }
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.amount() > 0L
                && isConfiguredFuzzyItem(candidate.what(), fuzzyItemIds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfiguredFuzzyItem(AEKey key, Set<ResourceLocation> fuzzyItemIds) {
        return key.getType().equals(AEKeyType.items()) && fuzzyItemIds.contains(key.getId());
    }

    private static List<ECOAE2StateCapacityTemplate> stateCapacityTemplates(
        List<SlotMaterialization> slots
    ) {
        List<ECOAE2StateCapacityTemplate> templates = new ArrayList<>();
        for (SlotMaterialization slot : slots) {
            Map<AEKey, AEKey> transitions = new LinkedHashMap<>();
            for (Candidate candidate : slot.candidates()) {
                if (candidate.hasStateTransition()) {
                    candidate.remainingKey().ifPresent(next -> transitions.put(candidate.template().what(), next));
                }
            }
            if (!transitions.isEmpty()) {
                templates.add(new ECOAE2StateCapacityTemplate(transitions));
            }
        }
        return List.copyOf(templates);
    }

    private static boolean hasStateChangingOutput(
        List<SlotMaterialization> slots,
        List<GenericStack> outputs
    ) {
        for (SlotMaterialization slot : slots) {
            for (Candidate candidate : slot.candidates()) {
                AEKey input = candidate.template().what();
                if (!input.getType().equals(AEKeyType.items())) {
                    continue;
                }
                for (GenericStack output : outputs) {
                    AEKey produced = output.what();
                    if (produced.getType().equals(AEKeyType.items())
                        && input.getPrimaryKey().equals(produced.getPrimaryKey())
                        && !input.equals(produced)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String describeCandidates(List<Candidate> candidates) {
        List<String> descriptions = new ArrayList<>(Math.min(candidates.size(), 12));
        for (Candidate candidate : candidates) {
            descriptions.add(
                "{key=" + candidate.template().what()
                    + ",amount=" + candidate.template().amount()
                    + ",remaining=" + candidate.remainingKey().map(Object::toString).orElse("consumed")
                    + ",transition=" + candidate.hasStateTransition() + "}"
            );
        }
        return ECOPlanningFailureDiagnostics.describeIterable(descriptions, candidates.size());
    }

    private static String describeOperations(
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations
    ) {
        List<String> descriptions = new ArrayList<>(Math.min(operations.size(), 12));
        for (var operation : operations) {
            descriptions.add(
                "{ordinal=" + operation.reference().ordinal()
                    + ",inputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.inputs())
                    + ",outputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.outputs())
                    + ",stateTransitionInputs=" + operation.stateTransitionInputs() + "}"
            );
        }
        return ECOPlanningFailureDiagnostics.describeIterable(descriptions, operations.size());
    }

    private static PatternRejection limit(String context) {
        return new PatternRejection(ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED, context);
    }

    private static PatternRejection reject(String context) {
        return new PatternRejection(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, context);
    }

    private static PatternRejection reject(String context, Throwable cause) {
        return new PatternRejection(ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE, context, cause);
    }

    record Candidate(GenericStack template, Optional<AEKey> remainingKey, boolean stateTransition) {
        Candidate {
            Objects.requireNonNull(template, "template");
            remainingKey = Objects.requireNonNull(remainingKey, "remainingKey");
        }

        boolean hasStateTransition() {
            return stateTransition;
        }
    }

    private static final class SlotMaterialization {
        private final IPatternDetails.IInput input;
        private final long multiplier;
        private final IECOPlannerCompatiblePattern.InputSemantics inputSemantics;
        private final List<Candidate> candidates;
        private final Set<AEKey> visited;

        private SlotMaterialization(
            IPatternDetails.IInput input,
            long multiplier,
            IECOPlannerCompatiblePattern.InputSemantics inputSemantics,
            List<Candidate> candidates,
            Set<AEKey> visited
        ) {
            this.input = input;
            this.multiplier = multiplier;
            this.inputSemantics = inputSemantics;
            this.candidates = candidates;
            this.visited = visited;
        }

        private IPatternDetails.IInput input() {
            return input;
        }

        private long multiplier() {
            return multiplier;
        }

        private IECOPlannerCompatiblePattern.InputSemantics inputSemantics() {
            return inputSemantics;
        }

        private List<Candidate> candidates() {
            return candidates;
        }

        private Set<AEKey> visited() {
            return visited;
        }
    }

    static final class PatternRejection extends RuntimeException {
        private final ECOPlannerFallbackReason reason;

        private PatternRejection(ECOPlannerFallbackReason reason, String context) {
            super(context);
            this.reason = reason;
        }

        private PatternRejection(ECOPlannerFallbackReason reason, String context, Throwable cause) {
            super(context, cause);
            this.reason = reason;
        }

        ECOPlannerFallbackReason reason() {
            return reason;
        }

        String context() {
            return getMessage();
        }

        PatternRejection withContext(String outerContext) {
            return new PatternRejection(
                reason,
                outerContext + " context=" + context(),
                this
            );
        }
    }

    public static record PatternExpansion(
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations,
        Set<AEKey> dependencyKeys,
        boolean inventoryDependent,
        boolean stateful,
        boolean truncatedStateExpansion,
        List<ECOAE2StateCapacityTemplate> stateCapacityTemplates
    ) {
        public PatternExpansion {
            operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
            dependencyKeys = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(dependencyKeys, "dependencyKeys"))
            );
            stateCapacityTemplates = List.copyOf(Objects.requireNonNull(
                stateCapacityTemplates, "stateCapacityTemplates"
            ));
        }
    }
}
