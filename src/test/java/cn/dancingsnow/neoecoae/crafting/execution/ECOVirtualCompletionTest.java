package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.config.Actionable;
import appeng.crafting.CraftingLink;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOVirtualCompletionTest {
    @Test
    void virtualCompletionWaitsForPendingPhysicalFinalOutputs() throws Exception {
        var output = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        var plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 2));
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 2L));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = spy(new ECOCraftingCPULogic(cpu));
        doNothing().when(logic).finishJob(true);
        ExecutingCraftingJob job;
        try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
        }
        field(ECOCraftingCPULogic.class, "job").set(logic, job);
        var delivery = new ECOCraftingOutputDelivery(logic);
        var dynamicOutput = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var pending = new Object2LongLinkedOpenHashMap<AEKey>();
        pending.put(dynamicOutput, 2L);
        field(ECOCraftingOutputDelivery.class, "pendingFinalOutputs").set(delivery, pending);
        logic.getInventory().insert(dynamicOutput, 2L, Actionable.MODULATE);
        when(link.insert(dynamicOutput, 2L, Actionable.MODULATE)).thenReturn(0L, 2L);
        field(ECOCraftingCPULogic.class, "outputDelivery").set(logic, delivery);

        assertTrue(logic.tryCompleteVirtualCrafting(pattern, 2L));

        assertEquals(0L, job.tasks.get(pattern).value);
        assertEquals(2L, job.remainingAmount, "Physical delivery still owns the final amount");
        verify(logic, never()).finishJob(true);

        delivery.deliverStoredFinalOutput();
        assertEquals(2L, job.remainingAmount);
        assertEquals(2L, logic.getInventory().list.get(dynamicOutput));
        verify(logic, never()).finishJob(true);

        delivery.deliverStoredFinalOutput();
        assertEquals(0L, job.remainingAmount);
        assertEquals(0L, logic.getInventory().list.get(dynamicOutput));
        assertFalse(delivery.hasPendingFinalOutputs());
        verify(logic).finishJob(true);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
