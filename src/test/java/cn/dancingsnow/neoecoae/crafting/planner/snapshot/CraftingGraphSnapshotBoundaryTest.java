package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
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

    @Test void graphPacketKeepsWideRecipeEdgesFiringsAndSeedAmounts() {
        var amount = cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.ONE.shiftLeft(160);
        var key = AEItemKey.of(Items.IRON_INGOT);
        var relationship = new CraftingGraphSnapshot.Relationship(0, amount);
        var edge = new CraftingGraphSnapshot.Edge(-2, 0, amount, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true);
        var pattern = new CraftingGraphSnapshot.PatternNode(-2, "wide", List.of(relationship), List.of(), amount,
            CraftingGraphSnapshot.CandidateStatus.SELECTED, null, 0);
        var cycle = new CraftingGraphSnapshot.CycleGroup(0, List.of(0), List.of(edge), "SOLVED",
            List.of(new CraftingGraphSnapshot.KeyAmount(key, amount)), List.of(),
            List.of(new CraftingGraphSnapshot.KeyAmount(key, amount)),
            List.of(new CraftingGraphSnapshot.PatternAmount(-2, amount)), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), ExecutionCountKnowledge.EXACT,
            cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus.SUCCESS,
            new CraftingGraphSnapshot.Recursion(List.of(new CraftingGraphSnapshot.KeyAmount(key, 1)),
                List.of(new CraftingGraphSnapshot.RecursionLayer(List.of(new CraftingGraphSnapshot.KeyAmount(key, amount)),
                    List.of(new CraftingGraphSnapshot.KeyAmount(key, amount.add(PlannerAmount.ONE))),
                    List.of(new CraftingGraphSnapshot.PatternAmount(-2, amount)), true)), true));
        var graph = new CraftingGraphSnapshot(-1, List.of(), List.of(pattern), List.of(edge), List.of(cycle), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 0, 1, 1, 1, 0));
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            graph.writeToPacket(buffer);
            assertEquals(graph, new CraftingGraphSnapshot(buffer));
            var report = graph.materialReport();
            report.writeToPacket(buffer);
            assertEquals(report, new CraftingGraphSnapshot(buffer));
            var reportCycle = report.cycleGroups().get(0);
            assertEquals(cycle.requiredOutputs(), reportCycle.requiredOutputs());
            assertEquals(cycle.requiredSeed(), reportCycle.requiredSeed());
            assertEquals(cycle.executionCountKnowledge(), reportCycle.executionCountKnowledge());
            assertEquals(cycle.solveStatus(), reportCycle.solveStatus());
            assertTrue(report.patterns().isEmpty());
            assertTrue(report.edges().isEmpty());
            assertTrue(reportCycle.patternTimes().isEmpty());
            assertTrue(reportCycle.recursion().layers().isEmpty());
        } finally { buffer.release(); }
    }

    @Test void indexedTraceRelationshipsKeepExactAmountsCandidateOrderAndComponents() {
        var output = AEItemKey.of(Items.IRON_BLOCK);
        var input = AEItemKey.of(Items.IRON_INGOT);
        var other = AEItemKey.of(Items.GOLD_INGOT);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenReturn(new GenericStack[] {new GenericStack(output, 1)});
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        var trace = new ECOPlanTrace();
        trace.addComponent(new cn.dancingsnow.neoecoae.crafting.planner.trace.ComponentTrace(7,
            cn.dancingsnow.neoecoae.crafting.planner.trace.ComponentTrace.Type.ACYCLIC, List.of(output)));
        var wide = PlannerAmount.ONE.shiftLeft(160);
        trace.addEdge(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceEdge(output, input,
            Long.MAX_VALUE, wide.toBigInteger()));
        trace.addEdge(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceEdge(other, input, 3));
        trace.addEdge(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceEdge(output, input, 9));
        for (var selected : List.of(PlanTraceNode.Selection.REJECTED, PlanTraceNode.Selection.SELECTED)) {
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, output, pattern,
                4, 0, 4, 0, 4, selected, null));
        }
        var graph = CraftingGraphSnapshotFactory.create(new ECOPlanningResult(PlanningStatus.MISSING_ITEMS,
            shell(output), trace, List.of(), 0));
        assertEquals(2, graph.patterns().size());
        assertEquals(CraftingGraphSnapshot.CandidateStatus.REJECTED, graph.patterns().get(0).status());
        assertEquals(CraftingGraphSnapshot.CandidateStatus.SELECTED, graph.patterns().get(1).status());
        for (var candidate : graph.patterns()) {
            assertEquals(7, candidate.componentId());
            assertEquals(List.of(wide, PlannerAmount.of(9)), candidate.inputs().stream()
                .map(CraftingGraphSnapshot.Relationship::plannerAmount).toList());
        }
    }

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
        assertEquals(CraftingGraphSnapshot.CandidateStatus.UNSUPPORTED, graph.patterns().get(0).status());
        assertTrue(graph.patterns().get(0).inputs().isEmpty());
        assertEquals(graph.rootNodeId(), graph.patterns().get(0).outputs().get(0).materialNodeId());
    }

    @Test
    void disabledCycleNeedsNeitherSolveResultNorParallelDiagnostic() {
        var member = AEItemKey.of(Items.IRON_INGOT);
        var trace = new ECOPlanTrace();
        trace.addCycle(new CycleTrace(7, List.of(member), List.of(), Map.of(member, 3L), CyclePlanningStatus.DISABLED));
        var result = new ECOPlanningResult(PlanningStatus.CYCLE_UNRESOLVED, null, trace, List.of(), 0);
        var graph = CraftingGraphSnapshotFactory.create(result);
        assertEquals(1, graph.nodes().size());
        assertEquals(CraftingGraphSnapshot.MaterialStatus.CYCLE, graph.nodes().get(0).status());
        var cycle = graph.cycleGroups().get(0);
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
