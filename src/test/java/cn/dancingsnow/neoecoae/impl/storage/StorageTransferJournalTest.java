package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTransferJournalTest {
    @TempDir
    Path directory;

    @Test
    void interruptionAtEveryWriteRecoversBeforeInventoriesAreMounted() throws Exception {
        for (int cut = 1; cut <= 3; cut++) {
            Path world = directory.resolve("cut" + cut);
            Path first = world.resolve("data/neoecoae_cells/first.dat");
            Path second = world.resolve("data/neoecoae_cells/second.dat");
            Path source = world.resolve("data/neoecoae_cells/source.dat");
            AtomicSavedDataFile.write(first, amount(0), 3465);
            AtomicSavedDataFile.write(second, amount(0), 3465);
            AtomicSavedDataFile.write(source, amount(300), 3465);
            AtomicInteger count = new AtomicInteger();
            int boundary = cut;
            assertThrows(
                    SimulatedCrash.class,
                    () -> StorageTransferJournal.commit(
                            world,
                            UUID.randomUUID(),
                            List.of(
                                    new StorageTransferJournal.Snapshot(first, amount(100), false),
                                    new StorageTransferJournal.Snapshot(second, amount(200), false)),
                            new StorageTransferJournal.Snapshot(source, amount(0), false),
                            3465,
                            () -> {
                                if (count.incrementAndGet() == boundary) throw new SimulatedCrash();
                            }));
            StorageTransferJournal.recoverAll(world);
            StorageTransferJournal.recoverAll(world); // Idempotent across repeated restart attempts.
            assertEquals(100, AtomicSavedDataFile.read(first).getLong("amount"));
            assertEquals(200, AtomicSavedDataFile.read(second).getLong("amount"));
            assertEquals(0, AtomicSavedDataFile.read(source).getLong("amount"));
            try (var paths = Files.list(world.resolve("data/neoecoae_transfers"))) {
                assertEquals(0, paths.count());
            }
        }
    }

    @Test
    void failedJournalPublicationDoesNotTouchAnyCommittedInventory() throws Exception {
        Path first = directory.resolve("data/neoecoae_cells/first.dat");
        Path source = directory.resolve("data/neoecoae_cells/source.dat");
        AtomicSavedDataFile.write(first, amount(0), 3465);
        AtomicSavedDataFile.write(source, amount(100), 3465);
        CompoundTag invalid = amount(100);
        invalid.putString("invalid", "x".repeat(70000));
        assertThrows(
                java.io.IOException.class,
                () -> StorageTransferJournal.commit(
                        directory,
                        UUID.randomUUID(),
                        List.of(new StorageTransferJournal.Snapshot(first, invalid, false)),
                        new StorageTransferJournal.Snapshot(source, amount(0), false),
                        3465));
        assertEquals(0, AtomicSavedDataFile.read(first).getLong("amount"));
        assertEquals(100, AtomicSavedDataFile.read(source).getLong("amount"));
    }

    @Test
    void failedRestartReplayBlocksOnlyAffectedInventories() throws Exception {
        Path first = directory.resolve("data/neoecoae_cells/first.dat");
        Path second = directory.resolve("data/neoecoae_cells/second.dat");
        Path source = directory.resolve("data/neoecoae_cells/source.dat");
        AtomicSavedDataFile.write(source, amount(300), 3465);
        Files.createDirectories(second);
        Files.writeString(second.resolve("blocked"), "cannot replace a directory");
        assertThrows(
                java.io.IOException.class,
                () -> StorageTransferJournal.commit(
                        directory,
                        UUID.randomUUID(),
                        List.of(
                                new StorageTransferJournal.Snapshot(first, amount(100), false),
                                new StorageTransferJournal.Snapshot(second, amount(200), false)),
                        new StorageTransferJournal.Snapshot(source, amount(0), false),
                        3465));
        StorageTransferJournal.recoverAll(directory);
        assertTrue(StorageTransferJournal.isBlocked(first));
        assertTrue(StorageTransferJournal.isBlocked(source));
        assertFalse(StorageTransferJournal.isBlocked(directory.resolve("data/neoecoae_cells/unrelated.dat")));
        assertEquals(300, AtomicSavedDataFile.read(source).getLong("amount"));
    }

    private static CompoundTag amount(long amount) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("amount", amount);
        return tag;
    }

    @Test
    void abruptJvmTerminationRecoversEveryCommitBoundary() throws Exception {
        var classpath = new java.util.LinkedHashSet<String>();
        classpath.addAll(List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) {
                for (var url : urls.getURLs())
                    if (url.getProtocol().equals("file"))
                        classpath.add(Path.of(url.toURI()).toString());
            }
        }
        for (int cut = 1; cut <= 3; cut++) {
            Path world = directory.resolve("process" + cut);
            Path first = world.resolve("data/neoecoae_cells/first.dat");
            Path second = world.resolve("data/neoecoae_cells/second.dat");
            Path source = world.resolve("data/neoecoae_cells/source.dat");
            AtomicSavedDataFile.write(first, amount(0), 3465);
            AtomicSavedDataFile.write(second, amount(0), 3465);
            AtomicSavedDataFile.write(source, amount(300), 3465);
            Path output = world.resolve("child.log");
            Process process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            String.join(java.io.File.pathSeparator, classpath),
                            StorageTransferCrashProcess.class.getName(),
                            world.toString(),
                            Integer.toString(cut))
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            try {
                assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Child JVM timed out");
                assertEquals(73, process.exitValue(), () -> {
                    try {
                        return Files.readString(output);
                    } catch (Exception e) {
                        return e.toString();
                    }
                });
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
            StorageTransferJournal.recoverAll(world);
            assertEquals(100, AtomicSavedDataFile.read(first).getLong("amount"));
            assertEquals(200, AtomicSavedDataFile.read(second).getLong("amount"));
            assertEquals(0, AtomicSavedDataFile.read(source).getLong("amount"));
        }
    }

    private static final class SimulatedCrash extends RuntimeException {}
}
