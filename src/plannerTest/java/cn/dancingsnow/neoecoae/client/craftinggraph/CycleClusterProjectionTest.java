package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CycleClusterProjectionTest {
    @Test
    void directAtoBBecomesOneVisualClusterButKeepsTwoSccs() {
        var snapshot = snapshot(2, List.of(flow(0, 1, 100, 4, 7)));
        var clusters = CycleClusterProjection.derive(snapshot);

        assertEquals(1, clusters.size());
        assertEquals(List.of(1, 2), clusters.getFirst().componentIds());
        assertEquals(100, clusters.getFirst().flows().getFirst().materialNodeId());
        var main = ClientCraftingGraph.main(snapshot, false);
        assertEquals(1, main.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER).count());
        assertEquals(0, main.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP).count());

        var focus = ClientCraftingGraph.cluster(snapshot, clusters.getFirst(), false);
        var layout = new CycleClusterLayout().layout(focus, 1);
        assertNotNull(layout.box(100), "the inter-cycle material remains explicit");
        assertTrue(layout.box(0).centerX() < layout.box(1).centerX(), "A must precede B in the condensation DAG");
    }

    @Test
    void chainAtoBtoCUsesMonotonicCondensationLayers() {
        var snapshot = snapshot(3, List.of(flow(0, 1, 100, 2, 3), flow(1, 2, 101, 5, 6)));
        CycleCluster cluster = CycleClusterProjection.derive(snapshot).getFirst();
        var layout = new CycleClusterLayout().layout(ClientCraftingGraph.cluster(snapshot, cluster, false), 1);

        assertEquals(List.of(1, 2, 3), cluster.componentIds());
        assertTrue(layout.box(0).centerX() < layout.box(1).centerX());
        assertTrue(layout.box(1).centerX() < layout.box(2).centerX());
    }

    @Test
    void multipleMaterialsBetweenTheSameSccPairStayDistinct() {
        var snapshot = snapshot(2, List.of(flow(0, 1, 100, 2, 5), flow(0, 1, 101, 11, 13)));
        CycleCluster cluster = CycleClusterProjection.derive(snapshot).getFirst();
        var focus = ClientCraftingGraph.cluster(snapshot, cluster, false);

        assertEquals(2, cluster.flows().size());
        assertEquals(java.util.Set.of(100, 101), cluster.flows().stream()
            .map(InterCycleFlow::materialNodeId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of(100, 101), focus.links().stream().filter(link -> link.materialNodeId() >= 100)
            .map(ClientCraftingGraph.Link::materialNodeId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4, focus.links().stream().filter(link -> link.materialNodeId() >= 100).count(),
            "each material keeps its producer and consumer edge; amounts are never summed");
    }

    @Test
    void collapsedMainGraphDedupeIncludesMaterialIdentity() {
        var materials = materials(4, List.of());
        var cycles = List.of(cycle(1, List.of(0, 1)), cycle(2, List.of(2, 3)));
        var edges = List.of(
            new CraftingGraphSnapshot.Edge(0, 2, 1, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true),
            new CraftingGraphSnapshot.Edge(1, 3, 1, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true));
        var snapshot = new CraftingGraphSnapshot(0, materials, List.of(), edges, cycles,
            summary(materials.size(), 0, cycles.size()));

        var main = ClientCraftingGraph.main(snapshot, false);
        assertEquals(2, main.links().size());
        assertEquals(java.util.Set.of(2, 3), main.links().stream().map(ClientCraftingGraph.Link::materialNodeId)
            .collect(java.util.stream.Collectors.toSet()));
        var compact = CompactTreeProjection.project(main, 4, java.util.Set.of(), java.util.Set.of());
        assertEquals(2, compact.links().size());
        assertEquals(java.util.Set.of(2, 3), compact.links().stream().map(ClientCraftingGraph.Link::materialNodeId)
            .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void forkAtoBAndAtoCPlacesConsumersInTheSameFollowingLayer() {
        var snapshot = snapshot(3, List.of(flow(0, 1, 100, 2, 3), flow(0, 2, 101, 5, 7)));
        CycleCluster cluster = CycleClusterProjection.derive(snapshot).getFirst();
        var layout = new CycleClusterLayout().layout(ClientCraftingGraph.cluster(snapshot, cluster, false), 1);

        assertTrue(layout.box(0).centerX() < layout.box(1).centerX());
        assertTrue(layout.box(0).centerX() < layout.box(2).centerX());
        assertEquals(layout.box(1).centerX(), layout.box(2).centerX(), 0.01);
    }

    @Test
    void mutuallyReachablePatternsAlreadyOwnedByOneSccAreNotClientMergedAgain() {
        var materials = materials(2, List.of());
        var pattern = new CraftingGraphSnapshot.PatternNode(-2, "mutual", List.of(
            new CraftingGraphSnapshot.Relationship(0, 1), new CraftingGraphSnapshot.Relationship(1, 1)), List.of(
                new CraftingGraphSnapshot.Relationship(0, 1), new CraftingGraphSnapshot.Relationship(1, 1)), 1,
            CraftingGraphSnapshot.CandidateStatus.SELECTED, null, 1);
        var cycle = cycle(1, List.of(0, 1));
        var snapshot = new CraftingGraphSnapshot(0, materials, List.of(pattern), List.of(), List.of(cycle),
            summary(materials.size(), 1, 1));

        assertTrue(CycleClusterProjection.derive(snapshot).isEmpty());
        var main = ClientCraftingGraph.main(snapshot, false);
        assertEquals(1, main.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP).count());
        assertEquals(0, main.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER).count());
    }

    @Test
    void isolatedSingleSccKeepsTheExistingCycleFocus() {
        var snapshot = snapshot(1, List.of());
        assertTrue(CycleClusterProjection.derive(snapshot).isEmpty());
        assertEquals(ClientCraftingGraph.View.CYCLE_FOCUS, ClientCraftingGraph.cycle(snapshot, 1, false).view());
    }

    private static CraftingGraphSnapshot snapshot(int componentCount, List<Flow> flows) {
        List<CraftingGraphSnapshot.MaterialNode> materials = materials(componentCount, flows);
        List<CraftingGraphSnapshot.PatternNode> patterns = new ArrayList<>();
        List<CraftingGraphSnapshot.CycleGroup> cycles = new ArrayList<>();
        for (int component = 0; component < componentCount; component++) {
            List<CraftingGraphSnapshot.Relationship> inputs = new ArrayList<>();
            List<CraftingGraphSnapshot.Relationship> outputs = new ArrayList<>();
            inputs.add(new CraftingGraphSnapshot.Relationship(component, 1));
            outputs.add(new CraftingGraphSnapshot.Relationship(component, 1));
            for (Flow flow : flows) {
                if (flow.from() == component) outputs.add(new CraftingGraphSnapshot.Relationship(
                    flow.materialId(), flow.outputAmount()));
                if (flow.to() == component) inputs.add(new CraftingGraphSnapshot.Relationship(
                    flow.materialId(), flow.inputAmount()));
            }
            patterns.add(new CraftingGraphSnapshot.PatternNode(-component - 2, "pattern-" + component, inputs,
                outputs, 1, CraftingGraphSnapshot.CandidateStatus.SELECTED, null, component + 1));
            cycles.add(cycle(component + 1, List.of(component)));
        }
        return new CraftingGraphSnapshot(0, materials, patterns, List.of(), cycles,
            summary(materials.size(), patterns.size(), cycles.size()));
    }

    private static List<CraftingGraphSnapshot.MaterialNode> materials(int componentCount, List<Flow> flows) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (int i = 0; i < componentCount; i++) ids.add(i);
        flows.forEach(flow -> ids.add(flow.materialId()));
        return ids.stream().map(id -> new CraftingGraphSnapshot.MaterialNode(id, null, 1, 0, 1, 0,
            CraftingGraphSnapshot.MaterialStatus.CYCLE)).toList();
    }

    private static CraftingGraphSnapshot.CycleGroup cycle(int componentId, List<Integer> members) {
        return new CraftingGraphSnapshot.CycleGroup(componentId, members, List.of(), "SOLVED", List.of(),
            List.of(), List.of(), List.of(), List.of());
    }

    private static CraftingGraphSnapshot.Summary summary(int materials, int patterns, int cycles) {
        return new CraftingGraphSnapshot.Summary("SUCCESS", materials, patterns, 0, cycles, 0);
    }

    private static Flow flow(int from, int to, int materialId, long outputAmount, long inputAmount) {
        return new Flow(from, to, materialId, outputAmount, inputAmount);
    }

    private record Flow(int from, int to, int materialId, long outputAmount, long inputAmount) {}
}
