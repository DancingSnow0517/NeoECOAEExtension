package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftingGraphPatternIdentityTest {
    @Test void snapshotAggregatesGrossTaskConsumptionAndProduction() {
        var output = PlannerTestKey.of("flow_output");
        var catalyst = PlannerTestKey.of("flow_catalyst");
        var pattern = new PlannerFixtures.Pattern("flow",
            new appeng.api.crafting.IPatternDetails.IInput[] {new PlannerFixtures.Input(catalyst, 1, true)},
            List.of(new GenericStack(output, 2)));
        var plan = new CraftingPlan(new GenericStack(output, 12), 0, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 6L));
        ECOPlanTrace trace = new ECOPlanTrace();
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.GOAL, output, null, 12, 0, 12, 0, 0,
            PlanTraceNode.Selection.NOT_APPLICABLE, null));
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, plan, trace, List.of(), List.of(), List.of(), 0);

        var snapshot = CraftingGraphSnapshotFactory.create(result);
        var consumed = snapshot.nodes().stream().filter(node -> catalyst.equals(node.key())).findFirst().orElseThrow();
        var produced = snapshot.nodes().stream().filter(node -> output.equals(node.key())).findFirst().orElseThrow();
        assertEquals("6", consumed.exactConsumed());
        assertEquals("6", consumed.exactProduced(), "returned catalyst must remain visible despite zero net change");
        assertEquals("12", produced.exactProduced());
    }

    @Test void sameClassAndPrimaryOutputWithDifferentInputsHaveDistinctNodeIds() {
        var output = PlannerTestKey.of("same_output");
        var inputA = PlannerTestKey.of("input_a");
        var inputB = PlannerTestKey.of("input_b");
        var first = PlannerFixtures.pattern("same_display_a", output, 1, inputA, 1L);
        var second = PlannerFixtures.pattern("same_display_b", output, 1, inputB, 1L);
        ECOPlanTrace trace = new ECOPlanTrace();
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.GOAL, output, null, 1, 0, 1, 0, 0,
            PlanTraceNode.Selection.NOT_APPLICABLE, null));
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.MATERIAL, inputA, null, 1, 0, 1, 0, 0,
            PlanTraceNode.Selection.NOT_APPLICABLE, null));
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.MATERIAL, inputB, null, 1, 0, 1, 0, 0,
            PlanTraceNode.Selection.NOT_APPLICABLE, null));
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, output, first, 0, 0, 0, 0, 1,
            PlanTraceNode.Selection.SELECTED, null));
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, output, second, 0, 0, 0, 0, 1,
            PlanTraceNode.Selection.SELECTED, null));
        var result = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, null, trace, List.of(), List.of(), List.of(), 0);

        var snapshot = CraftingGraphSnapshotFactory.create(result);
        assertEquals(2, snapshot.patterns().size());
        var one = snapshot.patterns().get(0);
        var two = snapshot.patterns().get(1);
        assertNotEquals(one.patternNodeId(), two.patternNodeId());
        assertEquals(one.displayIdentity(), two.displayIdentity(), "display identity is intentionally non-unique");
        assertTrue(snapshot.edges().stream().anyMatch(edge -> edge.fromId() == one.patternNodeId()
            || edge.toId() == one.patternNodeId()));
        assertTrue(snapshot.edges().stream().anyMatch(edge -> edge.fromId() == two.patternNodeId()
            || edge.toId() == two.patternNodeId()));
    }
}
