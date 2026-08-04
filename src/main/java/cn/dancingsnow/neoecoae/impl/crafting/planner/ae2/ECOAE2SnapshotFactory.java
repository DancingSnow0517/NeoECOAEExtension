package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
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
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final int MAX_OPERATIONS = 8_192;
    private static final int MAX_INVENTORY_DEPENDENT_GRAPHS = 64;
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
        return capture(
            grid, requester, requestedKey, requestedAmount, strategy, craftableGeneration, null
        );
    }

    /** Captures a snapshot with the server level required for exact input validation. */
    public static Optional<ECOAE2PlanningSnapshot> capture(
        IGrid grid,
        ICraftingSimulationRequester requester,
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long craftableGeneration,
        Level level
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
        long captureStarted = System.nanoTime();
        try {
            long inventoryStarted = System.nanoTime();
            Map<AEKey, Long> inventory = copyInventory(grid, requester);
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
                requestedKey, requestedAmount, strategy,
                "inventory_copy", inventoryStarted, "inventoryKeys=" + inventory.size()
            );
            ECOPlanningFailureDiagnostics.logTrace(
                requestedKey,
                requestedAmount,
                strategy,
                "snapshot_start generation=" + craftableGeneration
                    + " inventoryKeys=" + inventory.size()
                    + " level=" + (level == null ? "missing" : level.dimension().location())
            );
            ICraftingService craftingService = Objects.requireNonNull(
                grid.getCraftingService(), "craftingService"
            );
            long graphStarted = System.nanoTime();
            PatternGraph graph = graphFor(
                craftingService,
                requestedKey,
                inventory,
                level,
                craftableGeneration,
                requestedAmount,
                strategy
            );
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.GRAPH,
                requestedKey, requestedAmount, strategy,
                "graph_build", graphStarted,
                "operations=" + graph.operations().size()
                    + " materials=" + graph.materialCount()
                    + " cacheable=" + graph.cacheable()
            );
            List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = graph.operations();
            if (operations.isEmpty()) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.GRAPH,
                    ECOPlannerFallbackReason.SNAPSHOT_REJECTED,
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "graph_empty generation=" + craftableGeneration
                        + " targetHasProducer=" + graph.targetHasProducer()
                        + " visitedMaterials=" + graph.materialCount()
                        + " unresolvedMaterials=" + summarizeKeys(graph.unresolvedMaterials())
                );
                ECOPlanningFailureDiagnostics.logTiming(
                    ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
                    requestedKey, requestedAmount, strategy,
                    "snapshot_capture_total", captureStarted,
                    "result=failure reason=graph_empty"
                );
                return Optional.empty();
            }

            // Existing copies of the requested output must not short-circuit a normal request,
            // but they remain valid seed material for a self-increasing target.
            boolean requestedIsInput = operations.stream()
                .anyMatch(operation -> operation.inputs().containsKey(requestedKey));
            if (!requestedIsInput) {
                inventory.remove(requestedKey);
            }
            retainRelevantInventory(inventory, operations, requestedKey);

            var problem = new ECOPlanningProblem<>(
                operations,
                inventory,
                Map.of(requestedKey, requestedAmount)
            );
            LOGGER.debug(
                "Captured ECO planning snapshot for {} x{}: generation={}, operations={}, "
                    + "materials={}, inventoryKeys={}, truncatedStateExpansion={}",
                requestedKey,
                requestedAmount,
                craftableGeneration,
                operations.size(),
                graph.materialCount(),
                inventory.size(),
                graph.truncatedStateExpansion()
            );
            Optional<ECOAE2PlanningSnapshot> snapshot = Optional.of(new ECOAE2PlanningSnapshot(
                problem,
                requestedKey,
                requestedAmount,
                graph.multiplePaths(),
                graph.inputSlotCounts(),
                graph.truncatedStateExpansion(),
                graph.excludedDynamicPaths()
            ));
            ECOPlanningFailureDiagnostics.logTiming(
                ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
                requestedKey, requestedAmount, strategy,
                "snapshot_capture_total", captureStarted,
                "result=success operations=" + operations.size()
            );
            return snapshot;
        } catch (ECOAE2PatternMaterializer.PatternRejection rejection) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.OPERATION_MATERIALIZATION,
                rejection.reason(),
                requestedKey,
                requestedAmount,
                strategy,
                rejection.context(),
                rejection
            );
            logCaptureFailureTiming(requestedKey, requestedAmount, strategy, captureStarted, rejection.reason());
            return Optional.empty();
        } catch (SnapshotRejection rejection) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.GRAPH,
                rejection.reason(),
                requestedKey,
                requestedAmount,
                strategy,
                rejection.context(),
                rejection
            );
            logCaptureFailureTiming(requestedKey, requestedAmount, strategy, captureStarted, rejection.reason());
            return Optional.empty();
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
            logCaptureFailureTiming(
                requestedKey, requestedAmount, strategy, captureStarted,
                ECOPlannerFallbackReason.SNAPSHOT_REJECTED
            );
            return Optional.empty();
        }
    }

    private static PatternGraph graphFor(
        ICraftingService craftingService,
        AEKey requestedKey,
        Map<AEKey, Long> inventory,
        Level level,
        long craftableGeneration,
        long requestedAmount,
        CalculationStrategy strategy
    ) {
        if (craftableGeneration == NO_GENERATION) {
            return buildGraph(
                craftingService, requestedKey, inventory, level, requestedAmount, strategy
            );
        }
        synchronized (GRAPH_CACHE) {
            CachedGraphs cached = GRAPH_CACHE.get(craftingService);
            if (cached == null || cached.generation() != craftableGeneration) {
                cached = new CachedGraphs(
                    craftableGeneration,
                    new LinkedHashMap<>(),
                    newInventoryDependentGraphCache()
                );
                GRAPH_CACHE.put(craftingService, cached);
            }
            PatternGraph graph = cached.graphs().get(requestedKey);
            if (graph != null) {
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "graph_cache_hit generation=" + craftableGeneration
                        + " operations=" + graph.operations().size()
                        + " materials=" + graph.materialCount()
                );
                return graph;
            }
            InventoryGraphKey inventoryKey = new InventoryGraphKey(
                requestedKey,
                Set.copyOf(inventory.keySet())
            );
            graph = cached.inventoryDependentGraphs().get(inventoryKey);
            if (graph != null) {
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "inventory_graph_cache_hit generation=" + craftableGeneration
                        + " operations=" + graph.operations().size()
                        + " materials=" + graph.materialCount()
                        + " inventoryKeys=" + inventoryKey.availableKeys().size()
                );
                return graph;
            }
            graph = buildGraph(
                craftingService, requestedKey, inventory, level, requestedAmount, strategy
            );
            if (graph.cacheable()) {
                cached.graphs().put(requestedKey, graph);
            } else if (graph.inventoryDependent()
                && graph.inventoryCacheable()
                && !graph.stateful()
                && !graph.excludedDynamicPaths()) {
                cached.inventoryDependentGraphs().put(inventoryKey, graph);
            }
            return graph;
        }
    }

    private static Map<InventoryGraphKey, PatternGraph> newInventoryDependentGraphCache() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<InventoryGraphKey, PatternGraph> eldest) {
                return size() > MAX_INVENTORY_DEPENDENT_GRAPHS;
            }
        };
    }

    private static PatternGraph buildGraph(
        ICraftingService craftingService,
        AEKey requestedKey,
        Map<AEKey, Long> inventory,
        Level level,
        long requestedAmount,
        CalculationStrategy strategy
    ) {
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        Set<AEKey> visitedMaterials = new LinkedHashSet<>();
        Set<AEKey> unresolvedMaterials = new LinkedHashSet<>();
        Map<AEItemKey, CapturedPattern> canonicalPatterns = new LinkedHashMap<>();
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations = new ArrayList<>();
        Map<ECOAE2PatternVariant, Integer> inputSlotCounts = new LinkedHashMap<>();
        boolean multiplePaths = false;
        boolean truncatedStateExpansion = false;
        boolean excludedDynamicPaths = false;
        boolean inventoryDependent = false;
        boolean inventoryCacheable = true;
        boolean stateful = false;
        boolean cacheable = true;
        pending.add(requestedKey);

        while (!pending.isEmpty()) {
            AEKey material = pending.removeFirst();
            if (!visitedMaterials.add(material)) {
                continue;
            }
            if (visitedMaterials.size() > MAX_MATERIALS) {
                throw reject(
                    ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                    "material_limit=" + MAX_MATERIALS
                );
            }

            List<IPatternDetails> producers;
            try {
                var rawProducers = craftingService.getCraftingFor(material);
                if (rawProducers == null) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "crafting_for_returned_null material=" + material
                    );
                }
                producers = new ArrayList<>(rawProducers);
            } catch (RuntimeException | LinkageError failure) {
                throw reject(
                    ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                    "crafting_for_exception material=" + material,
                    failure
                );
            }
            if (producers.isEmpty()) {
                unresolvedMaterials.add(material);
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "material_no_producer material=" + material
                        + " target=" + material.equals(requestedKey)
                        + " visitedMaterials=" + visitedMaterials.size()
                );
                continue;
            }
            trace(
                requestedKey,
                requestedAmount,
                strategy,
                "material_producers material=" + material
                    + " target=" + material.equals(requestedKey)
                    + " producerCount=" + producers.size()
                    + " visitedMaterials=" + visitedMaterials.size()
            );
            Set<AEItemKey> logicalProducerIdentities = new HashSet<>();
            boolean retainedProducer = false;
            for (int producerIndex = 0; producerIndex < producers.size(); producerIndex++) {
                IPatternDetails details = producers.get(producerIndex);
                if (details == null) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "null_pattern_details material=" + material
                            + " producerIndex=" + producerIndex
                    );
                }
                AEItemKey definition;
                try {
                    definition = details.getDefinition();
                } catch (RuntimeException | LinkageError failure) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "definition_read_exception pattern=" + details.getClass().getName(),
                        failure
                    );
                }
                if (definition == null) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "pattern_definition_missing pattern=" + details.getClass().getName()
                    );
                }
                String patternContext = patternContext(material, producerIndex, details, definition);
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "pattern_discovered " + patternContext
                );
                logicalProducerIdentities.add(definition);

                CapturedPattern existing = canonicalPatterns.get(definition);
                // AE2 may expose the same logical pattern through more than one provider. Keep
                // one operation set, but verify that distinct detail instances captured the same
                // exact shape before reusing it.
                if (existing != null) {
                    if (existing.details() == details) {
                        retainedProducer = true;
                        continue;
                    }
                    ECOAE2PatternCompatibility.Assessment duplicateAssessment =
                        ECOAE2PatternCompatibility.assess(details, craftingService, level);
                    if (!duplicateAssessment.compatible()) {
                        if ("provider_scoped_nbt".equals(duplicateAssessment.rejection())) {
                            excludedDynamicPaths = true;
                            cacheable = false;
                            trace(
                                requestedKey,
                                requestedAmount,
                                strategy,
                                "pattern_excluded " + patternContext
                                    + " context=provider_scoped_nbt duplicate=true"
                            );
                            continue;
                        }
                        throw reject(
                            ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                            "duplicate_pattern_incompatible " + patternContext
                                + " context=" + duplicateAssessment.rejection()
                        );
                    }
                    ECOAE2PatternMaterializer.PatternExpansion duplicateExpansion;
                    try {
                        duplicateExpansion = ECOAE2PatternMaterializer.expand(
                            details, duplicateAssessment, inventory, craftingService, level
                        );
                    } catch (ECOAE2PatternMaterializer.PatternRejection rejection) {
                        throw rejection.withContext(patternContext + " phase=duplicate_materialization");
                    }
                    if (!sameMaterialization(existing.expansion(), duplicateExpansion)) {
                        throw reject(
                            ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                            "one_pattern_definition_has_multiple_shapes definition=" + definition
                        );
                    }
                    cacheable &= cacheablePattern(details, duplicateExpansion);
                    inventoryDependent |= duplicateExpansion.inventoryDependent();
                    inventoryCacheable &= inventoryCacheablePattern(details, duplicateExpansion);
                    stateful |= duplicateExpansion.stateful();
                    retainedProducer = true;
                    continue;
                }
                ECOAE2PatternCompatibility.Assessment assessment =
                    ECOAE2PatternCompatibility.assess(details, craftingService, level);
                if (!assessment.compatible()) {
                    if ("provider_scoped_nbt".equals(assessment.rejection())) {
                        excludedDynamicPaths = true;
                        cacheable = false;
                        trace(
                            requestedKey,
                            requestedAmount,
                            strategy,
                            "pattern_excluded " + patternContext
                                + " context=provider_scoped_nbt"
                        );
                        continue;
                    }
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "pattern_incompatible " + patternContext
                            + " context=" + assessment.rejection()
                    );
                }
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "pattern_compatible " + patternContext
                        + " inputSemantics=" + assessment.inputSemantics()
                        + " fuzzy=" + assessment.includeFuzzyInventory()
                        + " stateExpansionAllowed=" + assessment.stateExpansionAllowed()
                );
                ECOAE2PatternMaterializer.PatternExpansion expansion;
                try {
                    expansion = ECOAE2PatternMaterializer.expand(
                        details, assessment, inventory, craftingService, level
                    );
                } catch (ECOAE2PatternMaterializer.PatternRejection rejection) {
                    throw rejection.withContext(patternContext + " phase=materialization");
                }
                if (expansion.operations().isEmpty()) {
                    throw reject(
                        ECOPlannerFallbackReason.PATTERN_INCOMPATIBLE,
                        "pattern_materialized_without_operations definition=" + definition
                    );
                }
                if (expansion.operations().size() > MAX_OPERATIONS - operations.size()) {
                    throw reject(
                        ECOPlannerFallbackReason.SNAPSHOT_LIMIT_EXCEEDED,
                        "materialized_operation_limit=" + MAX_OPERATIONS
                            + " attempted=" + (operations.size() + expansion.operations().size())
                            + " definition=" + definition
                    );
                }
                canonicalPatterns.put(definition, new CapturedPattern(details, expansion));
                operations.addAll(expansion.operations());
                for (var operation : expansion.operations()) {
                    inputSlotCounts.put(operation.reference(), operation.reference().selectedInputs().size());
                }
                pending.addAll(expansion.dependencyKeys());
                multiplePaths |= expansion.operations().size() > 1;
                truncatedStateExpansion |= expansion.truncatedStateExpansion();
                inventoryDependent |= expansion.inventoryDependent();
                inventoryCacheable &= inventoryCacheablePattern(details, expansion);
                stateful |= expansion.stateful();
                cacheable &= cacheablePattern(details, expansion);
                retainedProducer = true;
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "pattern_materialized " + patternContext
                        + " variants=" + expansion.operations().size()
                        + " dependencyCount=" + expansion.dependencyKeys().size()
                        + " dependencies=" + summarizeKeys(expansion.dependencyKeys())
                        + " inventoryDependent=" + expansion.inventoryDependent()
                        + " stateful=" + expansion.stateful()
                        + " truncatedStateExpansion=" + expansion.truncatedStateExpansion()
                );
            }
            if (!retainedProducer) {
                unresolvedMaterials.add(material);
                trace(
                    requestedKey,
                    requestedAmount,
                    strategy,
                    "material_no_eco_provider material=" + material
                        + " target=" + material.equals(requestedKey)
                        + " excludedDynamicPaths=" + excludedDynamicPaths
                );
            }
            multiplePaths |= logicalProducerIdentities.size() > 1;
            multiplePaths |= producers.size() > 1;
        }
        return new PatternGraph(
            List.copyOf(operations),
            Map.copyOf(inputSlotCounts),
            multiplePaths,
            truncatedStateExpansion,
            excludedDynamicPaths,
            cacheable,
            inventoryDependent,
            inventoryCacheable,
            stateful,
            visitedMaterials.size(),
            unresolvedMaterials,
            !unresolvedMaterials.contains(requestedKey)
        );
    }

    /** Compatibility helper retained for input-selection execution tests. */
    static List<List<ECOAE2InputSelection>> inputSelections(
        IPatternDetails details,
        IPatternDetails.IInput[] inputs,
        List<List<GenericStack>> choices,
        IECOPlannerCompatiblePattern.InputSemantics semantics
    ) {
        Objects.requireNonNull(details, "details");
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

    private static Map<AEKey, Long> copyInventory(
        IGrid grid,
        ICraftingSimulationRequester requester
    ) {
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

    private static void retainRelevantInventory(
        Map<AEKey, Long> inventory,
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations,
        AEKey requestedKey
    ) {
        Set<AEKey> relevant = new LinkedHashSet<>();
        relevant.add(requestedKey);
        for (var operation : operations) {
            relevant.addAll(operation.inputs().keySet());
            relevant.addAll(operation.outputs().keySet());
        }
        inventory.keySet().removeIf(key -> !relevant.contains(key));
    }

    private static boolean sameMaterialization(
        ECOAE2PatternMaterializer.PatternExpansion left,
        ECOAE2PatternMaterializer.PatternExpansion right
    ) {
        if (left.inventoryDependent() != right.inventoryDependent()
            || left.stateful() != right.stateful()
            || left.truncatedStateExpansion() != right.truncatedStateExpansion()
            || !left.dependencyKeys().equals(right.dependencyKeys())
            || left.operations().size() != right.operations().size()) {
            return false;
        }
        for (int i = 0; i < left.operations().size(); i++) {
            var leftOperation = left.operations().get(i);
            var rightOperation = right.operations().get(i);
            var leftVariant = leftOperation.reference();
            var rightVariant = rightOperation.reference();
            if (leftVariant.ordinal() != rightVariant.ordinal()
                || !leftVariant.selectedInputs().equals(rightVariant.selectedInputs())
                || !leftOperation.inputs().equals(rightOperation.inputs())
                || !leftOperation.outputs().equals(rightOperation.outputs())
                || !leftOperation.selectableOutputs().equals(rightOperation.selectableOutputs())
                || !leftOperation.stateTransitionInputs().equals(rightOperation.stateTransitionInputs())) {
                return false;
            }
        }
        return true;
    }

    private static boolean cacheablePattern(
        IPatternDetails details,
        ECOAE2PatternMaterializer.PatternExpansion expansion
    ) {
        return (details instanceof IECOPlannerCompatiblePattern
                || ECOAE2PatternCompatibility.isKnownBuiltIn(details))
            && !expansion.inventoryDependent()
            && !expansion.stateful();
    }

    private static boolean inventoryCacheablePattern(
        IPatternDetails details,
        ECOAE2PatternMaterializer.PatternExpansion expansion
    ) {
        return (details instanceof IECOPlannerCompatiblePattern
                || ECOAE2PatternCompatibility.isKnownBuiltIn(details))
            && !expansion.stateful();
    }

    private static String patternContext(
        AEKey material,
        int producerIndex,
        IPatternDetails details,
        AEItemKey definition
    ) {
        return "material=" + material
            + " producerIndex=" + producerIndex
            + " patternClass=" + details.getClass().getName()
            + " definition=" + definition;
    }

    private static String summarizeKeys(Iterable<? extends AEKey> keys) {
        StringBuilder result = new StringBuilder("[");
        int count = 0;
        for (AEKey key : keys) {
            if (count > 0) {
                result.append(", ");
            }
            if (count == 8) {
                result.append("...");
                break;
            }
            result.append(key);
            count++;
        }
        return result.append(']').toString();
    }

    private static void trace(
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        String context
    ) {
        ECOPlanningFailureDiagnostics.logTrace(
            requestedKey, requestedAmount, strategy, context
        );
    }

    private static void logCaptureFailureTiming(
        AEKey requestedKey,
        long requestedAmount,
        CalculationStrategy strategy,
        long captureStarted,
        ECOPlannerFallbackReason reason
    ) {
        ECOPlanningFailureDiagnostics.logTiming(
            ECOPlanningFailureDiagnostics.Stage.SNAPSHOT,
            requestedKey, requestedAmount, strategy,
            "snapshot_capture_total", captureStarted,
            "result=failure reason=" + reason.id()
        );
    }

    private static SnapshotRejection reject(ECOPlannerFallbackReason reason, String context) {
        return new SnapshotRejection(reason, context);
    }

    private static SnapshotRejection reject(
        ECOPlannerFallbackReason reason,
        String context,
        Throwable cause
    ) {
        return new SnapshotRejection(reason, context, cause);
    }

    private record CapturedPattern(
        IPatternDetails details,
        ECOAE2PatternMaterializer.PatternExpansion expansion
    ) {
    }

    private record PatternGraph(
        List<ECOPlanningOperation<AEKey, ECOAE2PatternVariant>> operations,
        Map<ECOAE2PatternVariant, Integer> inputSlotCounts,
        boolean multiplePaths,
        boolean truncatedStateExpansion,
        boolean excludedDynamicPaths,
        boolean cacheable,
        boolean inventoryDependent,
        boolean inventoryCacheable,
        boolean stateful,
        int materialCount,
        Set<AEKey> unresolvedMaterials,
        boolean targetHasProducer
    ) {
        private PatternGraph {
            operations = List.copyOf(operations);
            inputSlotCounts = Map.copyOf(inputSlotCounts);
            unresolvedMaterials = Set.copyOf(unresolvedMaterials);
        }
    }

    private record InventoryGraphKey(AEKey requestedKey, Set<AEKey> availableKeys) {
        private InventoryGraphKey {
            availableKeys = Set.copyOf(availableKeys);
        }
    }

    private static final class SnapshotRejection extends RuntimeException {
        private final ECOPlannerFallbackReason reason;

        private SnapshotRejection(ECOPlannerFallbackReason reason, String context) {
            super(context);
            this.reason = reason;
        }

        private SnapshotRejection(
            ECOPlannerFallbackReason reason,
            String context,
            Throwable cause
        ) {
            super(context, cause);
            this.reason = reason;
        }

        private ECOPlannerFallbackReason reason() {
            return reason;
        }

        private String context() {
            return getMessage();
        }
    }

    private record CachedGraphs(
        long generation,
        Map<AEKey, PatternGraph> graphs,
        Map<InventoryGraphKey, PatternGraph> inventoryDependentGraphs
    ) {
    }
}
