package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32C;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Independent journals: a damaged shard cannot invalidate the other shards. No game objects reach the worker. */
final class InfiniteStorageJournal implements AutoCloseable {
    static final int SHARDS = 16;
    private static final int MAGIC = 0x45434F32;
    private static final int MAX_RECORD_BYTES = 64 * 1024 * 1024;
    private static final long CHECKPOINT_BYTES = 8 * 1024 * 1024;
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
        1, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "ECO storage checkpoint");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    private final Path directory;
    private final Shard[] shards = new Shard[SHARDS];

    InfiniteStorageJournal(Path directory) {
        this(directory, CHECKPOINT_BYTES);
    }

    InfiniteStorageJournal(Path directory, long checkpointBytes) {
        this.directory = directory;
        for (int i = 0; i < SHARDS; i++) shards[i] = new Shard(directory.resolve(Integer.toString(i)), checkpointBytes);
    }

    static int shard(CompoundTag key) { return Math.floorMod(key.hashCode(), SHARDS); }

    boolean exists() { return Files.isRegularFile(directory.resolve("ready")); }

    void initialize(CompoundTag baseline) throws IOException {
        Files.createDirectories(directory);
        ListTag[] entries = new ListTag[SHARDS];
        ListTag[] receipts = new ListTag[SHARDS];
        for (int i = 0; i < SHARDS; i++) { entries[i] = new ListTag(); receipts[i] = new ListTag(); }
        for (Tag raw : baseline.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            entries[shard(entry.getCompound("key"))].add(entry.copy());
        }
        for (Tag receipt : baseline.getList("migrations", Tag.TAG_INT_ARRAY)) {
            receipts[Math.floorMod(receipt.hashCode(), SHARDS)].add(receipt.copy());
        }
        for (int i = 0; i < SHARDS; i++) {
            CompoundTag initial = new CompoundTag();
            initial.put("entries", entries[i]);
            initial.put("migrations", receipts[i]);
            initial.put("rawEntries", i == 0 ? baseline.getList("rawEntries", Tag.TAG_COMPOUND).copy() : new ListTag());
            shards[i].initialize(initial);
        }
        try (FileChannel ready = FileChannel.open(directory.resolve("ready"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ready.write(ByteBuffer.wrap(new byte[]{2}));
            ready.force(true);
        }
    }

    CompoundTag load() throws IOException {
        if (Files.size(directory.resolve("ready")) != 1L || Files.readAllBytes(directory.resolve("ready"))[0] != 2) {
            throw new IOException("Invalid storage journal initialization marker");
        }
        CompoundTag data = new CompoundTag();
        ListTag entries = new ListTag();
        Set<Tag> receipts = new LinkedHashSet<>();
        for (Shard shard : shards) {
            try {
                CompoundTag state = shard.load();
                entries.addAll(state.getList("entries", Tag.TAG_COMPOUND));
                entries.addAll(state.getList("rawEntries", Tag.TAG_COMPOUND));
                receipts.addAll(state.getList("migrations", Tag.TAG_INT_ARRAY));
            } catch (IOException | RuntimeException e) {
                shard.unreadable = true;
                shard.failure = e.toString();
            }
        }
        ListTag migrations = new ListTag();
        migrations.addAll(receipts);
        data.putInt("version", ECOInfiniteStorageData.CURRENT_VERSION);
        data.put("entries", entries);
        data.put("migrations", migrations);
        return data;
    }

    boolean readable(int shard) { return !shards[shard].unreadable; }
    boolean writable(int shard) { return readable(shard) && !shards[shard].writeFailed; }
    boolean degraded() {
        for (Shard shard : shards) if (shard.unreadable || shard.failure != null) return true;
        return false;
    }
    boolean allReadable() {
        for (Shard shard : shards) if (shard.unreadable) return false;
        return true;
    }
    List<String> failures() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < SHARDS; i++) {
            if (shards[i].failure != null) result.add("shard " + i + ": " + shards[i].failure);
        }
        return List.copyOf(result);
    }

    void append(int index, CompoundTag changes) throws IOException { shards[index].append(changes); }

    java.util.concurrent.CompletableFuture<boolean[]> appendAsync(Map<Integer, CompoundTag> batches) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            boolean[] results = new boolean[SHARDS];
            batches.forEach((index, changes) -> {
                try { append(index, changes); results[index] = true; }
                catch (IOException | RuntimeException e) { shards[index].failure = e.toString(); }
            });
            return results;
        }, WORKERS);
    }

    @Override
    public void close() {
        for (Shard shard : shards) {
            Future<?> task = shard.checkpoint;
            if (task != null) {
                try { task.get(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (java.util.concurrent.ExecutionException ignored) { /* Failure remains on the shard. */ }
            }
        }
    }

    private static final class Shard {
        private final Path path;
        private final long checkpointBytes;
        private final Map<CompoundTag, CompoundTag> entries = new HashMap<>();
        private final Set<Tag> receipts = new LinkedHashSet<>();
        private ListTag rawEntries = new ListTag();
        private long sequence;
        private volatile boolean unreadable;
        private volatile boolean writeFailed;
        private volatile String failure;
        private volatile Future<?> checkpoint;
        private boolean rotationPending;

        private Shard(Path path, long checkpointBytes) { this.path = path; this.checkpointBytes = checkpointBytes; }
        private Path file(String name) { return path.resolve(name); }

        private void initialize(CompoundTag initial) throws IOException {
            Files.createDirectories(path);
            entries.clear();
            receipts.clear();
            sequence = 0L;
            rawEntries = initial.getList("rawEntries", Tag.TAG_COMPOUND).copy();
            apply(initial);
            writeSnapshot(file("snapshot.dat"), snapshot());
            // An interrupted bootstrap has never accepted writes and may be initialized again.
            for (String name : List.of("journal.log", "previous.log")) {
                try (FileChannel channel = FileChannel.open(file(name), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { channel.force(true); }
            }
            writeHead(0L);
        }

        private CompoundTag load() throws IOException {
            entries.clear();
            receipts.clear();
            CompoundTag base;
            boolean recoveredBackup = false;
            try { base = readSnapshot(file("snapshot.dat")); }
            catch (IOException | RuntimeException failure) {
                base = readSnapshot(file("backup.dat"));
                recoveredBackup = true;
            }
            sequence = base.getLong("sequence");
            rawEntries = base.getList("rawEntries", Tag.TAG_COMPOUND).copy();
            apply(base);
            replay(file("previous.log"));
            replay(file("journal.log"));
            try (DataInputStream head = new DataInputStream(Files.newInputStream(file("head")))) {
                long acknowledged = head.readLong();
                if (head.readInt() != checksum(acknowledged, new byte[0]) || sequence < acknowledged) {
                    throw new IOException("Missing or damaged committed journal records");
                }
            }
            rotationPending = recoveredBackup || sequence > base.getLong("sequence");
            return snapshot();
        }

        private void replay(Path journal) throws IOException {
            if (!Files.exists(journal)) return;
            try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long validEnd = 0;
                ByteBuffer header = ByteBuffer.allocate(20);
                while (channel.position() < channel.size()) {
                    header.clear();
                    if (!readFully(channel, header)) {
                        channel.truncate(validEnd);
                        channel.force(true);
                        break;
                    }
                    header.flip();
                    int magic = header.getInt();
                    int length = header.getInt();
                    long nextSequence = header.getLong();
                    int checksum = header.getInt();
                    if (magic != MAGIC || length < 0 || length > MAX_RECORD_BYTES) throw new IOException("Invalid journal header");
                    ByteBuffer body = ByteBuffer.allocate(length);
                    if (!readFully(channel, body)) {
                        channel.truncate(validEnd);
                        channel.force(true);
                        break;
                    }
                    byte[] bytes = body.array();
                    if (checksum(nextSequence, bytes) != checksum) throw new IOException("Journal checksum mismatch at " + validEnd);
                    if (nextSequence > sequence) {
                        if (nextSequence != sequence + 1L) throw new IOException("Journal sequence gap after " + sequence);
                        apply(decode(bytes));
                        sequence = nextSequence;
                    }
                    validEnd = channel.position();
                }
            }
        }

        private synchronized void append(CompoundTag changes) throws IOException {
            if (unreadable) throw new IOException("Shard is quarantined");
            byte[] payload = encode(changes);
            if (payload.length > MAX_RECORD_BYTES) throw new IOException("Journal batch exceeds 64 MiB");
            long next = sequence + 1L;
            ByteBuffer record = ByteBuffer.allocate(20 + payload.length);
            record.putInt(MAGIC).putInt(payload.length).putLong(next).putInt(checksum(next, payload)).put(payload).flip();
            try (FileChannel channel = FileChannel.open(file("journal.log"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                long start = channel.size();
                channel.position(start);
                try {
                    while (record.hasRemaining()) channel.write(record);
                    channel.force(true);
                } catch (IOException e) {
                    try { channel.truncate(start); channel.force(true); }
                    catch (IOException rollback) { unreadable = true; e.addSuppressed(rollback); }
                    throw e;
                }
            } catch (IOException e) {
                writeFailed = true;
                failure = e.toString();
                throw e;
            }
            apply(changes);
            sequence = next;
            try { writeHead(next); }
            catch (IOException e) {
                unreadable = true;
                failure = "Commit acknowledgement failed: " + e;
                throw e;
            }
            writeFailed = false;
            if (checkpoint == null) failure = null;
            requestCheckpoint();
        }

        private void writeHead(long value) throws IOException {
            Path temp = file("head.temp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.allocate(12).putLong(value).putInt(checksum(value, new byte[0]));
                bytes.flip();
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            move(temp, file("head"));
        }

        private void apply(CompoundTag changes) {
            for (Tag raw : changes.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                CompoundTag key = entry.getCompound("key");
                if (key.isEmpty()) { rawEntries.add(entry.copy()); continue; }
                CompoundTag amount = entry.getCompound("amount");
                if (amount.contains("long", Tag.TAG_LONG) && amount.getLong("long") == 0L
                    && !amount.contains("big") && !entry.contains("restore")) entries.remove(key);
                else entries.put(key, entry);
            }
            receipts.addAll(changes.getList("migrations", Tag.TAG_INT_ARRAY));
        }

        private CompoundTag snapshot() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("format", 2);
            tag.putLong("sequence", sequence);
            ListTag values = new ListTag();
            values.addAll(entries.values());
            tag.put("entries", values);
            ListTag migrations = new ListTag();
            migrations.addAll(receipts);
            tag.put("migrations", migrations);
            tag.put("rawEntries", rawEntries.copy());
            return tag;
        }

        private void requestCheckpoint() {
            if (checkpoint != null && !checkpoint.isDone()) return;
            try {
                if (!rotationPending && Files.size(file("journal.log")) < checkpointBytes) return;
                checkpoint = WORKERS.submit(() -> {
                    try {
                        CompoundTag frozen;
                        synchronized (this) {
                            if (!rotationPending) {
                                Files.copy(file("snapshot.dat"), file("backup.dat"), StandardCopyOption.REPLACE_EXISTING);
                                force(file("backup.dat"));
                                move(file("journal.log"), file("previous.log"));
                                rotationPending = true;
                            }
                            frozen = snapshot();
                        }
                        writeSnapshot(file("snapshot.dat"), frozen);
                        synchronized (this) {
                            rotationPending = false;
                            if (!writeFailed) failure = null;
                        }
                    } catch (IOException | RuntimeException e) {
                        failure = "checkpoint: " + e;
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // A full worker queue leaves the durable journal intact; the next commit retries.
            } catch (IOException e) {
                failure = "checkpoint: " + e;
            }
        }
    }

    private static boolean readFully(FileChannel channel, ByteBuffer target) throws IOException {
        while (target.hasRemaining()) if (channel.read(target) < 0) return false;
        return true;
    }
    private static int checksum(long sequence, byte[] bytes) {
        CRC32C crc = new CRC32C();
        crc.update(ByteBuffer.allocate(8).putLong(sequence).array());
        crc.update(bytes);
        return (int) crc.getValue();
    }
    private static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { NbtIo.write(tag, output); }
        return bytes.toByteArray();
    }
    private static CompoundTag decode(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.read(input, NbtAccounter.create(MAX_RECORD_BYTES));
        }
    }
    private static CompoundTag readSnapshot(Path file) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (tag.getInt("format") != 2) throw new IOException("Unsupported shard snapshot format");
        if (!tag.contains("sequence", Tag.TAG_LONG) || tag.getLong("sequence") < 0L) throw new IOException("Invalid checkpoint sequence");
        validateList(tag, "entries", Tag.TAG_COMPOUND);
        validateList(tag, "rawEntries", Tag.TAG_COMPOUND);
        validateList(tag, "migrations", Tag.TAG_INT_ARRAY);
        return tag;
    }
    private static void validateList(CompoundTag tag, String name, int elementType) throws IOException {
        if (!(tag.get(name) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IOException("Invalid checkpoint field: " + name);
        }
    }
    private static void writeSnapshot(Path file, CompoundTag tag) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".temp");
        NbtIo.writeCompressed(tag, temp);
        force(temp);
        move(temp, file);
    }
    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private static void move(Path from, Path to) throws IOException {
        // A non-atomic replacement is not a committed checkpoint.
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
