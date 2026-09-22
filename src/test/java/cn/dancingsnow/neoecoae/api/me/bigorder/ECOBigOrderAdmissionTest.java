package cn.dancingsnow.neoecoae.api.me.bigorder;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOBigOrderAdmissionTest {
    @Test void unrepresentableCycleStartsAsParentCarrierInsteadOfAnIncompleteExecutionPlan() {
        var goal = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var intermediate = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var projection = new appeng.crafting.CraftingPlan(
            new appeng.api.stacks.GenericStack(goal, 5_000_000_000_000_000_000L), 0, true, false,
            new appeng.api.stacks.KeyCounter(), new appeng.api.stacks.KeyCounter(),
            new appeng.api.stacks.KeyCounter(), Map.of());
        var trace = new cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace();
        trace.addNode(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode(
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Kind.MATERIAL,
            intermediate, null, 0, 0, 0, 0, 0,
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Selection.UNSUPPORTED, null)
            .withExact(BigInteger.TEN.pow(20), BigInteger.ZERO, BigInteger.TEN.pow(20),
                BigInteger.ZERO, BigInteger.ZERO));
        var cycle = new cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult(1,
            cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC,
            cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.UNREPRESENTABLE,
            Map.of(), Set.of(), Set.of(),
            cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus.UNREPRESENTABLE,
            null, Map.of(), "amount exceeds long", null,
            cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition.BLOCKED, Map.of());
        var result = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            projection, trace, List.of(), List.of(cycle), List.of(1), 0L);
        var options = new cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions(true, false, Set.of());

        var request = ECOBigOrderRequest.fromPlanningResult(result, false, options);

        assertEquals(BigInteger.valueOf(5_000_000_000_000_000_000L), request.requested());
        assertEquals(BigInteger.TEN.pow(20), request.pendingPreview().get(intermediate));
        assertSame(request, request.submit(carrier -> ECOBigOrderRequest.forSubmission(carrier)));
        assertNull(ECOBigOrderRequest.forSubmission(request.carrier()));
    }

    @Test void unresolvedAndUnemittedCyclesRemainRejectedEvenWhenForced() {
        for (var status : java.util.List.of(
                cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.UNRESOLVED,
                cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.UNSUPPORTED,
                cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.SOLVED_NOT_EMITTED)) {
            var result = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult.class);
            org.mockito.Mockito.when(result.status()).thenReturn(
                cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE);
            org.mockito.Mockito.when(result.plan()).thenReturn(org.mockito.Mockito.mock(appeng.crafting.CraftingPlan.class));
            org.mockito.Mockito.when(result.components()).thenReturn(java.util.List.of(
                new cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult(1,
                    cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC,
                    status, java.util.Map.of(), null, null)));
            assertFalse(ECOBigOrderAdmission.allows(result, false), status.name());
            assertFalse(ECOBigOrderAdmission.allows(result, true), status.name());
        }
    }

    @Test void unlimitedParentReservationUsesABoundedAccessProbe() {
        BigInteger required = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        assertTrue(ECOBigOrderAdmission.hasStoredAmount(required, true, amount -> {
            assertEquals(Long.MAX_VALUE, amount);
            return amount;
        }));
        assertFalse(ECOBigOrderAdmission.hasStoredAmount(required, false, amount -> {
            fail("Finite stock cannot cover a reservation beyond long");
            return amount;
        }));
        assertFalse(ECOBigOrderAdmission.hasStoredAmount(required, true, amount -> 0),
            "A creative listing must not bypass extraction permissions or current availability");
    }

    @Test void finiteReservationsStillCheckTheExactRequestedAmount() {
        assertTrue(ECOBigOrderAdmission.hasStoredAmount(BigInteger.valueOf(64), false, amount -> {
            assertEquals(64, amount);
            return 64;
        }));
        assertFalse(ECOBigOrderAdmission.hasStoredAmount(BigInteger.valueOf(64), false, amount -> 63));
    }
}
