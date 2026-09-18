package cn.dancingsnow.neoecoae.api.me.bigorder;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOBigOrderAdmissionTest {
    @Test void unresolvedAndUnemittedCyclesRemainRejectedEvenWhenForced() {
        for (var status : java.util.List.of(
                cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.UNRESOLVED,
                cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.UNSUPPORTED,
                cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.SOLVED_NOT_EMITTED)) {
            var result = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult.class);
            org.mockito.Mockito.when(result.status()).thenReturn(
                cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE);
            org.mockito.Mockito.when(result.plan()).thenReturn(org.mockito.Mockito.mock(appeng.crafting.CraftingPlan.class));
            org.mockito.Mockito.when(result.components()).thenReturn(java.util.List.of(
                new cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult(1,
                    cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC,
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
