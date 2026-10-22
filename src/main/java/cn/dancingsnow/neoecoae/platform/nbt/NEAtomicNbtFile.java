package cn.dancingsnow.neoecoae.platform.nbt;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;
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
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE);
                 GZIPOutputStream gzip = new GZIPOutputStream(Channels.newOutputStream(channel), 32768);
                 DataOutputStream output = new DataOutputStream(gzip)) {
                NbtIo.write(root, output);
                gzip.finish();
                output.flush();
                channel.force(true);
            }
            if (!root.equals(NbtIo.readCompressed(pending.toFile()))) {
                throw new IOException("ECO snapshot read-back verification failed: " + target);
            }
            if (Files.isRegularFile(absolute)) {
                Path backup = absolute.resolveSibling(absolute.getFileName() + ".bak");
                Path temporaryBackup = Files.createTempFile(parent, absolute.getFileName() + ".", ".backup");
                try {
                    Files.copy(absolute, temporaryBackup, StandardCopyOption.REPLACE_EXISTING);
                    try (FileChannel channel = FileChannel.open(temporaryBackup, StandardOpenOption.WRITE)) { channel.force(true); }
                    Files.move(temporaryBackup, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporaryBackup); }
            }
            // If atomic replacement is unsupported, retain the old file and retry on a later save.
            Files.move(pending, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
            catch (IOException | UnsupportedOperationException ignored) { }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
