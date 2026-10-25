package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import appeng.api.networking.energy.IEnergyService;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOProcessingStepControlTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void stoppingOneStepPreservesTheJobItsInputsAndAlreadyAcceptedOutputLedger() {
        var fixture = new Fixture(false);
        fixture.job.waitingFor.insert(fixture.stone, 3, Actionable.MODULATE);
        assertTrue(fixture.logic.setProcessingStepStopped(fixture.stone, true));
        assertTrue(fixture.job.isProcessingStepStopped(fixture.first));
        assertFalse(fixture.job.isProcessingStepStopped(fixture.second));
        assertEquals(10, fixture.job.tasks.get(fixture.first).value);
        assertEquals(10, fixture.job.tasks.get(fixture.second).value);
        assertEquals(3, fixture.job.waitingFor.list.get(fixture.stone));
        assertEquals(100, fixture.job.remainingAmount);
        assertFalse(fixture.job.suspended);
        assertFalse(fixture.job.link.isCanceled());
        assertTrue(fixture.logic.hasJob());
        assertTrue(fixture.logic.setProcessingStepStopped(fixture.stone, false));
        assertFalse(fixture.job.isProcessingStepStopped(fixture.first));
    }

    @Test void coProductsShareOneStepAndResumingEitherProductResumesThatPattern() {
        var fixture = new Fixture(true);
        assertTrue(fixture.logic.setProcessingStepStopped(fixture.stone, true));
        assertTrue(fixture.logic.isProcessingStepStopped(fixture.dirt));
        assertTrue(fixture.logic.setProcessingStepStopped(fixture.dirt, false));
        assertFalse(fixture.logic.isProcessingStepStopped(fixture.stone));
        assertTrue(fixture.job.stoppedProcessingOutputs.isEmpty());
    }

    @Test void aFullyDispatchedOrUnrelatedProductCannotCancelOrModifyTheJob() {
        var fixture = new Fixture(false);
        fixture.job.tasks.get(fixture.first).accept(10);
        assertFalse(fixture.logic.canControlProcessingStep(fixture.stone));
        assertFalse(fixture.logic.setProcessingStepStopped(fixture.stone, true));
        assertFalse(fixture.logic.setProcessingStepStopped(AEItemKey.of(Items.DIAMOND), true));
        assertFalse(fixture.job.link.isCanceled());
        assertTrue(fixture.job.stoppedProcessingOutputs.isEmpty());
    }

    @Test void refreshingManyGridEntriesDoesNotRescanThePatternsForEveryStoppedFlag() {
        var fixture = new Fixture(false);
        fixture.logic.setProcessingStepStopped(fixture.stone, true);
        clearInvocations(fixture.first, fixture.second);
        for (int i = 0; i < 1000; i++) {
            assertTrue(fixture.logic.isProcessingStepStopped(fixture.stone));
            assertFalse(fixture.logic.isProcessingStepStopped(fixture.dirt));
        }
        verify(fixture.first, times(2)).getOutputs();
        verify(fixture.second, times(1)).getOutputs();
    }

    @Test void aStoppedStepNeverReachesProviderLookupAndResumingMakesItEligibleAgain() {
        var fixture = new Fixture(true);
        var scheduler = new ECOCraftingTaskScheduler(mock(ECOCraftingProviderDispatcher.class));
        var service = mock(CraftingService.class);
        var energy = mock(IEnergyService.class);
        var level = mock(net.minecraft.world.level.Level.class);
        var inventory = new ListCraftingInventory(ignored -> {});
        var context = new ECOCraftingCpuContext(fixture.cpu, fixture.job.link.getCraftingID(), true, 1, 0);
        var lookups = new AtomicInteger();
        java.util.function.Function<IPatternDetails, Iterable<appeng.api.networking.crafting.ICraftingProvider>> providers = pattern -> {
            lookups.incrementAndGet(); return List.of();
        };
        fixture.logic.setProcessingStepStopped(fixture.stone, true);
        scheduler.execute(1, service, energy, level, fixture.job, inventory, providers, () -> true, context, (request, provider) -> false);
        assertEquals(0, lookups.get());
        fixture.logic.setProcessingStepStopped(fixture.stone, false);
        scheduler.execute(1, service, energy, level, fixture.job, inventory, providers, () -> true, context, (request, provider) -> false);
        assertTrue(lookups.get() > 0);
        assertEquals(10, fixture.job.tasks.get(fixture.first).value);
    }

    private static final class Fixture {
        final AEItemKey stone = AEItemKey.of(Items.STONE), dirt = AEItemKey.of(Items.DIRT);
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(cpu);
        final IPatternDetails first = mock(IPatternDetails.class), second = mock(IPatternDetails.class);
        final ExecutingCraftingJob job;
        Fixture(boolean coProducts) {
            when(first.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(second.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(first.getOutputs()).thenReturn(coProducts
                ? new GenericStack[]{new GenericStack(stone, 1), new GenericStack(dirt, 1)}
                : new GenericStack[]{new GenericStack(stone, 1)});
            when(second.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(dirt, 1)});
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(stone, 100));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            when(plan.patternTimes()).thenReturn(coProducts ? Map.of(first, 10L) : Map.of(first, 10L, second, 10L));
            job = new ExecutingCraftingJob(plan, ignored -> {}, new CraftingLink(
                CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu), null);
            logic.setJobFromPersistence(job);
        }
    }
}
