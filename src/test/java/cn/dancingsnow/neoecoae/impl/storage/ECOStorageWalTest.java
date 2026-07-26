package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The log carries opaque bytes, so its framing and its damage handling can be exercised without a Minecraft registry.
 * That is the whole point of keeping {@link ECOStorageWal} key-agnostic.
 */
class ECOStorageWalTest {
    private static final int MAX_RECORD_BYTES = 1024;
    private static final int BUFFER_BYTES = 64;
    private static final int FRAME_HEADER_BYTES = Integer.BYTES * 2;

    @TempDir
    private Path tempDir;

    @Test
    void replaysEveryFrameInAppendOrder() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta", "gamma");
        writer.close();

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.OK, replay.status());
        assertEquals(List.of("alpha", "beta", "gamma"), replay.payloads());
    }

    @Test
    void replayingAMissingLogIsNotAnError() {
        Replay replay = replay(logPath());
        assertEquals(ECOStorageWal.Status.OK, replay.status());
        assertEquals(List.of(), replay.payloads());
    }

    @Test
    void sizeCountsFramingAndResetsOnTruncate() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        assertEquals(0L, writer.sizeBytes());

        append(writer, "alpha");
        assertEquals(FRAME_HEADER_BYTES + 5L, writer.sizeBytes());

        append(writer, "beta");
        assertEquals(2L * FRAME_HEADER_BYTES + 9L, writer.sizeBytes());

        writer.truncate();
        assertEquals(0L, writer.sizeBytes());
        assertEquals(List.of(), replay(path).payloads());
    }

    @Test
    void reopeningPicksUpTheExistingSize() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha");
        writer.close();

        assertEquals(FRAME_HEADER_BYTES + 5L, wal(path).sizeBytes());
    }

    @Test
    void discardsAPayloadCutShortAtTheEnd() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.close();

        // The process died partway through the second frame's payload.
        truncateFile(path, Files.size(path) - 2L);

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.TAIL_REPAIRED, replay.status());
        assertEquals(List.of("alpha"), replay.payloads());
    }

    @Test
    void discardsAHeaderCutShortAtTheEnd() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.close();

        // The process died inside the second frame's length/crc header.
        truncateFile(path, FRAME_HEADER_BYTES + 5L + 3L);

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.TAIL_REPAIRED, replay.status());
        assertEquals(List.of("alpha"), replay.payloads());
    }

    @Test
    void appendsAfterATornTailStartFromACleanBoundary() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.close();
        truncateFile(path, Files.size(path) - 2L);

        ECOStorageWal reopened = wal(path);
        assertEquals(ECOStorageWal.Status.TAIL_REPAIRED, reopened.replay(payload -> {}));
        append(reopened, "gamma");
        reopened.close();

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.OK, replay.status());
        assertEquals(List.of("alpha", "gamma"), replay.payloads());
    }

    @Test
    void reportsDamageFoundBeforeTheEnd() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.close();

        // Flip a byte inside the first frame's payload; its checksum no longer matches and a good frame follows it.
        byte[] contents = Files.readAllBytes(path);
        contents[FRAME_HEADER_BYTES] ^= 0x7F;
        Files.write(path, contents);

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.CORRUPT, replay.status());
        assertEquals(List.of(), replay.payloads());
    }

    @Test
    void reportsAnImpossibleLengthFoundBeforeTheEnd() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.close();

        byte[] contents = Files.readAllBytes(path);
        contents[0] = 0x7F;
        Files.write(path, contents);

        assertEquals(ECOStorageWal.Status.CORRUPT, replay(path).status());
    }

    @Test
    void rejectsFramesOutsideTheSizeLimit() {
        ECOStorageWal writer = wal(logPath());
        assertThrows(IOException.class, () -> writer.append(new byte[0]));
        assertThrows(IOException.class, () -> writer.append(new byte[MAX_RECORD_BYTES + 1]));
    }

    @Test
    void truncateDropsEverythingAlreadyWritten() throws IOException {
        Path path = logPath();
        ECOStorageWal writer = wal(path);
        append(writer, "alpha", "beta");
        writer.truncate();
        append(writer, "gamma");
        writer.close();

        Replay replay = replay(path);
        assertEquals(ECOStorageWal.Status.OK, replay.status());
        assertEquals(List.of("gamma"), replay.payloads());
    }

    private Path logPath() {
        return tempDir.resolve("nested").resolve("wal_000.log");
    }

    private static ECOStorageWal wal(Path path) {
        return new ECOStorageWal(path, MAX_RECORD_BYTES, BUFFER_BYTES);
    }

    private static void append(ECOStorageWal wal, String... payloads) throws IOException {
        for (String payload : payloads) {
            wal.append(payload.getBytes(StandardCharsets.UTF_8));
        }
        wal.sync();
    }

    private static Replay replay(Path path) {
        List<String> payloads = new ArrayList<>();
        ECOStorageWal.Status status =
                wal(path).replay(payload -> payloads.add(new String(payload, StandardCharsets.UTF_8)));
        return new Replay(status, payloads);
    }

    private static void truncateFile(Path path, long size) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }

    private record Replay(ECOStorageWal.Status status, List<String> payloads) {}
}
