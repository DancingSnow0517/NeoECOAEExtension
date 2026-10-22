package cn.dancingsnow.neoecoae.platform.sync;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ManagedRenderUpdateTest {
    @Test
    void synchronizedGeometryChangesInvalidateTheCurrentClientModel() {
        var host = mock(NEManagedBlockEntity.class, CALLS_REAL_METHODS);
        var entity = mock(BlockEntity.class);
        var level = mock(Level.class);
        var state = mock(BlockState.class);
        doReturn(entity).when(host).getSelf();
        when(entity.getLevel()).thenReturn(level);
        when(entity.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(entity.getBlockState()).thenReturn(state);
        when(level.isClientSide()).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(entity);

        host.scheduleRenderUpdate();

        verify(entity).requestModelDataUpdate();
        verify(level).sendBlockUpdated(BlockPos.ZERO, state, state, Block.UPDATE_CLIENTS);
    }

    @Test
    void lateNotificationsCannotRebuildAReplacedClientBlockEntity() {
        var host = mock(NEManagedBlockEntity.class, CALLS_REAL_METHODS);
        var entity = mock(BlockEntity.class);
        var level = mock(Level.class);
        doReturn(entity).when(host).getSelf();
        when(entity.getLevel()).thenReturn(level);
        when(entity.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(level.isClientSide()).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(mock(BlockEntity.class));

        host.scheduleRenderUpdate();

        verify(entity, never()).requestModelDataUpdate();
        verify(level, never()).sendBlockUpdated(any(), any(), any(), anyInt());
    }
}
