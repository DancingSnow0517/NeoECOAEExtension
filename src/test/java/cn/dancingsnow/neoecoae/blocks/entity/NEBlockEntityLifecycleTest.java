package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

class NEBlockEntityLifecycleTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedClearRemovedCallbacksDoNotRecreateLiveNode() throws Exception {
        var fixture = member(false);
        var ready = new AtomicBoolean();
        when(fixture.node().isReady()).thenAnswer(call -> ready.get());
        doAnswer(call -> {
            if (ready.getAndSet(true)) {
                throw new IllegalStateException("The node has already been initialized");
            }
            return null;
        }).when(fixture.node()).create(fixture.level(), BlockPos.ZERO);
        // Unlike an unloaded node, a fresh node accepts its initial exposed sides.
        doReturn(fixture.node()).when(fixture.node()).setExposedOnSides(anySet());
        var callbacks = new ArrayList<Consumer<AEBaseBlockEntity>>();
        try (var gridHelper = mockStatic(GridHelper.class)) {
            gridHelper.when(() -> GridHelper.onFirstTick(eq(fixture.host()), any()))
                .thenAnswer(call -> {
                    callbacks.add(call.getArgument(1));
                    return null;
                });
            fixture.host().clearRemoved();
            fixture.host().clearRemoved();
            assertEquals(2, callbacks.size());
            for (var callback : callbacks) {
                assertDoesNotThrow(() -> callback.accept(fixture.host()));
            }
            // A later copy of an already ticking machine must also be harmless.
            fixture.host().clearRemoved();
            assertEquals(3, callbacks.size());
            assertDoesNotThrow(() -> callbacks.get(2).accept(fixture.host()));
        }
        assertTrue(ready.get());
        verify(fixture.node()).create(fixture.level(), BlockPos.ZERO);
        verify(fixture.node()).setIdlePowerUsage(16);
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
        doReturn(state).when(host).getBlockState();
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
