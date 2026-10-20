package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOFastPathWaitingLimitTest {
    @Test void existingWaitingInventoryAndCombinedRemaindersBoundDispatchBeforeExtraction() {
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var plan = new CraftingPlan(new GenericStack(key, 100), 1, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        try (var types = mockStatic(AEKeyTypes.class)) {
            var job = new ExecutingCraftingJob(plan, ignored -> {}, mock(CraftingLink.class), null);
            job.waitingFor.list.set(key, Long.MAX_VALUE - 5);
            var outputs = new KeyCounter();
            outputs.add(key, 2);
            var remainders = new KeyCounter();
            remainders.add(key, 1);
            var request = mock(ECOCraftingDispatchRequest.class);
            when(request.job()).thenReturn(job);
            when(request.allowedCrafts()).thenReturn(100L);
            when(request.outputs()).thenReturn(outputs);
            when(request.remainders()).thenReturn(remainders);
            assertEquals(1, ECOCraftingFastPathDispatcher.safeWaitingBatch(request, 0));
            job.waitingFor.list.set(key, Long.MAX_VALUE);
            assertEquals(0, ECOCraftingFastPathDispatcher.safeWaitingBatch(request, 0));
            assertEquals(Long.MAX_VALUE, job.waitingFor.list.get(key));
        }
    }
}
