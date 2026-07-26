package cn.dancingsnow.neoecoae.impl.storage;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only write-ahead log shared by the ECO storage subsystems.
 *
 * <p>Each frame is {@code [int length][int crc32][payload]}. Payloads are opaque bytes, so this class carries no
 * {@code AEKey} or Minecraft registry dependency and the durability core can be unit tested on its own.
 *
 * <p>A frame that is cut short or fails its checksum <em>at the very end of the file</em> is a torn tail: the process
 * died mid-append, the mutation was never acknowledged, and the tail is truncated away. The same damage anywhere
 * before the end means the log itself is unreliable and is reported as {@link Status#CORRUPT}.
 */
public final class ECOStorageWal {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageWal.class);
    private static final int FRAME_HEADER_BYTES = Integer.BYTES * 2;

    public enum Status {
        /** Every frame in the log was intact. */
        OK,
        /** A partially written frame at the end was discarded; everything before it replayed. */
        TAIL_REPAIRED,
        /** Damage was found before the end of the log. The replayed state must not be treated as complete. */
        CORRUPT
    }

    private final Path path;
    private final int maxRecordBytes;
    private final int bufferBytes;

    @Nullable private DataOutputStream out;

    @Nullable private FileOutputStream fileOut;

    private volatile long sizeBytes;

    public ECOStorageWal(Path path, int maxRecordBytes, int bufferBytes) {
        this.path = path;
        this.maxRecordBytes = maxRecordBytes;
        this.bufferBytes = bufferBytes;
        this.sizeBytes = currentFileSize();
    }

    public Path path() {
        return path;
    }

    public int maxRecordBytes() {
        return maxRecordBytes;
    }

    /** Bytes handed to {@link #append}, including frames still sitting in the buffer. Readable from any thread. */
    public long sizeBytes() {
        return sizeBytes;
    }

    public void append(byte[] payload) throws IOException {
        if (payload.length <= 0 || payload.length > maxRecordBytes) {
            throw new IOException("ECO storage WAL frame of " + payload.length + " bytes is out of range");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        DataOutputStream stream = output();
        stream.writeInt(payload.length);
        stream.writeInt((int) crc.getValue());
        stream.write(payload);
        sizeBytes += FRAME_HEADER_BYTES + (long) payload.length;
    }

    /** Flushes the buffer and forces it to the platter. Every appended frame is durable once this returns. */
    public void sync() throws IOException {
        if (out == null) {
            return;
        }
        out.flush();
        if (fileOut != null) {
            fileOut.getChannel().force(false);
        }
    }

    /**
     * Feeds every intact frame to {@code consumer} in append order. A torn tail is truncated away as a side effect, so
     * a subsequent {@link #append} starts from a clean boundary.
     */
    public Status replay(Consumer<byte[]> consumer) {
        if (!Files.isRegularFile(path)) {
            return Status.OK;
        }
        long repairOffset = -1L;
        boolean corrupt = false;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            long fileSize = Files.size(path);
            long offset = 0L;
            while (offset < fileSize) {
                long frameStart = offset;
                if (fileSize - offset < FRAME_HEADER_BYTES) {
                    repairOffset = frameStart;
                    break;
                }
                int length = in.readInt();
                int expectedCrc = in.readInt();
                offset += FRAME_HEADER_BYTES;
                if (length <= 0 || length > maxRecordBytes) {
                    if (offset == fileSize) {
                        repairOffset = frameStart;
                    } else {
                        corrupt = true;
                        LOGGER.error("Invalid ECO storage WAL frame length {} in {}", length, path);
                    }
                    break;
                }
                if (fileSize - offset < length) {
                    repairOffset = frameStart;
                    break;
                }
                byte[] payload = new byte[length];
                in.readFully(payload);
                offset += length;
                CRC32 crc = new CRC32();
                crc.update(payload);
                if ((int) crc.getValue() != expectedCrc) {
                    if (offset == fileSize) {
                        repairOffset = frameStart;
                    } else {
                        corrupt = true;
                        LOGGER.error("CRC mismatch in ECO storage WAL {}", path);
                    }
                    break;
                }
                consumer.accept(payload);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Unable to replay ECO storage WAL {}", path, e);
            return Status.CORRUPT;
        }
        if (corrupt) {
            return Status.CORRUPT;
        }
        if (repairOffset >= 0L) {
            return repairTail(repairOffset) ? Status.TAIL_REPAIRED : Status.CORRUPT;
        }
        return Status.OK;
    }

    public void truncate() throws IOException {
        close();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        sizeBytes = 0L;
    }

    public void close() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            LOGGER.warn("Unable to close ECO storage WAL {}", path, e);
        } finally {
            out = null;
            fileOut = null;
        }
    }

    private DataOutputStream output() throws IOException {
        if (out == null) {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            fileOut = new FileOutputStream(path.toFile(), true);
            out = new DataOutputStream(new BufferedOutputStream(fileOut, bufferBytes));
        }
        return out;
    }

    private boolean repairTail(long validLength) {
        close();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(validLength);
            channel.force(true);
            sizeBytes = validLength;
            LOGGER.warn("Discarded incomplete ECO storage WAL tail in {} at byte {}", path, validLength);
            return true;
        } catch (IOException e) {
            LOGGER.error("Unable to repair ECO storage WAL tail {}", path, e);
            return false;
        }
    }

    private long currentFileSize() {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}
