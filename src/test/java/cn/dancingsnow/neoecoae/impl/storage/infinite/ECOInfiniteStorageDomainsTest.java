package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDomainsTest {
    @TempDir
    private Path tempDir;

    @Test
    void discoversLegacyDimensionDirectoryWithoutMutatingIt() throws Exception {
        UUID domainId = UUID.randomUUID();
        Path legacy = tempDir.resolve("data")
                .resolve("neoecoae_storage")
                .resolve("dim_minecraft_overworld")
                .resolve("domain_" + domainId);
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("wal_000.log"), "wal");

        var discovered = ECOInfiniteStorageDomains.findLegacyDomainPaths(tempDir, domainId);

        assertEquals(java.util.List.of(legacy.toAbsolutePath().normalize()), discovered);
        assertEquals("wal", Files.readString(legacy.resolve("wal_000.log")));
    }

    @Test
    void reportsEveryAmbiguousLegacyDirectoryForDomainQuarantine() throws Exception {
        UUID domainId = UUID.randomUUID();
        Path root = tempDir.resolve("data").resolve("neoecoae_storage");
        Path first = root.resolve("dim_a").resolve("domain_" + domainId);
        Path second = root.resolve("dim_b").resolve("domain_" + domainId);
        Files.createDirectories(first);
        Files.createDirectories(second);

        assertEquals(
                java.util.Set.of(
                        first.toAbsolutePath().normalize(),
                        second.toAbsolutePath().normalize()),
                java.util.Set.copyOf(ECOInfiniteStorageDomains.findLegacyDomainPaths(tempDir, domainId)));
    }

    @Test
    void discoversPersistedDomainIdsAcrossSupportedLayouts() throws Exception {
        UUID v2Domain = UUID.randomUUID();
        UUID archivedDomain = UUID.randomUUID();
        UUID legacyDomain = UUID.randomUUID();
        Files.createDirectories(tempDir.resolve("data").resolve("neoecoae_infinite"));
        Files.createFile(tempDir.resolve("data").resolve("neoecoae_infinite").resolve("domain_" + v2Domain + ".dat"));
        Files.createDirectories(tempDir.resolve("neoecoae_storage_v1_archive").resolve("domain_" + archivedDomain));
        Files.createDirectories(tempDir.resolve("neoecoae_storage").resolve("dim_minecraft_overworld")
                .resolve("domain_" + legacyDomain));
        Files.createDirectories(tempDir.resolve("data").resolve("neoecoae_storage").resolve("domain_invalid"));

        assertEquals(
                java.util.List.of(v2Domain, archivedDomain, legacyDomain).stream().sorted().toList(),
                ECOInfiniteStorageDomains.discoverDomainIds(tempDir));
    }
}
