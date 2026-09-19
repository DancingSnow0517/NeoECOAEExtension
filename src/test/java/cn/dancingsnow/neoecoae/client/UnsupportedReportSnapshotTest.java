package cn.dancingsnow.neoecoae.client;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnsupportedReportSnapshotTest {
    @Test
    void earlyUnsupportedExitStillReportsRequestedItemWithoutInventingShortage() {
        AEKey goal = mock(AEKey.class);
        var plan = new AE2CraftingPlanBridge().unsupported(goal, 1);
        var result = new ECOPlanningResult(PlanningStatus.PARTIAL_UNSUPPORTED, plan,
            new ECOPlanTrace(), List.of(), List.of(), List.of(), 1L);
        var snapshot = CraftingGraphSnapshotFactory.create(result);
        assertEquals(1, snapshot.nodes().size());
        var root = snapshot.nodes().getFirst();
        assertEquals(root.nodeId(), snapshot.rootNodeId());
        assertSame(goal, root.key());
        assertEquals(BigInteger.ONE, root.requestedBigInteger());
        assertEquals(BigInteger.ZERO, root.missingBigInteger());
        assertEquals(BigInteger.ZERO, root.toCraftBigInteger());
        assertTrue(plan.simulation());
        assertTrue(plan.patternTimes().isEmpty());
    }
}
