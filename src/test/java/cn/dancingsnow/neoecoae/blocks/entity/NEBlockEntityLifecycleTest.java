package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NEBlockEntityLifecycleTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void clusterDestructionUpdatesMemberStateAfterNodeUnload() throws Exception {
        var fixture = member(false);
        var cluster = new NEIntegratedWorkingStationCluster(BlockPos.ZERO, new BlockPos(2, 1, 1));
        cluster.addBlockEntity((net.minecraft.world.level.block.entity.BlockEntity) fixture.host());
        field(fixture.host(), "cluster", cluster);
        fixture.host().setFormed(true);

        assertDoesNotThrow(cluster::destroy);

        assertFalse(fixture.host().isRemoved());
        assertFalse(fixture.host().isFormed());
        assertNull(fixture.host().getCluster());
        verify(fixture.level()).setBlock(BlockPos.ZERO, fixture.unformedState(), Block.UPDATE_CLIENTS);
        verify(fixture.node(), never()).setExposedOnSides(anySet());
    }

    @Test
    void readyUnpoweredMemberStillRefreshesConnections() throws Exception {
        var fixture = member(true);
        assertFalse(fixture.node().isActive());

        fixture.host().updateCluster(null);

        verify(fixture.node()).setExposedOnSides(anySet());
        verify(fixture.level()).setBlock(BlockPos.ZERO, fixture.unformedState(), Block.UPDATE_CLIENTS);
    }

    @Test
    void updateWithoutExposureDoesNotRefreshReadyNode() throws Exception {
        var fixture = member(true);

        fixture.host().updateState(false);

        verify(fixture.node(), never()).setExposedOnSides(anySet());
        verify(fixture.level()).setBlock(BlockPos.ZERO, fixture.unformedState(), Block.UPDATE_CLIENTS);
    }

    private static Fixture member(boolean ready) throws Exception {
        var host = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class, CALLS_REAL_METHODS);
        var node = mock(IManagedGridNode.class);
        doReturn(node).when(host).getMainNode();
        when(node.isReady()).thenReturn(ready);
        if (!ready) {
            doThrow(new IllegalStateException("Node already initialized and destroyed"))
                .when(node).setExposedOnSides(anySet());
        }
        doReturn(null).when(host).getOrientation();
        var level = mock(Level.class);
        var state = mock(BlockState.class);
        var unformedState = mock(BlockState.class);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
        when(state.hasProperty(NEBlock.FORMED)).thenReturn(true);
        when(state.setValue(NEBlock.FORMED, false)).thenReturn(unformedState);
        field(host, "level", level);
        field(host, "worldPosition", BlockPos.ZERO);
        return new Fixture(host, node, level, unformedState);
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

    private record Fixture(ECOLargeIntegratedWorkingStationInputHatchBlockEntity host,
                           IManagedGridNode node, Level level, BlockState unformedState) { }
}
