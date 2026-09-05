package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfiniteStorageJournalTest {
    @TempDir Path directory;

    private static CompoundTag key(String name) {
        CompoundTag key = new CompoundTag();
        key.putString("id", name);
        return key;
    }

    private static CompoundTag changes(CompoundTag key, long amount, UUID receipt) {
        CompoundTag entry = new CompoundTag();
        entry.put("key", key);
        entry.put("amount", HugeAmount.of(amount).write());
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag changes = new CompoundTag();
        changes.put("entries", entries);
        ListTag migrations = new ListTag();
        if (receipt != null) migrations.add(NbtUtils.createUUID(receipt));
        changes.put("migrations", migrations);
        return changes;
    }

    private InfiniteStorageJournal create() throws Exception {
        InfiniteStorageJournal journal = new InfiniteStorageJournal(directory);
        journal.initialize(new CompoundTag());
        return journal;
    }

    private long amount(CompoundTag loaded, CompoundTag key) {
        for (Tag raw : loaded.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.getCompound("key").equals(key)) return HugeAmount.read(entry.getCompound("amount")).toLongSaturated();
        }
        return 0L;
    }

    @Test
    void replayKeepsLatestAmountAndItsMigrationReceiptTogether() throws Exception {
        CompoundTag key = key("iron");
        UUID receipt = UUID.randomUUID();
        try (var journal = create()) {
            journal.append(InfiniteStorageJournal.shard(key), changes(key, 200L, receipt));
            journal.append(InfiniteStorageJournal.shard(key), changes(key, 175L, null));
        }
        try (var recovered = new InfiniteStorageJournal(directory)) {
            CompoundTag loaded = recovered.load();
            assertEquals(175L, amount(loaded, key));
            assertTrue(loaded.getList("migrations", Tag.TAG_INT_ARRAY).contains(NbtUtils.createUUID(receipt)));
            assertTrue(recovered.allReadable());
        }
    }

    @Test
    void unacknowledgedPartialTailIsTruncatedBeforeNextAppend() throws Exception {
        CompoundTag key = key("gold");
        int shard = InfiniteStorageJournal.shard(key);
        try (var journal = create()) { journal.append(shard, changes(key, 10L, null)); }
        Path log = directory.resolve(shard + "/journal.log");
        long validLength = Files.size(log);
        Files.write(log, new byte[]{1, 2, 3}, StandardOpenOption.APPEND);
        try (var journal = new InfiniteStorageJournal(directory)) {
            assertEquals(10L, amount(journal.load(), key));
            assertEquals(validLength, Files.size(log));
            journal.append(shard, changes(key, 9L, null));
        }
        try (var journal = new InfiniteStorageJournal(directory)) {
            assertEquals(9L, amount(journal.load(), key));
        }
    }

    @Test
    void missingAcknowledgedTailQuarantinesOnlyItsShard() throws Exception {
        CompoundTag bad = key("damaged");
        CompoundTag good = key("healthy");
        while (InfiniteStorageJournal.shard(bad) == InfiniteStorageJournal.shard(good)) good.putString("id", good.getString("id") + "x");
        int shard = InfiniteStorageJournal.shard(bad);
        try (var journal = create()) {
            journal.append(shard, changes(bad, 100L, null));
            journal.append(InfiniteStorageJournal.shard(good), changes(good, 22L, null));
        }
        try (var channel = FileChannel.open(directory.resolve(shard + "/journal.log"), StandardOpenOption.WRITE)) {
            channel.truncate(channel.size() - 5L);
        }
        try (var journal = new InfiniteStorageJournal(directory)) {
            CompoundTag loaded = journal.load();
            assertFalse(journal.readable(shard));
            assertFalse(journal.writable(shard));
            assertEquals(22L, amount(loaded, good));
            assertFalse(journal.failures().isEmpty());
        }
    }

    @Test
    void corruptPayloadNeverBecomesAnEmptyWritableShard() throws Exception {
        CompoundTag key = key("broken");
        int shard = InfiniteStorageJournal.shard(key);
        try (var journal = create()) { journal.append(shard, changes(key, 5L, null)); }
        try (var channel = FileChannel.open(directory.resolve(shard + "/journal.log"), StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{127}), 25L);
        }
        try (var journal = new InfiniteStorageJournal(directory)) {
            journal.load();
            assertFalse(journal.readable(shard));
            assertThrows(java.io.IOException.class, () -> journal.append(shard, changes(key, 7L, null)));
        }
    }

    @Test
    void asyncBatchAndDeletionSurviveReopen() throws Exception {
        CompoundTag key = key("removed");
        int shard = InfiniteStorageJournal.shard(key);
        try (var journal = create()) {
            assertTrue(journal.appendAsync(Map.of(shard, changes(key, Long.MAX_VALUE, null))).get()[shard]);
            assertTrue(journal.appendAsync(Map.of(shard, changes(key, 0L, null))).get()[shard]);
        }
        try (var journal = new InfiniteStorageJournal(directory)) {
            assertEquals(0L, amount(journal.load(), key));
            assertTrue(journal.allReadable());
        }
    }

    @Test
    void previousGenerationPlusJournalRecoversACorruptLatestCheckpoint() throws Exception {
        CompoundTag key = key("checkpoint");
        int shard = InfiniteStorageJournal.shard(key);
        try (var journal = new InfiniteStorageJournal(directory, 1L)) {
            journal.initialize(new CompoundTag());
            for (int i = 1; i <= 8; i++) {
                journal.append(shard, changes(key, i, null));
                journal.close();
            }
        }
        Files.write(directory.resolve(shard + "/snapshot.dat"), new byte[]{1, 2, 3});
        try (var journal = new InfiniteStorageJournal(directory)) {
            assertEquals(8L, amount(journal.load(), key));
            assertTrue(journal.readable(shard));
        }
    }

    @Test
    void checkpointFailureDoesNotDisableDurableJournalWrites() throws Exception {
        CompoundTag key = key("checkpoint_failure");
        int shard = InfiniteStorageJournal.shard(key);
        try (var journal = new InfiniteStorageJournal(directory, 1L)) {
            journal.initialize(new CompoundTag());
            Path obstacle = directory.resolve(shard + "/backup.dat");
            Files.createDirectories(obstacle);
            Files.writeString(obstacle.resolve("obstacle"), "keep");
            journal.append(shard, changes(key, 1L, null));
            journal.close();
            assertTrue(journal.writable(shard));
            assertTrue(journal.degraded());
            journal.append(shard, changes(key, 2L, null));
            journal.close();
        }
        try (var recovered = new InfiniteStorageJournal(directory)) {
            assertEquals(2L, amount(recovered.load(), key));
            assertTrue(recovered.readable(shard));
        }
    }
}
