package cn.dancingsnow.neoecoae.crafting.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CycleStripLayoutTest {
    private static final int CRYSTAL = 0;
    private static final int SEED = 1;
    private static final int DUST = 2;
    private static final int WATER = 3;
    private static final int BYPRODUCT = 4;
    private static final int GROW = 10;
    private static final int MAKE_SEED = 11;

    /** crystal -> [make seed] -> seed -> [grow] -> crystal, fed by dust and water, leaking one byproduct. */
    private static ClientCraftingGraph seedLoop() {
        List<AEKey> keys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AEKey key = mock(AEKey.class);
            when(key.getDisplayName()).thenReturn(Component.literal("key" + i));
            keys.add(key);
        }
        List<CraftingGraphSnapshot.MaterialNode> materials = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            materials.add(new CraftingGraphSnapshot.MaterialNode(i, keys.get(i), 1, 0, 0, i == DUST ? 5 : 0,
                i < 2 ? CraftingGraphSnapshot.MaterialStatus.CYCLE : CraftingGraphSnapshot.MaterialStatus.MISSING));
        }
        var selected = CraftingGraphSnapshot.CandidateStatus.SELECTED;
        var grow = new CraftingGraphSnapshot.PatternNode(GROW, "grow",
            List.of(new CraftingGraphSnapshot.Relationship(SEED, 1)),
            List.of(new CraftingGraphSnapshot.Relationship(CRYSTAL, 1)), 4, selected, null, 7);
        var makeSeed = new CraftingGraphSnapshot.PatternNode(MAKE_SEED, "make seed",
            List.of(new CraftingGraphSnapshot.Relationship(CRYSTAL, 1),
                new CraftingGraphSnapshot.Relationship(DUST, 2),
                new CraftingGraphSnapshot.Relationship(WATER, 1)),
            List.of(new CraftingGraphSnapshot.Relationship(SEED, 2),
                new CraftingGraphSnapshot.Relationship(BYPRODUCT, 1)), 2, selected, null, 7);
        var cycle = new CraftingGraphSnapshot.CycleGroup(7, List.of(CRYSTAL, SEED), List.of(), "OK",
            List.of(new CraftingGraphSnapshot.KeyAmount(keys.get(CRYSTAL), 64)), List.of(), List.of(), List.of(),
            List.of());
        var snapshot = new CraftingGraphSnapshot(CRYSTAL, materials, List.of(grow, makeSeed), List.of(),
            List.of(cycle), List.of(), new CraftingGraphSnapshot.Summary("OK", 5, 2, 0, 1, 0));
        return ClientCraftingGraph.cycle(snapshot, 7, false);
    }

    @Test
    void cycleUnrollsIntoOneRowEndingWithTheRequiredOutput() {
        var graph = seedLoop();
        var layout = new CycleStripLayout().layout(graph, 1);
        var makeSeed = layout.box(MAKE_SEED);
        var seed = layout.box(SEED);
        var grow = layout.box(GROW);
        var crystal = layout.box(CRYSTAL);

        assertTrue(makeSeed.x() < seed.x() && seed.x() < grow.x() && grow.x() < crystal.x(),
            "flow order runs left to right and the required output closes the strip");
        for (var box : List.of(makeSeed, seed, grow)) assertEquals(crystal.centerY(), box.centerY(), 0.01);

        float coreTop = Math.min(crystal.y(), makeSeed.y());
        float coreBottom = Math.max(crystal.y() + crystal.height(), makeSeed.y() + makeSeed.height());
        assertTrue(layout.box(DUST).y() + layout.box(DUST).height() < coreTop, "external inputs sit above");
        assertTrue(layout.box(WATER).y() + layout.box(WATER).height() < coreTop, "external inputs sit above");
        assertTrue(layout.box(BYPRODUCT).y() > coreBottom, "boundary outputs sit below");
    }

    @Test
    void everyRouteIsOrthogonalAndTouchesBothEndpoints() {
        var graph = seedLoop();
        var layout = new CycleStripLayout().layout(graph, 1);
        assertEquals(graph.links().size(), layout.edgeRoutes().size());
        for (var link : graph.links()) {
            var route = layout.edgePoints(link);
            assertTrue(route.size() >= 2, () -> "missing route for " + link);
            for (int i = 1; i < route.size(); i++) {
                var a = route.get(i - 1);
                var b = route.get(i);
                assertTrue(a.x() == b.x() || a.y() == b.y(), () -> "diagonal segment in " + link + ": " + route);
            }
            assertOnBorder(layout.box(link.fromId()), route.getFirst());
            assertOnBorder(layout.box(link.toId()), route.getLast());
        }
    }

    @Test
    void loopClosureRunsBelowTheRowBackToTheStart() {
        var graph = seedLoop();
        var layout = new CycleStripLayout().layout(graph, 1);
        var closure = graph.links().stream()
            .filter(link -> link.fromId() == CRYSTAL && link.toId() == MAKE_SEED).findFirst().orElseThrow();
        var route = layout.edgePoints(closure);
        var crystal = layout.box(CRYSTAL);
        assertTrue(route.getLast().x() < route.getFirst().x(), "closure travels back to the left");
        float lowest = (float) route.stream().mapToDouble(GraphLayoutSnapshot.Point::y).max().orElseThrow();
        assertTrue(lowest > crystal.y() + crystal.height(), "closure uses a lane below the row");
    }

    @Test
    void nodesNeverOverlap() {
        var layout = new CycleStripLayout().layout(seedLoop(), 1);
        var boxes = new ArrayList<>(layout.boxes().values());
        for (int i = 0; i < boxes.size(); i++) for (int j = i + 1; j < boxes.size(); j++) {
            var a = boxes.get(i);
            var b = boxes.get(j);
            boolean separate = a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
                || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y();
            assertTrue(separate, a + " overlaps " + b);
        }
    }

    private static void assertOnBorder(GraphLayoutSnapshot.Box box, GraphLayoutSnapshot.Point point) {
        boolean insideX = point.x() >= box.x() - 0.01 && point.x() <= box.x() + box.width() + 0.01;
        boolean insideY = point.y() >= box.y() - 0.01 && point.y() <= box.y() + box.height() + 0.01;
        boolean onVertical = Math.abs(point.x() - box.x()) < 0.01
            || Math.abs(point.x() - box.x() - box.width()) < 0.01;
        boolean onHorizontal = Math.abs(point.y() - box.y()) < 0.01
            || Math.abs(point.y() - box.y() - box.height()) < 0.01;
        assertTrue(insideX && insideY && (onVertical || onHorizontal), point + " not on border of " + box);
    }
}
