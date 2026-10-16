package cn.dancingsnow.neoecoae.crafting.display;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExactCycleAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCycleItemProjectionTest {
    @Test void unresolvedMembersAreHiddenUnlessTheyHaveAConcreteStartupShortage() {
        var hidden = mock(AEKey.class);
        var seed = mock(AEKey.class);
        var cycle = mock(CraftingGraphSnapshot.CycleGroup.class);
        when(cycle.executionCountKnowledge()).thenReturn(ExecutionCountKnowledge.UNKNOWN);
        when(cycle.exactSingleNetOutputs()).thenReturn(List.of(amount(hidden), amount(seed)));
        when(cycle.requiredSeed()).thenReturn(List.of(new CraftingGraphSnapshot.KeyAmount(seed, 1L)));
        var graph = mock(CraftingGraphSnapshot.class);
        when(graph.cycleGroups()).thenReturn(List.of(cycle));
        var hiddenMaterial = material(hidden, 0);
        var seedMaterial = material(seed, 7);
        when(graph.nodes()).thenReturn(List.of(hiddenMaterial, seedMaterial));
        var result = mock(ECOPlanningResult.class);
        var projected = ECOCycleItemProjection.project(result, graph);
        assertEquals(1, projected.items().size());
        var item = projected.items().getFirst();
        assertSame(seed, item.what());
        assertEquals(BigInteger.valueOf(7), item.exactMissing());
        assertEquals(-1, item.seedParallelism());
    }

    @Test void knownTotalsKeepStableFirstOccurrenceOrderAcrossCycleGroups() {
        var first = mock(AEKey.class);
        var second = mock(AEKey.class);
        var cycle = mock(CraftingGraphSnapshot.CycleGroup.class);
        when(cycle.executionCountKnowledge()).thenReturn(ExecutionCountKnowledge.EXACT);
        when(cycle.exactSingleNetOutputs()).thenReturn(List.of(amount(first), amount(second)));
        when(cycle.requiredSeed()).thenReturn(List.of(new CraftingGraphSnapshot.KeyAmount(first, 1L)));
        var graph = mock(CraftingGraphSnapshot.class);
        when(graph.cycleGroups()).thenReturn(List.of(cycle, cycle));
        var projected = ECOCycleItemProjection.project(mock(ECOPlanningResult.class), graph);
        assertEquals(List.of(first, second), projected.items().stream().map(item -> item.what()).toList());
    }

    private static ExactKeyAmount amount(AEKey key) {
        return new ExactKeyAmount(key, new ExactCycleAmount(BigInteger.ONE));
    }

    private static CraftingGraphSnapshot.MaterialNode material(AEKey key, long missing) {
        var material = mock(CraftingGraphSnapshot.MaterialNode.class);
        when(material.key()).thenReturn(key);
        when(material.consumedBigInteger()).thenReturn(BigInteger.ZERO);
        when(material.producedBigInteger()).thenReturn(BigInteger.ZERO);
        when(material.missingBigInteger()).thenReturn(BigInteger.valueOf(missing));
        return material;
    }
}
