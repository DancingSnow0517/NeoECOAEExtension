package cn.dancingsnow.neoecoae.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.storage.StorageTestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageCheckpointSchedulingTest {
    @BeforeAll static void bootstrap() throws Exception { StorageTestContext.bootstrap(); }

    private static final class Record extends StorageSavedData {
        long amount;
        boolean changeAfterWrite;
        void add(long value) { amount += value; setDirty(); }
        @Override public CompoundTag save(CompoundTag tag) { tag.putLong("amount",amount); return tag; }
        @Override protected void snapshotSaved() {
            if (changeAfterWrite) { changeAfterWrite=false; add(7); }
        }
    }

    @Test void repeatedChangesRetainTheFirstDeadlineAndPersistAllRevisions(@TempDir Path directory) throws Exception {
        var session=new ECOStorageRepository.Session(null,directory);
        var data=new Record();data.bind(directory.resolve("record.dat"));session.track(data);
        for(int i=0;i<10000;i++)data.add(1);
        assertEquals(10000,data.contentRevision());
        session.tick(99);assertEquals(0,data.savedSnapshots());
        session.tick(100);
        assertFalse(data.isDirty());assertEquals(1,data.savedSnapshots());
        assertEquals(10000,data.durableRevision());
        assertEquals(10000,NbtIo.readCompressed(data.dataFile().toFile()).getCompound("data").getLong("amount"));
        data.add(3);session.tick(199);assertEquals(1,data.savedSnapshots());
        session.tick(200);assertEquals(2,data.savedSnapshots());assertEquals(data.contentRevision(),data.durableRevision());
    }

    @Test void modificationAfterSnapshotNeedsASecondRealCheckpoint(@TempDir Path directory) throws Exception {
        var session=new ECOStorageRepository.Session(null,directory);
        var data=new Record();data.bind(directory.resolve("record.dat"));session.track(data);
        data.add(5);data.changeAfterWrite=true;
        session.tick(100);
        assertEquals(12,data.amount);assertTrue(data.isDirty());
        assertEquals(1,data.durableRevision());assertEquals(2,data.contentRevision());
        assertEquals(5,NbtIo.readCompressed(data.dataFile().toFile()).getCompound("data").getLong("amount"));
        session.tick(199);assertTrue(data.isDirty());
        session.tick(200);assertFalse(data.isDirty());
        assertEquals(2,data.durableRevision());
        assertEquals(12,NbtIo.readCompressed(data.dataFile().toFile()).getCompound("data").getLong("amount"));
    }

    @Test void failedWriteStaysQueuedAndRecoversWithoutAnotherMutation(@TempDir Path directory) throws Exception {
        Path blocker=directory.resolve("blocked");Files.writeString(blocker,"file, not a directory");
        var session=new ECOStorageRepository.Session(null,directory);
        var data=new Record();data.bind(blocker.resolve("record.dat"));session.track(data);data.add(9);
        session.tick(100);
        assertTrue(data.isDirty());assertNotNull(data.writeFailure());assertEquals(-1,data.durableRevision());
        Files.delete(blocker);
        session.tick(200);
        assertFalse(data.isDirty());assertNull(data.writeFailure());assertEquals(1,data.durableRevision());
        assertEquals(9,NbtIo.readCompressed(data.dataFile().toFile()).getCompound("data").getLong("amount"));
    }

    @Test void explicitFlushDoesNotLoseTheAlreadyQueuedDeadline(@TempDir Path directory) throws Exception {
        var session=new ECOStorageRepository.Session(null,directory);
        var data=new Record();data.bind(directory.resolve("record.dat"));session.track(data);data.add(5);
        assertTrue(data.flushThrough(data.contentRevision()));
        data.add(3);session.tick(100);
        assertEquals(2,data.savedSnapshots());assertEquals(2,data.durableRevision());assertFalse(data.isDirty());
        session.flush();assertEquals(2,data.savedSnapshots());
    }

    @Test void closingFlushRetainsFailedRecordsAndLaterRetries(@TempDir Path directory) throws Exception {
        Path blocker=directory.resolve("blocked");Files.writeString(blocker,"blocked");
        var session=new ECOStorageRepository.Session(null,directory);
        var data=new Record();data.bind(blocker.resolve("record.dat"));session.track(data);data.add(11);
        assertThrows(IllegalStateException.class,session::flush);
        assertTrue(data.isDirty());assertEquals(-1,data.durableRevision());
        Files.delete(blocker);session.flush();
        assertEquals(1,data.durableRevision());assertFalse(data.isDirty());
    }
}
