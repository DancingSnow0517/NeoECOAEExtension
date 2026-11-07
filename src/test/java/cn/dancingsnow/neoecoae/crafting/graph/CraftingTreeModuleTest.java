package cn.dancingsnow.neoecoae.crafting.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.*;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftingTreeModuleTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test
    void recursiveModuleKeepsExternalConnectionsAndUnrollsStockUntilExactDelivery() {
        CraftingGraphSnapshot snapshot = recursionFixture(true);
        var main = ClientCraftingGraph.main(snapshot, false);
        int module = ClientCraftingGraph.cycleVisualId(4);
        assertEquals(1, main.nodes().values().stream().filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP).count());
        assertFalse(main.nodes().containsKey(0), "The feedback seed is packaged inside the module");
        assertFalse(main.nodes().containsKey(-2), "The cyclic recipe is packaged inside the module");
        assertTrue(main.links().stream().anyMatch(link -> link.fromId() == -3 && link.toId() == module));
        assertTrue(main.links().stream().anyMatch(link -> link.fromId() == module && link.toId() == 1));
        assertTrue(main.links().stream().anyMatch(link -> link.fromId() == 3 && link.toId() == module),
            "A byproduct must remain connected to the module that produces it");
        assertTrue(main.links().stream().noneMatch(link -> link.fromId() == link.toId()));

        var graph = ClientCraftingGraph.cycle(snapshot, 4, false);
        assertTrue(graph.isRecursiveCycle());
        var seed = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.SEED).findFirst().orElseThrow();
        assertEquals(PlannerAmount.ONE, seed.recursion().amount());
        var stock = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.STOCK)
            .filter(node -> node.key().equals(seed.key())).map(node -> node.recursion().amount()).toList();
        assertEquals(List.of(PlannerAmount.of(2), PlannerAmount.of(4), PlannerAmount.of(8)), stock);
        assertEquals(PlannerAmount.of(7), graph.nodes().get(graph.rootId()).recursion().amount());
        assertEquals(Role.OUTPUT, graph.nodes().get(graph.rootId()).recursion().role());
        assertTrue(graph.nodes().values().stream().anyMatch(node -> node.recursion().role() == Role.RETAINED
            && node.key().equals(seed.key()) && node.recursion().amount().equals(PlannerAmount.ONE)));
        assertEquals(49L, graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.INPUT)
            .mapToLong(node -> node.recursion().amount().longValueExact()).sum());
        var layout = new GraphLayout(GraphLayout.Mode.COMPACT_TREE).layout(graph, 1);
        assertEquals(graph.nodes().keySet(), layout.boxes().keySet(),
            "Execution icons and delivery must be visible and inspectable too");
        for (var box : layout.boxes().values()) {
            if (graph.nodes().get(box.nodeId()).key() != null) {
                assertEquals(GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH, box.width());
                assertEquals(GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT, box.height());
            } else if (graph.nodes().get(box.nodeId()).recursion().role() == Role.STEP) {
                assertEquals(GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH, box.width(),
                    "The hammer must have a compact hit box instead of an invisible execution bar");
                assertEquals(box.width(), box.height());
            }
        }
        var firstStock = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.STOCK
            && node.key().equals(seed.key())).findFirst().orElseThrow();
        assertTrue(layout.box(firstStock.id()).y() + layout.box(firstStock.id()).height() < layout.box(seed.id()).y());
        assertEquals(layout.box(seed.id()).x(), layout.box(firstStock.id()).x(), "A material stays in its column");
        assertSame(snapshot.cycleGroups().get(0).recursion().layers().get(0), firstStock.recursion().layer(),
            "Cards and the execution icon share the same layer data");
        assertEquals(Set.of(Role.SEED, Role.STOCK, Role.INPUT, Role.OUTPUT, Role.RETAINED),
            layout.recursionRegions().stream().map(GraphLayoutSnapshot.RecursionRegion::role)
                .collect(java.util.stream.Collectors.toSet()));
        float stockLeft = layout.boxes().values().stream().filter(box -> graph.nodes().get(box.nodeId()).key() != null
            && graph.nodes().get(box.nodeId()).recursion().role() != Role.INPUT)
            .map(box -> box.x()).min(Float::compare).orElseThrow();
        for (var node : graph.nodes().values()) if (node.recursion().role() == Role.INPUT) {
            assertTrue(layout.box(node.id()).x() + layout.box(node.id()).width() < stockLeft,
                "External material cards belong to the left lane");
        }
        float remainingRight = layout.boxes().values().stream().filter(box -> graph.nodes().get(box.nodeId()).recursion().role() == Role.RETAINED)
            .map(box -> box.x() + box.width()).max(Float::compare).orElseThrow();
        assertTrue(layout.box(graph.rootId()).x() > remainingRight, "Delivery has its own right-hand zone");
        for (var link : graph.links()) {
            assertTrue(graph.nodes().get(link.fromId()).recursion().level()
                < graph.nodes().get(link.toId()).recursion().level(), "Every edge advances to a later layer");
            var route = layout.edgePoints(link);
            assertTrue(route.size() >= 2);
            assertTrue(route.get(0).y() > route.get(route.size() - 1).y(),
                "Every material or junction link must point upward");
            for (int i = 1; i < route.size(); i++) {
                assertTrue(route.get(i - 1).x() == route.get(i).x() || route.get(i - 1).y() == route.get(i).y());
                assertTrue(route.get(i - 1).y() >= route.get(i).y());
            }
        }
        for (var a : layout.boxes().values()) for (var b : layout.boxes().values()) {
            if (a.nodeId() != b.nodeId()) assertFalse(a.intersects(b.x(), b.y(), b.x() + b.width(), b.y() + b.height()));
        }
        assertEquals(4, snapshot.nodes().size(), "Occurrences must not mutate the synchronized material list");
    }

    @Test
    void unknownCycleShowsItsTargetWithoutInventingGrowthOrDelivery() {
        var graph = ClientCraftingGraph.cycle(recursionFixture(false), 4, false);
        assertTrue(graph.isRecursiveCycle());
        assertTrue(graph.nodes().values().stream().anyMatch(node -> node.recursion().role() == Role.UNKNOWN));
        assertFalse(graph.nodes().values().stream().anyMatch(node -> node.recursion().role() == Role.STEP
            || node.recursion().role() == Role.DELIVERY || node.recursion().role() == Role.RETAINED));
        assertTrue(graph.links().stream().noneMatch(ClientCraftingGraph.Link::selected));
        assertEquals(PlannerAmount.of(7), graph.nodes().get(graph.rootId()).recursion().amount());
        var layout = new GraphLayout().layout(graph, 1);
        assertEquals(graph.nodes().keySet(), layout.boxes().keySet());
        assertTrue(RecursiveCycleLedger.summarize(graph.nodes().get(graph.rootId()).cycle()).stream()
            .allMatch(balance -> balance.remaining() == null && balance.netChange() == null));
    }

    @Test
    void ledgerKeepsExactSeedReturnsByproductGainAndFullyConsumedExternalMaterials() {
        var original = recursionFixture(true);
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var byproduct = AEItemKey.of(Items.GOLD_NUGGET);
        var external = AEItemKey.of(Items.COAL);
        var huge = PlannerAmount.parse("10000000000000000000000000000000000000000000");
        var initial = List.of(new KeyAmount(seed, huge), new KeyAmount(byproduct, 16));
        var inputs = List.of(new KeyAmount(external, 144), new KeyAmount(AEItemKey.of(Items.GOLD_INGOT), 32));
        var cycle = new CycleGroup(4, List.of(0), List.of(), "MISSING", List.of(new KeyAmount(seed, 28)),
            inputs, initial, List.of(), List.of(), List.of(), List.of(),
            List.of(new KeyAmount(seed, huge.subtract(12)), new KeyAmount(byproduct, 16)), List.of(), List.of(),
            ExecutionCountKnowledge.EXACT, CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT,
            new Recursion(initial, List.of(new RecursionLayer(inputs,
                List.of(new KeyAmount(seed, huge.add(28)), new KeyAmount(byproduct, 229)), List.of(), false)), true));
        var snapshot = new CraftingGraphSnapshot(original.rootNodeId(), original.nodes(), original.patterns(),
            original.edges(), List.of(cycle), List.of(), original.summary());
        var graph = ClientCraftingGraph.cycle(snapshot, 4, false);
        var retained = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.RETAINED)
            .collect(java.util.stream.Collectors.toMap(ClientCraftingGraph.Node::key, ClientCraftingGraph.Node::recursion));
        assertEquals(huge, retained.get(seed).amount());
        assertEquals(PlannerAmount.ZERO, retained.get(seed).delta());
        assertEquals(PlannerAmount.of(12), retained.get(seed).balance().seedShortfall());
        assertEquals(PlannerAmount.of(213), retained.get(byproduct).delta());
        assertEquals(PlannerAmount.ZERO, retained.get(external).amount(), "Consumed inputs must still have a depleted card");
        assertEquals(PlannerAmount.of(144), retained.get(external).balance().external());
        assertEquals(PlannerAmount.ZERO, retained.get(external).delta(), "Net change is remainder minus seed, not minus external inputs");
        var layout = new GraphLayout().layout(graph, 1);
        var inputBoxes = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.INPUT)
            .map(node -> layout.box(node.id())).sorted(java.util.Comparator.comparingDouble(GraphLayoutSnapshot.Box::x)).toList();
        assertEquals(inputs.size(), inputBoxes.size());
        for (int i = 1; i < inputBoxes.size(); i++) {
            assertEquals(inputBoxes.get(0).y(), inputBoxes.get(i).y(), "All external additions for a layer share one row");
            assertTrue(inputBoxes.get(i - 1).x() + inputBoxes.get(i - 1).width() < inputBoxes.get(i).x());
        }
        for (var link : graph.links()) {
            var route = layout.edgePoints(link);
            for (var box : layout.boxes().values()) {
                if (box.nodeId() == link.fromId() || box.nodeId() == link.toId()) continue;
                for (int i = 1; i < route.size(); i++) {
                    var a = route.get(i - 1); var b = route.get(i);
                    boolean crosses = a.x() == b.x()
                        ? a.x() > box.x() && a.x() < box.x() + box.width()
                            && Math.max(a.y(), b.y()) > box.y() && Math.min(a.y(), b.y()) < box.y() + box.height()
                        : a.y() > box.y() && a.y() < box.y() + box.height()
                            && Math.max(a.x(), b.x()) > box.x() && Math.min(a.x(), b.x()) < box.x() + box.width();
                    assertFalse(crosses, "A layer input wire must go around other material cards");
                }
            }
        }
    }

    @Test
    void initialOutputStockIsNotMislabelledAsStartupSeed() {
        var original = recursionFixture(true);
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var cycle = new CycleGroup(4, List.of(0), List.of(), "NOT_REQUIRED", List.of(new KeyAmount(seed, 28)),
            List.of(), List.of(new KeyAmount(seed, 14)), List.of(), List.of(), List.of(), List.of(),
            List.of(new KeyAmount(seed, 42)), List.of(), List.of(), ExecutionCountKnowledge.EXACT,
            CycleSolveStatus.SUCCESS, new Recursion(List.of(new KeyAmount(seed, 42)), List.of(), true));
        var snapshot = new CraftingGraphSnapshot(original.rootNodeId(), original.nodes(), original.patterns(),
            original.edges(), List.of(cycle), List.of(), original.summary());
        var graph = ClientCraftingGraph.cycle(snapshot, 4, false);
        var nodes = graph.nodes().values().stream().filter(node -> node.recursion().level() == 0).toList();
        assertEquals(PlannerAmount.of(14), nodes.stream().filter(node -> node.recursion().role() == Role.SEED)
            .findFirst().orElseThrow().recursion().amount());
        assertEquals(PlannerAmount.of(28), nodes.stream().filter(node -> node.recursion().role() == Role.STOCK)
            .findFirst().orElseThrow().recursion().amount());
        var layout = new GraphLayout().layout(graph, 1);
        var a = layout.box(nodes.get(0).id()); var b = layout.box(nodes.get(1).id());
        assertFalse(a.intersects(b.x(), b.y(), b.x() + b.width(), b.y() + b.height()));
        assertEquals(PlannerAmount.of(14), RecursiveCycleLedger.summarize(cycle).get(0).remaining());
    }

    @Test
    void connectedCyclesOpenAsModulesWithTheirSharedMaterialBetweenThem() {
        var original = recursionFixture(true);
        var nextSeed = AEItemKey.of(Items.DIAMOND);
        var materials = new java.util.ArrayList<>(original.nodes());
        materials.add(new MaterialNode(4, nextSeed, 2, 1, 2, 0, MaterialStatus.CYCLE));
        var patterns = new java.util.ArrayList<>(original.patterns());
        patterns.add(new PatternNode(-4, "second growth", List.of(new Relationship(4, 1), new Relationship(3, 1)),
            List.of(new Relationship(4, 2)), 2, CandidateStatus.SELECTED, null, 5));
        var cycles = new java.util.ArrayList<>(original.cycleGroups());
        cycles.add(new CycleGroup(5, List.of(4), List.of(), "SOLVED", List.of(new KeyAmount(nextSeed, 2)),
            List.of(new KeyAmount(original.nodes().get(3).key(), 2)), List.of(new KeyAmount(nextSeed, 1)),
            List.of(new PatternAmount(-4, 2)), List.of()));
        var edges = new java.util.ArrayList<>(original.edges());
        edges.add(new Edge(4, -4, 2, EdgeKind.PATTERN_OUTPUT, true));
        edges.add(new Edge(-4, 4, 1, EdgeKind.PATTERN_INPUT, true));
        edges.add(new Edge(-4, 3, 1, EdgeKind.PATTERN_INPUT, true));
        var snapshot = new CraftingGraphSnapshot(4, materials, patterns, edges, cycles, List.of(),
            new Summary("SUCCESS", 5, 3, edges.size(), 2, 0));
        var main = ClientCraftingGraph.main(snapshot, false);
        var group = main.nodes().values().stream().filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER)
            .findFirst().orElseThrow();
        assertEquals(group.id(), main.rootId());
        assertFalse(main.nodes().containsKey(3), "The group's internal transfer must not form an outside feedback loop");
        var inside = ClientCraftingGraph.cluster(snapshot, group.cluster(), false);
        assertEquals(2, inside.nodes().values().stream().filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP).count());
        assertTrue(inside.nodes().values().stream().noneMatch(node -> node.kind() == ClientCraftingGraph.Kind.PATTERN));
        assertTrue(inside.links().stream().anyMatch(link -> link.fromId() == 3 && link.toId() == ClientCraftingGraph.cycleVisualId(4)));
        assertTrue(inside.links().stream().anyMatch(link -> link.fromId() == ClientCraftingGraph.cycleVisualId(5) && link.toId() == 3));
        var layout = new GraphLayout().layout(inside, 1);
        assertEquals(inside.nodes().keySet(), layout.boxes().keySet());
    }

    private static CraftingGraphSnapshot recursionFixture(boolean complete) {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var raw = AEItemKey.of(Items.COAL);
        var goal = AEItemKey.of(Items.GOLD_INGOT);
        var byproduct = AEItemKey.of(Items.GOLD_NUGGET);
        var layers = List.of(
            new RecursionLayer(List.of(new KeyAmount(raw, 7)), List.of(new KeyAmount(seed, 2), new KeyAmount(byproduct, 1)),
                List.of(new PatternAmount(-2, 1)), false),
            new RecursionLayer(List.of(new KeyAmount(raw, 14)), List.of(new KeyAmount(seed, 4), new KeyAmount(byproduct, 3)),
                List.of(new PatternAmount(-2, 2)), false),
            new RecursionLayer(List.of(new KeyAmount(raw, 28)), List.of(new KeyAmount(seed, 8), new KeyAmount(byproduct, 7)),
                List.of(new PatternAmount(-2, 4)), false));
        var cycle = new CycleGroup(4, List.of(0), List.of(), complete ? "SOLVED" : "UNKNOWN_BUDGET",
            List.of(new KeyAmount(seed, 7)), List.of(new KeyAmount(raw, 49)), List.of(new KeyAmount(seed, 1)),
            List.of(new PatternAmount(-2, 7)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            complete ? ExecutionCountKnowledge.EXACT : ExecutionCountKnowledge.UNKNOWN,
            complete ? CycleSolveStatus.SUCCESS : CycleSolveStatus.UNKNOWN_BUDGET,
            complete ? new Recursion(List.of(new KeyAmount(seed, 1)), layers, true) : Recursion.EMPTY);
        return new CraftingGraphSnapshot(2, List.of(
            new MaterialNode(0, seed, 7, 1, 7, 0, MaterialStatus.CYCLE),
            new MaterialNode(1, raw, 49, 49, 0, 0, MaterialStatus.SATISFIED),
            new MaterialNode(2, goal, 1, 0, 1, 0, MaterialStatus.CRAFTING),
            new MaterialNode(3, byproduct, 7, 0, 7, 0, MaterialStatus.CRAFTING)),
            List.of(new PatternNode(-2, "growth", List.of(new Relationship(0, 1), new Relationship(1, 7)),
                    List.of(new Relationship(0, 2), new Relationship(3, 1)), 7, CandidateStatus.SELECTED, null, 4),
                new PatternNode(-3, "finish", List.of(new Relationship(0, 7)), List.of(new Relationship(2, 1)),
                    1, CandidateStatus.SELECTED, null, -1)),
            List.of(new Edge(2, -3, 1, EdgeKind.PATTERN_OUTPUT, true),
                new Edge(-3, 0, 7, EdgeKind.PATTERN_INPUT, true),
                new Edge(0, -2, 2, EdgeKind.PATTERN_OUTPUT, true),
                new Edge(-2, 0, 1, EdgeKind.PATTERN_INPUT, true),
                new Edge(-2, 1, 7, EdgeKind.PATTERN_INPUT, true),
                new Edge(3, -2, 1, EdgeKind.PATTERN_OUTPUT, true)),
            List.of(cycle), List.of(), new Summary(complete ? "SUCCESS" : "CYCLE_UNRESOLVED", 4, 2, 6, 1, 0));
    }

    private static ClientCraftingGraph graph() {
        return ClientCraftingGraph.synthetic(0, Map.of(
            0, node(0), 1, node(1), 2, node(2)), List.of(
                new ClientCraftingGraph.Link(0, 1, 2,
                    CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true),
                new ClientCraftingGraph.Link(0, 2, 3,
                    CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true)));
    }

    private static ClientCraftingGraph.Node node(int id) {
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL,
            "Material " + id, null, null, null, null);
    }

    @Test
    void compactProjectionAndLayoutWorkWithoutOpeningAScreen() {
        var source = graph();
        var tree = CompactTreeProjection.project(source, 4, Set.of(), Set.of());
        assertTrue(tree.isCompactTree());
        assertEquals(3, source.nodes().size());
        assertEquals(2, source.links().size());
        var layout = new CompactTreeLayout().layout(tree, 1);
        assertEquals(tree.nodes().keySet(), layout.boxes().keySet());
        assertNotNull(layout.box(tree.rootId()));
        for (var link : tree.links()) {
            assertTrue(layout.edgePoints(link).size() >= 2);
            var parent = layout.box(link.fromId());
            var child = layout.box(link.toId());
            assertTrue(child.y() >= parent.y() + parent.height());
        }
        var root = layout.box(tree.rootId());
        assertTrue(layout.query(root.x(), root.y(), root.x() + root.width(),
            root.y() + root.height(), 0).contains(root));
    }

    @Test
    void layoutCacheSurvivesViewportQueriesAndRebuildsForNewGraph() {
        var source = graph();
        var cache = new GraphLayoutCache(GraphLayout.Mode.COMPACT_TREE);
        var first = cache.get(source);
        first.query(-100, -100, 500, 500, 0);
        first.query(1000, 1000, 1500, 1500, 0);
        assertSame(first, cache.get(source));
        assertNotSame(first, cache.get(graph()));
        cache.invalidate();
        assertNotSame(first, cache.get(source));
    }
}
