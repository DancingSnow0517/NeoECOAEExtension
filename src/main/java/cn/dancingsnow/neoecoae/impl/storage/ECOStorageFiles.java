package cn.dancingsnow.neoecoae.impl.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Durable file primitives shared by every ECO storage subsystem. */
public final class ECOStorageFiles {
    private ECOStorageFiles() {}

    /**
     * Writes {@code payload} to {@code target} so that a crash leaves either the previous file or the new one, never a
     * torn mix of the two. The temporary file is fsynced before the rename, otherwise the rename can reach the disk
     * ahead of the data it is supposed to publish.
     */
    public static void writeAtomically(Path target, byte[] payload) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        ByteBuffer data = ByteBuffer.wrap(payload);
        try (FileChannel channel = FileChannel.open(
                tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (data.hasRemaining()) {
                channel.write(data);
            }
            channel.force(true);
        }
        replaceAtomically(tmp, target);
    }

    public static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
