package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strictly imports V1 data through an isolated working copy. The source directory is only hashed and copied.
 */
final class LegacyV1Reader {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private LegacyV1Reader() {}

    static Snapshot read(UUID domainId, Path sourceDomain, Path migrationRoot) throws IOException {
        Path source = normalizeDirectory(sourceDomain, "V1 source");
        validateUnambiguousLayout(source);
        validateKnownLayout(source);
        Path staging = stagingPath(migrationRoot, domainId);
        resetStaging(staging, migrationRoot, domainId);
        Path copy = staging.resolve("legacy_v1_copy");

        DirectoryFingerprint before = fingerprint(source);
        copyTree(source, copy);
        requireUnchanged(before, fingerprint(source), "while copying V1 data");
        normalizeWorkingCopyForLegacyReader(copy);

        FileBackedInfiniteStorageEngine.LegacyV1Snapshot imported;
        try {
            imported = FileBackedInfiniteStorageEngine.readMigrationSnapshot(domainId, copy);
        } catch (RuntimeException e) {
            throw new IOException("Unable to read V1 infinite-storage data without loss", e);
        }
        requireUnchanged(before, fingerprint(source), "while parsing V1 data");
        return new Snapshot(imported.amounts(), imported.receipts(), imported.revision(), before.digest());
    }

    static void verifySource(Path sourceDomain, String expectedFingerprint) throws IOException {
        DirectoryFingerprint current = fingerprint(normalizeDirectory(sourceDomain, "V1 source"));
        if (!current.digest().equals(expectedFingerprint)) {
            throw new IOException("V1 infinite-storage files changed during migration");
        }
    }

    static void archive(Path sourceDomain, Path archiveDomain, String expectedFingerprint) throws IOException {
        Path source = normalizeDirectory(sourceDomain, "V1 source");
        Path archive = archiveDomain.toAbsolutePath().normalize();
        verifySource(source, expectedFingerprint);
        if (!source.equals(archive)) {
            if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("V1 archive already exists: " + archive);
            }
            Files.createDirectories(archive.getParent());
            try {
                Files.move(source, archive, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, archive);
            }
        }
        verifySource(archive, expectedFingerprint);
        forceDirectoryBestEffort(archive.getParent());
    }

    static void discardWorkingCopy(Path migrationRoot, UUID domainId) throws IOException {
        Path staging = stagingPath(migrationRoot, domainId);
        validateStaging(staging, migrationRoot, domainId);
        deleteTree(staging);
    }

    private static Path stagingPath(Path migrationRoot, UUID domainId) {
        return migrationRoot.toAbsolutePath().normalize().resolve("domain_" + domainId + ".migrating");
    }

    private static void resetStaging(Path staging, Path migrationRoot, UUID domainId) throws IOException {
        validateStaging(staging, migrationRoot, domainId);
        deleteTree(staging);
        Files.createDirectories(staging);
    }

    private static void validateStaging(Path staging, Path migrationRoot, UUID domainId) throws IOException {
        Path root = migrationRoot.toAbsolutePath().normalize();
        Path expected = root.resolve("domain_" + domainId + ".migrating");
        if (!staging.toAbsolutePath().normalize().equals(expected)) {
            throw new IOException("Unsafe V1 migration staging path: " + staging);
        }
    }

    private static Path normalizeDirectory(Path path, String description) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " is not a directory: " + normalized);
        }
        return normalized;
    }

    private static void validateUnambiguousLayout(Path source) throws IOException {
        if (Files.isDirectory(source.resolve("shards"))
                && hasMatchingRegularFile(source, name -> name.startsWith("shard_") && name.endsWith(".dat"))) {
            throw new IOException("V1 domain contains shard files in both legacy and current locations");
        }
        if (Files.isDirectory(source.resolve("wal")) && hasMatchingRegularFile(source, LegacyV1Reader::isWalFile)) {
            throw new IOException("V1 domain contains WAL files in both legacy and current locations");
        }
        if (Files.isDirectory(source.resolve("txn")) && Files.isDirectory(source.resolve("transactions"))) {
            throw new IOException("V1 domain contains transaction receipts in two authoritative locations");
        }
    }

    private static void validateKnownLayout(Path source) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (path.equals(source)) {
                    continue;
                }
                Path relative = source.relativize(path);
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Symbolic links are not supported in V1 storage data: " + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (relative.getNameCount() != 1 || !isKnownDirectory(relative.toString())) {
                        throw new IOException("Unknown directory in V1 storage data: " + relative);
                    }
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!isKnownFile(relative)) {
                        throw new IOException("Unknown file in V1 storage data: " + relative);
                    }
                } else {
                    throw new IOException("Unsupported filesystem entry in V1 storage data: " + path);
                }
            }
        }
    }

    private static boolean isKnownDirectory(String name) {
        return name.equals("shards") || name.equals("wal") || name.equals("txn") || name.equals("transactions");
    }

    private static boolean isKnownFile(Path relative) {
        String name = relative.getFileName().toString();
        if (relative.getNameCount() == 1) {
            return name.equals("domain.meta") || name.equals("domain.meta.tmp") || isShardFile(name) || isWalFile(name);
        }
        if (relative.getNameCount() != 2) {
            return false;
        }
        String directory = relative.getName(0).toString();
        return switch (directory) {
            case "shards" -> isShardFile(name);
            case "wal" -> isWalFile(name);
            case "txn" -> name.equals("receipts.log") || isTransactionReceipt(name);
            case "transactions" -> isTransactionReceipt(name);
            default -> false;
        };
    }

    private static boolean isShardFile(String name) {
        String canonical = name.endsWith(".tmp") ? name.substring(0, name.length() - ".tmp".length()) : name;
        if (canonical.length() != "shard_000.dat".length()
                || !canonical.startsWith("shard_")
                || !canonical.endsWith(".dat")) {
            return false;
        }
        try {
            int shard = Integer.parseInt(canonical.substring(6, 9));
            return shard >= 0 && shard < 256;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isTransactionReceipt(String name) {
        if (!name.endsWith(".done")) {
            return false;
        }
        try {
            UUID.fromString(name.substring(0, name.length() - ".done".length()));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasMatchingRegularFile(Path directory, java.util.function.Predicate<String> selector)
            throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .anyMatch(selector);
        }
    }

    private static boolean isWalFile(String name) {
        return name.equals("wal_000.log")
                || name.startsWith("wal_") && name.endsWith(".sealed")
                || name.startsWith("wal-") && name.endsWith(".log");
    }

    private static DirectoryFingerprint fingerprint(Path root) throws IOException {
        Map<String, FileFingerprint> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Symbolic links are not supported in V1 storage data: " + path);
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                files.put(relative, new FileFingerprint(Files.size(path), sha256(path)));
            }
        }
        if (files.isEmpty()) {
            throw new IOException("V1 infinite-storage directory contains no files: " + root);
        }
        return new DirectoryFingerprint(Map.copyOf(files));
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[HASH_BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        List<Path> paths;
        try (var walk = Files.walk(source)) {
            paths = walk.sorted().toList();
        }
        for (Path path : paths) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Symbolic links are not supported in V1 storage data: " + path);
            }
            Path destination = target.resolve(source.relativize(path)).normalize();
            if (!destination.startsWith(target)) {
                throw new IOException("V1 migration copy escaped its staging directory");
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(destination);
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                throw new IOException("Unsupported filesystem entry in V1 storage data: " + path);
            }
        }
    }

    /**
     * The 1.20.1 V1 reader expects root shard/WAL files and {@code transactions/*.done}.
     * Earlier V1 releases also wrote equivalent data below directories, so normalize only
     * the disposable copy. Unsupported record formats are rejected rather than ignored.
     */
    private static void normalizeWorkingCopyForLegacyReader(Path copy) throws IOException {
        moveDirectoryFiles(copy.resolve("shards"), copy, LegacyV1Reader::isShardFile);

        Path walDirectory = copy.resolve("wal");
        if (Files.isDirectory(walDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.list(walDirectory)) {
                for (Path path : paths.toList()) {
                    String name = path.getFileName().toString();
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Unsupported WAL entry in V1 storage data: " + path);
                    }
                    if (!name.equals("wal_000.log")) {
                        throw new IOException("Unsupported sealed or segmented V1 WAL: " + path);
                    }
                    moveFile(path, copy.resolve(name));
                }
            }
            Files.delete(walDirectory);
        }

        Path transactions = copy.resolve("transactions");
        Path txn = copy.resolve("txn");
        if (Files.isDirectory(txn, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(transactions);
            try (var paths = Files.list(txn)) {
                for (Path path : paths.toList()) {
                    String name = path.getFileName().toString();
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Unsupported transaction entry in V1 storage data: " + path);
                    }
                    if (name.equals("receipts.log")) {
                        throw new IOException("Unsupported V1 transaction ledger: " + path);
                    }
                    if (!isTransactionReceipt(name)) {
                        throw new IOException("Unsupported transaction receipt in V1 storage data: " + path);
                    }
                    moveFile(path, transactions.resolve(name));
                }
            }
            Files.delete(txn);
        }
    }

    private static void moveDirectoryFiles(
            Path directory, Path destinationRoot, java.util.function.Predicate<String> allowed) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !allowed.test(name)) {
                    throw new IOException("Unsupported V1 storage entry: " + path);
                }
                moveFile(path, destinationRoot.resolve(name));
            }
        }
        Files.delete(directory);
    }

    private static void moveFile(Path source, Path destination) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ambiguous V1 storage data in working copy: " + destination);
        }
        Files.move(source, destination);
    }

    private static void requireUnchanged(DirectoryFingerprint expected, DirectoryFingerprint actual, String operation)
            throws IOException {
        if (!expected.equals(actual)) {
            throw new IOException("V1 infinite-storage files changed " + operation);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> paths;
        try (var walk = Files.walk(root)) {
            paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    private static void forceDirectoryBestEffort(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory forcing is not supported on every platform; the data files themselves are fully forced.
        }
    }

    record Snapshot(Map<AEKey, HugeAmount> amounts, Set<UUID> receipts, long revision, String sourceFingerprint) {}

    private record FileFingerprint(long size, String sha256) {}

    private record DirectoryFingerprint(Map<String, FileFingerprint> files) {
        String digest() {
            MessageDigest digest = sha256Digest();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Long.toString(entry.getValue().size()).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) 0);
                digest.update(entry.getValue().sha256().getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) '\n');
            });
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
