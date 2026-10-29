package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import appeng.api.storage.cells.CellState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class DrivePersistenceNotificationTest {
    @Test void oneQueuedNotificationPerDriveAndBlockUpdatesOnlyOnStateChange() throws Exception {
        ECODriveBlockEntity drive = StorageDriveTestFixtures.drive();
        List<Runnable> queued = new ArrayList<>();
        MinecraftServer server = mock(MinecraftServer.class);
        doAnswer(call -> queued.add(call.getArgument(0))).when(server).executeIfPossible(any(Runnable.class));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server);
        set(BlockEntity.class, drive, "level", level);
        set(ECODriveBlockEntity.class, drive, "cellState", CellState.ABSENT);
        doNothing().when(drive).setChanged();
        doNothing().when(drive).markForUpdate();

        // A burst of FE transfers before the server runs the task queues it only once.
        drive.notifyPersistence();
        drive.notifyPersistence();
        drive.notifyPersistence();
        assertEquals(1, queued.size());
        queued.remove(0).run();
        verify(drive, times(1)).setChanged();
        verify(drive, never()).markForUpdate();

        // After it ran, the next change is queued again; a real state change sends one block update.
        drive.notifyPersistence();
        assertEquals(1, queued.size());
        set(ECODriveBlockEntity.class, drive, "cellState", CellState.NOT_EMPTY);
        queued.remove(0).run();
        verify(drive, times(2)).setChanged();
        verify(drive, times(1)).markForUpdate();
        assertEquals(CellState.ABSENT, drive.getCellState());
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
