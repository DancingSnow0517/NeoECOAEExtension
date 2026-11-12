package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.helpers.MultiCraftingTracker;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOAutomaticOrderCompletionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(longs = {0, 2, 4})
    void rejectedOutputReturnsToMeAndCompletionReleasesTheRealCardTracker(long accepted) {
        var f = new Fixture();
        when(f.requester.insertCraftedItems(f.requestLink, f.key, 4, Actionable.MODULATE)).thenReturn(accepted);
        f.acceptNetworkRemainder();
        f.logic.insert(f.key, 4, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertNull(f.logic.getJob());
        assertTrue(f.requestLink.isDone());
        assertTrue(f.tracker.getRequestedJobs().isEmpty());
        assertEquals(0, f.logic.getInventory().list.get(f.key));
        if (accepted < 4) verify(f.grid.getStorageService().getInventory()).insert(
                eq(f.key), eq(4 - accepted), eq(Actionable.MODULATE), any());
        verify(f.requester, times(1)).jobStateChange(f.requestLink);
        f.tracker.handleCrafting(0, f.key, 4, null, f.service, null);
        verify(f.service, times(2)).beginCraftingCalculation(any(), any(), eq(f.key), eq(4L), any());
    }

    @Test void fullDestinationAndFullNetworkRetainOutputUntilStorageCanAcceptIt() {
        var f = new Fixture();
        f.logic.insert(f.key, 4, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertNotNull(f.logic.getJob());
        assertFalse(f.requestLink.isDone());
        assertEquals(4, f.logic.getInventory().list.get(f.key));
        assertEquals(4, f.logic.getJob().remainingAmount);
        f.acceptNetworkRemainder();
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertTrue(f.requestLink.isDone());
        assertNull(f.logic.getJob());
        assertEquals(0, f.logic.getInventory().list.get(f.key));
    }

    @Test void claimedVariantAccountsRequesterAndNetworkAcceptanceSeparately() {
        var f = new Fixture();
        var actual = AEItemKey.of(Items.DIRT);
        when(f.requester.insertCraftedItems(f.requestLink, actual, 4, Actionable.MODULATE)).thenReturn(1L);
        f.acceptNetworkRemainder();
        var claim = f.logic.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
                f.requestLink.getCraftingID(), f.key, actual, 4, Actionable.MODULATE));
        assertEquals(1, claim.deliveredToRequester());
        assertEquals(3, claim.deliveredToNetwork());
        assertEquals(0, claim.storedInCpu());
        assertTrue(claim.jobFinished());
        assertTrue(f.requestLink.isDone());
    }

    @Test void pendingProcessingReturnsPreventPrematureCompletion() {
        var f = new Fixture();
        var container = AEItemKey.of(Items.BUCKET);
        f.logic.getJob().waitingFor.insert(container, 1, Actionable.MODULATE);
        f.acceptNetworkRemainder();
        f.logic.insert(f.key, 4, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertFalse(f.requestLink.isDone());
        assertNotNull(f.logic.getJob());
        f.logic.insert(container, 1, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertTrue(f.requestLink.isDone());
        assertNull(f.logic.getJob());
    }

    @Test void outputClaimsKeepFeedbackForTheNextGrowthStep() {
        var f = new Fixture();
        var grow = mock(appeng.api.crafting.IPatternDetails.class);
        var input = mock(appeng.api.crafting.IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(f.key, 1)});
        when(input.isValid(any(), any())).thenAnswer(call -> f.key.equals(call.getArgument(0)));
        when(grow.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(grow.getInputs()).thenReturn(new appeng.api.crafting.IPatternDetails.IInput[]{input});
        when(grow.getOutputs()).thenReturn(java.util.List.of(new GenericStack(f.key, 2)));
        when(grow.getPrimaryOutput()).thenReturn(new GenericStack(f.key, 2));
        var task = new ExecutingCraftingJob.TaskProgress();
        task.value = 1;
        f.logic.getJob().tasks.put(grow, task);
        when(f.requester.insertCraftedItems(eq(f.requestLink), eq(f.key), anyLong(), any()))
                .thenAnswer(call -> call.getArgument(2));
        var claim = f.logic.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
                f.requestLink.getCraftingID(), f.key, f.key, 2, Actionable.MODULATE));
        assertEquals(1, claim.deliveredToRequester());
        assertEquals(1, claim.storedInCpu());
        assertEquals(1, f.logic.getInventory().list.get(f.key));
        assertFalse(f.requestLink.isDone());
    }

    @Test void substitutedOverproductionDoesNotOverdeliverOrKeepTheOrderBusyForever() {
        var f = new Fixture();
        var actual = AEItemKey.of(Items.DIRT);
        f.logic.getJob().waitingFor.insert(f.key, 2, Actionable.MODULATE);
        when(f.requester.insertCraftedItems(eq(f.requestLink), eq(actual), anyLong(), any()))
                .thenAnswer(call -> call.getArgument(2));
        f.acceptNetworkRemainder();
        var claim = f.logic.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
                f.requestLink.getCraftingID(), f.key, actual, 6, Actionable.MODULATE));
        assertEquals(4, claim.deliveredToRequester());
        assertEquals(2, claim.storedInCpu());
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertTrue(f.requestLink.isDone());
        assertNull(f.logic.getJob());
        verify(f.requester, times(1)).insertCraftedItems(f.requestLink, actual, 4, Actionable.MODULATE);
        // Deferred idle returns retain ownership even when their shared tick budget delays the actual insert.
        long retained = f.logic.getInventory().list.get(actual);
        if (retained == 0) verify(f.grid.getStorageService().getInventory()).insert(
                eq(actual), eq(2L), eq(Actionable.MODULATE), any());
        else assertEquals(2, retained);
    }

    @Test void ambiguousRequesterFailureDoesNotSendTheSameOutputToTheNetwork() {
        var f = new Fixture();
        when(f.requester.insertCraftedItems(f.requestLink, f.key, 4, Actionable.MODULATE))
                .thenThrow(new IllegalStateException("unknown acceptance"));
        f.acceptNetworkRemainder();
        f.logic.insert(f.key, 4, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertNotNull(f.logic.getJob());
        assertEquals(4, f.logic.getInventory().list.get(f.key));
        assertEquals("FINAL_OUTPUT_DELIVERY_UNCERTAIN", f.logic.getPermanentExecutionError());
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        verify(f.requester, times(1)).insertCraftedItems(f.requestLink, f.key, 4, Actionable.MODULATE);
        f.logic.cancel();
        assertEquals("FINAL_OUTPUT_DELIVERY_UNCERTAIN", f.logic.getPermanentExecutionError());
        assertEquals(4, f.logic.getInventory().list.get(f.key));
        verify(f.grid.getStorageService().getInventory(), never()).insert(any(), anyLong(), any(), any());
        var checkpoint = new net.minecraft.nbt.CompoundTag();
        f.logic.inventoryReturnForPersistence().write(checkpoint);
        var restoredHost = mock(ECOCraftingCPULogic.class);
        var restoredReturn = new ECOCpuInventoryReturn(restoredHost);
        restoredReturn.read(checkpoint);
        restoredReturn.run(false);
        assertEquals("FINAL_OUTPUT_DELIVERY_UNCERTAIN", restoredReturn.failure());
        verifyNoInteractions(restoredHost);
    }

    @Test void simulationFailureDoesNotSuspendOrMutateTheOrder() {
        var f = new Fixture();
        when(f.requester.insertCraftedItems(f.requestLink, f.key, 4, Actionable.SIMULATE))
                .thenThrow(new IllegalStateException("simulation failed"));
        f.logic.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
                f.requestLink.getCraftingID(), f.key, f.key, 4, Actionable.SIMULATE));
        assertNull(f.logic.getPermanentExecutionError());
        assertFalse(f.logic.isJobSuspended());
        assertEquals(4, f.logic.getJob().remainingAmount);
        assertEquals(4, f.logic.getJob().waitingFor.list.get(f.key));
        assertEquals(0, f.logic.getInventory().list.get(f.key));
        verify(f.grid.getStorageService().getInventory(), never()).insert(any(), anyLong(), any(), any());
    }

    @Test void uncertainNetworkReturnRetainsOnlyThePartTheRequesterDidNotAccept() {
        var f = new Fixture();
        when(f.requester.insertCraftedItems(f.requestLink, f.key, 4, Actionable.MODULATE)).thenReturn(1L);
        when(f.grid.getStorageService().getInventory().insert(eq(f.key), eq(3L), any(), any()))
                .thenThrow(new IllegalStateException("unknown storage acceptance"));
        f.logic.insert(f.key, 4, Actionable.MODULATE);
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertEquals(3, f.logic.getJob().remainingAmount);
        assertEquals(3, f.logic.getInventory().list.get(f.key));
        assertTrue(f.logic.isJobSuspended());
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        verify(f.requester, times(1)).insertCraftedItems(f.requestLink, f.key, 4, Actionable.MODULATE);
    }

    private static final class Fixture {
        final AEItemKey key = AEItemKey.of(Items.STONE);
        final IGrid grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        final ICraftingService service = mock(ICraftingService.class);
        final ICraftingRequester requester = mock(ICraftingRequester.class);
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(cpu);
        final MultiCraftingTracker tracker = new MultiCraftingTracker(requester, 1);
        final CraftingLink requestLink;

        Fixture() {
            when(cpu.getGrid()).thenReturn(grid);
            var id = UUID.randomUUID();
            var cpuLink = new CraftingLink(cn.dancingsnow.neoecoae.util.LegacyNbt.input(cn.dancingsnow.neoecoae.util.LegacyNbt.registries(), CraftingCpuHelper.generateLinkData(id, false, false)), cpu);
            requestLink = new CraftingLink(cn.dancingsnow.neoecoae.util.LegacyNbt.input(cn.dancingsnow.neoecoae.util.LegacyNbt.registries(), CraftingCpuHelper.generateLinkData(id, false, true)), requester);
            var nexus = new CraftingLinkNexus(id);
            cpuLink.setNexus(nexus);
            requestLink.setNexus(nexus);
            doAnswer(call -> { tracker.jobStateChange(call.getArgument(0)); return null; })
                    .when(requester).jobStateChange(any());
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(key, 4));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            when(plan.patternTimes()).thenReturn(Map.of());
            try (var time = mockConstruction(ElapsedTimeTracker.class)) {
                logic.setJobFromPersistence(new ExecutingCraftingJob(plan, ignored -> {}, cpuLink, null));
            }
            logic.getJob().waitingFor.insert(key, 4, Actionable.MODULATE);
            when(service.beginCraftingCalculation(any(), any(), any(), anyLong(), any()))
                    .thenReturn(CompletableFuture.completedFuture(plan));
            when(service.submitJob(eq(plan), eq(requester), isNull(), eq(false), any()))
                    .thenReturn(CraftingSubmitResult.successful(requestLink));
            tracker.handleCrafting(0, key, 4, null, service, null);
            assertTrue(tracker.handleCrafting(0, key, 4, null, service, null));
            assertEquals(1, tracker.getRequestedJobs().size());
        }

        void acceptNetworkRemainder() {
            when(grid.getStorageService().getInventory().insert(any(), anyLong(), any(), any()))
                    .thenAnswer(call -> call.getArgument(1));
        }
    }
}
