package cn.dancingsnow.neoecoae.impl.storage;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Synchronous SavedData commit: validate a new file before replacing the committed inventory. */
public final class AtomicSavedDataFile {
    private AtomicSavedDataFile() {}

    public static CompoundTag read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(input);
            if (!root.contains("data", Tag.TAG_COMPOUND)) {
                throw new IOException("Missing SavedData compound: " + file);
            }
            return root.getCompound("data");
        }
    }

    public static void write(Path file, CompoundTag data, int dataVersion) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", dataVersion);
            root.put("data", data);
            try (var output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                NbtIo.writeCompressed(root, output);
            }
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!data.equals(read(temporary))) throw new IOException("SavedData read-back mismatch: " + file);
            StorageFileHistory.preserve(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
