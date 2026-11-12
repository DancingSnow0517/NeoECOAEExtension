package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.helpers.ForgeEnergyAdapter;
import appeng.me.energy.StoredEnergyAmount;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayDeque;
import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeWorkstationConnectionTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void formationDisconnectsExternalSidesAndDisassemblyRestoresThem() throws Exception {
        var host = controller();
        var node = host.getMainNode();
        assertEquals(EnumSet.allOf(Direction.class), host.getGridConnectableSides(null));
        assertNotNull(host.getEnergyStorage(null));

        host.setFormed(true);
        assertTrue(host.getGridConnectableSides(null).isEmpty());
        verify(node).setExposedOnSides(EnumSet.noneOf(Direction.class));
        assertNull(host.getEnergyStorage(null));
        for (Direction side : Direction.values()) {
            assertEquals(AECableType.NONE, host.getCableConnectionType(side));
            assertNull(host.getEnergyStorage(side));
        }

        host.setFormed(false);
        verify(node).setExposedOnSides(EnumSet.allOf(Direction.class));
        assertEquals(EnumSet.allOf(Direction.class), host.getGridConnectableSides(null));
        for (Direction side : Direction.values()) {
            assertEquals(AECableType.COVERED, host.getCableConnectionType(side));
            assertNotNull(host.getEnergyStorage(side));
        }
    }

    @Test
    void formedControllerRetainsOnlyConnectionsToItsOwnStructureMembers() throws Exception {
        var host = controller();
        var level = mock(Level.class);
        var cluster = new NEIntegratedWorkingStationCluster(BlockPos.ZERO, new BlockPos(2, 1, 1));
        var member = mock(BlockEntity.class);
        var unrelated = mock(BlockEntity.class);
        cluster.addBlockEntity(member);
        field(host, "level", level);
        field(host, "worldPosition", BlockPos.ZERO);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO.north())).thenReturn(member);
        when(level.getBlockEntity(BlockPos.ZERO.south())).thenReturn(unrelated);
        host.updateCluster(cluster);

        assertEquals(EnumSet.of(Direction.NORTH), host.getGridConnectableSides(null));
        verify(host.getMainNode()).setExposedOnSides(EnumSet.of(Direction.NORTH));
        assertNull(host.getEnergyStorage(Direction.NORTH));
        assertNull(host.getEnergyStorage(Direction.SOUTH));
        verify(level).invalidateCapabilities(BlockPos.ZERO);
    }

    @Test
    void cachedEnergyAdapterCannotInsertAfterFormationAndWorksAgainAfterDisassembly() throws Exception {
        var host = controller();
        var adapter = host.getEnergyStorage(Direction.NORTH);
        assertNotNull(adapter);
        assertTrue(receive(adapter, 1000, false) > 0);
        double stored = host.getAECurrentPower();

        host.setFormed(true);
        assertEquals(0, receive(adapter, 1000, true));
        assertEquals(0, receive(adapter, 1000, false));
        assertEquals(0.0, host.getExternalPowerDemand(PowerUnit.AE, 1000));
        assertEquals(1000.0, host.injectExternalPower(PowerUnit.AE, 1000, Actionable.MODULATE));
        assertEquals(stored, host.getAECurrentPower());

        // Refunds from the multiblock's own batch processing still use its internal AE buffer.
        assertEquals(0.0, host.injectAEPower(1000, Actionable.MODULATE));
        assertEquals(stored + 1000, host.getAECurrentPower());

        host.setFormed(false);
        assertTrue(receive(adapter, 1000, false) > 0);
    }

    @Test
    void clientFormationStateHidesCableConnectionBeforeFieldSynchronization() throws Exception {
        var host = controller();
        when(host.getBlockState().hasProperty(ECOIntegratedWorkingStation.FORMED)).thenReturn(true);
        when(host.getBlockState().getValue(ECOIntegratedWorkingStation.FORMED)).thenReturn(true);
        assertFalse(host.isFormed());
        assertEquals(AECableType.NONE, host.getCableConnectionType(Direction.NORTH));
    }

    @Test
    void destroyingClusterAfterControllerNodeUnloadStillClearsFormationAndCapabilities() throws Exception {
        var host = controller();
        var level = mock(Level.class);
        field(host, "level", level);
        field(host, "worldPosition", BlockPos.ZERO);
        var cluster = new NEIntegratedWorkingStationCluster(BlockPos.ZERO, new BlockPos(2, 1, 1));
        cluster.addBlockEntity(host);
        host.updateCluster(cluster);
        assertTrue(host.isFormed());

        var node = host.getMainNode();
        when(node.isReady()).thenReturn(false);
        doThrow(new IllegalStateException("Node already initialized and destroyed"))
            .when(node).setExposedOnSides(anySet());
        clearInvocations(node, level, host);

        assertDoesNotThrow(cluster::destroy);

        assertFalse(host.isRemoved());
        assertFalse(host.isFormed());
        assertNull(host.getCluster());
        assertNotNull(host.getEnergyStorage(Direction.NORTH));
        verify(node, never()).setExposedOnSides(anySet());
        verify(host).updateState(true);
        verify(level).invalidateCapabilities(BlockPos.ZERO);
        verify(host).markForUpdate();
    }

    @Test
    void allRotationsExposeExactlyTheFourUpperCornersAndKeepTheTwoCoolantBlocksSeparate() throws Exception {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var host = controller();
            var origin = new BlockPos(16, 64, 16);
            field(host, "worldPosition", origin);
            when(host.getBlockState().getValue(ECOIntegratedWorkingStation.FACING)).thenReturn(facing);
            var cluster = new NEIntegratedWorkingStationCluster(origin.offset(-1, -1, -1), origin.offset(1, 0, 1));
            var input = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class);
            var smart = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class);
            when(smart.isSmartCoolingModule()).thenReturn(true);
            cluster.addBlockEntity(host);
            cluster.addBlockEntity((BlockEntity) smart);
            cluster.addBlockEntity((BlockEntity) input);
            assertSame(input, cluster.getInputHatch());
            assertSame(smart, cluster.getSmartCoolingController());
            int ports = 0;
            for (int back = 0; back <= 1; back++) for (int x = -1; x <= 1; x++) {
                BlockPos pos = origin.relative(facing.getOpposite(), back).relative(facing.getClockWise(), x);
                boolean expected = x != 0;
                assertEquals(expected, cluster.isTopPowerPort(pos), facing + " " + pos);
                assertFalse(cluster.isTopPowerPort(pos.below()));
                if (expected) ports++;
            }
            assertEquals(4, ports);
        }
    }

    @Test
    void topSocketSharesControllerPowerAndCachedAdapterStopsWhenStructureBreaks() throws Exception {
        var host = controller();
        var origin = new BlockPos(16, 64, 16);
        field(host, "worldPosition", origin);
        when(host.getBlockState().getValue(ECOIntegratedWorkingStation.FACING)).thenReturn(Direction.NORTH);
        var cluster = new NEIntegratedWorkingStationCluster(origin.offset(-1, -1, 0), origin.offset(1, 0, 1));
        cluster.addBlockEntity(host);
        host.updateCluster(cluster);

        var port = mock(ECOLargeIntegratedWorkingStationCasingBlockEntity.class, CALLS_REAL_METHODS);
        field(port, "worldPosition", origin.west());
        field(port, "formed", true);
        field(port, "cluster", cluster);
        field(port, "energyAdapter", new ForgeEnergyAdapter(port));
        assertEquals(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST),
            port.getGridConnectableSides(null));
        for (Direction side : Direction.values()) {
            if (side.getAxis().isHorizontal()) assertNotNull(port.getEnergyStorage(side));
            else assertNull(port.getEnergyStorage(side));
        }
        assertNull(port.getEnergyStorage(null));
        var energy = port.getEnergyStorage(Direction.NORTH);
        assertNotNull(energy);
        assertEquals(1000, receive(energy, 1000, true));
        assertEquals(0, host.getAECurrentPower());
        assertEquals(1000, receive(energy, 1000, false));
        assertEquals(PowerUnit.FE.convertTo(PowerUnit.AE, 1000), host.getAECurrentPower(), 1e-9);

        host.setInternalCurrentPower(host.getAEMaxPower() - PowerUnit.FE.convertTo(PowerUnit.AE, 8));
        assertEquals(8, receive(energy, 1000, false));
        assertEquals(host.getAEMaxPower(), host.getAECurrentPower());
        assertEquals(0, receive(energy, 1, false));
        assertEquals(0, extract(energy, 100));
        host.updateCluster(null);
        assertNull(port.getEnergyStorage(Direction.NORTH));
        assertEquals(0, receive(energy, 1000, true));
        assertEquals(0, receive(energy, 1000, false));
    }

    private static ECOLargeIntegratedWorkingStationBlockEntity controller() throws Exception {
        var host = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(mock(IManagedGridNode.class)).when(host).getMainNode();
        when(host.getMainNode().isReady()).thenReturn(true);
        doReturn(mock(BlockState.class)).when(host).getBlockState();
        doReturn(null).when(host).getOrientation();
        doNothing().when(host).markForUpdate();
        doNothing().when(host).updateState(anyBoolean());
        field(host, "pendingBatches", new ArrayDeque<>());
        field(host, "stored", new StoredEnergyAmount(0, 16_000_000, event -> {}));
        field(host, "forgeEnergyAdapter", new ForgeEnergyAdapter(host));
        field(host, "internalPowerSides", EnumSet.allOf(Direction.class));
        return host;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    /** 26.1: IEnergyStorage#receiveEnergy became EnergyHandler#insert inside a transaction. */
    private static int receive(net.neoforged.neoforge.transfer.energy.EnergyHandler handler, int amount, boolean simulate) {
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            if (!simulate) transaction.commit();
            return inserted;
        }
    }
    private static int extract(net.neoforged.neoforge.transfer.energy.EnergyHandler handler, int amount) {
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            return handler.extract(amount, transaction);
        }
    }
}
