package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDomainsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void newDomainsUseWorldGlobalDirectory() {
        UUID domainId = UUID.randomUUID();

        Path resolved = ECOInfiniteStorageDomains.resolveDomainPath(temporaryDirectory, domainId);

        assertEquals(temporaryDirectory.resolve("domain_" + domainId), resolved);
    }

    @Test
    void migratesLegacyDimensionDirectoryWithoutLosingFiles() throws Exception {
        UUID domainId = UUID.randomUUID();
        Path legacy = temporaryDirectory.resolve("dim_minecraft_overworld").resolve("domain_" + domainId);
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("wal_000.log"), "kept");

        Path resolved = ECOInfiniteStorageDomains.resolveDomainPath(temporaryDirectory, domainId);

        assertEquals(temporaryDirectory.resolve("domain_" + domainId), resolved);
        assertTrue(Files.isRegularFile(resolved.resolve("wal_000.log")));
        assertEquals("kept", Files.readString(resolved.resolve("wal_000.log")));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void refusesAmbiguousLegacyDomains() throws Exception {
        UUID domainId = UUID.randomUUID();
        Files.createDirectories(temporaryDirectory.resolve("dim_a").resolve("domain_" + domainId));
        Files.createDirectories(temporaryDirectory.resolve("dim_b").resolve("domain_" + domainId));

        assertThrows(
            IllegalStateException.class,
            () -> ECOInfiniteStorageDomains.resolveDomainPath(temporaryDirectory, domainId)
        );
    }
}
