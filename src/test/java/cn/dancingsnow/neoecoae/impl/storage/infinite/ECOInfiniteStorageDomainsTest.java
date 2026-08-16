package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.IOUtilities;
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

    @Test
    void rejectsQuarantinedSnapshotWithoutDataRoot() throws Exception {
        Path dataFile = temporaryDirectory.resolve("domain.dat");
        IOUtilities.writeNbtCompressed(new CompoundTag(), dataFile);

        assertThrows(
            IOException.class,
            () -> SavedDataInfiniteStorageEngine.loadFromDisk(null, UUID.randomUUID(), null, dataFile)
        );
    }

    @Test
    void loadsAValidV2SnapshotWithoutUsingTheSavedDataCache() throws Exception {
        UUID domainId = UUID.randomUUID();
        Path dataFile = temporaryDirectory.resolve("domain.dat");
        SavedDataInfiniteStorageEngine source = SavedDataInfiniteStorageEngine.createNew(
            null,
            domainId,
            null,
            dataFile
        );
        CompoundTag root = new CompoundTag();
        root.put("data", source.save(new CompoundTag(), null));
        IOUtilities.writeNbtCompressed(root, dataFile);

        ECOInfiniteStorageEngine loaded = SavedDataInfiniteStorageEngine.loadFromDisk(
            null,
            domainId,
            null,
            dataFile
        );

        assertEquals(ECOInfiniteDomainState.READY, loaded.getState());
        assertTrue(loaded.isEmpty());
    }
}
