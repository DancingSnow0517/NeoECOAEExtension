package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import com.google.common.math.LongMath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict read-only importer for the retired file-backed ordinary cell formats. */
final class LegacyECOCellReader {
    private static final int CELL_VERSION = 3;
    private static final String CELL_FILE = "cell.dat";
    private static final ResourceLocation AE2_MISSING_CONTENT =
            ResourceLocation.fromNamespaceAndPath("ae2", "missing_content");

    private LegacyECOCellReader() {}

    static boolean isPresent(Path source) {
        return Files.isRegularFile(source.resolve(CELL_FILE), LinkOption.NOFOLLOW_LINKS)
                || ECOLegacyCellShardReader.isPresent(source);
    }

    static Snapshot copyAndRead(UUID storageId, Path source, Path migrationRoot) throws IOException {
        requireDirectory(source, "Legacy ECO storage path");
        String fingerprint = fingerprint(source);
        Path working = migrationRoot
                .resolve(storageId + "-" + UUID.randomUUID())
                .toAbsolutePath()
                .normalize();
        try {
            copyDirectory(source, working);
            verifySource(source, fingerprint);
            Snapshot parsed = read(storageId, working, fingerprint);
            verifySource(source, fingerprint);
            return parsed;
        } finally {
            discardWorkingCopy(migrationRoot, working);
        }
    }

    static void verifySource(Path source, String expectedFingerprint) throws IOException {
        if (!expectedFingerprint.equals(fingerprint(source))) {
            throw new IOException("Legacy ECO storage source changed while migration was in progress");
        }
    }

    static void archive(Path source, Path archive, String expectedFingerprint) throws IOException {
        verifySource(source, expectedFingerprint);
        requireDirectory(source, "Legacy ECO storage path");
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Legacy ECO storage archive already exists: " + archive);
        }
        Files.createDirectories(archive.getParent());
        try {
            Files.move(source, archive, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, archive);
        }
    }

    static boolean isSha256(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private static Snapshot read(UUID storageId, Path source, String sourceFingerprint) throws IOException {
        Path cell = source.resolve(CELL_FILE);
        if (Files.isRegularFile(cell, LinkOption.NOFOLLOW_LINKS)) {
            return readCellFile(storageId, cell, sourceFingerprint);
        }
        if (!ECOLegacyCellShardReader.isPresent(source)) {
            throw new IOException("Legacy ECO storage source contains no known cell data");
        }
        ECOLegacyCellShardReader reader = new ECOLegacyCellShardReader(source);
        reader.readBudgeted(0L);
        if (reader.isFailed()) {
            throw new IOException("Unable to read every legacy ECO storage shard");
        }
        return new Snapshot(Map.copyOf(reader.finish()), Math.max(0L, reader.revision()), sourceFingerprint);
    }

    private static Snapshot readCellFile(UUID storageId, Path cell, String sourceFingerprint) throws IOException {
        CompoundTag tag;
        try (InputStream input = Files.newInputStream(cell)) {
            tag = NbtIo.readCompressed(input);
        }
        if (!tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != CELL_VERSION
                || !tag.contains("kind", Tag.TAG_STRING)
                || !"cell".equals(tag.getString("kind"))
                || !tag.hasUUID("id")
                || !storageId.equals(tag.getUUID("id"))
                || !tag.contains("revision", Tag.TAG_LONG)
                || tag.getLong("revision") < 0L
                || !tag.contains("storedTypes", Tag.TAG_INT)
                || tag.getInt("storedTypes") < 0
                || !tag.contains("storedAmount", Tag.TAG_STRING)) {
            throw new IOException("Unsupported or malformed legacy ECO storage cell file: " + cell);
        }
        Tag rawEntries = tag.get("entries");
        if (!(rawEntries instanceof ListTag entries)
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException("Malformed legacy ECO storage cell entries: " + cell);
        }
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        long total = 0L;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains("key", Tag.TAG_COMPOUND) || !entry.contains("amount", Tag.TAG_LONG)) {
                throw new IOException("Malformed legacy ECO storage cell entry: " + cell);
            }
            AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
            long amount = entry.getLong("amount");
            if (!isResolved(key) || amount <= 0L || amounts.putIfAbsent(key, amount) != null) {
                throw new IOException("Invalid or duplicate legacy ECO storage cell entry: " + cell);
            }
            total = LongMath.saturatedAdd(total, amount);
        }
        long declaredAmount;
        try {
            declaredAmount = Long.parseLong(tag.getString("storedAmount"));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid legacy ECO storage stored amount: " + cell, e);
        }
        if (declaredAmount < 0L || declaredAmount != total || tag.getInt("storedTypes") != amounts.size()) {
            throw new IOException("Legacy ECO storage cell summary does not match its entries: " + cell);
        }
        return new Snapshot(Map.copyOf(amounts), tag.getLong("revision"), sourceFingerprint);
    }

    private static String fingerprint(Path source) throws IOException {
        requireDirectory(source, "Legacy ECO storage path");
        List<Path> files;
        try (var paths = Files.walk(source)) {
            files = paths.sorted(
                            Comparator.comparing(path -> source.relativize(path).toString()))
                    .toList();
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        for (Path path : files) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Legacy ECO storage contains a symbolic link: " + path);
            }
            Path relative = source.relativize(path);
            byte[] name = relative.toString().getBytes(StandardCharsets.UTF_8);
            digest.update(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? (byte) 1 : (byte) 2);
            digest.update((byte) (name.length >>> 24));
            digest.update((byte) (name.length >>> 16));
            digest.update((byte) (name.length >>> 8));
            digest.update((byte) name.length);
            digest.update(name);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Legacy ECO storage contains an unsupported path: " + path);
            }
            try (InputStream input = Files.newInputStream(path)) {
                input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Legacy ECO storage contains a symbolic link: " + directory);
                }
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file) || !attributes.isRegularFile()) {
                    throw new IOException("Legacy ECO storage contains an unsupported path: " + file);
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void discardWorkingCopy(Path migrationRoot, Path working) throws IOException {
        Path normalizedRoot = migrationRoot.toAbsolutePath().normalize();
        Path normalizedWorking = working.toAbsolutePath().normalize();
        if (!normalizedWorking.startsWith(normalizedRoot) || normalizedWorking.equals(normalizedRoot)) {
            throw new IOException("Refusing to discard an invalid ECO storage migration path: " + working);
        }
        if (!Files.exists(normalizedWorking, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> paths;
        try (var found = Files.walk(normalizedWorking)) {
            paths = new ArrayList<>(found.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException(label + " is not a normal directory: " + path);
        }
    }

    private static boolean isResolved(AEKey key) {
        return key != null && !AE2_MISSING_CONTENT.equals(key.getId());
    }

    record Snapshot(Map<AEKey, Long> amounts, long revision, String sourceFingerprint) {}
}
