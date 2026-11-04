package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.CycleTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingGraphSnapshotBoundaryTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void earlyFailureWithoutPlanOrTraceHasNoInventedRoot() {
        var result = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, new ECOPlanTrace(), List.of(), 0);
        var graph = CraftingGraphSnapshotFactory.create(result);
        assertEquals(-1, graph.rootNodeId());
        assertTrue(graph.nodes().isEmpty());
        assertTrue(graph.patterns().isEmpty());
        assertTrue(graph.edges().isEmpty());
        assertTrue(graph.cycleGroups().isEmpty());
    }

    @Test
    void unsupportedResultWithoutGoalTraceStillShowsRequestedItem() {
        var output = AEItemKey.of(Items.IRON_BLOCK);
        var result = new ECOPlanningResult(PlanningStatus.UNSUPPORTED, shell(output), new ECOPlanTrace(), List.of(), 0);
        var graph = CraftingGraphSnapshotFactory.create(result);
        var root = graph.nodes().get(graph.rootNodeId());
        assertEquals(output, root.key());
        assertEquals(4, root.requested());
        assertEquals(CraftingGraphSnapshot.MaterialStatus.UNSUPPORTED, root.status());
    }

    @Test
    void unreadablePatternRemainsAnExplanationInsteadOfAbortingSnapshot() {
        var output = AEItemKey.of(Items.IRON_BLOCK);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenThrow(new IllegalStateException("optional backend unavailable"));
        var trace = new ECOPlanTrace();
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, output, pattern,
            4, 0, 0, 0, 0, PlanTraceNode.Selection.UNSUPPORTED, "UNSUPPORTED_PATTERN"));
        var result = new ECOPlanningResult(PlanningStatus.UNSUPPORTED, shell(output), trace, List.of(), 0);
        var graph = CraftingGraphSnapshotFactory.create(result);
        assertEquals(1, graph.patterns().size());
        assertEquals(CraftingGraphSnapshot.CandidateStatus.UNSUPPORTED, graph.patterns().getFirst().status());
        assertTrue(graph.patterns().getFirst().inputs().isEmpty());
        assertEquals(graph.rootNodeId(), graph.patterns().getFirst().outputs().getFirst().materialNodeId());
    }

    @Test
    void disabledCycleNeedsNeitherSolveResultNorParallelDiagnostic() {
        var member = AEItemKey.of(Items.IRON_INGOT);
        var trace = new ECOPlanTrace();
        trace.addCycle(new CycleTrace(7, List.of(member), List.of(), Map.of(member, 3L), CyclePlanningStatus.DISABLED));
        var result = new ECOPlanningResult(PlanningStatus.CYCLE_UNRESOLVED, null, trace, List.of(), 0);
        var graph = CraftingGraphSnapshotFactory.create(result);
        assertEquals(1, graph.nodes().size());
        assertEquals(CraftingGraphSnapshot.MaterialStatus.CYCLE, graph.nodes().getFirst().status());
        var cycle = graph.cycleGroups().getFirst();
        assertEquals("DISABLED", cycle.status());
        assertTrue(cycle.requiredSeed().isEmpty());
        assertTrue(cycle.patternTimes().isEmpty());
        assertEquals(ExecutionCountKnowledge.UNKNOWN, cycle.executionCountKnowledge());
    }

    private static CraftingPlan shell(AEItemKey output) {
        return new CraftingPlan(new GenericStack(output, 4), 0, true, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
    }
}
