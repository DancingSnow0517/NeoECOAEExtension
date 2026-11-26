package cn.dancingsnow.neoecoae.platform.diagnostics;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.all.NEBlocks;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
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
    private static void verifyForcedRecovery(ServerLevel world, Path root) throws Exception {
        MinecraftServer server = world.getServer();
        AEKey key = ECOKeyFactory.item(Items.DIAMOND);
        IActionSource action = IActionSource.empty();
        UUID domainId = UUID.randomUUID();
        var domain = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(world, domainId);
        check(domain.insert(key, 100, Actionable.MODULATE) == 100 && domain.flush(), "Cannot seed frozen domain");
        ECOInfiniteStorageDomains.release(server, domainId);
        var drives = recoveryDrives(world, 40);
        var ordinary = recoveryDrive(world, new BlockPos(40, 100, 1));
        check(ordinary.getCellInventory().insert(key, 25, Actionable.MODULATE, action) == 25, "Cannot seed ordinary cell");
        var all = java.util.Arrays.copyOf(drives, drives.length + 1);
        all[drives.length] = ordinary;
        var host = recoveryHost(world, 40, all);
        host.tick(world, host.getBlockPos(), host.getBlockState());
        check(host.recoverInfiniteDomain(domainId) == null && host.canUseHostDomainStorage(), "Cannot claim saved infinite domain");
        check(domainId.toString().equals(host.getInfiniteDomainText()) && host.getMissingInfiniteMembers() == 0,
            "Claim lost UUID or counted a nonempty matrix");
        var view = new java.util.concurrent.atomic.AtomicReference<appeng.api.storage.MEStorage>();
        host.mountInventories((storage, priority) -> view.set(storage));
        check(view.get() != null && view.get().getAvailableStacks().get(key) == 100, "Domain did not publish its stock");
        var other = recoveryHost(world, 72);
        check(other.forceRecoverInfiniteDomain(domainId, ignored -> {}) != null, "A second loaded host claimed the same domain");
        ItemStack removed = drives[0].HANDLER.extractItem(0, 1, false);
        check(!removed.isEmpty() && !host.canUseHostDomainStorage() && host.getMissingInfiniteMembers() == 1,
            "Removing the twelfth matrix did not freeze the domain");
        check(view.get().extract(key, 1, Actionable.MODULATE, action) == 0
            && view.get().getAvailableStacks().isEmpty(), "Retired domain view remained accessible");
        check(domain.getAmount(key).toBigInteger().intValueExact() == 100
            && ordinary.getCellInventory().getAvailableStacks().get(key) == 25, "Freeze moved physical stock");
        check(drives[0].HANDLER.insertItem(0, removed, false).isEmpty() && host.canUseHostDomainStorage(), "Replacement matrix did not resume the domain");
        var componentField = ECOStorageSystemBlockEntity.class.getDeclaredField("infiniteComponentItemHandler");
        componentField.setAccessible(true);
        var componentHandler = (net.minecraftforge.items.IItemHandler) componentField.get(host);
        ItemStack components = componentHandler.extractItem(0, 1, false);
        check(!components.isEmpty() && !host.canUseHostDomainStorage() && domain.getAmount(key).toBigInteger().intValueExact() == 100,
            "Component removal altered the frozen domain");
        check(componentHandler.insertItem(0, components, false).isEmpty() && host.canUseHostDomainStorage(), "Component replacement did not resume");
        check(view.get().getAvailableStacks().isEmpty(), "Old view revived after mode change");
        // Exercise the actual loot table and onRemove path, not just hand-built item NBT.
        ItemStack portable = controllerDrop(world, host);
        check(domainId.equals(ECOStorageSystemBlockEntity.readControllerDomain(portable)), "Controller drop lost its domain");
        world.removeBlock(host.getBlockPos(), false);
        var moved = recoveryHost(world, 120, portable, recoveryDrives(world, 120));
        moved.tick(world, moved.getBlockPos(), moved.getBlockState());
        check(moved.canUseHostDomainStorage() && domainId.toString().equals(moved.getInfiniteDomainText())
            && domain.getAmount(key).toBigInteger().intValueExact() == 100, "Moved controller did not recover its exact stock");
        ItemStack renewed = controllerDrop(world, moved);
        world.removeBlock(moved.getBlockPos(), false);
        var stale = recoveryHost(world, 136, portable, recoveryDrives(world, 136));
        stale.tick(world, stale.getBlockPos(), stale.getBlockState());
        check(!stale.canUseHostDomainStorage() && ECOInfiniteStorageDomains.mountedHosts(server, domainId) == 0,
            "A stale copy reclaimed the unbound domain");
        var frozen = recoveryHost(world, 152, renewed);
        frozen.tick(world, frozen.getBlockPos(), frozen.getBlockState());
        check(domainId.toString().equals(frozen.getInfiniteDomainText()) && !frozen.canUseHostDomainStorage()
            && ECOInfiniteStorageDomains.mountedHosts(server, domainId) == 1, "An unqualified moved host exposed or lost its claim");
        var fixture = new CompoundTag();
        fixture.putUUID("domain", domainId);
        fixture.put("stale", portable.save(new CompoundTag()));
        fixture.put("current", controllerDrop(world, frozen).save(new CompoundTag()));
        world.removeBlock(frozen.getBlockPos(), false);
        NEAtomicNbtFile.write(fixture, root.resolve("controller-relocation.dat"));
        UUID restoreId = UUID.randomUUID();
        var restoring = (SavedDataInfiniteStorageEngine) ECOInfiniteStorageDomains.acquire(world, restoreId);
        check(restoring.insert(key, 100, Actionable.MODULATE) == 100, "Cannot seed legacy restore");
        var restoredDrive = recoveryDrive(world, new BlockPos(96, 100, 2));
        ECOInfiniteStorageMember.markMember(restoredDrive.getCellStack(), restoreId);
        UUID identity = ECOInfiniteStorageMember.getIdentity(restoredDrive.getCellStack()).orElseThrow();
        UUID transaction = UUID.randomUUID();
        check(restoring.reserveRestores(Map.of(key, transaction), Set.of(identity), Map.of(key, Map.of(identity,
            new ECOInfiniteStorageEngine.RestoreTargetAmounts(0, 100, 100)))) && restoring.flush(), "Cannot reserve legacy restore");
        check(((ECOStorageCell) restoredDrive.getCellInventory()).insertForMigration(key, 40, Actionable.MODULATE, action) == 40,
            "Cannot seed partial legacy restore");
        ((ECOStorageCell) restoredDrive.getCellInventory()).persistMigrationContents(world);
        UUID token = UUID.randomUUID();
        check(restoring.persistOwnerToken(token), "Cannot save legacy owner");
        ECOInfiniteStorageDomains.release(server, restoreId);
        var legacy = recoveryHost(world, 96, restoredDrive);
        var legacyTag = legacy.saveWithFullMetadata();
        legacyTag.putUUID("infiniteDomainId", restoreId);
        legacyTag.putUUID("infiniteBindingToken", token);
        legacyTag.putString("infiniteHostMode", "restoring_to_normal");
        legacy.load(legacyTag);
        legacy.tick(world, legacy.getBlockPos(), legacy.getBlockState());
        check(!legacy.canUseHostDomainStorage() && !restoring.hasPendingRestore()
            && restoring.getAmount(key).toBigInteger().intValueExact() == 60
            && restoredDrive.getCellInventory().getAvailableStacks().get(key) == 40,
            "Legacy restore did not preserve exact remaining ownership");
        check(restoredDrive.getCellInventory().getAvailableStacks().get(key) + restoring.getAmount(key).toBigInteger().longValueExact() == 100,
            "Legacy reconciliation violated conservation");
    }

    private static ECODriveBlockEntity[] recoveryDrives(ServerLevel world, int offset) {
        return java.util.stream.IntStream.range(0, 12).mapToObj(index -> recoveryDrive(world, new BlockPos(offset, 100, 4 + index * 3)))
            .toArray(ECODriveBlockEntity[]::new);
    }

    private static ECODriveBlockEntity recoveryDrive(ServerLevel world, int offset) {
        return recoveryDrive(world, new BlockPos(offset, 100, 0));
    }

    private static ECODriveBlockEntity recoveryDrive(ServerLevel world, BlockPos pos) {
        world.setBlockAndUpdate(pos, NEBlocks.ECO_DRIVE.getDefaultState());
        var drive = (ECODriveBlockEntity) world.getBlockEntity(pos);
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("neoecoae", "eco_item_storage_cell_256m"));
        check(drive != null && drive.HANDLER.insertItem(0, new ItemStack(item), false).isEmpty(), "Cannot install fixture disk");
        check(drive.getCellInventory() instanceof ECOStorageCell, "Cannot load fixture disk");
        return drive;
    }

    private static ItemStack controllerDrop(ServerLevel world, ECOStorageSystemBlockEntity host) {
        return net.minecraft.world.level.block.Block.getDrops(host.getBlockState(), world, host.getBlockPos(), host).stream()
            .filter(stack -> stack.is(host.getBlockState().getBlock().asItem())).findFirst().orElseThrow();
    }

    private static ECOStorageSystemBlockEntity recoveryHost(ServerLevel world, int offset, ECODriveBlockEntity... drives) {
        return recoveryHost(world, offset, ItemStack.EMPTY, drives);
    }

    private static ECOStorageSystemBlockEntity recoveryHost(ServerLevel world, int offset, ItemStack placed, ECODriveBlockEntity... drives) {
        var pos = new BlockPos(offset, 100, 0);
        world.setBlockAndUpdate(pos, NEBlocks.STORAGE_SYSTEM_L9.getDefaultState());
        var host = (ECOStorageSystemBlockEntity) world.getBlockEntity(pos);
        check(host != null, "Cannot place fixture host");
        var state = host.saveWithFullMetadata();
        state.remove("infiniteComponentInventory");
        var component = BuiltInRegistries.ITEM.get(new ResourceLocation("neoecoae", "eco_infinite_cell_component"));
        CompoundTag legacy = new CompoundTag();
        var items = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new ItemStack(component, 64).save(new CompoundTag());
        entry.putByte("Slot", (byte) 0);
        items.add(entry);
        legacy.put("Items", items);
        state.put("infiniteComponentSlot", legacy);
        host.load(state);
        if (!placed.isEmpty()) host.getBlockState().getBlock().setPlacedBy(world, pos, host.getBlockState(), null, placed);
        var cluster = new NEStorageCluster(pos, pos);
        for (ECODriveBlockEntity drive : drives) cluster.addBlockEntity(drive);
        cluster.addBlockEntity(host);
        for (ECODriveBlockEntity drive : drives) drive.updateCluster(cluster);
        host.updateCluster(cluster);
        cluster.updateFormed(true);
        return host;
    }

    private static void run(MinecraftServer server, String phase) throws Exception {
        var world = server.overworld();
        var root = server.getWorldPath(LevelResource.ROOT);
        if (phase.equals("recovery") || phase.equals("recovery-reload")) {
            check(root.toAbsolutePath().normalize().getFileName().toString().startsWith("saveddata-recovery-"),
                "Use an isolated recovery world");
            if (phase.equals("recovery")) {
                verifyForcedRecovery(world, root);
            } else {
                var fixture = NbtIo.readCompressed(root.resolve("controller-relocation.dat").toFile());
                UUID id = fixture.getUUID("domain");
                var stale = recoveryHost(world, 168, ItemStack.of(fixture.getCompound("stale")), recoveryDrives(world, 168));
                stale.tick(world, stale.getBlockPos(), stale.getBlockState());
                check(!stale.canUseHostDomainStorage() && ECOInfiniteStorageDomains.mountedHosts(server, id) == 0,
                    "Restart revived a stale controller claim");
                var current = recoveryHost(world, 184, ItemStack.of(fixture.getCompound("current")), recoveryDrives(world, 184));
                current.tick(world, current.getBlockPos(), current.getBlockState());
                var view = new java.util.concurrent.atomic.AtomicReference<appeng.api.storage.MEStorage>();
                current.mountInventories((storage, priority) -> view.set(storage));
                check(id.toString().equals(current.getInfiniteDomainText()) && current.canUseHostDomainStorage()
                    && view.get().getAvailableStacks().get(ECOKeyFactory.item(Items.DIAMOND)) == 100,
                    "Restart lost the renewed claim or the exact domain contents");
            }
            return;
        }
        var fixturePath = root.resolve("saveddata-acceptance-fixture.dat");
        AEKey diamond = ECOKeyFactory.item(Items.DIAMOND), iron = ECOKeyFactory.item(Items.IRON_INGOT);
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
        var method = cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator.class.getDeclaredMethod(
            "transfer", ECOStorageRepository.Session.class,
            cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData.class, UUID.class,
            SavedDataInfiniteStorageEngine.class, UUID.class, java.util.function.Consumer.class);
        method.setAccessible(true);
        java.util.function.Consumer<cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator.Phase> interrupt = phase -> {
            if (phase == cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator.Phase.DESTINATION_SAVED)
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
