package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures the immutable AE2 input view consumed by the ECO planning worker. */
public final class ECOAE2SnapshotFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_MATERIALS = 16_384;
    private static final int MAX_OPERATIONS = 65_536;
    private static final long NO_GENERATION = Long.MIN_VALUE;
    private static final Map<ICraftingService, CachedGraphs> GRAPH_CACHE = new WeakHashMap<>();

    private ECOAE2SnapshotFactory() {
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy
    ) {
        return capture(grid, requester, requestedKey, requestedAmount, strategy, NO_GENERATION);
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration
    ) {
        if (requestedAmount <= 0
            || (strategy != CalculationStrategy.REPORT_MISSING_ITEMS
                && strategy != CalculationStrategy.CRAFT_LESS)) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
                ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                requestedKey,
                requestedAmount,
                strategy,
                "invalid_request_or_unsupported_strategy"
            );
            return Optional.empty();
        }
        try {
            Map<AEKey, Long> inventory = copyInventory(grid, requester);

            var craftingService = grid.getCraftingService();
            Optional<PatternGraph> graph = graphFor(craftingService, requestedKey, craftableGeneration);
            if (graph.isEmpty()) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "graph_empty generation=" + craftableGeneration
                );
                return Optional.empty();
            }

            List<ECOPlanningOperation<AEKey, IPatternDetails>> operations = materialize(
                graph.get(),
                inventory,
                craftingService
            );

            // Stored copies of the requested output must not short-circuit a normal
            // request, but they are valid seed material for self-increasing patterns.
            boolean requestedIsInput = operations.stream()
                .anyMatch(operation -> operation.inputs().containsKey(requestedKey));
            if (!requestedIsInput) {
                inventory.remove(requestedKey);
            }

            var problem = new ECOPlanningProblem<>(
                operations,
                inventory,
                Map.of(requestedKey, requestedAmount)
            );
            return Optional.of(new ECOAE2PlanningSnapshot(
                problem,
                requestedKey,
                requestedAmount,
                graph.get().multiplePaths(),
                graph.get().inputSlotCounts()
            ));
        } catch (RuntimeException | LinkageError failure) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
                ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                requestedKey,
                requestedAmount,
                strategy,
                "snapshot_capture_exception",
                failure
            );
            LOGGER.debug("ECO AE2 snapshot capture failed; the caller will use AE2 crafting calculation", failure);
            return Optional.empty();
        }
    }

    private static Optional<PatternGraph> graphFor(
        ICraftingService craftingService,
        AEKey requestedKey,
        long craftableGeneration
    ) {
        if (craftableGeneration == NO_GENERATION) {
            return buildGraph(craftingService, requestedKey);
        }
        synchronized (GRAPH_CACHE) {
            CachedGraphs cached = GRAPH_CACHE.get(craftingService);
            if (cached == null || cached.generation() != craftableGeneration) {
                cached = new CachedGraphs(craftableGeneration, new LinkedHashMap<>());
                GRAPH_CACHE.put(craftingService, cached);
            }
            return cached.graphs().computeIfAbsent(
                requestedKey,
                ignored -> buildGraph(craftingService, requestedKey)
            );
        }
    }

    private static Optional<PatternGraph> buildGraph(
        ICraftingService craftingService,
        AEKey requestedKey
    ) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        Set<AEKey> visitedMaterials = new HashSet<>();
        Set<AEItemKey> visitedPatterns = new HashSet<>();
        Map<AEItemKey, IPatternDetails> canonicalPatterns = new LinkedHashMap<>();
        List<IPatternDetails> patterns = new ArrayList<>();
        Map<IPatternDetails, Integer> inputSlotCounts = new LinkedHashMap<>();
        boolean multiplePaths = false;
        pending.add(requestedKey);

        while (!pending.isEmpty()) {
            AEKey material = pending.removeFirst();
            if (!visitedMaterials.add(material)) {
                continue;
            }
            if (visitedMaterials.size() > MAX_MATERIALS) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                    requestedKey,
                    0L,
                    "graph",
                    "material_limit=" + MAX_MATERIALS
                        + " visitedMaterials=" + visitedMaterials.size()
                );
                return Optional.empty();
            }
            var producers = List.copyOf(craftingService.getCraftingFor(material));
            Set<AEItemKey> logicalProducerIdentities = new HashSet<>();
            for (IPatternDetails details : producers) {
                AEItemKey logicalIdentity = details.getDefinition();
                if (logicalIdentity == null) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.GRAPH,
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        requestedKey,
                        0L,
                        "graph",
                        "pattern_definition_missing material=" + material
                    );
                    return Optional.empty();
                }
                logicalProducerIdentities.add(logicalIdentity);
                IPatternDetails canonical = canonicalPatterns.computeIfAbsent(logicalIdentity, ignored -> details);
                if (!visitedPatterns.add(logicalIdentity)) {
                    continue;
                }
                if (!inspect(canonical, pending, requestedKey)) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.GRAPH,
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        requestedKey,
                        0L,
                        "graph",
                        "pattern_inspection_failed material=" + material
                            + " pattern=" + logicalIdentity
                    );
                    return Optional.empty();
                }
                patterns.add(canonical);
                multiplePaths |= hasAlternativeInput(canonical);
                inputSlotCounts.put(canonical, canonical.getInputs().length);
                if (patterns.size() > MAX_OPERATIONS) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.GRAPH,
                        ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                        requestedKey,
                        0L,
                        "graph",
                        "graph operation_limit=" + MAX_OPERATIONS
                            + " patterns=" + patterns.size()
                    );
                    return Optional.empty();
                }
            }
            multiplePaths |= logicalProducerIdentities.size() > 1;
        }
        return Optional.of(new PatternGraph(patterns, inputSlotCounts, multiplePaths));
    }

    private static boolean inspect(
        IPatternDetails details,
        ArrayDeque<AEKey> pending,
        AEKey requestedKey
    ) {
        if (details.getPrimaryOutput() == null || details.getOutputs().isEmpty()) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.GRAPH,
                ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                requestedKey,
                0L,
                "pattern_inspection",
                "missing_primary_or_all_outputs definition=" + details.getDefinition()
            );
            return false;
        }
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    requestedKey,
                    0L,
                    "pattern_inspection",
                    "invalid_output definition=" + details.getDefinition()
                );
                return false;
            }
        }
        for (IPatternDetails.IInput input : details.getInputs()) {
            if (input.getMultiplier() <= 0) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    requestedKey,
                    0L,
                    "pattern_inspection",
                    "invalid_input_multiplier=" + input.getMultiplier()
                        + " definition=" + details.getDefinition()
                );
                return false;
            }
            GenericStack[] choices = input.getPossibleInputs();
            boolean hasChoice = false;
            for (GenericStack choice : choices) {
                if (choice != null && choice.amount() > 0) {
                    hasChoice = true;
                    pending.addLast(choice.what());
                }
            }
            if (!hasChoice) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    requestedKey,
                    0L,
                    "pattern_inspection",
                    "input_has_no_valid_choice definition=" + details.getDefinition()
                );
                return false;
            }
        }
        return true;
    }

    private static boolean hasAlternativeInput(IPatternDetails details) {
        for (IPatternDetails.IInput input : details.getInputs()) {
            int choices = 0;
            for (GenericStack choice : input.getPossibleInputs()) {
                if (choice != null && choice.amount() > 0 && ++choices > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ECOPlanningOperation<AEKey, IPatternDetails>> materialize(
        PatternGraph graph,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        List<ECOPlanningOperation<AEKey, IPatternDetails>> operations = new ArrayList<>(graph.patterns().size());
        for (IPatternDetails details : graph.patterns()) {
            var operation = convert(details, inventory, craftingService).orElse(null);
            if (operation == null) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    details.getPrimaryOutput() == null ? null : details.getPrimaryOutput().what(),
                    0L,
                    "materialize",
                    "pattern_conversion_returned_empty definition=" + details.getDefinition()
                );
                throw new IllegalArgumentException("ECO pattern conversion returned empty");
            }
            operations.add(operation);
        }
        return List.copyOf(operations);
    }

    /** Compatibility helper retained for the input-selection execution tests. */
    static List<List<ECOAE2InputSelection>> inputSelections(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        List<List<GenericStack>> choices,
        IECOPlannerCompatiblePattern.InputSemantics semantics
    ) {
        List<List<ECOAE2InputSelection>> result = new ArrayList<>(inputs.length);
        for (int slot = 0; slot < inputs.length; slot++) {
            long multiplier = inputs[slot].getMultiplier();
            List<GenericStack> slotChoices = choices.get(slot);
            result.add(semantics == IECOPlannerCompatiblePattern.InputSemantics.MIXABLE_ALTERNATIVES
                ? mixedSelections(slotChoices, multiplier)
                : slotChoices.stream()
                    .map(choice -> ECOAE2InputSelection.single(choice, multiplier))
                    .toList());
        }
        return List.copyOf(result);
    }

    private static List<ECOAE2InputSelection> mixedSelections(
        List<GenericStack> choices,
        long multiplier
    ) {
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
            if (entry.getLongValue() > 0) {
                inventory.put(entry.getKey(), entry.getLongValue());
            }
        }
        return inventory;
    }

    private static Optional<ECOPlanningOperation<AEKey, IPatternDetails>> convert(
        IPatternDetails details,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        GenericStack primaryOutput = details.getPrimaryOutput();
        if (primaryOutput == null) {
            return Optional.empty();
        }
        Map<AEKey, Long> inputs = new LinkedHashMap<>();
        List<GenericStack> selectedInputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack selected = selectInput(input, inventory, craftingService);
            if (selected == null || selected.amount() <= 0 || input.getMultiplier() <= 0) {
                return Optional.empty();
            }
            selectedInputs.add(selected);
            long multiplier = input.getMultiplier();
            long amount = Math.multiplyExact(selected.amount(), multiplier);
            inputs.merge(selected.what(), amount, Math::addExact);
        }

        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                return Optional.empty();
            }
            outputs.merge(output.what(), output.amount(), Math::addExact);
        }
        for (int i = 0; i < details.getInputs().length; i++) {
            IPatternDetails.IInput input = details.getInputs()[i];
            GenericStack selected = selectedInputs.get(i);
            if (selected == null) {
                return Optional.empty();
            }
            AEKey remainingKey = input.getRemainingKey(selected.what());
            if (remainingKey != null) {
                outputs.merge(remainingKey, input.getMultiplier(), Math::addExact);
            }
        }
        if (outputs.isEmpty()) {
            return Optional.empty();
        }
        // A processing pattern may expose useful secondary outputs. Keep all of
        // them selectable so a dependency can be satisfied by the same execution.
        return Optional.of(new ECOPlanningOperation<>(details, inputs, outputs));
    }

    private static GenericStack selectInput(
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        GenericStack selected = null;
        long selectedInventory = Long.MIN_VALUE;
        boolean selectedCraftable = false;
        int selectedRank = Integer.MIN_VALUE;
        long multiplier = Math.max(1L, input.getMultiplier());
        for (GenericStack candidate : input.getPossibleInputs()) {
            if (candidate == null || candidate.amount() <= 0) {
                continue;
            }
            long available = inventory.getOrDefault(candidate.what(), 0L);
            boolean craftable = !craftingService.getCraftingFor(candidate.what()).isEmpty();
            long required;
            try {
                required = Math.multiplyExact(candidate.amount(), multiplier);
            } catch (ArithmeticException ignored) {
                required = Long.MAX_VALUE;
            }
            int rank = available >= required ? 2 : craftable ? 1 : 0;
            if (selected == null
                || rank > selectedRank
                || (rank == selectedRank && available > selectedInventory)
                || (rank == selectedRank && available == selectedInventory && craftable && !selectedCraftable)) {
                selected = candidate;
                selectedInventory = available;
                selectedCraftable = craftable;
                selectedRank = rank;
            }
        }
        return selected;
    }

    private record PatternGraph(
        List<IPatternDetails> patterns,
        Map<IPatternDetails, Integer> inputSlotCounts,
        boolean multiplePaths
    ) {
        private PatternGraph {
            patterns = List.copyOf(patterns);
            inputSlotCounts = Map.copyOf(inputSlotCounts);
        }
    }

    private record CachedGraphs(
        long generation,
        Map<AEKey, Optional<PatternGraph>> graphs
    ) {
    }
}
