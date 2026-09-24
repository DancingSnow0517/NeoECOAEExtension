package cn.dancingsnow.neoecoae.crafting.planner.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class CondensationGraphTest {
    @Test
    void sharedInputKeepsOneTopologicalDependencyAndExecutesBeforeBothConsumers() throws Exception {
        TestKey goal = new TestKey("goal");
        TestKey left = new TestKey("left");
        TestKey right = new TestKey("right");
        TestKey source = new TestKey("source");
        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        for (AEKey key : List.of(goal, left, right, source)) {
            nodes.put(key, new CraftingGraphNode(key, List.of()));
        }
        var graph = new CraftingDependencyGraph(goal, nodes, List.of(
                edge(goal, left), edge(goal, right), edge(left, source), edge(right, source)));
        var sccs = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        var condensed = CondensationGraph.build(graph, sccs, ECOCancellation.NONE);

        assertEquals(4, condensed.components().size());
        assertEquals(4, condensed.dependencies().size());
        var topological = condensed.topologicalOrder();
        assertEquals(goal, topological.get(0).members().get(0));
        assertEquals(source, topological.get(3).members().get(0));
        assertEquals(source, condensed.executionOrder().get(0).members().get(0));
        assertEquals(goal, condensed.executionOrder().get(3).members().get(0));
        assertNull(condensed.componentFor(new TestKey("unreachable")));
    }

    private static CraftingGraphEdge edge(AEKey producer, AEKey input) {
        return new CraftingGraphEdge(producer, input, null, null);
    }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE = new AEKeyType(
                ResourceLocation.fromNamespaceAndPath("test", "graph"), TestKey.class, Component.empty()) {
            @Override public int getAmountPerByte() { return 1; }
            @Override public AEKey readFromPacket(FriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
            @Override public AEKey loadKeyFromTag(CompoundTag tag) { throw new UnsupportedOperationException(); }
        };
        private final String name;

        private TestKey(String name) { this.name = name; }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return name; }
        @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("test", name); }
        @Override public void writeToPacket(FriendlyByteBuf buffer) {}
        @Override protected Component computeDisplayName() { return Component.literal(name); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
