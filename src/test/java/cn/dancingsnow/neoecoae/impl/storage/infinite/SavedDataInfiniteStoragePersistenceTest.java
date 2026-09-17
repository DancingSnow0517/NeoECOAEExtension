package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.impl.storage.ECOSavedDataPersistence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedDataInfiniteStoragePersistenceTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void version() {
        SharedConstants.tryDetectVersion();
    }

    @AfterEach
    void clearBackends() {
        ECOSavedDataPersistence.clear();
    }

    @Test
    void vanillaSaveCompletesBeforeReturningAndLaterChangesAreCommitted() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        engine.importLegacy(Map.of(), List.of(), "a".repeat(64), 1);
        engine.save(path.toFile());
        assertEquals(1, InfiniteStorageSnapshot.read(path).getLong("revision"));
        assertFalse(engine.needsPersistence());
        engine.importLegacy(Map.of(), List.of(), "a".repeat(64), 2);
        engine.flushAndAwait();
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertEquals(2, InfiniteStorageSnapshot.read(path).getLong("revision"));
        assertFalse(engine.needsPersistence());
        engine.closeAndFlush();
    }

    @Test
    void quarantinedEngineCannotOverwriteExistingEvidenceDuringAutosave() throws Exception {
        Path path = directory.resolve("domain.dat");
        var engine = SavedDataInfiniteStorageEngine.createNew(UUID.randomUUID(), null, path);
        engine.setDirty();
        engine.flushAndAwait();
        byte[] original = Files.readAllBytes(path);
        engine.persistenceFailed(new IOException("Injected persistence failure"));
        engine.setDirty(); // Even an external dirty mark must not bypass the save gate.
        engine.save(path.toFile());
        engine.flushAndAwait();
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void isolatedInventoryAndReceiptRecordsSurviveSaveWithoutDisablingDomain() throws Exception {
        UUID id = UUID.randomUUID();
        Path path = directory.resolve("domain.dat");
        var initial = SavedDataInfiniteStorageEngine.createNew(id, null, path);
        CompoundTag data = initial.save(new CompoundTag());
        CompoundTag broken = new CompoundTag();
        broken.putString("original", "broken-record-evidence");
        data.getList("entries", 10).add(broken.copy());
        ListTag receipts = new ListTag();
        receipts.add(broken.copy());
        data.put("transfer_receipts", receipts);
        var engine = SavedDataInfiniteStorageEngine.load(data, id, null, path);
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
        assertEquals(2, engine.getEntryFailures().size());
        assertFalse(engine.canTransfer());
        assertFalse(engine.isEmpty());
        engine.setDirty();
        engine.flushAndAwait();
        CompoundTag persisted = InfiniteStorageSnapshot.read(path);
        assertEquals(broken, persisted.getList("entries", 10).getCompound(0));
        assertEquals(broken, persisted.getList("transfer_receipts", 10).getCompound(0));
        assertEquals(ECOInfiniteDomainState.READY, engine.getState());
    }
}
