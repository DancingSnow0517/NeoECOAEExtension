package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

class CraftingGraphProductionAcceptanceTest {
    @Test void snapshotSerializationAndFirstFramePipelineAtProductionScales() {
        benchmark(1_000);
        benchmark(10_000);
        benchmark(20_000);
    }

    @Test void equalUnchangedSnapshotIsNotSentByGuiSyncUpdate() {
        var host = new SyncHost();
        var sync = new DataSynchronization(host);
        var initial = buffer();
        sync.writeFull(initial);
        assertFalse(sync.hasChanges());

        host.snapshot = new CraftingGraphSnapshot(-1, List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("EMPTY", 0, 0, 0, 0, 0));
        assertFalse(sync.hasChanges(), "record equality must suppress an equal reconstructed snapshot");
        var update = buffer();
        sync.writeUpdate(update);
        assertTrue(update.readableBytes() <= 5, "unchanged update must contain only AE2's field terminator");
    }

    @Test void cameraFitAndZoomDoNotChangeLayoutVersion() {
        var graph = ClientCraftingGraph.main(snapshot(1_000), false);
        var cache = new GraphLayoutCache();
        var initial = cache.get(graph);
        float cameraX = 120;
        float cameraY = -45;
        float zoom = 0.75f;
        GraphRenderer.collectVisible(initial, cameraX, cameraY, zoom, 1920, 1080);
        assertSame(initial, cache.get(graph));
        assertEquals(1, initial.version());
    }

    @Test void collapseChangesLayoutVersionExactlyOnce() {
        var graph = ClientCraftingGraph.main(snapshot(100), false);
        var cache = new GraphLayoutCache();
        var full = cache.get(graph);
        var collapsed = graph.limited(graph.rootId(), 4, Set.of(graph.rootId()));
        var after = cache.get(collapsed);
        assertEquals(full.version() + 1, after.version());
        assertSame(after, cache.get(collapsed));
    }

    @Test void cycleFocusEnterAndExitInvalidateCacheOncePerTransition() {
        var snapshot = cycleSnapshot();
        var main = ClientCraftingGraph.main(snapshot, false);
        var focus = ClientCraftingGraph.cycle(snapshot, 7, false);
        var cache = new GraphLayoutCache();
        var mainLayout = cache.get(main);
        var focusLayout = cache.get(focus);
        var returnLayout = cache.get(main);
        assertEquals(mainLayout.version() + 1, focusLayout.version());
        assertEquals(focusLayout.version() + 1, returnLayout.version());
        assertNotSame(mainLayout, returnLayout);
    }

    @Test void depthAndAdvancedToggleInvalidateOnceWithoutExtraCacheMisses() {
        var source = snapshotWithRejected(100);
        var base = ClientCraftingGraph.main(source, false);
        var cache = new GraphLayoutCache();
        var initial = cache.get(base);
        var depthLimited = base.limited(base.rootId(), 2, Set.of());
        var depthLayout = cache.get(depthLimited);
        assertEquals(initial.version() + 1, depthLayout.version());
        assertSame(depthLayout, cache.get(depthLimited));

        var advanced = ClientCraftingGraph.main(source, true);
        var advancedLayout = cache.get(advanced);
        assertEquals(depthLayout.version() + 1, advancedLayout.version());
        assertEquals(base.nodes().size() + 1, advanced.nodes().size());
        assertSame(advancedLayout, cache.get(advanced));
    }

    @Test void twentyThousandNodeQueryDoesNotVisitWholeModel() {
        var graph = ClientCraftingGraph.main(snapshot(20_000), false);
        var layout = new LayeredGraphLayout().layout(graph, 1);
        int candidates = layout.candidateNodeCount(-10, -10, 800, 300, 0);
        assertTrue(candidates < 20, "grid query candidates=" + candidates);
        assertEquals(20_000, graph.nodes().size());
    }

    @Test void crossingEdgeWithBothEndpointsOutsideIsDeliberatelyCulled() {
        var nodes = new java.util.LinkedHashMap<Integer, ClientCraftingGraph.Node>();
        nodes.put(0, node(0));
        nodes.put(1, node(1));
        var graph = ClientCraftingGraph.synthetic(0, nodes,
            List.of(new ClientCraftingGraph.Link(0, 1, 1, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true)));
        var boxes = java.util.Map.of(
            0, new GraphLayoutSnapshot.Box(0, -1000, 50, 132, 76),
            1, new GraphLayoutSnapshot.Box(1, 1000, 50, 132, 76));
        var layout = new GraphLayoutSnapshot(boxes, graph.links(),
            new GraphLayoutSnapshot.Bounds(-1000, 50, 1132, 126), 0, 1);
        assertTrue(layout.queryLinks(-100, 0, 100, 200, 0).isEmpty());
    }

    private static void benchmark(int totalNodes) {
        CraftingGraphSnapshot source = snapshot(totalNodes);
        long encodeStarted = System.nanoTime();
        RegistryFriendlyByteBuf encoded = buffer();
        source.writeToPacket(encoded);
        long encodeNanos = System.nanoTime() - encodeStarted;
        int bytes = encoded.readableBytes();
        byte[] payload = new byte[bytes];
        encoded.getBytes(0, payload);

        long decodeStarted = System.nanoTime();
        CraftingGraphSnapshot decoded = new CraftingGraphSnapshot(
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload), RegistryAccess.EMPTY));
        long decodeNanos = System.nanoTime() - decodeStarted;
        assertEquals(source, decoded);
        long modelStarted = System.nanoTime();
        ClientCraftingGraph graph = ClientCraftingGraph.main(decoded, false);
        long modelNanos = System.nanoTime() - modelStarted;
        long firstFrameStarted = System.nanoTime();
        GraphLayoutSnapshot layout = new LayeredGraphLayout().layout(graph, 1);
        var visible = GraphRenderer.collectVisible(layout, 0, 30, 1, 1920, 1080);
        long firstFrameNanos = System.nanoTime() - firstFrameStarted;
        long renderStarted = System.nanoTime();
        GraphRenderer.collectVisible(layout, 0, 30, 1, 1920, 1080);
        long renderNanos = System.nanoTime() - renderStarted;
        long pipelineNanos = decodeNanos + modelNanos + firstFrameNanos;

        assertEquals(totalNodes, graph.nodes().size());
        assertTrue(visible.nodes().size() < 100);
        System.out.printf(Locale.ROOT,
            "ECO pipeline %,d: bytes=%d encode=%.3fms decode=%.3fms model=%.3fms layout=%.3fms "
                + "index=%.3fms firstSync=%.3fms firstFrame=%.3fms pipeline=%.3fms visible=%d/%d "
                + "renderPlan=%.3fms version=%d%n",
            totalNodes, bytes, ms(encodeNanos), ms(decodeNanos), ms(modelNanos), ms(layout.layoutNanos()),
            ms(layout.spatialIndexNanos()), ms(encodeNanos + decodeNanos), ms(firstFrameNanos), ms(pipelineNanos),
            visible.nodes().size(), visible.edges().size(), ms(renderNanos), layout.version());
    }

    private static CraftingGraphSnapshot snapshot(int totalNodes) {
        int materials = (totalNodes + 1) / 2;
        int patternCount = totalNodes - materials;
        var key = BenchmarkKey.of("synthetic_material");
        var nodes = new ArrayList<CraftingGraphSnapshot.MaterialNode>(materials);
        for (int i = 0; i < materials; i++) {
            nodes.add(new CraftingGraphSnapshot.MaterialNode(i, key, i + 1L, i % 3, i + 1L, 0,
                CraftingGraphSnapshot.MaterialStatus.CRAFTING));
        }
        var patterns = new ArrayList<CraftingGraphSnapshot.PatternNode>(patternCount);
        var edges = new ArrayList<CraftingGraphSnapshot.Edge>(patternCount * 2);
        for (int i = 0; i < patternCount; i++) {
            int id = -i - 2;
            int output = i % materials;
            int input = (i + 1) % materials;
            patterns.add(new CraftingGraphSnapshot.PatternNode(id, "SyntheticPattern:minecraft:stone",
                List.of(new CraftingGraphSnapshot.Relationship(input, 1)),
                List.of(new CraftingGraphSnapshot.Relationship(output, 1)), 1,
                CraftingGraphSnapshot.CandidateStatus.SELECTED, null, -1));
            edges.add(new CraftingGraphSnapshot.Edge(output, id, 1,
                CraftingGraphSnapshot.EdgeKind.PATTERN_OUTPUT, true));
            edges.add(new CraftingGraphSnapshot.Edge(id, input, 1,
                CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true));
        }
        return new CraftingGraphSnapshot(0, nodes, patterns, edges, List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", materials, patternCount, edges.size(), 0, 0));
    }

    private static CraftingGraphSnapshot cycleSnapshot() {
        var key = BenchmarkKey.of("cycle_material");
        var material = new CraftingGraphSnapshot.MaterialNode(0, key, 1, 0, 1, 0,
            CraftingGraphSnapshot.MaterialStatus.CYCLE);
        var cycle = new CraftingGraphSnapshot.CycleGroup(7, List.of(0), List.of(), "SOLVED",
            List.of(new CraftingGraphSnapshot.KeyAmount(key, 1)), List.of(), List.of(), List.of(), List.of());
        return new CraftingGraphSnapshot(0, List.of(material), List.of(), List.of(), List.of(cycle),
            new CraftingGraphSnapshot.Summary("SUCCESS", 1, 0, 0, 1, 0));
    }

    private static CraftingGraphSnapshot snapshotWithRejected(int totalNodes) {
        var source = snapshot(totalNodes);
        var patterns = new ArrayList<>(source.patterns());
        patterns.add(new CraftingGraphSnapshot.PatternNode(-1_000_000, "RejectedSyntheticPattern",
            List.of(), List.of(), 0, CraftingGraphSnapshot.CandidateStatus.REJECTED, "MISSING", -1));
        return new CraftingGraphSnapshot(source.rootNodeId(), source.nodes(), patterns, source.edges(),
            source.cycleGroups(), new CraftingGraphSnapshot.Summary("SUCCESS", source.nodes().size(), patterns.size(),
                source.edges().size(), source.cycleGroups().size(), 0));
    }

    private static ClientCraftingGraph.Node node(int id) {
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL, "node-" + id,
            null, null, null, null);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }

    private static final class SyncHost {
        @GuiSync(1)
        public CraftingGraphSnapshot snapshot = CraftingGraphSnapshot.EMPTY;
    }

    private static final class BenchmarkKey extends AEKey {
        private static final Type TYPE = new Type();
        private static boolean initialized;
        private final String value;

        private BenchmarkKey(String value) { this.value = value; }

        static BenchmarkKey of(String value) {
            if (!initialized) {
                var registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
                AEKeyTypesInternal.setRegistry(registry);
                AEKeyTypesInternal.register(TYPE);
                registry.freeze();
                initialized = true;
            }
            return new BenchmarkKey(value);
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
            return obj instanceof BenchmarkKey other && value.equals(other.value);
        }
        @Override public int hashCode() { return Objects.hash(value); }

        private static final class Type extends AEKeyType {
            private Type() {
                super(ResourceLocation.fromNamespaceAndPath("neoecoae_test", "benchmark_keys"), BenchmarkKey.class,
                    Component.literal("Benchmark Keys"));
            }
            @Override public MapCodec<? extends AEKey> codec() { throw new UnsupportedOperationException(); }
            @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
                return new BenchmarkKey(input.readUtf());
            }
        }
    }
}
