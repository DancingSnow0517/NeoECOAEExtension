package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedInitialSyncTest {
    @Test
    void fullInitialSnapshotRetainsPendingChangesForExistingViewers() {
        var host = mock(IAutoSyncBlockEntity.class);
        var storage = mock(IManagedStorage.class);
        var dirty = mock(IRef.class);
        var clean = mock(IRef.class);
        when(host.getRootStorage()).thenReturn(storage);
        when(storage.getSyncFields()).thenReturn(new IRef[] { dirty, clean });
        when(dirty.isSyncDirty()).thenReturn(true);
        var payload = mock(SPacketManagedPayload.class);
        var tag = new CompoundTag();
        when(payload.serializeNBT()).thenReturn(tag);
        try (var packets = mockStatic(SPacketManagedPayload.class)) {
            packets.when(() -> SPacketManagedPayload.of(host, true)).thenReturn(payload);
            assertSame(tag, ManagedInitialSync.write(host));
        }
        verify(dirty).markAsDirty();
        verify(clean, never()).markAsDirty();
    }

    @Test
    void failedEncodingStillRetainsPendingChanges() {
        var host = mock(IAutoSyncBlockEntity.class);
        var storage = mock(IManagedStorage.class);
        var field = mock(IRef.class);
        when(host.getRootStorage()).thenReturn(storage);
        when(storage.getSyncFields()).thenReturn(new IRef[] { field });
        when(field.isSyncDirty()).thenReturn(true);
        try (var packets = mockStatic(SPacketManagedPayload.class)) {
            packets.when(() -> SPacketManagedPayload.of(host, true)).thenThrow(new IllegalArgumentException("bad field"));
            assertThrows(IllegalArgumentException.class, () -> ManagedInitialSync.write(host));
        }
        verify(field).markAsDirty();
    }
}
