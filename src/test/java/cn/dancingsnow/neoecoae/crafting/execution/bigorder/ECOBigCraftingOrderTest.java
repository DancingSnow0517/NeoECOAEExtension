package cn.dancingsnow.neoecoae.crafting.execution.bigorder;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

class ECOBigCraftingOrderTest {
    @Test void wholeLongChildrenCompleteAnExactBigIntegerParent() {
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)).add(BigInteger.valueOf(17));
        var order = new ECOBigCraftingOrder(UUID.randomUUID(), total, false);
        order.startChild(order.candidate());
        assertEquals(BigInteger.ZERO, order.completed());
        assertFalse(order.completeChild());
        order.startChild(order.candidate());
        assertFalse(order.completeChild());
        assertEquals(17, order.candidate());
        order.startChild(17);
        assertTrue(order.completeChild());
        assertEquals(total, order.completed());
        assertEquals(BigInteger.ZERO, order.remaining());
        assertThrows(IllegalStateException.class, order::completeChild);
        assertThrows(IllegalStateException.class, () -> order.startChild(1));
    }

    @Test void retryStartsAtTwentyCapsAtTwoHundredAndResetsOnAChild() {
        var order = new ECOBigCraftingOrder(UUID.randomUUID(), BigInteger.TEN, true);
        for (int expected : new int[]{20, 40, 80, 160, 200, 200}) {
            order.waitFor(ECOBigOrderState.WAITING_MATERIALS, "MATERIALS");
            assertEquals(expected, order.retryTicks());
            for (int i = 1; i < expected; i++) assertFalse(order.tickRetry());
            assertTrue(order.tickRetry());
            order.planning();
        }
        order.startChild(2);
        order.completeChild();
        order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "CAPACITY");
        assertEquals(20, order.retryTicks());
    }

    @Test void checkpointPreservesIdentityAndDoesNotReplayCompletedChildren() {
        var original = new ECOBigCraftingOrder(UUID.randomUUID(), BigInteger.valueOf(100), true);
        original.startChild(40);
        original.completeChild();
        original.startChild(30);
        var restored = ECOBigCraftingOrder.restore(original.id(), original.requested(), original.completed(),
            original.forced(), original.state(), original.childTarget(), original.retryTicks(),
            original.retryDelay(), original.reason());
        assertEquals(original.progress(12), restored.progress(12));
        restored.completeChild();
        assertEquals(BigInteger.valueOf(70), restored.completed());
        assertEquals(30, restored.candidate());
        restored.cancel();
        assertFalse(restored.tickRetry());
        assertThrows(IllegalStateException.class, () -> restored.startChild(30));
    }

    @Test void malformedAndOversizedAmountsAreRejected() {
        for (String value : new String[]{"", "-1", "+1", " 1", "1e20", "1".repeat(1025)})
            assertThrows(IllegalArgumentException.class, () -> ECOBigCraftingOrder.decode(value));
        assertEquals(BigInteger.ZERO, ECOBigCraftingOrder.decode("0"));
        assertThrows(IllegalArgumentException.class, () -> ECOBigCraftingOrder.restore(UUID.randomUUID(),
            BigInteger.TEN, BigInteger.valueOf(11), false, ECOBigOrderState.PLANNING, 0, 0, 20, ""));
        assertThrows(IllegalArgumentException.class, () -> new ECOBigOrderProgress(UUID.randomUUID(),
            ECOBigOrderState.PLANNING, BigInteger.TEN, BigInteger.ONE, BigInteger.TEN, 0, 0, ""));
    }

    @Test void onlyExplicitCompleteOrForcedMissingResultsCanEnter() {
        for (var status : PlanningStatus.values()) {
            assertEquals(status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
                ECOBigOrderAdmission.allows(status, false));
            assertEquals(status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || status == PlanningStatus.MISSING_ITEMS, ECOBigOrderAdmission.allows(status, true));
        }
    }
}
