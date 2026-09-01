package cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.CandidateStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.Edge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.EdgeKind;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.PatternAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.PatternNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.Relationship;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts planner-owned explanation data into the stable UI contract. */
public final class CraftingGraphSnapshotFactory {
    private CraftingGraphSnapshotFactory() {}

    public static CraftingGraphSnapshot create(ECOPlanningResult result) {
        var trace = result.trace();
        LinkedHashMap<AEKey, MutableMaterial> materials = new LinkedHashMap<>();
        AEKey rootKey = null;
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
        for (PlanTraceNode node : trace.nodes()) {
            if (node.kind() != PlanTraceNode.Kind.PATTERN || node.pattern() == null) continue;
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
            for (var internalEdge : cycle.internalEdges()) {
                collectPatternMaterials(internalEdge.pattern(), materials);
            }
        }

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
        for (PlanTraceNode node : trace.nodes()) {
            if (node.kind() != PlanTraceNode.Kind.PATTERN || node.pattern() == null || node.key() == null) continue;
            Integer outputId = nodeIds.get(node.key());
            if (outputId == null) continue;
            int patternNodeId = patternNodeId(patterns.size());
            patternIds.put(node.pattern(), patternNodeId);
            List<Relationship> inputs = selectedInputs(node.key(), trace.edges(), nodeIds);
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
            int componentId = componentFor(node.key(), trace.components());
            PatternNode pattern = new PatternNode(patternNodeId, displayIdentity(node.pattern()), inputs, outputs,
                node.firingCount(), status, node.reason(), componentId);
            patterns.add(pattern);
            boolean selected = status == CandidateStatus.SELECTED;
            for (Relationship output : outputs) {
                EdgeKind kind = output.materialNodeId() == outputId ? EdgeKind.PATTERN_OUTPUT : EdgeKind.BYPRODUCT;
                edges.add(new Edge(output.materialNodeId(), patternNodeId, output.amount(), kind, selected));
            }
            for (Relationship input : inputs) {
                edges.add(new Edge(patternNodeId, input.materialNodeId(), input.amount(), EdgeKind.PATTERN_INPUT,
                    selected));
            }
        }

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
                        return inputId == null || !input.amountPerPattern().fitsLong() ? null
                            : new Relationship(inputId, input.amountPerPattern().longValueExact());
                    })
                    .filter(java.util.Objects::nonNull).toList();
                List<Relationship> outputs = internalEdge.pattern().outputs().stream()
                    .filter(output -> nodeIds.containsKey(output.what()))
                    .map(output -> new Relationship(nodeIds.get(output.what()), output.amount())).toList();
                long firingCount = cycle.solveResult() == null ? 0
                    : cycle.solveResult().patternTimes().getOrDefault(details, 0L);
                patterns.add(new PatternNode(patternNodeId, displayIdentity(details), inputs, outputs, firingCount,
                    firingCount > 0 ? CandidateStatus.SELECTED : CandidateStatus.REJECTED,
                    firingCount > 0 ? null : "CYCLIC_CANDIDATE", cycle.componentId()));
                for (Relationship output : outputs) {
                    edges.add(new Edge(output.materialNodeId(), patternNodeId, output.amount(), EdgeKind.PATTERN_OUTPUT,
                        firingCount > 0));
                }
                for (Relationship input : inputs) {
                    edges.add(new Edge(patternNodeId, input.materialNodeId(), input.amount(), EdgeKind.PATTERN_INPUT,
                        firingCount > 0));
                }
            }
            List<Integer> memberIds = cycle.members().stream().map(nodeIds::get).filter(java.util.Objects::nonNull)
                .toList();
            List<Edge> internal = new ArrayList<>();
            for (var internalEdge : cycle.internalEdges()) {
                Integer from = nodeIds.get(internalEdge.producer());
                Integer to = nodeIds.get(internalEdge.requiredInput());
                if (from != null && to != null) {
                    if (internalEdge.input().amountPerPattern().fitsLong()) {
                        internal.add(new Edge(from, to, internalEdge.input().amountPerPattern().longValueExact(),
                            EdgeKind.CYCLE_INTERNAL, true));
                    }
                }
            }
            for (var externalEdge : cycle.externalEdges()) {
                Integer from = nodeIds.get(externalEdge.producer());
                Integer to = nodeIds.get(externalEdge.requiredInput());
                if (from != null && to != null) {
                    if (externalEdge.input().amountPerPattern().fitsLong()) {
                        edges.add(new Edge(from, to, externalEdge.input().amountPerPattern().longValueExact(),
                            EdgeKind.PATTERN_INPUT, true));
                    }
                }
            }
            var solve = cycle.solveResult();
            Map<AEKey, Long> externalInputValues = new LinkedHashMap<>();
            Map<AEKey, PlannerAmount> exactExternalInputs = new LinkedHashMap<>();
            for (var edge : cycle.externalEdges()) {
                exactExternalInputs.merge(edge.requiredInput(), edge.input().amountPerPattern(), PlannerAmount::add);
            }
            exactExternalInputs.forEach((key, value) -> {
                if (value.fitsLong()) externalInputValues.put(key, value.longValueExact());
            });
            if (solve != null) solve.externalDemand().forEach(externalInputValues::put);
            List<KeyAmount> externalInputs = keyAmounts(externalInputValues);
            CycleDiagnostic diagnostic = cycleIndex < result.cycles().size() ? result.cycles().get(cycleIndex) : null;
            cycles.add(new CraftingGraphSnapshot.CycleGroup(cycle.componentId(), memberIds, internal,
                cycle.status().name(), keyAmounts(cycle.requiredOutputs()),
                externalInputs,
                solve == null ? List.of() : keyAmounts(solve.requiredSeed()),
                solve == null ? List.of() : solve.patternTimes().entrySet().stream()
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
                    ? cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionCountKnowledge.UNKNOWN
                    : diagnostic.executionCountKnowledge(),
                diagnostic == null ? cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus.NOT_IMPLEMENTED
                    : diagnostic.solveStatus()));
        }

        Integer rootId = rootKey == null ? null : nodeIds.get(rootKey);
        int rootNodeId = rootId == null ? (rootKey == null && !nodes.isEmpty() ? 0 : -1) : rootId;
        var summary = new CraftingGraphSnapshot.Summary(result.status().name(), nodes.size(), patterns.size(),
            edges.size(), cycles.size(), result.calculationNanos());
        return new CraftingGraphSnapshot(rootNodeId, nodes, patterns, edges, cycles, summary);
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
        for (GenericStack output : pattern.outputs()) {
            if (output != null && output.what() != null) materials.computeIfAbsent(output.what(), MutableMaterial::new);
        }
    }

    private static List<Relationship> selectedInputs(AEKey output, List<cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceEdge> edges,
            Map<AEKey, Integer> nodeIds) {
        List<Relationship> result = new ArrayList<>();
        for (var edge : edges) {
            if (edge.from().equals(output) && nodeIds.containsKey(edge.to())) {
                result.add(new Relationship(nodeIds.get(edge.to()), edge.amount()));
            }
        }
        return List.copyOf(result);
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
        return List.copyOf(result);
    }

    private static List<Relationship> inputs(IPatternDetails pattern, Map<AEKey, Integer> nodeIds) {
        List<Relationship> result = new ArrayList<>();
        try {
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0) continue;
                GenericStack possible = input.getPossibleInputs()[0];
                Integer id = nodeIds.get(possible.what());
                PlannerAmount amount = PlannerAmount.of(possible.amount()).multiply(input.getMultiplier());
                if (id != null && amount.fitsLong()) result.add(new Relationship(id, amount.longValueExact()));
            }
        } catch (RuntimeException ignored) {
            // A malformed/rejected candidate is still represented, but without unsafe relationship data.
        }
        return List.copyOf(result);
    }

    private static int componentFor(AEKey key, List<cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ComponentTrace> components) {
        for (var component : components) if (component.members().contains(key)) return component.componentId();
        return -1;
    }

    private static String displayIdentity(IPatternDetails pattern) {
        StringBuilder value = new StringBuilder(pattern.getClass().getSimpleName());
        try {
            if (!pattern.getOutputs().isEmpty()) value.append(':').append(pattern.getOutputs().getFirst().what());
        } catch (RuntimeException ignored) {
        }
        return value.toString();
    }

    private static List<KeyAmount> keyAmounts(Map<AEKey, Long> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(entry -> new KeyAmount(entry.getKey(), entry.getValue())).toList();
    }

    private static List<ExactKeyAmount> exactKeyAmounts(
            Map<AEKey, cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExactCycleAmount> values) {
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

        private MaterialNode freeze(int id) {
            MaterialStatus status = cycle ? MaterialStatus.CYCLE
                : unsupported ? MaterialStatus.UNSUPPORTED
                : missing > 0 ? MaterialStatus.MISSING
                : toCraft > 0 ? MaterialStatus.CRAFTING : MaterialStatus.SATISFIED;
            return new MaterialNode(id, key, requested, fromInventory, toCraft, missing, status,
                exactRequested.toString(), exactFromInventory.toString(), exactToCraft.toString(), exactMissing.toString());
        }
    }
}
