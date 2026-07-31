package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDomainsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversV2ArchiveAndLegacyDomainIds() throws Exception {
        UUID v2 = UUID.randomUUID();
        UUID archive = UUID.randomUUID();
        UUID legacy = UUID.randomUUID();
        UUID dimensionLegacy = UUID.randomUUID();

        Path world = temporaryDirectory.resolve("world");
        Files.createDirectories(world.resolve("data/neoecoae_infinite"));
        Files.createFile(world.resolve("data/neoecoae_infinite/domain_" + v2 + ".dat"));
        Files.createDirectories(world.resolve("neoecoae_storage_v1_archive/domain_" + archive));
        Files.createDirectories(world.resolve("neoecoae_storage/domain_" + legacy));
        Files.createDirectories(world.resolve("data/neoecoae_storage/dim_0/domain_" + dimensionLegacy));
        Files.createFile(world.resolve("data/neoecoae_infinite/domain_not-a-uuid.dat"));

        assertEquals(
            Set.of(v2, archive, legacy, dimensionLegacy),
            Set.copyOf(ECOInfiniteStorageDomains.discoverDomainIds(world))
        );
    }
}
