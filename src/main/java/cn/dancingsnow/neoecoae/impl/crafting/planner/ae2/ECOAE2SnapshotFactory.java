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
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerInputPolicy;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerNoticeDispatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures the immutable AE2 input view consumed by the ECO planning worker. */
public final class ECOAE2SnapshotFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int MAX_MATERIALS = 16_384;
    private static final int MAX_OPERATIONS = 65_536;
    private static final int MAX_VARIANTS_PER_PATTERN = 1_024;
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
        return capture(grid, requester, requestedKey, requestedAmount, strategy, NO_GENERATION, null);
    }

    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration
    ) {
        return capture(grid, requester, requestedKey, requestedAmount, strategy, craftableGeneration, null);
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
        ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_STARTED);
        if (requestedAmount <= 0
            || (strategy != CalculationStrategy.REPORT_MISSING_ITEMS
                && strategy != CalculationStrategy.CRAFT_LESS)) {
            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.UNSUPPORTED_REQUEST);
            return Optional.empty();
        }
        try {
            ECOPlannerNoticeDispatcher.Target noticeTarget = ECOPlannerNoticeDispatcher.targetFor(requester);
            Map<AEKey, Long> inventory = copyInventory(grid, requester);

            var craftingService = grid.getCraftingService();
            Optional<PatternGraph> graph = graphFor(craftingService, requestedKey, craftableGeneration);
            if (graph.isEmpty()) {
                ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_REJECTED);
                return Optional.empty();
            }

            List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = materialize(
                graph.get(),
                inventory,
                craftingService,
                level
            );
            Map<ECOAE2PatternVariant, Integer> inputSlotCounts = new LinkedHashMap<>();
            for (var operation : operations) {
                inputSlotCounts.put(operation.reference(), operation.reference().selectedInputs().size());
            }

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
                inputSlotCounts,
                noticeTarget
            ));
        } catch (RuntimeException | LinkageError failure) {
            ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.SNAPSHOT_REJECTED);
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
                return Optional.empty();
            }
            var producers = List.copyOf(craftingService.getCraftingFor(material));
            Set<AEItemKey> logicalProducerIdentities = new HashSet<>();
            for (IPatternDetails details : producers) {
                AEItemKey logicalIdentity = details.getDefinition();
                if (logicalIdentity == null) {
                    return Optional.empty();
                }
                logicalProducerIdentities.add(logicalIdentity);
                IPatternDetails canonical = canonicalPatterns.computeIfAbsent(logicalIdentity, ignored -> details);
                if (!visitedPatterns.add(logicalIdentity)) {
                    continue;
                }
                if (!inspect(canonical, pending)) {
                    return Optional.empty();
                }
                patterns.add(canonical);
                multiplePaths |= hasAlternativeInput(canonical);
                inputSlotCounts.put(canonical, canonical.getInputs().length);
                if (patterns.size() > MAX_OPERATIONS) {
                    return Optional.empty();
                }
            }
            multiplePaths |= logicalProducerIdentities.size() > 1;
        }
        return Optional.of(new PatternGraph(patterns, inputSlotCounts, multiplePaths));
    }

    private static boolean inspect(IPatternDetails details, ArrayDeque<AEKey> pending) {
        if (details.getPrimaryOutput() == null || details.getOutputs().isEmpty()) {
            return false;
        }
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                return false;
            }
        }
        for (IPatternDetails.IInput input : details.getInputs()) {
            if (input.getMultiplier() <= 0) {
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

    private static List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> materialize(
        PatternGraph graph,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level
    ) {
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = new ArrayList<>();
        for (IPatternDetails details : graph.patterns()) {
            List<List<GenericStack>> choices = orderedChoices(details, inventory, craftingService, level);
            long variantCount = 1L;
            for (List<GenericStack> slot : choices) {
                variantCount = Math.multiplyExact(variantCount, slot.size());
                if (variantCount > MAX_VARIANTS_PER_PATTERN
                    || operations.size() + variantCount > MAX_OPERATIONS) {
                    ECOPlanningDiagnostics.record(ECOPlanningDiagnostics.Outcome.VARIANT_LIMIT_REJECTED);
                    throw new IllegalArgumentException("Pattern input alternatives exceed ECO planning limits");
                }
            }
            expandVariants(details, choices, 0, new ArrayList<>(), operations);
        }
        return List.copyOf(operations);
    }

    private static List<List<GenericStack>> orderedChoices(
        IPatternDetails details,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService,
        Level level
    ) {
        List<List<GenericStack>> result = new ArrayList<>();
        IPatternDetails.IInput[] inputs = details.getInputs();
        try (ECOAE2NbtTearCompatibility.Scope nbtTear = ECOAE2NbtTearCompatibility.open(details, craftingService)) {
            for (int slot = 0; slot < inputs.length; slot++) {
                IPatternDetails.IInput input = inputs[slot];
                List<GenericStack> choices = choicesForSlot(details, slot, input, inventory, level, nbtTear);
                if (choices.isEmpty()) {
                    throw new IllegalArgumentException("Pattern input has no usable alternatives");
                }
                choices.sort((left, right) -> compareInputChoices(left, right, input, inventory, craftingService));
                result.add(List.copyOf(choices));
            }
        }
        return List.copyOf(result);
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

    private static List<GenericStack> choicesForSlot(
        IPatternDetails details,
        int slot,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        Level level,
        ECOAE2NbtTearCompatibility.Scope nbtTear
    ) {
        Map<AEKey, Long> choices = new LinkedHashMap<>();
        for (GenericStack candidate : input.getPossibleInputs()) {
            if (candidate != null && candidate.amount() > 0) {
                choices.putIfAbsent(candidate.what(), candidate.amount());
            }
        }
        IECOPlannerInputPolicy.MatchMode mode = details instanceof IECOPlannerInputPolicy policy
            ? policy.getPlannerInputMatchMode(slot, input)
            : IECOPlannerInputPolicy.MatchMode.STRICT;
        if (mode == IECOPlannerInputPolicy.MatchMode.ITEM_ONLY) {
            addItemOnlyInventoryVariants(choices, inventory);
        }
        ECOAE2NbtTearCompatibility.addInventoryVariants(input, choices, inventory, level, nbtTear);
        List<GenericStack> result = new ArrayList<>(choices.size());
        choices.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return result;
    }

    private static void addItemOnlyInventoryVariants(Map<AEKey, Long> choices, Map<AEKey, Long> inventory) {
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
                if (amount != null) {
                    choices.putIfAbsent(key, amount);
                }
            }
        }
    }

    private static int inputRank(
        GenericStack candidate,
        IPatternDetails.IInput input,
        Map<AEKey, Long> inventory,
        ICraftingService craftingService
    ) {
        long multiplier = Math.max(1L, input.getMultiplier());
        long required;
        try {
            required = Math.multiplyExact(candidate.amount(), multiplier);
        } catch (ArithmeticException ignored) {
            required = Long.MAX_VALUE;
        }
        long available = inventory.getOrDefault(candidate.what(), 0L);
        if (available >= required) return 2;
        return craftingService.getCraftingFor(candidate.what()).isEmpty() ? 0 : 1;
    }

    private static void expandVariants(
        IPatternDetails details,
        List<List<GenericStack>> choices,
        int slot,
        List<GenericStack> selected,
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> target
    ) {
        if (slot == choices.size()) {
            int ordinal = target.size();
            ECOAE2PatternVariant variant = new ECOAE2PatternVariant(details, ordinal, selected);
            target.add(operationFor(variant).orElseThrow());
            return;
        }
        for (GenericStack choice : choices.get(slot)) {
            selected.add(choice);
            expandVariants(details, choices, slot + 1, selected, target);
            selected.removeLast();
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

    private static Optional<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operationFor(
        ECOAE2PatternVariant variant
    ) {
        IPatternDetails details = variant.pattern();
        GenericStack primaryOutput = details.getPrimaryOutput();
        if (primaryOutput == null) {
            return Optional.empty();
        }
        Map<AEKey, Long> inputs = new LinkedHashMap<>();
        List<GenericStack> selectedInputs = variant.selectedInputs();
        for (int i = 0; i < details.getInputs().length; i++) {
            IPatternDetails.IInput input = details.getInputs()[i];
            GenericStack selected = selectedInputs.get(i);
            if (selected == null || selected.amount() <= 0 || input.getMultiplier() <= 0) {
                return Optional.empty();
            }
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
        return Optional.of(new ECOPlanningOperation<>(variant, inputs, outputs));
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
