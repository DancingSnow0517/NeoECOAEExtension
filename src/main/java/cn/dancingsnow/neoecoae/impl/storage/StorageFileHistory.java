package cn.dancingsnow.neoecoae.impl.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** One previous file generation; unchanged large bases are not copied for small delta commits. */
public final class StorageFileHistory {
    private StorageFileHistory() {}

    public static Path previous(Path file) {
        return file.resolveSibling(file.getFileName() + ".previous");
    }

    public static void preserve(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        Path temporary = file.resolveSibling(file.getFileName() + ".history-" + UUID.randomUUID() + ".tmp");
        try {
            // Atomic replacement leaves the old inode immutable; linking avoids copying a large base snapshot.
            try {
                Files.createLink(temporary, file);
            } catch (IOException | UnsupportedOperationException e) {
                Files.copy(file, temporary);
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
            }
            Files.move(temporary, previous(file), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keep original evidence before an administrator explicitly chooses a rollback. */
    public static void archive(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".recovery-" + UUID.randomUUID()));
        }
    }
}
