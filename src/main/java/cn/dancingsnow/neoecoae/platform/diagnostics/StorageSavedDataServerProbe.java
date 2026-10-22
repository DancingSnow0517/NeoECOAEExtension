package cn.dancingsnow.neoecoae.platform.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.LoggerFactory;

/** Opt-in development-server acceptance. Never runs in an installed production instance. */
public final class StorageSavedDataServerProbe {
    private StorageSavedDataServerProbe() {}
    public static void started(ServerStartedEvent event) {
        String phase = System.getProperty("neoecoae.storageSavedDataSmoke", "");
        if (net.minecraftforge.fml.loading.FMLEnvironment.production || phase.isEmpty()) return;
        MinecraftServer server = event.getServer();
        try {
            run(server, phase);
            LoggerFactory.getLogger(StorageSavedDataServerProbe.class).info("SAVEDDATA_ACCEPTANCE_PASS phase={}", phase);
        } catch (Throwable failure) {
            LoggerFactory.getLogger(StorageSavedDataServerProbe.class).error("SAVEDDATA_ACCEPTANCE_FAIL phase=" + phase, failure);
            throw new IllegalStateException("SavedData acceptance failed", failure);
        } finally { server.halt(false); }
    }
    private static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private static void run(MinecraftServer server, String phase) throws Exception {
        var world = server.overworld();
        var root = server.getWorldPath(LevelResource.ROOT);
        var fixturePath = root.resolve("saveddata-acceptance-fixture.dat");
        AEKey diamond = AEItemKey.of(Items.DIAMOND), iron = AEItemKey.of(Items.IRON_INGOT);
        IActionSource action = IActionSource.empty();
        ISaveProvider owner = () -> {};
        JsonObject report = new JsonObject(); report.addProperty("phase", phase);
        ItemStack disk, bigDisk;
        UUID domainId, hugeDomainId;
        if (phase.equals("write")) {
            check(!Files.exists(fixturePath), "Use a new acceptance world for the write phase");
            // Only L9 disks are eligible for domain migration.
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation("neoecoae", "eco_item_storage_cell_256m"));
            check(item instanceof ECOStorageCellItem, "Missing production L9 ECO disk");
            disk = new ItemStack(item);
            bigDisk = new ItemStack(item);
            ECOStorageCell cell = ECOStorageCellItem.getCellInventory(disk, owner);
            check(cell != null && cell.storageWritable(), "Production disk mount failed");
            check(cell.insert(diamond, 400, Actionable.MODULATE, action) == 400, "Finite insert failed");
            check(cell.insert(iron, 1500, Actionable.MODULATE, action) == 1500, "Second type insert failed");
            check(cell.extract(diamond, 25, Actionable.MODULATE, action) == 25, "Finite extraction failed");
            cell.persistMigrationContents(world);
            ECOStorageCell duplicate = ECOStorageCellItem.getCellInventory(disk.copy(), () -> {});
            check(duplicate != null && duplicate.extract(diamond, 1, Actionable.MODULATE, action) == 0, "Duplicate UUID mounted");
            domainId = UUID.randomUUID(); hugeDomainId = UUID.randomUUID();
            var target = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(world, domainId);
            check(ECOInfiniteStorageTransfer.isEligible(cell), "L9 disk was not eligible for migration");
            check(new ECOInfiniteStorageTransfer().step(disk, cell, domainId, target, () -> {},
                () -> ECOInfiniteStorageMember.markMember(disk, domainId), (key, amount) -> UUID.randomUUID()), "Migration failed");
            check(target.getAmount(diamond).toBigInteger().equals(BigInteger.valueOf(375)), "Domain quantity differs");
            check(cell.getAvailableStacks().isEmpty(), "Retired finite disk remained exposed");
            UUID identity = ECOInfiniteStorageMember.identity(disk);
            UUID diamondTx = UUID.randomUUID(), ironTx = UUID.randomUUID();
            Map<AEKey, UUID> transactions = Map.of(diamond, diamondTx, iron, ironTx);
            Map<AEKey, Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts>> plan = Map.of(
                diamond, Map.of(identity, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 375, 375)),
                iron, Map.of(identity, new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 1500, 1500)));
            check(target.reserveRestores(transactions, Set.of(identity), plan) && target.flush(), "Restore intent not durable");
            check(ECOInfiniteStorageTransfer.restoreTarget(cell, diamond, plan.get(diamond).get(identity), action), "Diamond restore failed");
            check(ECOInfiniteStorageTransfer.restoreTarget(cell, iron, plan.get(iron).get(identity), action), "Iron restore failed");
            cell.persistMigrationContents(world);
            check(target.finishRestores(transactions) && target.flush(), "Restore source not durable");
            ECOInfiniteStorageMember.clearMember(disk);
            check(cell.extract(diamond, 25, Actionable.MODULATE, action) == 25, "Restored disk remained locked");
            cell.persistMigrationContents(world);
            var huge = ECOInfiniteStorageDomains.acquire(world, hugeDomainId);
            huge.insert(diamond, hugeFixtureAmount(), Actionable.MODULATE);
            check(huge.flush(), "BigInteger snapshot failed");
            ECOStorageCell bigCell = wideCell(bigDisk, owner);
            BigInteger offered = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(12345));
            check(bigCell.insertBigInteger(diamond, offered, Actionable.MODULATE, action).equals(offered), "Exact finite insertion failed");
            check(bigCell.extract(diamond, 17, Actionable.MODULATE, action) == 17, "Long projection lost a large stack");
            BigInteger taken = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(31));
            check(bigCell.extractBigInteger(diamond, taken, Actionable.MODULATE, action).equals(taken), "Exact finite extraction failed");
            UUID bigTransferDomain = UUID.randomUUID();
            var bigTarget = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(world, bigTransferDomain);
            check(new ECOInfiniteStorageTransfer().step(bigDisk, bigCell, bigTransferDomain, bigTarget, () -> {},
                () -> ECOInfiniteStorageMember.markMember(bigDisk, bigTransferDomain), (key, amount) -> UUID.randomUUID()), "Exact disk migration failed");
            check(bigTarget.getAmount(diamond).toBigInteger().equals(bigFixtureAmount()), "Exact migration truncated quantity");
            UUID bigIdentity = ECOInfiniteStorageMember.identity(bigDisk), bigRestore = UUID.randomUUID();
            var bigGoal = new ECOInfiniteStorageEngine.RestoreTargetAmounts(BigInteger.ZERO, bigFixtureAmount(), bigFixtureAmount());
            var bigTransactions = Map.of(diamond, bigRestore);
            check(bigTarget.reserveRestores(bigTransactions, Set.of(bigIdentity), Map.of(diamond, Map.of(bigIdentity, bigGoal)))
                && bigTarget.flush(), "Exact restore plan failed");
            check(ECOInfiniteStorageTransfer.restoreTarget(bigCell, diamond, bigGoal, action), "Exact restore insertion failed");
            bigCell.persistMigrationContents(world);
            check(bigTarget.finishRestores(bigTransactions) && bigTarget.flush() && bigTarget.isEmpty(), "Exact source retirement failed");
            ECOInfiniteStorageMember.clearMember(bigDisk);
            bigCell.persistMigrationContents(world);
            ECOInfiniteStorageDomains.release(server, bigTransferDomain);
            CompoundTag fixture = new CompoundTag(); fixture.put("disk", disk.save(new CompoundTag()));
            fixture.put("bigDisk", bigDisk.save(new CompoundTag()));
            fixture.putUUID("domain", domainId); fixture.putUUID("hugeDomain", hugeDomainId);
            prepareInterruptedTransfer(server, fixture, item, owner, diamond);
            prepareMissingIdentities(server, fixture, item, owner, diamond);
            NEAtomicNbtFile.write(fixture, fixturePath);
            ECOInfiniteStorageDomains.release(server, domainId); ECOInfiniteStorageDomains.release(server, hugeDomainId);
            report.addProperty("migrationAndRestore", "PASS");
        } else {
            check(phase.equals("read"), "Unknown acceptance phase");
            CompoundTag fixture = NbtIo.readCompressed(fixturePath.toFile()); disk = ItemStack.of(fixture.getCompound("disk"));
            bigDisk = ItemStack.of(fixture.getCompound("bigDisk"));
            domainId = fixture.getUUID("domain"); hugeDomainId = fixture.getUUID("hugeDomain");
            verifyInterruptedTransferAndMissingIdentities(server, fixture, owner, diamond);
            report.addProperty("coldRestartRecoveryAndMissingFiles", "PASS");
        }
        ECOStorageCell cell = ECOStorageCellItem.getCellInventory(disk, owner);
        check(cell != null && cell.getMigrationAmount(diamond) == 350 && cell.getMigrationAmount(iron) == 1500,
            "Finite quantities did not survive acceptance/restart");
        check(ECOCellReference.id(disk).equals(cell.storageData().id()), "Disk identity changed");
        var domain = ECOInfiniteStorageDomains.acquire(world, domainId);
        var huge = ECOInfiniteStorageDomains.acquire(world, hugeDomainId);
        check(domain.isEmpty(), "Restored domain still contains resources");
        check(huge.getAmount(diamond).toBigInteger().equals(hugeFixtureAmount()),
            "Exact BigInteger quantity changed across restart");
        ECOStorageCell bigCell = wideCell(bigDisk, owner);
        check(bigCell.getMigrationAmountBigInteger(diamond).equals(bigFixtureAmount()), "BigInteger disk restart failed");
        check(bigCell.extractBigInteger(diamond, BigInteger.TEN.pow(100), Actionable.SIMULATE, action).equals(bigFixtureAmount()),
            "Exact extraction simulation truncated quantity");
        var listing = new appeng.api.stacks.KeyCounter(); listing.set(diamond, Long.MAX_VALUE);
        bigCell.getAvailableStacks(listing); check(listing.get(diamond) == Long.MAX_VALUE, "AE2 quantity projection wrapped");
        var exactListing = new java.util.HashMap<AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount>();
        bigCell.neoecoae$visitExactAmounts(exactListing::put);
        check(exactListing.get(diamond).value().equals(bigFixtureAmount()), "Exact terminal listing truncated quantity");
        // Benchmark actual production cell methods. Only the final batch close updates the item summary.
        try (var ignored = ECOCellMutationBatch.open()) {
            for (int i = 0; i < 50_000; i++) {
                cell.insert(diamond, 1, Actionable.MODULATE, action); cell.extract(diamond, 1, Actionable.MODULATE, action);
            }
        }
        var bean = ManagementFactory.getThreadMXBean();
        var allocation = bean instanceof com.sun.management.ThreadMXBean memory && memory.isThreadAllocatedMemorySupported() ? memory : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long bytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        long snapshots = cell.storageData().savedSnapshots(), start = System.nanoTime();
        int iterations = 250_000;
        try (var ignored = ECOCellMutationBatch.open()) {
            for (int i = 0; i < iterations; i++) {
                check(cell.insert(diamond, 1, Actionable.MODULATE, action) == 1, "Benchmark insert rejected");
                check(cell.extract(diamond, 1, Actionable.MODULATE, action) == 1, "Benchmark extraction rejected");
            }
        }
        long elapsed = System.nanoTime() - start;
        long allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - bytes;
        check(cell.storageData().savedSnapshots() == snapshots, "Hot path performed disk I/O");
        check(cell.getMigrationAmount(diamond) == 350 && cell.getMigrationAmount(iron) == 1500, "Benchmark changed quantities");
        cell.persistMigrationContents(world);
        report.addProperty("productionOperations", iterations * 2);
        report.addProperty("productionElapsedMs", elapsed / 1_000_000.0);
        report.addProperty("productionNsPerOperation", elapsed / (double) (iterations * 2));
        report.addProperty("productionAllocatedBytesPerOperation", allocated < 0 ? -1 : allocated / (double) (iterations * 2));
        report.addProperty("hotPathSnapshotWrites", 0); report.addProperty("diskId", ECOCellReference.id(disk).toString());
        for (int i = 0; i < 50_000; i++) { huge.insert(diamond, 1, Actionable.MODULATE); huge.extract(diamond, 1, Actionable.MODULATE); }
        bytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        long durableBefore = ((SavedDataInfiniteStorageEngine) huge).durableRevision(); start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            huge.insert(diamond, 1, Actionable.MODULATE); huge.extract(diamond, 1, Actionable.MODULATE);
        }
        elapsed = System.nanoTime() - start;
        allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - bytes;
        check(((SavedDataInfiniteStorageEngine) huge).durableRevision() == durableBefore, "BigInteger hot path performed I/O");
        check(huge.getAmount(diamond).toBigInteger().equals(hugeFixtureAmount()), "BigInteger hot path lost precision");
        check(huge.flush(), "BigInteger final checkpoint failed");
        report.addProperty("bigIntegerOperations", iterations * 2);
        report.addProperty("bigIntegerElapsedMs", elapsed / 1_000_000.0);
        report.addProperty("bigIntegerAllocatedBytesPerOperation", allocated < 0 ? -1 : allocated / (double) (iterations * 2));
        report.addProperty("bigIntegerDiskMigrationRestoreAndListing", "PASS");
        report.addProperty("exactAmountsAndIdentity", "PASS");
        Files.writeString(root.resolve("saveddata-acceptance-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        ECOInfiniteStorageDomains.release(server, domainId); ECOInfiniteStorageDomains.release(server, hugeDomainId);
        ECOStorageRepository.flush(server);
    }
    private static BigInteger hugeFixtureAmount() {
        return BigInteger.TEN.pow(100).add(BigInteger.valueOf(Long.MAX_VALUE)).add(BigInteger.valueOf(17));
    }
    private static BigInteger bigFixtureAmount() {
        return BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(12297));
    }
    /** Tests the public storage adapter with an addon-sized capacity; registered gameplay capacities are unchanged. */
    private static ECOStorageCell wideCell(ItemStack disk, ISaveProvider owner) {
        return new ECOStorageCell(disk, owner) { @Override public long getTotalBytes() { return Long.MAX_VALUE; } };
    }
    private static void prepareInterruptedTransfer(MinecraftServer server, CompoundTag fixture,
            net.minecraft.world.item.Item item, ISaveProvider owner, AEKey key) throws Exception {
        ItemStack disk = new ItemStack(item); ECOStorageCell source = wideCell(disk, owner);
        check(source.insertBigInteger(key, bigFixtureAmount(), Actionable.MODULATE, IActionSource.empty())
            .equals(bigFixtureAmount()), "Interrupted source preparation failed");
        source.persistMigrationContents(server.overworld());
        UUID domain = UUID.randomUUID(), transaction = UUID.randomUUID();
        var destination = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(server.overworld(), domain);
        var method = cn.dancingsnow.neoecoae.impl.storage.persistence.StorageTransferCoordinator.class.getDeclaredMethod(
            "transfer", ECOStorageRepository.Session.class,
            cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellSavedData.class, UUID.class,
            SavedDataInfiniteStorageEngine.class, UUID.class, java.util.function.Consumer.class);
        method.setAccessible(true);
        java.util.function.Consumer<cn.dancingsnow.neoecoae.impl.storage.persistence.StorageTransferCoordinator.Phase> interrupt = phase -> {
            if (phase == cn.dancingsnow.neoecoae.impl.storage.persistence.StorageTransferCoordinator.Phase.DESTINATION_SAVED)
                throw new ProbeInterruption();
        };
        try {
            method.invoke(null, source.storageSession(), source.storageData(), domain, destination, transaction, interrupt);
            throw new IllegalStateException("Acceptance interruption did not run");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (!(failure.getCause() instanceof ProbeInterruption)) throw failure;
        }
        check(!source.storageData().amounts().isEmpty() && !destination.canUseMountedStorage(), "Interrupted source was exposed");
        fixture.put("interruptedDisk", disk.save(new CompoundTag())); fixture.putUUID("interruptedDomain", domain);
        ECOInfiniteStorageDomains.release(server, domain);
    }
    private static void prepareMissingIdentities(MinecraftServer server, CompoundTag fixture,
            net.minecraft.world.item.Item item, ISaveProvider owner, AEKey key) throws Exception {
        ItemStack missing = new ItemStack(item); var cell = ECOStorageCellItem.getCellInventory(missing, owner);
        check(cell != null && cell.insert(key, 777, Actionable.MODULATE, IActionSource.empty()) == 777, "Missing disk fixture failed");
        cell.persistMigrationContents(server.overworld()); fixture.put("missingDisk", missing.save(new CompoundTag()));
        var file = cell.storageData().dataFile(); Files.delete(file);
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
        UUID domainId = UUID.randomUUID(); var domain = ECOInfiniteStorageDomains.acquire(server.overworld(), domainId);
        domain.insert(key, 777, Actionable.MODULATE); check(domain.flush(), "Missing domain fixture failed");
        var domainFile = server.getWorldPath(LevelResource.ROOT).resolve("data/neoecoae_infinite_" + domainId + "_v3.dat");
        Files.delete(domainFile); Files.deleteIfExists(domainFile.resolveSibling(domainFile.getFileName() + ".bak"));
        fixture.putUUID("missingDomain", domainId); ECOInfiniteStorageDomains.release(server, domainId);
    }
    private static void verifyInterruptedTransferAndMissingIdentities(MinecraftServer server, CompoundTag fixture,
            ISaveProvider owner, AEKey key) {
        ItemStack interrupted = ItemStack.of(fixture.getCompound("interruptedDisk"));
        ECOStorageCell source = ECOStorageCellItem.getCellInventory(interrupted, owner);
        check(source != null && source.storageData().amounts().isEmpty() && ECOInfiniteStorageMember.isMember(interrupted),
            "Cold restart did not retire the sealed source");
        UUID domainId = fixture.getUUID("interruptedDomain"); var target = ECOInfiniteStorageDomains.acquire(server.overworld(), domainId);
        check(target.getAmount(key).toBigInteger().equals(bigFixtureAmount()), "Cold recovery duplicated or lost BigInteger resources");
        check(target.canUseMountedStorage(), "Cold recovery left the destination locked");
        ECOInfiniteStorageDomains.release(server, domainId);
        ItemStack missing = ItemStack.of(fixture.getCompound("missingDisk")); UUID original = ECOCellReference.id(missing);
        var unavailable = ECOStorageCellItem.getCellInventory(missing, owner);
        check(unavailable != null && !unavailable.storageWritable() && original.equals(ECOCellReference.id(missing))
            && unavailable.extract(key, 1, Actionable.MODULATE, IActionSource.empty()) == 0,
            "Missing disk became an empty writable disk");
        UUID missingDomain = fixture.getUUID("missingDomain"); var absent = ECOInfiniteStorageDomains.acquire(server.overworld(), missingDomain);
        check(!absent.isHealthy() && !absent.isEmpty() && absent.insert(key, 1, Actionable.MODULATE) == 0,
            "Missing published domain became a new writable domain");
        ECOInfiniteStorageDomains.release(server, missingDomain);
    }
    private static final class ProbeInterruption extends Error {}
}
