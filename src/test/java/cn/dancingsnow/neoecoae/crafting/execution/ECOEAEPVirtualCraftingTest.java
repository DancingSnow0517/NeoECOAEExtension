package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOEAEPVirtualCraftingTest {
    interface Provider extends ICraftingProvider, PatternProviderLogicVirtualCompatBridge {}

    final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final AEKey container = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final IPatternDetails pattern = mock(IPatternDetails.class);
    final Provider provider = mock(Provider.class);
    final ExecutingCraftingJob job;
    final ECOCraftingDispatchRequest request;
    final ECOCraftingDispatchAccounting accounting = new ECOCraftingDispatchAccounting(
            ignored -> {}, () -> {}, current -> new
                    cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext(
                            mock(appeng.api.networking.crafting.ICraftingCPU.class), java.util.UUID.randomUUID(),
                            current.finalOutput, 4, current.remainingAmount), ignored -> {});

    ECOEAEPVirtualCraftingTest() {
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        var plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 4));
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 4L));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, mock(CraftingLink.class), null);
        }
        request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[0],
                new KeyCounter(), new KeyCounter(), 4, new ListCraftingInventory(ignored -> {}), null);
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(true);
    }

    void accept(long count, List<GenericStack> remainders) {
        accounting.apply(request, new ECOCraftingDispatchResult(count,
                List.of(new GenericStack(output, count)), remainders), () -> {}, provider);
    }

    @Test void virtualBatchesAdvanceOnlyTheirOwningJobAndRequireNoFinalReturn() {
        accept(3, List.of());
        assertEquals(1, job.tasks.get(pattern).value);
        assertEquals(1, job.remainingAmount);
        assertTrue(job.waitingFor.list.isEmpty());
        accept(1, List.of());
        assertEquals(0, job.tasks.get(pattern).value);
        assertEquals(0, job.remainingAmount);
        verify(job.timeTracker).decrementItems(3, output.getType());
    }

    @Test void removingCardRestoresPhysicalWaitingWithoutClearingEarlierWaits() {
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(false);
        accept(1, List.of());
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(true);
        accept(3, List.of(new GenericStack(container, 1)));
        assertEquals(1, job.remainingAmount);
        assertEquals(1, job.waitingFor.list.get(output));
        assertEquals(1, job.waitingFor.list.get(container));
    }

    @Test void dependenciesAndCycleSeedsMustStillReturnPhysically() {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        accept(1, List.of());
        assertEquals(4, job.remainingAmount);
        assertEquals(1, job.waitingFor.list.get(output));
    }

    @Test void unrelatedProviderDoesNotEnableVirtualCompletion() {
        accounting.apply(request, new ECOCraftingDispatchResult(1,
                List.of(new GenericStack(output, 1)), List.of()), () -> {}, mock(ICraftingProvider.class));
        assertEquals(4, job.remainingAmount);
        assertEquals(1, job.waitingFor.list.get(output));
    }

    @Test void virtualOverproductionDoesNotLeaveAnUnfulfillableWait() {
        job.remainingAmount = 1;
        accept(4, List.of());
        assertEquals(0, job.remainingAmount);
        assertTrue(job.waitingFor.list.isEmpty());
    }

    @Test void normalFinalizationStillWaitsForPhysicalContainers() {
        accept(4, List.of(new GenericStack(container, 1)));
        var host = mock(ECOCraftingCPULogic.class);
        when(host.getJob()).thenReturn(job);
        when(host.getInventory()).thenReturn(request.inventory());
        when(host.taskSchedulerForOutput()).thenReturn(mock(ECOCraftingTaskScheduler.class));
        var delivery = new ECOCraftingOutputDelivery(host);
        delivery.deliverStoredFinalOutput();
        verify(host, never()).finishJob(true);
        job.waitingFor.extract(container, 1, appeng.api.config.Actionable.MODULATE);
        delivery.deliverStoredFinalOutput();
        verify(host).finishJob(true);
    }
}
