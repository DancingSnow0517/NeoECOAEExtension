package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.CandidateStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.Edge;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.EdgeKind;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialNode;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.PatternAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.PatternNode;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.Relationship;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Converts planner-owned explanation data into the stable UI contract. */
public final class CraftingGraphSnapshotFactory {
    private CraftingGraphSnapshotFactory() {}

    public static CraftingGraphSnapshot create(ECOPlanningResult result) {
        LinkedHashMap<AEKey, MutableMaterial> materials = new LinkedHashMap<>();
        AEKey rootKey = collectMaterials(result, materials);
        Map<AEKey, Integer> nodeIds = new LinkedHashMap<>();
        List<MaterialNode> nodes = new ArrayList<>(materials.size());
        for (MutableMaterial material : materials.values()) {
            int id = nodes.size();
            nodeIds.put(material.key, id);
            nodes.add(material.freeze(id));
        }

        List<PatternNode> patterns = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Map<IPatternDetails, Integer> patternIds = new IdentityHashMap<>();
        appendTracedPatterns(result.trace(), nodeIds, patterns, edges, patternIds);
        List<CraftingGraphSnapshot.CycleGroup> cycles = appendCycles(result, nodeIds, patterns, edges, patternIds);

        Integer rootId = rootKey == null ? null : nodeIds.get(rootKey);
        int rootNodeId = rootId == null ? (rootKey == null && !nodes.isEmpty() ? 0 : -1) : rootId;
        var summary = new CraftingGraphSnapshot.Summary(result.status().name(), nodes.size(), patterns.size(),
            edges.size(), cycles.size(), result.calculationNanos());
        List<net.minecraft.resources.ResourceLocation> fuzzyPlanningItemIds = result.fuzzyPlanningItemIds().stream()
            .sorted(Comparator.comparing(net.minecraft.resources.ResourceLocation::toString)).toList();
        return new CraftingGraphSnapshot(rootNodeId, nodes, patterns, edges, cycles, fuzzyPlanningItemIds, summary);
    }

    private static @Nullable AEKey collectMaterials(ECOPlanningResult result,
            Map<AEKey, MutableMaterial> materials) {
        var trace = result.trace();
        AEKey rootKey = null;
        // Early unsupported exits may contain no goal trace at all. Keep the requested item visible.
        if (result.plan() != null) {
            var output = result.plan().finalOutput();
            rootKey = output.what();
            var root = materials.computeIfAbsent(rootKey, MutableMaterial::new);
            root.requested = Math.max(0L, output.amount());
            root.exactRequested = PlannerAmount.of(root.requested);
            root.unsupported = result.shouldUseNativeFallback();
        }
        for (PlanTraceNode node : trace.nodes()) {
            if (node.key() == null || node.kind() == PlanTraceNode.Kind.PATTERN) continue;
            MutableMaterial current = materials.computeIfAbsent(node.key(), MutableMaterial::new);
            current.merge(node);
            if (node.kind() == PlanTraceNode.Kind.GOAL) rootKey = node.key();
        }
        for (var edge : trace.edges()) {
            materials.computeIfAbsent(edge.from(), MutableMaterial::new);
            materials.computeIfAbsent(edge.to(), MutableMaterial::new);
        }
        var collectedPatterns = java.util.Collections.newSetFromMap(new IdentityHashMap<IPatternDetails, Boolean>());
        for (PlanTraceNode node : trace.nodes()) {
            if (node.kind() != PlanTraceNode.Kind.PATTERN || node.pattern() == null) continue;
            if (!collectedPatterns.add(node.pattern())) continue;
            try {
                for (GenericStack output : node.pattern().getOutputs()) {
                    if (output != null && output.what() != null) materials.computeIfAbsent(output.what(), MutableMaterial::new);
                }
                for (IPatternDetails.IInput input : node.pattern().getInputs()) {
                    if (input == null || input.getPossibleInputs() == null) continue;
                    for (GenericStack possible : input.getPossibleInputs()) {
                        if (possible != null && possible.what() != null) materials.computeIfAbsent(possible.what(), MutableMaterial::new);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        for (var cycle : trace.cycles()) {
            for (AEKey key : cycle.members()) materials.computeIfAbsent(key, MutableMaterial::new).cycle = true;
            // Surface the deficit on the actual startup key. Projecting the aggregate seed deficit onto every
            // requested cycle output makes unrelated outputs appear to be missing the same startup material.
            if (cycle.solveResult() != null && !cycle.solveResult().exactSeedShortfall().isEmpty()) {
                cycle.solveResult().exactSeedShortfall().forEach((key, amount) -> {
                    var material = materials.computeIfAbsent(key, MutableMaterial::new);
                    material.exactMissing = material.exactMissing.max(amount);
                    material.missing = material.exactMissing.fitsLong() ? material.exactMissing.longValueExact() : Long.MAX_VALUE;
                });
            }
            for (var internalEdge : cycle.internalEdges()) {
                collectPatternMaterials(internalEdge.pattern(), materials);
            }
        }

        collectMissingMaterials(result, materials);
        collectTaskMaterialFlow(result, materials);
        collectReservedInventory(result, materials);
        return rootKey;
    }

    private static void appendTracedPatterns(ECOPlanTrace trace, Map<AEKey, Integer> nodeIds,
            List<PatternNode> patterns, List<Edge> edges, Map<IPatternDetails, Integer> patternIds) {
        // A shared ingredient can have many traced candidates. Resolve its relationships and component once,
        // instead of rescanning the entire trace for every occurrence in a large order.
        Map<AEKey, List<Relationship>> inputsByOutput = new HashMap<>();
        for (var edge : trace.edges()) {
            Integer inputId = nodeIds.get(edge.to());
            if (inputId != null) inputsByOutput.computeIfAbsent(edge.from(), ignored -> new ArrayList<>())
                .add(new Relationship(inputId, PlannerAmount.of(edge.exactAmount())));
        }
        inputsByOutput.replaceAll((key, inputs) -> List.copyOf(inputs));
        Map<AEKey, Integer> componentIds = new HashMap<>();
        for (var component : trace.components()) {
            for (AEKey key : component.members()) componentIds.putIfAbsent(key, component.componentId());
        }
        var cyclePatterns = java.util.Collections.newSetFromMap(new IdentityHashMap<IPatternDetails, Boolean>());
        for (var cycle : trace.cycles()) {
            for (var edge : cycle.internalEdges()) cyclePatterns.add(edge.pattern().details());
        }
        for (PlanTraceNode node : trace.nodes()) {
            if (node.kind() != PlanTraceNode.Kind.PATTERN || node.pattern() == null || node.key() == null) continue;
            // A cycle candidate can be traced once per internal edge and again when a route is deferred.
            // Build it once below from the compiled cycle and its final solve result, not provisional rejections.
            if (cyclePatterns.contains(node.pattern())) continue;
            Integer outputId = nodeIds.get(node.key());
            if (outputId == null) continue;
            int patternNodeId = patternNodeId(patterns.size());
            patternIds.put(node.pattern(), patternNodeId);
            List<Relationship> inputs = inputsByOutput.getOrDefault(node.key(), List.of());
            if (inputs.isEmpty()) inputs = inputs(node.pattern(), nodeIds);
            List<Relationship> outputs = outputs(node.pattern(), nodeIds);
            if (outputs.stream().noneMatch(output -> output.materialNodeId() == outputId)) {
                outputs = new ArrayList<>(outputs);
                outputs.add(new Relationship(outputId, 0));
            }
            CandidateStatus status = switch (node.selection()) {
                case SELECTED -> CandidateStatus.SELECTED;
                case REJECTED -> CandidateStatus.REJECTED;
                default -> CandidateStatus.UNSUPPORTED;
            };
            int componentId = componentIds.getOrDefault(node.key(), -1);
            PatternNode pattern = new PatternNode(patternNodeId, displayIdentity(node.pattern()), inputs, outputs,
                PlannerAmount.of(node.exactFiringCount()), status, node.reason(), componentId);
            patterns.add(pattern);
            boolean selected = status == CandidateStatus.SELECTED;
            for (Relationship output : outputs) {
                EdgeKind kind = output.materialNodeId() == outputId ? EdgeKind.PATTERN_OUTPUT : EdgeKind.BYPRODUCT;
                edges.add(new Edge(output.materialNodeId(), patternNodeId, output.plannerAmount(), kind, selected));
            }
            for (Relationship input : inputs) {
                edges.add(new Edge(patternNodeId, input.materialNodeId(), input.plannerAmount(), EdgeKind.PATTERN_INPUT,
                    selected));
            }
        }
    }

    private static List<CraftingGraphSnapshot.CycleGroup> appendCycles(ECOPlanningResult result,
            Map<AEKey, Integer> nodeIds, List<PatternNode> patterns, List<Edge> edges,
            Map<IPatternDetails, Integer> patternIds) {
        var trace = result.trace();
        List<CraftingGraphSnapshot.CycleGroup> cycles = new ArrayList<>();
        for (int cycleIndex = 0; cycleIndex < trace.cycles().size(); cycleIndex++) {
            var cycle = trace.cycles().get(cycleIndex);
            for (var internalEdge : cycle.internalEdges()) {
                var details = internalEdge.pattern().details();
                if (patternIds.containsKey(details)) continue;
                int patternNodeId = patternNodeId(patterns.size());
                patternIds.put(details, patternNodeId);
                List<Relationship> inputs = internalEdge.pattern().inputs().stream()
                    .map(input -> {
                        Integer inputId = nodeIds.get(input.key());
                        return inputId == null ? null : new Relationship(inputId, input.amountPerPattern());
                    })
                    .filter(java.util.Objects::nonNull).toList();
                List<Relationship> outputs = internalEdge.pattern().grossOutputs().stream()
                    .filter(output -> nodeIds.containsKey(output.what()))
                    .map(output -> new Relationship(nodeIds.get(output.what()), output.amount())).toList();
                PlannerAmount firingCount = cycle.solveResult() == null ? PlannerAmount.ZERO
                    : cycle.solveResult().exactPatternTimes().getOrDefault(details, PlannerAmount.ZERO);
                patterns.add(new PatternNode(patternNodeId, displayIdentity(details), inputs, outputs, firingCount,
                    firingCount.signum() > 0 ? CandidateStatus.SELECTED : CandidateStatus.REJECTED,
                    firingCount.signum() > 0 ? null : "CYCLIC_CANDIDATE", cycle.componentId()));
                for (Relationship output : outputs) {
                    edges.add(new Edge(output.materialNodeId(), patternNodeId, output.plannerAmount(), EdgeKind.PATTERN_OUTPUT,
                        firingCount.signum() > 0));
                }
                for (Relationship input : inputs) {
                    edges.add(new Edge(patternNodeId, input.materialNodeId(), input.plannerAmount(), EdgeKind.PATTERN_INPUT,
                        firingCount.signum() > 0));
                }
            }
            List<Integer> memberIds = cycle.members().stream().map(nodeIds::get).filter(java.util.Objects::nonNull)
                .toList();
            List<Edge> internal = new ArrayList<>();
            for (var internalEdge : cycle.internalEdges()) {
                Integer from = nodeIds.get(internalEdge.producer());
                Integer to = nodeIds.get(internalEdge.requiredInput());
                if (from != null && to != null) {
                    internal.add(new Edge(from, to, internalEdge.input().amountPerPattern(), EdgeKind.CYCLE_INTERNAL, true));
                }
            }
            for (var externalEdge : cycle.externalEdges()) {
                Integer from = nodeIds.get(externalEdge.producer());
                Integer to = nodeIds.get(externalEdge.requiredInput());
                if (from != null && to != null) {
                    edges.add(new Edge(from, to, externalEdge.input().amountPerPattern(), EdgeKind.PATTERN_INPUT, true));
                }
            }
            var solve = cycle.solveResult();
            Map<AEKey, PlannerAmount> exactExternalInputs = new LinkedHashMap<>();
            for (var edge : cycle.externalEdges()) {
                if (edge.requiredInput() != null) {
                    exactExternalInputs.merge(edge.requiredInput(), edge.input().amountPerPattern(), PlannerAmount::add);
                }
            }
            if (solve != null) exactExternalInputs.putAll(solve.exactExternalDemand());
            List<KeyAmount> externalInputs = plannerKeyAmounts(exactExternalInputs);
            CycleDiagnostic diagnostic = cycleIndex < result.cycles().size() ? result.cycles().get(cycleIndex) : null;
            List<KeyAmount> requiredOutputs = result.components().stream().filter(component -> component.componentId() == cycle.componentId())
                    .findFirst().map(component -> plannerKeyAmounts(component.plannerRequiredOutputs()))
                    .orElseGet(() -> keyAmounts(cycle.requiredOutputs()));
            cycles.add(new CraftingGraphSnapshot.CycleGroup(cycle.componentId(), memberIds, internal,
                cycle.status().name(), requiredOutputs,
                externalInputs,
                solve == null ? List.of() : plannerKeyAmounts(solve.exactRequiredSeed()),
                solve == null ? List.of() : solve.exactPatternTimes().entrySet().stream()
                    .filter(entry -> patternIds.containsKey(entry.getKey()))
                    .map(entry -> new PatternAmount(patternIds.get(entry.getKey()), entry.getValue())).toList(),
                solve == null ? List.of() : solve.executionWitness().stream()
                    .map(firing -> patternIds.get(firing.pattern().details()))
                    .filter(java.util.Objects::nonNull).toList(),
                diagnostic == null ? List.of() : keyAmounts(diagnostic.netOutputs()),
                diagnostic == null ? List.of() : keyAmounts(diagnostic.totalNetOutputs()),
                diagnostic == null ? List.of() : keyAmounts(diagnostic.availableAmounts()),
                diagnostic == null ? List.of() : exactKeyAmounts(diagnostic.exactNetOutputs()),
                diagnostic == null ? List.of() : exactKeyAmounts(diagnostic.exactTotalNetOutputs()),
                diagnostic == null
                    ? cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge.UNKNOWN
                    : diagnostic.executionCountKnowledge(),
                diagnostic == null ? cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus.NOT_IMPLEMENTED
                    : diagnostic.solveStatus(), cycle.status()
                        == cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus.NOT_REQUIRED
                            ? new CraftingGraphSnapshot.Recursion(List.of(), List.of(), true)
                            : CycleRecursionPreview.create(solve, requiredOutputs, patternIds)));
        }
        return cycles;
    }

    /** Boundary shortages can arrive after tracing, including beyond the AE2 compatibility shell's range. */
    private static void collectMissingMaterials(ECOPlanningResult result,
            Map<AEKey, MutableMaterial> materials) {
        if (result.plan() != null) {
            for (var entry : result.plan().missingItems()) {
                materials.computeIfAbsent(entry.getKey(), MutableMaterial::new)
                    .mergeMissing(PlannerAmount.of(entry.getLongValue()));
            }
        }
        result.exactMissingItems().forEach((key, amount) -> {
            if (amount.signum() > 0) materials.computeIfAbsent(key, MutableMaterial::new).mergeMissing(amount);
        });
    }

    /** Pattern visual IDs never collide with material IDs and survive packet serialization. */
    public static int patternNodeId(int snapshotPatternIndex) {
        return -snapshotPatternIndex - 2;
    }

    private static void collectPatternMaterials(CompiledPattern pattern,
            Map<AEKey, MutableMaterial> materials) {
        for (var input : pattern.inputs()) {
            if (input.key() != null) materials.computeIfAbsent(input.key(), MutableMaterial::new);
        }
        for (GenericStack output : pattern.grossOutputs()) {
            if (output != null && output.what() != null) materials.computeIfAbsent(output.what(), MutableMaterial::new);
        }
    }

    private static List<Relationship> outputs(IPatternDetails pattern, Map<AEKey, Integer> nodeIds) {
        List<Relationship> result = new ArrayList<>();
        try {
            for (GenericStack output : pattern.getOutputs()) {
                Integer id = nodeIds.get(output.what());
                if (id != null) result.add(new Relationship(id, output.amount()));
            }
        } catch (RuntimeException ignored) {
            // Malformed candidates remain explainable without leaking the exception into the menu sync.
        }
        return result;
    }

    private static List<Relationship> inputs(IPatternDetails pattern, Map<AEKey, Integer> nodeIds) {
        List<Relationship> result = new ArrayList<>();
        try {
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0) continue;
                GenericStack possible = input.getPossibleInputs()[0];
                Integer id = nodeIds.get(possible.what());
                PlannerAmount amount = PlannerAmount.of(possible.amount()).multiply(input.getMultiplier());
                if (id != null) result.add(new Relationship(id, amount));
            }
        } catch (RuntimeException ignored) {
            // A malformed/rejected candidate is still represented, but without unsafe relationship data.
        }
        return result;
    }

    private static String displayIdentity(IPatternDetails pattern) {
        StringBuilder value = new StringBuilder(pattern.getClass().getSimpleName());
        try {
            if (pattern.getOutputs().length > 0) value.append(':').append(cn.dancingsnow.neoecoae.platform.JdkCompat.first(pattern.getOutputs()).what());
        } catch (RuntimeException ignored) {
        }
        return value.toString();
    }

    private static List<KeyAmount> keyAmounts(Map<AEKey, Long> values) {
        return values.entrySet().stream()
            .filter(entry -> entry.getKey() != null)
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(entry -> new KeyAmount(entry.getKey(), entry.getValue())).toList();
    }

    private static List<KeyAmount> plannerKeyAmounts(Map<AEKey, PlannerAmount> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(entry -> new KeyAmount(entry.getKey(), entry.getValue())).toList();
    }

    /** Use the committed exact firing vector; an unrepresentable plan has an empty AE2 compatibility shell. */
    private static void collectTaskMaterialFlow(ECOPlanningResult result,
            Map<AEKey, MutableMaterial> materials) {
        for (var firing : result.exactPatternTimes().entrySet()) {
            if (firing.getValue().signum() <= 0) continue;
            PatternSemantics semantics;
            try {
                semantics = PatternSemanticAdapters.analyze(firing.getKey());
            } catch (RuntimeException | LinkageError ignored) {
                continue;
            }
            if (!semantics.supported()) continue;
            PlannerAmount times = firing.getValue();
            for (var input : semantics.consumedInputs()) {
                var material = materials.computeIfAbsent(input.key(), MutableMaterial::new);
                material.exactConsumed = material.exactConsumed.add(input.amountPerPattern().multiply(times));
            }
            for (GenericStack output : semantics.producedOutputs()) {
                if (output != null && output.what() != null && output.amount() > 0L) {
                    var material = materials.computeIfAbsent(output.what(), MutableMaterial::new);
                    material.exactProduced = material.exactProduced.add(
                        PlannerAmount.of(output.amount()).multiply(times));
                }
            }
            for (GenericStack output : semantics.returnedOutputs()) {
                if (output != null && output.what() != null && output.amount() > 0L) {
                    var material = materials.computeIfAbsent(output.what(), MutableMaterial::new);
                    material.exactProduced = material.exactProduced.add(
                        PlannerAmount.of(output.amount()).multiply(times));
                }
            }
        }
    }

    /** Trace demand keys can differ from the component variants actually reserved from storage. */
    private static void collectReservedInventory(ECOPlanningResult result,
            Map<AEKey, MutableMaterial> materials) {
        Map<AEKey, PlannerAmount> reserved = new LinkedHashMap<>();
        if (result.plan() != null) {
            for (var entry : result.plan().usedItems()) {
                reserved.put(entry.getKey(), PlannerAmount.of(entry.getLongValue()));
            }
        }
        result.exactUsedItems().forEach((key, amount) -> reserved.merge(key, amount, PlannerAmount::max));
        reserved.forEach((key, amount) -> {
            if (amount.signum() > 0) materials.computeIfAbsent(key, MutableMaterial::new).mergeInventory(amount);
        });
    }

    private static List<ExactKeyAmount> exactKeyAmounts(
            Map<AEKey, cn.dancingsnow.neoecoae.crafting.planner.result.ExactCycleAmount> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(entry -> new ExactKeyAmount(entry.getKey(), entry.getValue())).toList();
    }

    private static final class MutableMaterial {
        private final AEKey key;
        private long requested;
        private long fromInventory;
        private long toCraft;
        private long missing;
        private PlannerAmount exactRequested = PlannerAmount.ZERO;
        private PlannerAmount exactFromInventory = PlannerAmount.ZERO;
        private PlannerAmount exactToCraft = PlannerAmount.ZERO;
        private PlannerAmount exactMissing = PlannerAmount.ZERO;
        private PlannerAmount exactConsumed = PlannerAmount.ZERO;
        private PlannerAmount exactProduced = PlannerAmount.ZERO;
        private boolean unsupported;
        private boolean cycle;

        private MutableMaterial(AEKey key) { this.key = key; }

        private void merge(PlanTraceNode node) {
            exactRequested = exactRequested.max(PlannerAmount.of(node.exactRequested()));
            exactFromInventory = exactFromInventory.max(PlannerAmount.of(node.exactFromInventory()));
            exactToCraft = exactToCraft.max(PlannerAmount.of(node.exactToCraft()));
            exactMissing = exactMissing.max(PlannerAmount.of(node.exactMissing()));
            requested = exactRequested.fitsLong() ? exactRequested.longValueExact() : Long.MAX_VALUE;
            fromInventory = exactFromInventory.fitsLong() ? exactFromInventory.longValueExact() : Long.MAX_VALUE;
            toCraft = exactToCraft.fitsLong() ? exactToCraft.longValueExact() : Long.MAX_VALUE;
            missing = exactMissing.fitsLong() ? exactMissing.longValueExact() : Long.MAX_VALUE;
            unsupported |= node.selection() == PlanTraceNode.Selection.UNSUPPORTED;
        }

        private void mergeMissing(PlannerAmount amount) {
            // Trace, the compatibility shell and exact counters describe one deficit, not separate demands.
            exactMissing = exactMissing.max(amount);
            missing = exactMissing.fitsLong() ? exactMissing.longValueExact() : Long.MAX_VALUE;
        }

        private void mergeInventory(PlannerAmount amount) {
            // Trace, exact counters and the AE2 plan describe the same reservation, not additive supplies.
            exactFromInventory = exactFromInventory.max(amount);
            exactRequested = exactRequested.max(exactFromInventory);
            fromInventory = exactFromInventory.fitsLong() ? exactFromInventory.longValueExact() : Long.MAX_VALUE;
            requested = exactRequested.fitsLong() ? exactRequested.longValueExact() : Long.MAX_VALUE;
        }

        private MaterialNode freeze(int id) {
            MaterialStatus status = cycle ? MaterialStatus.CYCLE
                : unsupported ? MaterialStatus.UNSUPPORTED
                : missing > 0 ? MaterialStatus.MISSING
                : toCraft > 0 ? MaterialStatus.CRAFTING : MaterialStatus.SATISFIED;
            return new MaterialNode(id, key, requested, fromInventory, toCraft, missing, status,
                exactRequested.toString(), exactFromInventory.toString(), exactToCraft.toString(), exactMissing.toString(),
                exactConsumed.toString(), exactProduced.toString());
        }
    }
}
