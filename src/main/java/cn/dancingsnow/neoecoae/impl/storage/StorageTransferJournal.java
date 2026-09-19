package cn.dancingsnow.neoecoae.impl.storage;

import cn.dancingsnow.neoecoae.impl.storage.infinite.InfiniteStorageSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** A redo record for rare cross-file transfers, not a journal on the normal inventory hot path. */
public final class StorageTransferJournal {
    private static final String DIRECTORY = "neoecoae_transfers";

    public record Snapshot(Path file, CompoundTag data, boolean infinite) {}

    private StorageTransferJournal() {}

    public static void commit(Path world, UUID transaction, List<Snapshot> targets, Snapshot source, int version)
            throws IOException {
        commit(world, transaction, targets, source, version, () -> {});
    }

    static void commit(
            Path world, UUID transaction, List<Snapshot> targets, Snapshot source, int version, Runnable afterWrite)
            throws IOException {
        Path root = world.toAbsolutePath().normalize().resolve("data");
        ListTag writes = new ListTag();
        for (Snapshot target : targets) writes.add(encode(root, target));
        writes.add(encode(root, source)); // The source is retired only after every destination is committed.
        CompoundTag journal = new CompoundTag();
        journal.putInt("format", 1);
        journal.putInt("data_version", version);
        journal.put("writes", writes);
        Path path = root.resolve(DIRECTORY).resolve(transaction + ".dat");
        validate(root, journal);
        AtomicSavedDataFile.write(path, journal, version);
        replay(root, path, journal, afterWrite);
    }

    /** Runs before Minecraft opens levels or any inventory caches; replay needs no mod registry access. */
    public static void recoverAll(Path world) throws IOException {
        Path root = world.toAbsolutePath().normalize().resolve("data");
        Path directory = root.resolve(DIRECTORY);
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".dat"))
                    .sorted()
                    .toList()) {
                replay(root, path, AtomicSavedDataFile.read(path), () -> {});
            }
        }
    }

    private static CompoundTag encode(Path root, Snapshot snapshot) throws IOException {
        Path file = snapshot.file().toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IOException("Transfer target outside world data");
        CompoundTag encoded = new CompoundTag();
        encoded.putString("file", root.relativize(file).toString().replace('\\', '/'));
        encoded.putBoolean("infinite", snapshot.infinite());
        encoded.put("data", snapshot.data().copy());
        return encoded;
    }

    private static List<Snapshot> validate(Path root, CompoundTag journal) throws IOException {
        if (journal.getInt("format") != 1
                || !journal.contains("data_version", Tag.TAG_INT)
                || !(journal.get("writes") instanceof ListTag writes)
                || writes.size() < 2
                || writes.getElementType() != Tag.TAG_COMPOUND) throw new IOException("Invalid transfer journal");
        List<Snapshot> result = new ArrayList<>();
        var unique = new HashSet<Path>();
        for (int i = 0; i < writes.size(); i++) {
            CompoundTag write = writes.getCompound(i);
            Path file = root.resolve(write.getString("file")).normalize();
            if (!file.startsWith(root)
                    || file.equals(root)
                    || !unique.add(file)
                    || !write.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Invalid transfer target");
            Path relative = root.relativize(file);
            if (relative.getNameCount() != 2
                    || !List.of("neoecoae_cells", "neoecoae_infinite", "ae_universal_cell_data")
                            .contains(relative.getName(0).toString())
                    || !file.toString().endsWith(".dat")) {
                throw new IOException("Unsupported transfer target: " + file);
            }
            result.add(new Snapshot(file, write.getCompound("data"), write.getBoolean("infinite")));
        }
        return result;
    }

    private static void replay(Path root, Path journalFile, CompoundTag journal, Runnable afterWrite)
            throws IOException {
        List<Snapshot> writes = validate(root, journal);
        for (Snapshot write : writes) {
            if (write.infinite()) {
                InfiniteStorageSnapshot.write(write.file(), write.data(), journal.getInt("data_version"));
                Files.deleteIfExists(write.file().resolveSibling(write.file().getFileName() + ".delta.dat"));
                InfiniteStorageSnapshot.markCommitted(write.file(), write.data(), journal.getInt("data_version"));
            } else {
                AtomicSavedDataFile.write(write.file(), write.data(), journal.getInt("data_version"));
            }
            afterWrite.run();
        }
        Files.delete(journalFile);
        Files.deleteIfExists(StorageFileHistory.previous(journalFile));
    }
}
