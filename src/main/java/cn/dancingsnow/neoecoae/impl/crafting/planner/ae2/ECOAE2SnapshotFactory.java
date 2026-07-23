package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Captures every mutable AE2 input needed by the ECO worker while still on the server thread. */
public final class ECOAE2SnapshotFactory {
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
        if (requestedAmount <= 0 || strategy != CalculationStrategy.REPORT_MISSING_ITEMS) {
            return Optional.empty();
        }
        try {
            Map<AEKey, Long> inventory = copyInventory(grid, requester);

            var craftingService = grid.getCraftingService();
            Optional<PatternGraph> graph = graphFor(
                craftingService,
                requestedKey,
                craftableGeneration,
                inventory
            );
            if (graph.isEmpty()) {
                return Optional.empty();
            }

            // Stored copies of the requested output must not short-circuit a normal
            // request, but they are valid seed material for self-increasing patterns.
            boolean requestedIsInput = graph.get().operations().stream()
                .anyMatch(operation -> operation.inputs().containsKey(requestedKey));
            if (!requestedIsInput) {
                inventory.remove(requestedKey);
            }

            var problem = new ECOPlanningProblem<>(
                graph.get().operations(),
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
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<PatternGraph> graphFor(
        ICraftingService craftingService,
        AEKey requestedKey,
        long craftableGeneration,
        Map<AEKey, Long> inventory
    ) {
        if (craftableGeneration == NO_GENERATION) {
            return buildGraph(craftingService, requestedKey, inventory);
        }
        long inventorySignature = inventorySignature(inventory);
        synchronized (GRAPH_CACHE) {
            CachedGraphs cached = GRAPH_CACHE.get(craftingService);
            if (cached == null
                || cached.generation() != craftableGeneration
                || cached.inventorySignature() != inventorySignature) {
                cached = new CachedGraphs(craftableGeneration, inventorySignature, new LinkedHashMap<>());
                GRAPH_CACHE.put(craftingService, cached);
            }
            return cached.graphs().computeIfAbsent(
                requestedKey,
                ignored -> buildGraph(craftingService, requestedKey, inventory)
            );
        }
    }

    private static Optional<PatternGraph> buildGraph(
        ICraftingService craftingService,
        AEKey requestedKey,
        Map<AEKey, Long> inventory
    ) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        Set<AEKey> visitedMaterials = new HashSet<>();
        Set<IPatternDetails> visitedPatterns = new HashSet<>();
        List<ECOPlanningOperation<AEKey, IPatternDetails>> operations = new ArrayList<>();
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
            multiplePaths |= producers.size() > 1;
            for (IPatternDetails details : producers) {
                if (!visitedPatterns.add(details)) {
                    continue;
                }
                Optional<ECOPlanningOperation<AEKey, IPatternDetails>> operation = convert(
                    details,
                    material,
                    pending,
                    inventory,
                    craftingService
                );
                if (operation.isEmpty()) {
                    return Optional.empty();
                }
                operations.add(operation.get());
                inputSlotCounts.put(details, details.getInputs().length);
                if (operations.size() > MAX_OPERATIONS) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new PatternGraph(operations, inputSlotCounts, multiplePaths));
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
        AEKey primaryMaterial,
        ArrayDeque<AEKey> pending,
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
            pending.addLast(selected.what());

        }

        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.amount() <= 0) {
                return Optional.empty();
            }
            outputs.merge(output.what(), output.amount(), Math::addExact);
        }
        if (!outputs.containsKey(primaryMaterial)) {
            return Optional.empty();
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

    private static long inventorySignature(Map<AEKey, Long> inventory) {
        long signature = 0xcbf29ce484222325L;
        for (var entry : inventory.entrySet()) {
            signature ^= entry.getKey().hashCode();
            signature *= 0x100000001b3L;
            signature ^= entry.getValue();
            signature *= 0x100000001b3L;
        }
        return signature;
    }

    private record PatternGraph(
        List<ECOPlanningOperation<AEKey, IPatternDetails>> operations,
        Map<IPatternDetails, Integer> inputSlotCounts,
        boolean multiplePaths
    ) {
        private PatternGraph {
            operations = List.copyOf(operations);
            inputSlotCounts = Map.copyOf(inputSlotCounts);
        }
    }

    private record CachedGraphs(
        long generation,
        long inventorySignature,
        Map<AEKey, Optional<PatternGraph>> graphs
    ) {
    }
}
