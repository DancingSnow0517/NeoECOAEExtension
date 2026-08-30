package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.sun.management.ThreadMXBean;
import java.util.ArrayList;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CompactTreeGraphLayoutTest {
    private static final CraftingGraphSnapshot.EdgeKind INPUT = CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT;

    @Test
    void defaultProjectionExpandsExactlyFourLevelsAndCreatesFolderAfterwards() {
        var source = chain(8);
        var projected = CompactTreeProjection.project(source, CompactTreeProjection.DEFAULT_DEPTH, Set.of(), Set.of());

        assertEquals(6, projected.nodes().size(), "root plus four levels and one folder");
        assertEquals(1, projected.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).count());
        var folder = projected.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).findFirst().orElseThrow();
        var metadata = projected.compactTreeNode(folder.id());
        assertEquals(2, metadata.hiddenNodeCount());
        assertEquals(2, metadata.hiddenDepth());
        assertEquals(5, metadata.depth());
    }

    @Test
    void singleExpansionRemovesFolderAndPreservesTreePath() {
        var source = chain(8);
        var first = CompactTreeProjection.project(source, 4, Set.of(), Set.of());
        var folder = first.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).findFirst().orElseThrow();
        var path = first.compactTreeNode(folder.id()).path();
        var expanded = CompactTreeProjection.project(source, 4, Set.of(path), Set.of());

        assertEquals(1, expanded.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).count(),
            "one click reveals one more level and keeps a deeper folder");
        assertEquals(7, expanded.nodes().size());
    }

    @Test
    void collapsedBranchCanBeReappliedToAnAlreadyVisibleNode() {
        var source = chain(7);
        var expanded = CompactTreeProjection.project(source, 6, Set.of(), Set.of());
        var material = expanded.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL
                && expanded.compactTreeNode(node.id()).sourceId() == 1)
            .findFirst().orElseThrow();
        var path = expanded.compactTreeNode(material.id()).path();
        var collapsed = CompactTreeProjection.project(source, 6, Set.of(), Set.of(), Set.of(path));

        assertTrue(collapsed.nodes().values().stream().anyMatch(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER));
    }

    @Test
    void patternBecomesAnEdgeAnnotationInsteadOfAVisualNode() {
        var pattern = new ClientCraftingGraph.Node(-1, ClientCraftingGraph.Kind.PATTERN, "pattern", null, null,
            new CraftingGraphSnapshot.PatternNode(-1, "pattern", List.of(), List.of(), 2,
                CraftingGraphSnapshot.CandidateStatus.SELECTED, null, -1), null);
        var nodes = new LinkedHashMap<Integer, ClientCraftingGraph.Node>();
        nodes.put(0, material(0));
        nodes.put(-1, pattern);
        nodes.put(2, material(2));
        var source = ClientCraftingGraph.synthetic(0, nodes,
            List.of(link(0, -1), link(-1, 2, 2)));
        var projected = CompactTreeProjection.project(source, 4, Set.of(), Set.of());

        assertFalse(projected.nodes().values().stream().anyMatch(node -> node.kind() == ClientCraftingGraph.Kind.PATTERN));
        assertEquals(2, projected.nodes().size());
        assertEquals(2, projected.links().getFirst().amount());
        assertEquals(CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, projected.links().getFirst().kind());
    }

    @Test
    void sharedDependencyIsReadableAsTwoTreeOccurrences() {
        var source = graph(4, List.of(link(0, 1), link(0, 2), link(1, 3), link(2, 3)));
        var projected = CompactTreeProjection.project(source, 4, Set.of(), Set.of());

        assertEquals(5, projected.nodes().size());
        assertEquals(2, projected.compactTreeNodes().values().stream()
            .filter(node -> node.sourceId() == 3).count());
        assertEquals(1, projected.compactTreeNodes().values().stream()
            .filter(CompactTreeNode::reference).count());
        assertNotEquals(projected.compactTreeNodes().values().stream()
            .filter(node -> node.sourceId() == 3).findFirst().orElseThrow().id(),
            projected.compactTreeNodes().values().stream().filter(node -> node.sourceId() == 3)
                .skip(1).findFirst().orElseThrow().id());
    }

    @Test
    void compactLayoutIsTopDownAndUsesCompactDimensions() {
        var source = graph(4, List.of(link(0, 1), link(0, 2), link(1, 3), link(2, 3)));
        var projected = CompactTreeProjection.project(source, 4, Set.of(), Set.of());
        var layout = new CompactTreeLayout().layout(projected, 1);
        var root = layout.box(projected.rootId());
        var children = projected.links().stream().filter(link -> link.fromId() == projected.rootId()).toList();

        assertEquals(GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH, root.width());
        assertEquals(GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT, root.height());
        assertEquals(20, root.width());
        assertEquals(30, root.height());
        for (var link : children) assertTrue(layout.box(link.toId()).y() > root.y());
        assertTrue(layout.bounds().height() > GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT);
        assertTrue(layout.edgePoints(children.getFirst()).size() >= 2);
    }

    @Test
    void cycleGroupStaysAsOneSpecialTreeNode() {
        int cycleId = ClientCraftingGraph.cycleVisualId(2);
        var cycle = new CraftingGraphSnapshot.CycleGroup(2, List.of(4, 5), List.of(), "SOLVED", List.of(),
            List.of(), List.of(), List.of(), List.of());
        var nodes = new LinkedHashMap<Integer, ClientCraftingGraph.Node>();
        nodes.put(0, material(0));
        nodes.put(cycleId, new ClientCraftingGraph.Node(cycleId, ClientCraftingGraph.Kind.CYCLE_GROUP, "Cycle #2",
            null, null, null, cycle));
        nodes.put(1, material(1));
        var projected = CompactTreeProjection.project(ClientCraftingGraph.synthetic(0, nodes,
            List.of(link(0, cycleId), link(cycleId, 1))), 4, Set.of(), Set.of());

        assertEquals(1, projected.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP).count());
        assertEquals(0, projected.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).count());
    }

    @Test
    void rawCycleDoesNotExpandForeverInTheClientProjection() {
        var source = graph(3, List.of(link(0, 1), link(1, 2), link(2, 1)));
        var projected = CompactTreeProjection.project(source, 4, Set.of(), Set.of());

        assertEquals(4, projected.nodes().size(), "the repeated cycle occurrence is bounded by the current path");
    }

    @Test
    void folderSummaryPropagatesMissingAndUnsupportedStatus() {
        Map<Integer, ClientCraftingGraph.Node> nodes = new LinkedHashMap<>();
        nodes.put(0, materialWithStatus(0, CraftingGraphSnapshot.MaterialStatus.CRAFTING));
        nodes.put(1, materialWithStatus(1, CraftingGraphSnapshot.MaterialStatus.MISSING));
        nodes.put(2, materialWithStatus(2, CraftingGraphSnapshot.MaterialStatus.UNSUPPORTED));
        var source = ClientCraftingGraph.synthetic(0, nodes,
            List.of(link(0, 1), link(1, 2)));
        var projected = CompactTreeProjection.project(source, 0, Set.of(), Set.of());
        var folder = projected.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.FOLDER).findFirst().orElseThrow();
        var summary = projected.compactTreeNode(folder.id());

        assertTrue(summary.containsMissing());
        assertTrue(summary.containsUnsupported());
        assertEquals(1, summary.hiddenNodeCount());
        var folderBox = new CompactTreeLayout().layout(projected, 1).box(folder.id());
        assertEquals(20, folderBox.width());
        assertEquals(30, folderBox.height());
    }

    @Test
    void cycleFocusStillUsesCircularLayoutWhenMainModeIsCompactTree() {
        var keys = List.of(new TestKey("cycle-focus-a"), new TestKey("cycle-focus-b"),
            new TestKey("cycle-focus-c"));
        var materials = new ArrayList<CraftingGraphSnapshot.MaterialNode>();
        for (int i = 0; i < keys.size(); i++) {
            materials.add(new CraftingGraphSnapshot.MaterialNode(4 + i, keys.get(i), 1, 0, 1, 0,
                CraftingGraphSnapshot.MaterialStatus.CYCLE));
        }
        var cycle = new CraftingGraphSnapshot.CycleGroup(4, List.of(4, 5, 6), List.of(), "SOLVED",
            List.of(new CraftingGraphSnapshot.KeyAmount(keys.getFirst(), 1)), List.of(), List.of(), List.of(), List.of());
        var edges = List.of(new CraftingGraphSnapshot.Edge(4, 5, 1, INPUT, true),
            new CraftingGraphSnapshot.Edge(5, 6, 1, INPUT, true),
            new CraftingGraphSnapshot.Edge(6, 4, 1, INPUT, true));
        var snapshot = new CraftingGraphSnapshot(4, materials, List.of(), edges, List.of(cycle),
            new CraftingGraphSnapshot.Summary("SUCCESS", 3, 0, 3, 1, 0));
        var focus = ClientCraftingGraph.cycle(snapshot, 4, false);
        var layout = new GraphLayout(GraphLayout.Mode.COMPACT_TREE).layout(focus, 1);

        assertEquals(3, layout.boxes().size());
        assertNotEquals(layout.box(4).x(), layout.box(5).x());
        assertNotEquals(layout.box(4).y(), layout.box(5).y());
        assertEquals(3, layout.edgeRoutes().size());
    }

    @Test
    void deepExpandedTreeUsesExplicitStack() {
        var source = chain(1_000);
        String rootPath = CompactTreeProjection.pathTo(source, source.rootId()).getFirst();
        var projected = CompactTreeProjection.project(source, 4, Set.of(), Set.of(rootPath));
        var layout = new CompactTreeLayout().layout(projected, 1);

        assertEquals(1_000, projected.nodes().size());
        assertEquals(1_000, layout.boxes().size());
    }

    @Test
    void searchPathIncludesAncestorsForAutomaticExpansion() {
        var source = chain(8);
        assertEquals(6, CompactTreeProjection.pathTo(source, 5).size());
        assertEquals(List.of(), CompactTreeProjection.pathTo(source, 99));
    }

    @Test
    void defaultGraphLayoutAutomaticallyAdaptsUnprojectedGraph() {
        var source = chain(10);
        var layout = new GraphLayout().layout(source, 1);
        assertEquals(GraphLayout.Mode.COMPACT_TREE, new GraphLayout().mode());
        assertTrue(layout.boxes().size() < source.nodes().size());
        assertTrue(layout.bounds().height() > GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT);
    }

    @Test
    void compactTreeFailureFallsBackWithoutLosingTheSnapshot() {
        var source = chain(4);
        var legacy = new LayeredGraphLayout();
        var expected = legacy.layout(source, 3);
        var facade = new GraphLayout(GraphLayout.Mode.COMPACT_TREE, legacy, new CircularCycleLayout(),
            (graph, version) -> { throw new IllegalStateException("test tree failure"); });

        var actual = facade.layout(source, 3);
        assertEquals(expected.boxes(), actual.boxes());
        assertEquals(expected.version(), actual.version());
    }

    @Test
    void compactTreeBenchmarkReportsOneTenAndTwentyThousandNodes() {
        new GraphLayout(GraphLayout.Mode.COMPACT_TREE).layout(chain(16), 0);
        new LayeredGraphLayout().layout(chain(16), 0);
        for (int size : List.of(100, 1_000, 10_000, 20_000)) {
            var source = chain(size);
            long legacyStarted = System.nanoTime();
            var legacy = new LayeredGraphLayout().layout(source, 1);
            long legacyNanos = System.nanoTime() - legacyStarted;
            BenchmarkResult depth4 = benchmark(source, CompactTreeProjection.DEFAULT_DEPTH, Set.of(), Set.of());
            BenchmarkResult depth6 = benchmark(source, 6, Set.of(), Set.of());
            List<String> path = CompactTreeProjection.pathTo(source, Math.min(size - 1, 64));
            Set<String> fullBranch = path.size() > 2 ? Set.of(path.get(2)) : Set.of();
            BenchmarkResult branch = size <= 1_000
                ? benchmark(source, CompactTreeProjection.DEFAULT_DEPTH, Set.of(), fullBranch)
                : BenchmarkResult.notMeasured(depth4.layoutNodes);
            System.out.printf("ECO compact benchmark %,d: legacy=%.3fms depth4=%.3fms depth6=%.3fms "
                    + "branch=%.3fms projected=%d/%d layoutNodes=%d/%d/%d visible=%d/%d/%d "
                    + "projection=%.3f/%.3f/%.3fms layout=%.3f/%.3f/%.3fms allocated=%d/%d/%dB%n", size,
                legacyNanos / 1_000_000.0, depth4.totalNanos / 1_000_000.0,
                depth6.totalNanos / 1_000_000.0, branch.totalNanos / 1_000_000.0, depth4.projectedNodes,
                size, depth4.layoutNodes, depth6.layoutNodes, branch.layoutNodes, depth4.visibleNodes,
                depth6.visibleNodes, branch.visibleNodes, depth4.projectionNanos / 1_000_000.0,
                depth6.projectionNanos / 1_000_000.0, branch.projectionNanos / 1_000_000.0,
                depth4.layoutNanos / 1_000_000.0, depth6.layoutNanos / 1_000_000.0,
                branch.layoutNanos / 1_000_000.0, depth4.allocatedBytes, depth6.allocatedBytes,
                branch.allocatedBytes);
            assertTrue(depth4.layoutNodes <= CompactTreeProjection.DEFAULT_DEPTH + 2);
            assertTrue(depth6.layoutNodes <= 8);
            if (size <= 1_000) assertTrue(branch.layoutNodes >= depth4.layoutNodes);
        }
    }

    private static BenchmarkResult benchmark(ClientCraftingGraph source, int depth, Set<String> expanded,
            Set<String> fullyExpanded) {
        long before = allocatedBytes();
        long projectionStarted = System.nanoTime();
        var projected = CompactTreeProjection.project(source, depth, expanded, fullyExpanded);
        long projectionNanos = System.nanoTime() - projectionStarted;
        long layoutStarted = System.nanoTime();
        var layout = new CompactTreeLayout().layout(projected, 1);
        long layoutNanos = System.nanoTime() - layoutStarted;
        long after = allocatedBytes();
        long allocation = before < 0 || after < before ? -1 : after - before;
        return new BenchmarkResult(projected.nodes().size(), layout.boxes().size(), layout.boxes().size(),
            projectionNanos, layoutNanos, projectionNanos + layoutNanos, allocation);
    }

    private record BenchmarkResult(int projectedNodes, int layoutNodes, int visibleNodes, long projectionNanos,
            long layoutNanos, long totalNanos, long allocatedBytes) {
        private static BenchmarkResult notMeasured(int visibleNodes) {
            return new BenchmarkResult(0, 0, visibleNodes, 0, 0, 0, -1);
        }
    }

    private static final ThreadMXBean ALLOCATION_BEAN = allocationBean();

    private static ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean threadBean) || !threadBean.isThreadAllocatedMemorySupported()) return null;
        if (!threadBean.isThreadAllocatedMemoryEnabled()) threadBean.setThreadAllocatedMemoryEnabled(true);
        return threadBean;
    }

    private static long allocatedBytes() {
        return ALLOCATION_BEAN == null ? -1
            : ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static final class TestKey extends AEKey {
        private static final Type TYPE = new Type();
        private static boolean initialized;
        private final String value;

        private TestKey(String value) { this.value = value; }

        private static TestKey of(String value) {
            if (!initialized) {
                var registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
                AEKeyTypesInternal.setRegistry(registry);
                AEKeyTypesInternal.register(TYPE);
                registry.freeze();
                initialized = true;
            }
            return new TestKey(value);
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("value", value);
            return tag;
        }
        @Override public Object getPrimaryKey() { return value; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("neoecoae_test", value);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { data.writeUtf(value); }
        @Override protected Component computeDisplayName() { return Component.literal(value); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && value.equals(other.value);
        }
        @Override public int hashCode() { return Objects.hash(value); }

        private static final class Type extends AEKeyType {
            private Type() {
                super(ResourceLocation.fromNamespaceAndPath("neoecoae_test", "compact_tree_keys"), TestKey.class,
                    Component.literal("Compact Tree Keys"));
            }

            @Override public MapCodec<? extends AEKey> codec() { throw new UnsupportedOperationException(); }
            @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
                return new TestKey(input.readUtf());
            }
        }
    }

    private static ClientCraftingGraph chain(int size) {
        List<ClientCraftingGraph.Link> links = new ArrayList<>();
        for (int i = 0; i + 1 < size; i++) links.add(link(i, i + 1));
        return graph(size, links);
    }

    private static ClientCraftingGraph graph(int size, List<ClientCraftingGraph.Link> links) {
        Map<Integer, ClientCraftingGraph.Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) nodes.put(i, material(i));
        return ClientCraftingGraph.synthetic(0, nodes, links);
    }

    private static ClientCraftingGraph.Node material(int id) {
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL, "node-" + id, null, null,
            null, null);
    }

    private static ClientCraftingGraph.Node materialWithStatus(int id, CraftingGraphSnapshot.MaterialStatus status) {
        var material = new CraftingGraphSnapshot.MaterialNode(id, null, 1, 0, 1,
            status == CraftingGraphSnapshot.MaterialStatus.MISSING ? 1 : 0, status);
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL, "node-" + id, null, material,
            null, null);
    }

    private static ClientCraftingGraph.Link link(int from, int to) {
        return link(from, to, 1);
    }

    private static ClientCraftingGraph.Link link(int from, int to, long amount) {
        return new ClientCraftingGraph.Link(from, to, amount, INPUT, true);
    }

}
