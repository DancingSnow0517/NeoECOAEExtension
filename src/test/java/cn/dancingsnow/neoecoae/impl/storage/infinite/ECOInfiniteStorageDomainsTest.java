package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDomainsTest {
    @TempDir
    private Path tempDir;

    @Test
    void migratesTheOnlyLegacyDimensionDirectoryToTheWorldGlobalPath() throws Exception {
        UUID domainId = UUID.randomUUID();
        Path legacy = tempDir.resolve("dim_minecraft_overworld").resolve("domain_" + domainId);
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("wal_000.log"), "wal");

        Path resolved = ECOInfiniteStorageDomains.resolveDomainPath(tempDir, domainId);

        assertEquals(tempDir.resolve("domain_" + domainId), resolved);
        assertEquals("wal", Files.readString(resolved.resolve("wal_000.log")));
    }

    @Test
    void refusesAmbiguousLegacyDirectories() throws Exception {
        UUID domainId = UUID.randomUUID();
        Files.createDirectories(tempDir.resolve("dim_a").resolve("domain_" + domainId));
        Files.createDirectories(tempDir.resolve("dim_b").resolve("domain_" + domainId));

        assertThrows(IllegalStateException.class, () -> ECOInfiniteStorageDomains.resolveDomainPath(tempDir, domainId));
    }
}
