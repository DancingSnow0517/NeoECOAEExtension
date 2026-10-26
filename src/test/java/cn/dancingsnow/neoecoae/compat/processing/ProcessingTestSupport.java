package cn.dancingsnow.neoecoae.compat.processing;

import static org.mockito.Mockito.*;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ProcessingTestSupport {
    private ProcessingTestSupport() {}
    public static <T extends BlockEntity> T machine(Class<T> type) {
        T machine = mock(type);
        when(machine.getLevel()).thenReturn(mock(ServerLevel.class));
        when(machine.getLevel().dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(machine.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        return machine;
    }
    public static void set(Object value, String name, Object contents) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(value, contents);
                return;
            } catch (NoSuchFieldException missing) { /* Superclass. */ }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("Missing field " + name);
    }
}
