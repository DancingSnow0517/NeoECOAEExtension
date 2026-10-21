package cn.dancingsnow.neoecoae.platform.nbt;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Writes a complete compressed snapshot before replacing the authoritative file. */
public final class NEAtomicNbtFile {
    private NEAtomicNbtFile() {}

    public static void write(CompoundTag root, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        Files.createDirectories(parent);
        Path pending = Files.createTempFile(parent, absolute.getFileName() + ".", ".pending");
        try {
            NbtIo.writeCompressed(root, pending.toFile());
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // If atomic replacement is unsupported, retain the old file and retry on a later save.
            Files.move(pending, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
