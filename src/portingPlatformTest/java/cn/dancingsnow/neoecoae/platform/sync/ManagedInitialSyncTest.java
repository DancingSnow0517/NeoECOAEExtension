package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedKey;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import com.lowdragmc.lowdraglib.syncdata.managed.IManagedVar;
import com.lowdragmc.lowdraglib.syncdata.managed.ManagedRef;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.nbt.CompoundTag;
import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedInitialSyncTest {
    @Test
    void fullInitialSnapshotRetainsPendingChangesWithoutPersistenceCallbacks() {
        var host = mock(IAutoSyncBlockEntity.class);
        var storage = mock(IManagedStorage.class);
        var sync = mock(BooleanConsumer.class);
        var persisted = mock(BooleanConsumer.class);
        IRef dirty = field(sync, persisted);
        IRef clean = field(sync, persisted);
        dirty.markAsDirty();
        clearInvocations(sync, persisted);
        when(host.getRootStorage()).thenReturn(storage);
        when(storage.getSyncFields()).thenReturn(new IRef[] { dirty, clean });
        var tag = new CompoundTag();
        List<Object> arguments = new ArrayList<>();
        try (var packets = mockConstruction(SPacketManagedPayload.class, (packet, context) -> {
            arguments.addAll(context.arguments());
            when(packet.serializeNBT()).thenReturn(tag);
        })) {
            assertSame(tag, ManagedInitialSync.write(host));
        }
        assertTrue(dirty.isSyncDirty());
        assertTrue(dirty.isPersistedDirty());
        assertFalse(clean.isSyncDirty());
        assertFalse(clean.isPersistedDirty());
        assertEquals(BitSet.valueOf(new long[] { 3 }), arguments.get(2));
        assertEquals(2, ((ITypedPayload<?>[]) arguments.get(3)).length);
        verifyNoInteractions(sync, persisted);
    }

    @Test
    void failedEncodingStillRetainsPendingChanges() {
        var host = mock(IAutoSyncBlockEntity.class);
        var storage = mock(IManagedStorage.class);
        var sync = mock(BooleanConsumer.class);
        var persisted = mock(BooleanConsumer.class);
        IRef field = field(sync, persisted);
        field.markAsDirty();
        clearInvocations(sync, persisted);
        when(host.getRootStorage()).thenReturn(storage);
        when(storage.getSyncFields()).thenReturn(new IRef[] { field });
        try (var packets = mockConstruction(SPacketManagedPayload.class, (packet, context) ->
            when(packet.serializeNBT()).thenThrow(new IllegalArgumentException("bad field")))) {
            assertThrows(IllegalArgumentException.class, () -> ManagedInitialSync.write(host));
        }
        assertTrue(field.isSyncDirty());
        assertTrue(field.isPersistedDirty());
        verifyNoInteractions(sync, persisted);
    }

    private static IRef field(BooleanConsumer sync, BooleanConsumer persisted) {
        ManagedRef field = ManagedRef.create(mock(IManagedVar.Int.class), false);
        var key = mock(ManagedKey.class);
        when(key.isDestSync()).thenReturn(true);
        when(key.isPersist()).thenReturn(true);
        when(key.readSyncedField(field, true)).thenReturn(mock(ITypedPayload.class));
        field.setKey(key);
        field.setOnSyncListener(sync);
        field.setOnPersistedListener(persisted);
        return field;
    }
}
