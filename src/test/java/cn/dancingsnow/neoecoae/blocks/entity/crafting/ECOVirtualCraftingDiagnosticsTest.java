package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity.VirtualLaneStartResult;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity.VirtualLaneStartResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ECOVirtualCraftingDiagnosticsTest {
    private Lane lane;
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
        lane = createLane(0);
        host = lane.host();
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
        var tooltipKeys = host.getMaintenanceStatusTooltip().getSiblings().stream()
            .map(Component::getContents).filter(TranslatableContents.class::isInstance)
            .map(TranslatableContents.class::cast).map(TranslatableContents::getKey).toList();
        assertTrue(tooltipKeys.contains("gui.neoecoae.crafting.virtual_blocked.coolant_hint"));
        assertTrue(tooltipKeys.contains("gui.neoecoae.crafting.virtual_blocked.coolant_recovery"));
        verify(host, never()).tryConsumeVirtualCraftingPower();
        verify(host, never()).tryConsumeVirtualLaneCoolant();
        verify(host, never()).getLocalAvailableCoolant(anyInt(), anyInt());
    }

    @Test
    void coolantFailureIsReportedBeforeAnyEnergyCharge() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(0).when(host).getLocalAvailableCoolant(10_000, 9);
        assertEquals(TickRateModulation.SLOWER, lane.thread().tick(host, 9, 1, 1));
        assertEquals(COOLANT_UNAVAILABLE, lane.worker().getVirtualCraftingBlockedResult(100L));
        assertNotice(host, "coolant");
        verify(host, never()).tryConsumeVirtualCraftingPower();
        verify(host, never()).tryConsumeVirtualLaneCoolant();
    }

    @Test
    void energyFailureDoesNotConsumeCoolantAndIsShownInBothUiSurfaces() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        assertEquals(ENERGY_UNAVAILABLE, lane.worker().getVirtualCraftingBlockedResult(100L));
        assertNotice(host, "energy");
        assertEquals("gui.neoecoae.crafting.virtual_blocked.title",
            translation(host.getMaintenanceStatusTooltip()).getKey());
        verify(host, never()).tryConsumeVirtualLaneCoolant();
        // Reading either UI surface must never probe or charge resources again.
        verify(host, times(1)).tryConsumeVirtualCraftingPower();
    }

    @Test
    void coolantConsumptionFailureIsNotMisreportedAsEnergyFailure() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(false).when(host).tryConsumeVirtualLaneCoolant();
        lane.thread().tick(host, 9, 1, 1);
        assertNotice(host, "coolant");
        assertFalse(host.getMaintenanceStatusTooltip().getSiblings().stream()
            .map(Component::getContents).filter(TranslatableContents.class::isInstance)
            .map(TranslatableContents.class::cast)
            .anyMatch(content -> content.getKey().equals("gui.neoecoae.crafting.virtual_blocked.coolant_hint")));
    }

    @Test
    void legacyStartMethodRetainsItsBooleanContract() {
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        assertFalse(host.tryStartVirtualLaneTick());
        doReturn(true).when(host).tryConsumeVirtualCraftingPower();
        assertTrue(host.tryStartVirtualLaneTick());
    }

    @Test
    void successfulRetryClearsTheExecutionWarning() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        assertNotice(host, "energy");
        doReturn(true).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        assertEquals("gui.neoecoae.crafting.virtual_status.infinite",
            translation(host.getMaintenanceStatusTooltip()).getKey());
    }

    @Test
    void expiredAttemptsAndBackwardTicksHideOldFailures() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        when(tickHandler.getCurrentTick()).thenReturn(201L);
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        when(tickHandler.getCurrentTick()).thenReturn(99L);
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void leavingVirtualExecutionClearsTheOldWarningBeforeReentry() {
        startBatch(lane, UUID.randomUUID(), 1L);
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        assertNotice(host, "energy");
        doReturn(false).when(host).isFullVirtualCraftingMode();
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        lane.thread().tick(host, 2, 1, 1);
        doReturn(true).when(host).isFullVirtualCraftingMode();
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = VirtualLaneStartResult.class, names = {"COOLANT_UNAVAILABLE", "ENERGY_UNAVAILABLE"})
    void everyNetworkMemberSeesTheExecutingLanesFailure(VirtualLaneStartResult failure)
        throws ReflectiveOperationException {
        var lanes = createNetwork();
        var executing = lanes.getFirst();
        startBatch(executing, UUID.randomUUID(), 1L);
        doReturn(failure).when(executing.host()).tryStartVirtualLaneTickWithResult();
        executing.thread().tick(executing.host(), 9, 1, 1);
        for (var member : lanes) {
            assertNotice(member.host(), failure == COOLANT_UNAVAILABLE ? "coolant" : "energy");
            assertEquals("gui.neoecoae.crafting.virtual_blocked.title",
                translation(member.host().getMaintenanceStatusTooltip()).getKey());
            verify(member.host(), never()).tryConsumeVirtualCraftingPower();
            verify(member.host(), never()).tryConsumeVirtualLaneCoolant();
        }
    }

    @Test
    void anotherSuccessfulLaneDoesNotHideAnActiveFailure() throws ReflectiveOperationException {
        var lanes = createNetwork();
        var blocked = lanes.getFirst();
        var successful = lanes.get(1);
        startBatch(blocked, UUID.randomUUID(), 1L);
        startBatch(successful, UUID.randomUUID(), 1L);
        doReturn(COOLANT_UNAVAILABLE).when(blocked.host()).tryStartVirtualLaneTickWithResult();
        blocked.thread().tick(blocked.host(), 9, 1, 1);
        doReturn(STARTED).when(successful.host()).tryStartVirtualLaneTickWithResult();
        successful.thread().tick(successful.host(), 9, 1, 1);
        for (var member : lanes) assertNotice(member.host(), "coolant");
    }

    @Test
    void cancellationClearsTheWarningBeforeWorkerRecoveryAndBeforeANewBatch() {
        var job = UUID.randomUUID();
        startBatch(lane, job, 1L);
        doReturn(false).when(host).tryConsumeVirtualCraftingPower();
        lane.thread().tick(host, 9, 1, 1);
        assertNotice(host, "energy");
        try (var jobs = mockStatic(ECOCraftingJobLifecycle.class)) {
            jobs.when(() -> ECOCraftingJobLifecycle.isTerminated(null, job)).thenReturn(true);
            // Cancellation is visible even before the next worker tick reconciles custody.
            assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
            lane.thread().reconcileJobTermination();
        }
        // A recovering batch may still be counted as busy, but has no execution blocker.
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
        var storage = mock(MEStorage.class);
        when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any()))
            .thenAnswer(call -> call.getArgument(1));
        assertTrue(lane.thread().recoverInputsToNetwork(storage));
        when(tickHandler.getCurrentTick()).thenReturn(101L);
        startBatch(lane, UUID.randomUUID(), 1L);
        assertTrue(host.getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void regroupedWorkersDoNotCarryThePreviousNetworksWarning() throws ReflectiveOperationException {
        var lanes = createNetwork();
        var executing = lanes.getFirst();
        startBatch(executing, UUID.randomUUID(), 1L);
        doReturn(ENERGY_UNAVAILABLE).when(executing.host()).tryStartVirtualLaneTickWithResult();
        executing.thread().tick(executing.host(), 9, 1, 1);
        assertNotice(lanes.get(1).host(), "energy");
        var replacement = new NECraftingNetworkCluster();
        var clusters = lanes.stream().map(Lane::cluster).toList();
        clusters.forEach(cluster -> cluster.setNetworkCluster(replacement));
        replacement.configure(clusters);
        for (var member : lanes) assertTrue(member.host().getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void pooledTierNineCoolantDoesNotTriggerALocalLowTierWarning() throws ReflectiveOperationException {
        var lanes = createNetwork();
        doReturn(2).when(lanes.getFirst().host()).getLocalCoolingMaxOverclock();
        doReturn(2).when(lanes.getFirst().host()).getCoolantMaxOverclock();
        assertEquals(9, lanes.getFirst().host().getDisplayedCoolingMaxOverclock());
        for (var member : lanes) assertTrue(member.host().getCraftingStatusNotice().getString().isEmpty());
    }

    @Test
    void virtualWorkerRetainsItsBatchOnBothFailuresAndAdvancesAfterRetry()
        throws ReflectiveOperationException {
        for (var failure : List.of(COOLANT_UNAVAILABLE, ENERGY_UNAVAILABLE)) {
            var current = createLane(1);
            var job = UUID.randomUUID();
            long crafts = 238_593_307L;
            startBatch(current, job, crafts);
            doReturn(failure).when(current.host()).tryStartVirtualLaneTickWithResult();
            assertEquals(TickRateModulation.SLOWER, current.thread().tick(current.host(), 2, 1, 1));
            var blocked = current.thread().createSnapshot();
            assertTrue(blocked.busy());
            assertEquals(0, blocked.progress());
            assertFalse(blocked.outputsReady());
            assertEquals(crafts, blocked.craftCount());
            assertEquals(job, blocked.craftingJobId());
            verify(current.worker(), never()).onBatchStopped();
            doReturn(STARTED).when(current.host()).tryStartVirtualLaneTickWithResult();
            current.thread().tick(current.host(), 9, 1, 1);
            var completed = current.thread().createSnapshot();
            assertEquals(100, completed.progress());
            assertTrue(completed.outputsReady());
            assertEquals(job, completed.craftingJobId());
        }
    }

    private Lane createLane(int index) throws ReflectiveOperationException {
        var host = mock(ECOCraftingSystemBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(new BlockPos(index, 0, 0)).when(host).getBlockPos();
        doReturn(true).when(host).isFullVirtualCraftingMode();
        doReturn(true).when(host).isActiveCooling();
        doReturn(9).when(host).getDisplayedCoolingMaxOverclock();
        doReturn(9).when(host).getLocalCoolingMaxOverclock();
        doReturn(1).when(host).getRunningThreadCount();
        doReturn(10_000).when(host).getLocalAvailableCoolant(10_000, 9);
        doReturn(true).when(host).tryConsumeVirtualCraftingPower();
        doReturn(true).when(host).tryConsumeVirtualLaneCoolant();
        var snapshot = mock(CraftingCapabilitySnapshot.class);
        when(snapshot.virtualMode()).thenReturn(true);
        doReturn(snapshot).when(host).getCapabilitySnapshot();
        setField(NEBlockEntity.class, host, "formed", true);
        var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
        setField(NEBlockEntity.class, host, "cluster", cluster);
        cluster.addBlockEntity(host);
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        when(worker.getMainNode()).thenReturn(mock(IManagedGridNode.class));
        when(worker.getCluster()).thenReturn(cluster);
        when(worker.isControlledBy(host)).thenReturn(true);
        setField(NEBlockEntity.class, worker, "cluster", cluster);
        var cache = mock(ECOCraftingFastPathCache.class);
        when(worker.getFastPathCache()).thenReturn(cache);
        var recipe = mock(ECOVerifiedFastPathRecipe.class);
        when(recipe.isIssuedBy(cache)).thenReturn(true);
        when(recipe.isCurrent(anyLong())).thenReturn(true);
        var thread = new ECOCraftingThread(worker);
        setField(ECOCraftingWorkerBlockEntity.class, worker, "craftingThreads", List.of(thread));
        doCallRealMethod().when(worker).getVirtualCraftingBlockedResult(anyLong());
        cluster.addBlockEntity(worker);
        return new Lane(host, worker, cluster, thread, recipe);
    }

    private List<Lane> createNetwork() throws ReflectiveOperationException {
        var lanes = new ArrayList<Lane>();
        var network = new NECraftingNetworkCluster();
        for (int i = 0; i < 8; i++) {
            var current = createLane(i);
            current.cluster().setNetworkCluster(network);
            doCallRealMethod().when(current.host()).getDisplayedCoolingMaxOverclock();
            lanes.add(current);
        }
        network.configure(lanes.stream().map(Lane::cluster).toList());
        return lanes;
    }

    private void startBatch(Lane lane, UUID job, long crafts) {
        var execution = new ECOVerifiedVirtualExecution(lane.recipe(), crafts, job,
            List.of(new GenericStack(AEItemKey.of(Items.STONE), crafts * 9L)),
            List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), crafts)), List.of());
        assertTrue(lane.thread().pushVirtualBatch(execution, lane.host()));
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
        throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void assertNotice(ECOCraftingSystemBlockEntity host, String reason) {
        assertEquals("gui.neoecoae.crafting.virtual_blocked." + reason,
            translation(host.getCraftingStatusNotice()).getKey());
    }

    private static TranslatableContents translation(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    private record Lane(ECOCraftingSystemBlockEntity host, ECOCraftingWorkerBlockEntity worker,
                        NECraftingCluster cluster, ECOCraftingThread thread, ECOVerifiedFastPathRecipe recipe) {}
}
