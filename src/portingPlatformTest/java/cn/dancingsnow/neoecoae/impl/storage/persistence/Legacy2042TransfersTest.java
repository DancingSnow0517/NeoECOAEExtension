package cn.dancingsnow.neoecoae.impl.storage.persistence;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class Legacy2042TransfersTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }
    private static final String DIMENSION = "minecraft:overworld";
    private static final long POSITION = 12345;
    // Independently calculated from 20.4.2's canonical {#c:"ae2:i",id:"minecraft:diamond"} fingerprint + ":90".
    private static final String DIAMOND_90_DIGEST = "6446ec7ff15e8386cbc0c9d64fd993d46ca2af23e4ba04da036bba1492bd11ed";
    private static AEKey key() { return AEItemKey.of(Items.DIAMOND); }
    private static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private static ECOStorageRepository.Session session(Path directory) {
        return new ECOStorageRepository.Session(new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer()), directory);
    }
    private static ItemStack disk(Path directory, UUID matrix) throws Exception {
        UUID identity = UUID.randomUUID(); var disk = new ItemStack(Items.PAPER);
        disk.getOrCreateTag().putUUID("eco_cell_id", identity); disk.getOrCreateTag().putUUID("neoecoae_infinite_matrix_id", matrix);
        CompoundTag data = new CompoundTag(), entry = new CompoundTag(), root = new CompoundTag(); ListTag entries = new ListTag();
        data.putInt("format", 1); data.putUUID("cell", identity); data.putLong("revision", 4);
        entry.put("key", key().toTagGeneric()); entry.putLong("amount", 90); entries.add(entry); data.put("entries", entries);
        root.put("data", data); Path file = directory.resolve("neoecoae_cells/" + identity + ".dat"); Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file.toFile()); return disk;
    }
    private static SavedDataInfiniteStorageEngine target(Path directory, UUID domain, UUID receipt, String digest) {
        CompoundTag old = new CompoundTag(), entry = new CompoundTag(), proof = new CompoundTag(); ListTag entries = new ListTag(), proofs = new ListTag();
        old.putInt("format", 2); old.putUUID("domain", domain); old.putLong("revision", 6);
        entry.put("key", key().toTagGeneric()); entry.putLong("amount_long", 97); entries.add(entry); old.put("entries", entries);
        if (receipt != null) { proof.putUUID("id", receipt); if (digest != null) proof.putString("contents_sha256", digest); proofs.add(proof); }
        old.put("transfer_receipts", proofs);
        var data = ECOInfiniteStorageData.load(Legacy2042Storage.domain(old, domain)); data.bind(directory.resolve("domain.dat")); data.setDirty();
        assertTrue(data.flush()); return new SavedDataInfiniteStorageEngine(data);
    }

    @Test void verifiedBatchReceiptPreventsDoubleImportAcrossSourceRetirementAndRestart(@TempDir Path directory) throws Exception {
        UUID matrix = UUID.randomUUID(), domain = UUID.randomUUID(); var disk = disk(directory, matrix); ItemStack stale = disk.copy();
        var session = session(directory); var source = session.open(disk, null).data();
        UUID receipt = id(domain + ":to-domain-v3:" + DIMENSION + ":" + matrix);
        assertEquals(DIAMOND_90_DIGEST, Legacy2042Transfers.digest(source, Map.of(key(), BigInteger.valueOf(90))));
        var target = target(directory, domain, receipt, DIAMOND_90_DIGEST);
        var aliases = Legacy2042Transfers.aliases(source, target, domain, DIMENSION, POSITION, disk);
        assertEquals(Set.of(receipt), aliases.get(key()));
        assertTrue(StorageTransferCoordinator.transfer(session, source, domain, target, UUID.randomUUID(), aliases));
        assertEquals(BigInteger.valueOf(97), target.getAmount(key()).toBigInteger()); assertTrue(source.amounts().isEmpty());
        assertFalse(source.legacy2042());
        var reloaded = session(directory).open(stale, null).data(); assertTrue(reloaded.amounts().isEmpty());
        assertTrue(reloaded.metadata().getBoolean("neoecoae_infinite_member"));
    }

    @Test void mismatchingBatchProofKeepsSourceAndTargetUnchanged(@TempDir Path directory) throws Exception {
        UUID matrix = UUID.randomUUID(), domain = UUID.randomUUID(); var disk = disk(directory, matrix); var source = session(directory).open(disk, null).data();
        var target = target(directory, domain, id(domain + ":to-domain-v3:" + DIMENSION + ":" + matrix), "0".repeat(64));
        assertThrows(IllegalStateException.class, () -> Legacy2042Transfers.aliases(source, target, domain, DIMENSION, POSITION, disk));
        assertEquals(90, source.amounts().visible(key())); assertEquals(97, target.getAmount(key()).toLongSaturated());
    }

    @Test void originalPositionBatchAndPerKeyReceiptsAreRecognized(@TempDir Path directory) throws Exception {
        UUID matrix = UUID.randomUUID(), domain = UUID.randomUUID(); var disk = disk(directory, matrix); var source = session(directory).open(disk, null).data();
        UUID batch = id(domain + ":to-domain-v2:" + POSITION);
        var target = target(directory, domain, batch, DIAMOND_90_DIGEST);
        assertEquals(Set.of(batch), Legacy2042Transfers.aliases(source, target, domain, DIMENSION, POSITION, disk).get(key()));
        UUID perKey = id(domain + ":to-domain:" + POSITION + ":" + key().toTagGeneric() + ":90");
        var legacy = target(directory, domain, perKey, null);
        assertEquals(Set.of(perKey), Legacy2042Transfers.aliases(source, legacy, domain, DIMENSION, POSITION, disk).get(key()));
    }

    @Test void independentNewInventoryDoesNotReuseHistoricalReleaseReceipts(@TempDir Path directory) throws Exception {
        UUID matrix = UUID.randomUUID(), domain = UUID.randomUUID(); var disk = disk(directory, matrix); var source = session(directory).open(disk, null).data();
        var target = target(directory, domain, id(domain + ":to-domain-v3:" + DIMENSION + ":" + matrix), "0".repeat(64));
        source.clearLegacy2042();
        assertTrue(Legacy2042Transfers.aliases(source, target, domain, DIMENSION, POSITION, disk).isEmpty());
    }

    @ParameterizedTest @EnumSource(StorageTransferCoordinator.Phase.class)
    void originalBatchProofSurvivesEveryNewJournalCrashBoundary(StorageTransferCoordinator.Phase crashAt, @TempDir Path directory) throws Exception {
        UUID matrix = UUID.randomUUID(), domain = UUID.randomUUID(), transaction = UUID.randomUUID();
        var disk = disk(directory, matrix); var session = session(directory); var source = session.open(disk, null).data();
        var target = target(directory, domain, id(domain + ":to-domain-v3:" + DIMENSION + ":" + matrix), DIAMOND_90_DIGEST);
        var aliases = Legacy2042Transfers.aliases(source, target, domain, DIMENSION, POSITION, disk);
        assertThrows(PowerLoss.class, () -> StorageTransferCoordinator.transfer(session, source, domain, target, transaction,
            phase -> { if (phase == crashAt) throw new PowerLoss(); }, aliases));
        var restarted = session(directory); var reloadedSource = restarted.open(disk, null).data();
        var reloadedData = ECOInfiniteStorageData.load(NbtIo.readCompressed(directory.resolve("domain.dat").toFile()).getCompound("data"));
        reloadedData.bind(directory.resolve("domain.dat")); var reloadedTarget = new SavedDataInfiniteStorageEngine(reloadedData);
        assertTrue(StorageTransferCoordinator.transfer(restarted, reloadedSource, domain, reloadedTarget, transaction, aliases));
        assertTrue(reloadedSource.amounts().isEmpty()); assertEquals(97, reloadedTarget.getAmount(key()).toLongSaturated());
        assertTrue(reloadedTarget.hasMigrationReceipt(transaction)); assertFalse(reloadedSource.legacy2042());
    }

    private static final class PowerLoss extends Error {}
}
