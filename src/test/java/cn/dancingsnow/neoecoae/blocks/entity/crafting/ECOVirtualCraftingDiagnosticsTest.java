package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity.VirtualLaneStartResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ECOVirtualCraftingDiagnosticsTest {
    private ECOCraftingSystemBlockEntity host;
    private TickHandler tickHandler;
    private MockedStatic<TickHandler> ticks;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void setup() throws ReflectiveOperationException {
        tickHandler = mock(TickHandler.class);
        when(tickHandler.getCurrentTick()).thenReturn(100L);
        ticks = mockStatic(TickHandler.class);
        ticks.when(TickHandler::instance).thenReturn(tickHandler);
        host = mock(ECOCraftingSystemBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(true).when(host).isFullVirtualCraftingMode();
        doReturn(true).when(host).isActiveCooling();
        doReturn(9).when(host).getDisplayedCoolingMaxOverclock();
        doReturn(1).when(host).getRunningThreadCount();
        doReturn(10_000).when(host).getLocalAvailableCoolant(10_000, 9);
        doReturn(true).when(host).tryConsumeVirtualCraftingPower();
        doReturn(true).when(host).tryConsumeVirtualLaneCoolant();
        var snapshot = mock(CraftingCapabilitySnapshot.class);
        when(snapshot.virtualMode()).thenReturn(true);
        doReturn(snapshot).when(host).getCapabilitySnapshot();
        var formed = NEBlockEntity.class.getDeclaredField("formed");
        formed.setAccessible(true);
        formed.setBoolean(host, true);
        assertEquals(STARTED, host.tryStartVirtualLaneTickWithResult());
        clearInvocations(host);
    }

    @AfterEach void closeTicks() { ticks.close(); }

    @Test
    void lowTierWaterIsVisibleBeforeDispatchWithoutChargingPower() {
        doReturn(2).when(host).getDisplayedCoolingMaxOverclock();
        doReturn(0).when(host).getRunningThreadCount();
        var notice = translation(host.getCraftingStatusNotice());
        assertEquals("gui.neoecoae.crafting.virtual_blocked.coolant_tier", notice.getKey());
        assertArrayEquals(new Object[] {2, 9}, notice.getArgs());
        assertEquals("gui.neoecoae.crafting.virtual_blocked.title",
            translation(host.getMaintenanceStatusTooltip()).getKey());
        verify(host, never()).tryConsumeVirtualCraftingPower();
        verify(host, never()).tryConsumeVirtualLaneCoolant();
        verify(host, never()).getLocalAvailableCoolant(anyInt(), anyInt());
    }

    @Test
    void coolantFailureIsReportedBeforeAnyEnergyCharge() {
        doReturn(0).when(host).getLocalAvailableCoolant(10_000, 9);
        assertEquals(COOLANT_UNAVAILABLE, host.tryStartVirtualLaneTickWithResult());
        assertNotice("coolant");
        verify(host, never()).tryConsumeVirtualCraftingPower();
        verify(host, never()).tryConsumeVirtualLaneCoolant();
    }

    @Test
    void energyFailureDoesNotConsumeCoolantAndIsShownInBothUiSurfaces() {
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        assertEquals(ENERGY_UNAVAILABLE, host.tryStartVirtualLaneTickWithResult());
        assertNotice("energy");
        assertEquals("gui.neoecoae.crafting.virtual_blocked.title",
            translation(host.getMaintenanceStatusTooltip()).getKey());
        verify(host, never()).tryConsumeVirtualLaneCoolant();
        // Reading diagnostics must not probe or charge energy again.
        verify(host, times(1)).tryConsumeVirtualCraftingPower();
    }

    @Test
    void coolantConsumptionFailureIsNotMisreportedAsEnergyFailure() {
        doReturn(false).when(host).tryConsumeVirtualLaneCoolant();
        assertEquals(COOLANT_UNAVAILABLE, host.tryStartVirtualLaneTickWithResult());
        assertNotice("coolant");
    }

    @Test
    void successfulRetryClearsTheExecutionWarning() {
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        assertFalse(host.tryStartVirtualLaneTick());
        assertNotice("energy");
        doReturn(true).when(host).tryConsumeVirtualCraftingPower();
        assertTrue(host.tryStartVirtualLaneTick());
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        assertEquals("gui.neoecoae.crafting.virtual_status.infinite",
            translation(host.getMaintenanceStatusTooltip()).getKey());
    }

    @Test
    void cancellationModeChangesAndExpiredAttemptsHideOldFailures() {
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        host.tryStartVirtualLaneTickWithResult();
        doReturn(0).when(host).getRunningThreadCount();
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        doReturn(1).when(host).getRunningThreadCount();
        doReturn(false).when(host).isFullVirtualCraftingMode();
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        doReturn(true).when(host).isFullVirtualCraftingMode();
        when(tickHandler.getCurrentTick()).thenReturn(201L);
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void pooledTierNineCoolantDoesNotTriggerALocalLowTierWarning() {
        doReturn(2).when(host).getCoolantMaxOverclock();
        doReturn(9).when(host).getDisplayedCoolingMaxOverclock();
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void virtualWorkerRetainsItsBatchOnBothFailuresAndAdvancesAfterRetry() {
        for (var failure : List.of(COOLANT_UNAVAILABLE, ENERGY_UNAVAILABLE)) {
            var worker = mock(ECOCraftingWorkerBlockEntity.class);
            when(worker.getMainNode()).thenReturn(mock(IManagedGridNode.class));
            when(worker.isControlledBy(host)).thenReturn(true);
            var cache = mock(ECOCraftingFastPathCache.class);
            when(worker.getFastPathCache()).thenReturn(cache);
            var recipe = mock(ECOVerifiedFastPathRecipe.class);
            when(recipe.isIssuedBy(cache)).thenReturn(true);
            when(recipe.isCurrent(anyLong())).thenReturn(true);
            var job = UUID.randomUUID();
            long crafts = 238_593_307L;
            var execution = new ECOVerifiedVirtualExecution(recipe, crafts, job,
                List.of(new GenericStack(AEItemKey.of(Items.STONE), crafts * 9L)),
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), crafts)), List.of());
            var thread = new ECOCraftingThread(worker);
            assertTrue(thread.pushVirtualBatch(execution, host));
            doReturn(failure).when(host).tryStartVirtualLaneTickWithResult();
            assertEquals(TickRateModulation.SLOWER, thread.tick(host, 2, 1, 1));
            var blocked = thread.createSnapshot();
            assertTrue(blocked.busy());
            assertEquals(0, blocked.progress());
            assertFalse(blocked.outputsReady());
            assertEquals(crafts, blocked.craftCount());
            assertEquals(job, blocked.craftingJobId());
            verify(worker, never()).onBatchStopped();
            doReturn(STARTED).when(host).tryStartVirtualLaneTickWithResult();
            thread.tick(host, 9, 1, 1);
            var completed = thread.createSnapshot();
            assertEquals(100, completed.progress());
            assertTrue(completed.outputsReady());
            assertEquals(job, completed.craftingJobId());
        }
    }

    private void assertNotice(String reason) {
        assertEquals("gui.neoecoae.crafting.virtual_blocked." + reason,
            translation(host.getCraftingStatusNotice()).getKey());
    }

    private static TranslatableContents translation(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }
}
