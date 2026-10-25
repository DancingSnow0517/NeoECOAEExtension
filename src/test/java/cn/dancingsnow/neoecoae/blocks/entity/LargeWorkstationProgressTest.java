package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeWorkstationProgressTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName());
        }
    }

    @Test void readsOwnedItemAndFluidOutputsWithoutChangingBatchAccounting() throws Exception {
        var batch = batch();
        var controller = controller(batch);
        for (var output : List.of(AEItemKey.of(Items.STONE), AEFluidKey.of(Fluids.WATER))) {
            var result = controller.getProcessingStatus(output);
            assertEquals(State.RUNNING, result.state());
            assertEquals(50, result.progressTicks());
            assertEquals(200, result.durationTicks());
            assertEquals(768, result.parallel());
            assertEquals("test:workstation", result.recipe());
        }
        assertEquals(50, field(batch, "progress"));
        assertEquals(768L, field(batch, "craftCount"));
        assertEquals(768, ((KeyCounter) field(batch, "outputTotal")).get(AEItemKey.of(Items.STONE)));
    }

    @Test void anotherProductAndAnIdleWorkstationHaveNoCycleProgress() throws Exception {
        var result = controller(batch()).getProcessingStatus(AEItemKey.of(Items.DIRT));
        assertEquals(State.QUEUED, result.state());
        assertEquals(0, result.durationTicks());
        assertEquals(0, result.parallel());
        assertEquals("", result.recipe());
        assertEquals(0, controller(null).getProcessingStatus(AEItemKey.of(Items.STONE)).durationTicks());
        var unformed = controller(batch());
        set(unformed, "formed", false);
        var unavailable = unformed.getProcessingStatus(AEItemKey.of(Items.STONE));
        assertEquals(State.UNAVAILABLE, unavailable.state());
        assertEquals(0, unavailable.durationTicks());
    }

    @Test void fallbackShowsTheActualPortionProgressAndParallelism() throws Exception {
        var batch = batch();
        set(batch, "segmented", true);
        set(batch, "progress", 33);
        set(batch, "progressRemainder", 256L);
        var result = controller(batch).getProcessingStatus(AEItemKey.of(Items.STONE));
        assertEquals(100, result.progressTicks());
        assertEquals(200, result.durationTicks());
        assertEquals(256, result.parallel());
    }

    @Test void powerAndOutputFailuresPreserveTheRealCycleAndNativeReason() throws Exception {
        var controller = controller(batch());
        var reasonType = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PauseReason");
        for (String name : List.of("POWER_MISSING", "FE_MISSING", "OUTPUT_BLOCKED", "COOLANT_MISSING")) {
            for (Object reason : reasonType.getEnumConstants()) if (((Enum<?>) reason).name().equals(name)) {
                set(controller, "pauseReasonId", ((Enum<?>) reason).ordinal());
            }
            var result = controller.getProcessingStatus(AEItemKey.of(Items.STONE));
            assertEquals(State.WAITING, result.state());
            assertFalse(result.reason().getString().isEmpty());
            assertEquals(50, result.progressTicks());
        }
    }

    @Test void disconnectedNetworkCannotAdvertiseRunning() throws Exception {
        var controller = controller(batch());
        var node = controller.getMainNode();
        doReturn(false).when(node).isActive();
        var result = controller.getProcessingStatus(AEItemKey.of(Items.STONE));
        assertEquals(State.WAITING, result.state());
        assertEquals(50, result.progressTicks());
    }

    @Test void scanReadsTheNativeControllerAndSkipsUnloadedChunks() throws Exception {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var server = mock(net.minecraft.server.MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
        var controller = controller(batch());
        var target = new cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target(
            Level.OVERWORLD.location(), controller.getBlockPos());
        var output = AEItemKey.of(Items.STONE);
        var unloaded = new cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus.Scan(List.of(target), output);
        unloaded.step(level);
        verify(level, never()).getBlockEntity(any());
        when(level.hasChunkAt(controller.getBlockPos())).thenReturn(true);
        when(level.getBlockEntity(controller.getBlockPos())).thenReturn(controller);
        var scan = new cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus.Scan(List.of(target), output);
        scan.step(level);
        assertTrue(scan.complete());
        assertEquals(List.of(target), scan.machineTargets());
        assertEquals(50, scan.machines().get(0).progressTicks());
        assertEquals(200, scan.machines().get(0).durationTicks());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    private static ECOLargeIntegratedWorkingStationBlockEntity controller(Object batch) throws Exception {
        var controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        controller.setLevel(level);
        doReturn(new BlockPos(10, 64, 20)).when(controller).getBlockPos();
        doReturn(Blocks.STONE.defaultBlockState()).when(controller).getBlockState();
        var node = mock(IManagedGridNode.class);
        when(node.isActive()).thenReturn(true);
        doReturn(node).when(controller).getMainNode();
        doReturn(false).when(controller).isTemperatureEnabled();
        set(controller, "formed", true);
        var batches = new ArrayDeque<>();
        if (batch != null) batches.add(batch);
        set(controller, "pendingBatches", batches);
        return controller;
    }

    private static Object batch() throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var output = new KeyCounter();
        output.add(AEItemKey.of(Items.STONE), 768);
        output.add(AEFluidKey.of(Fluids.WATER), 768000);
        var result = constructor.newInstance(768L, 100L, 0, 1, new KeyCounter(), output, null,
            new ResourceLocation("test:workstation"), null, null, 0L);
        set(result, "progress", 50);
        return result;
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
