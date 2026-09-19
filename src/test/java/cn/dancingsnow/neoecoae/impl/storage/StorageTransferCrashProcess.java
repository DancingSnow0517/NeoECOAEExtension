package cn.dancingsnow.neoecoae.impl.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;

/** Child JVM deliberately exits without shutdown hooks at a durable write boundary. */
public final class StorageTransferCrashProcess {
    public static void main(String[] args) throws Exception {
        Path world = Path.of(args[0]);
        int boundary = Integer.parseInt(args[1]);
        AtomicInteger writes = new AtomicInteger();
        StorageTransferJournal.commit(
                world,
                UUID.randomUUID(),
                List.of(
                        new StorageTransferJournal.Snapshot(
                                world.resolve("data/neoecoae_cells/first.dat"), amount(100), false),
                        new StorageTransferJournal.Snapshot(
                                world.resolve("data/neoecoae_cells/second.dat"), amount(200), false)),
                new StorageTransferJournal.Snapshot(world.resolve("data/neoecoae_cells/source.dat"), amount(0), false),
                3465,
                () -> {
                    if (writes.incrementAndGet() == boundary)
                        Runtime.getRuntime().halt(73);
                });
        throw new AssertionError("Crash boundary not reached");
    }

    private static CompoundTag amount(long amount) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("amount", amount);
        return tag;
    }
}
