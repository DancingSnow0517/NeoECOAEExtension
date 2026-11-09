package cn.dancingsnow.neoecoae.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.platform.nbt.NEAtomicNbtFile;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts;
import cn.dancingsnow.neoecoae.storage.persistence.StorageTransferCoordinator;
import cn.dancingsnow.neoecoae.storage.persistence.StorageTransferData;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Experimental whole-batch import. Work scales with resource types, never with their quantities. */
public final class GTLFastInfinityTransfer {
    private static final String BEFORE = "gtlSourceBefore";

    private GTLFastInfinityTransfer() {}

    /** Implemented only by GTL's physical fast cell, not by network aggregates or creative cells. */
    public interface Source extends MEStorage {
        StorageTransferData neoecoae$transferJournal();
        StorageAmounts neoecoae$physicalContents();
        void neoecoae$retireContents(Set<AEKey> keys);
        void neoecoae$flushContents();

        default boolean neoecoae$sealed() {
            var journal = neoecoae$transferJournal();
            return journal != null && (!journal.readable() || journal.writeFailure() != null
                    || journal.transaction() != null && !journal.completed());
        }
    }

    public static StorageTransferData journal(MinecraftServer server, ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().hasUUID("diskuuid")) return null;
        UUID id = UUID.nameUUIDFromBytes(("neoecoae:gtl_fast:" + stack.getTag().getUUID("diskuuid"))
                .getBytes(StandardCharsets.UTF_8));
        return StorageTransferCoordinator.journal(ECOStorageRepository.session(server), id, true);
    }

    /** Decode GTL's signed high word and unsigned low word without saturating through long or double. */
    public static BigInteger amount(Object counter) {
        try {
            long high = (long) counter.getClass().getMethod("getHigh").invoke(counter);
            long low = (long) counter.getClass().getMethod("getLow").invoke(counter);
            BigInteger amount = new BigInteger(ByteBuffer.allocate(16).putLong(high).putLong(low).array());
            if (amount.signum() < 0) throw new IllegalStateException("Negative GTL fast-cell quantity");
            return amount;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported GTL Int128 API", failure);
        }
    }

    /** GTL's StorageManager is vanilla SavedData named disk_manager in the overworld. */
    public static void flushSource(MinecraftServer server) {
        try {
            Object manager = Class.forName("org.gtlcore.gtlcore.GTLCore").getField("STORAGE_INSTANCE").get(null);
            if (!(manager instanceof SavedData data)) throw new IllegalStateException("GTL disk manager unavailable");
            CompoundTag root = new CompoundTag();
            root.put("data", data.save(new CompoundTag()));
            NbtUtils.addCurrentDataVersion(root);
            NEAtomicNbtFile.write(root, ECOStorageRepository.session(server).directory().resolve("disk_manager.dat"));
            data.setDirty(false);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("Could not durably save GTL disks; transfer remains sealed", failure);
        }
    }

    public static long transfer(MEStorage network, UUID domain, SavedDataInfiniteStorageEngine target,
            boolean excludeBuses, IActionSource action, Predicate<AEKey> filter) {
        long moved = 0;
        for (Candidate candidate : candidates(network, excludeBuses)) {
            long accepted = transfer(candidate.cell, domain, target, key -> filter.test(key)
                    && candidate.guards.stream().allMatch(guard ->
                        guard.extract(key, 1, Actionable.SIMULATE, action) == 1));
            moved = accepted > Long.MAX_VALUE - moved ? Long.MAX_VALUE : moved + accepted;
        }
        return moved;
    }

    /** A sealed transaction resumes its saved selection even if the interface filter has since changed. */
    static long transfer(Source cell, UUID domain, SavedDataInfiniteStorageEngine target, Predicate<AEKey> filter) {
        StorageTransferData journal = cell.neoecoae$transferJournal();
        if (journal == null) return 0;
        if (!journal.readable()) throw new IllegalStateException("Unreadable GTL transfer journal; disk remains sealed");
        if (journal.completed() && domain.equals(journal.domain())) {
            // A failed final journal write may have left the destination locked in this server session.
            if (!journal.flush()) return 0;
            target.endTransfer(journal.transaction());
        }
        if (journal.completed() && journal.writeFailure() != null) return 0;
        boolean pending = journal.transaction() != null && !journal.completed();
        if (pending && !domain.equals(journal.domain())) return 0;
        if (!target.isHealthy() || target.hasPendingRestore()) return 0;
        if (!pending) {
            if (!target.canUseMountedStorage()) return 0;
            StorageAmounts contents = cell.neoecoae$physicalContents();
            ECOCellSavedData selected = new ECOCellSavedData(journal.source());
            contents.forEach((key, amount) -> {
                if (filter.test(key)) selected.amounts().set(key, amount);
            });
            if (selected.amounts().isEmpty()) return 0;
            ECOCellSavedData baseline = new ECOCellSavedData(journal.source());
            contents.forEach((key, amount) -> baseline.amounts().set(key, amount));
            CompoundTag metadata = new CompoundTag();
            metadata.put(BEFORE, baseline.snapshot());
            selected.metadata(metadata);
            cell.neoecoae$flushContents();
            journal.prepare(UUID.randomUUID(), domain, selected);
        }
        // Persist the immutable selection before importing anything. GTL mutation hooks seal the source.
        if (!journal.flush()) return 0;
        ECOCellSavedData selected = ECOCellSavedData.load(journal.sourceSnapshot(), journal.source());
        ECOCellSavedData before = ECOCellSavedData.load(selected.metadata().getCompound(BEFORE), journal.source());
        if (!selected.canMutate() || !before.canMutate()) throw new IllegalStateException("Invalid GTL transfer snapshot");
        selected.amounts().forEach((key, amount) -> {
            if (!amount.equals(before.amounts().get(key)))
                throw new IllegalStateException("GTL transfer selection differs from its source snapshot");
        });
        StorageAmounts after = new StorageAmounts();
        before.amounts().forEach((key, amount) -> {
            if (selected.amounts().get(key).isZero()) after.set(key, amount);
        });
        StorageAmounts current = cell.neoecoae$physicalContents();
        boolean alreadyRetired = current.sameContents(after);
        boolean sourceChanged = !current.sameContents(before.amounts());
        // A runtime failure during native per-key cache updates may have retired only a subset.
        StorageAmounts expectedCurrent = new StorageAmounts();
        before.amounts().forEach((key, amount) -> {
            if (selected.amounts().get(key).isZero() || !current.get(key).isZero()) expectedCurrent.set(key, amount);
        });
        if (!current.sameContents(expectedCurrent))
            throw new IllegalStateException("Sealed GTL disk differs from its saved transfer snapshot");
        if (sourceChanged && !target.hasMigrationReceipt(journal.transaction()))
            throw new IllegalStateException("Retired GTL disk has no destination receipt");
        target.beginTransfer(journal.transaction());
        if (!target.importOnce(journal.transaction(), selected.amounts()) || !target.flush()) return 0;
        if (!alreadyRetired) {
            Set<AEKey> remaining = new java.util.HashSet<>(selected.amounts().visibleAmounts().keySet());
            remaining.removeIf(key -> current.get(key).isZero());
            cell.neoecoae$retireContents(Set.copyOf(remaining));
            if (!cell.neoecoae$physicalContents().sameContents(after))
                throw new IllegalStateException("GTL disk did not retire the selected contents");
        }
        cell.neoecoae$flushContents();
        journal.complete();
        if (!journal.flush()) return 0;
        target.endTransfer(journal.transaction());
        return selected.amounts().totalExact().min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    private record Candidate(Source cell, List<MEStorage> guards) {}

    private static List<Candidate> candidates(MEStorage network, boolean excludeBuses) {
        List<Candidate> result = new ArrayList<>();
        Set<MEStorage> path = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Source> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        visit(network, excludeBuses, List.of(), path, seen, result);
        return result;
    }

    private static void visit(MEStorage inventory, boolean excludeBuses, List<MEStorage> guards,
            Set<MEStorage> path, Set<Source> seen, List<Candidate> result) {
        if (inventory == null || path.size() >= 64 || !path.add(inventory)) return;
        try {
            if (StorageExtractionExclusions.contains(inventory)
                    || excludeBuses && inventory instanceof StorageBusTransferFilter.StorageBusInventory) return;
            if (inventory instanceof Source cell) {
                if (seen.add(cell)) result.add(new Candidate(cell, guards));
            } else if (inventory instanceof NetworkInventoryCache.Mounts mounts) {
                List<MEStorage> children = new ArrayList<>();
                // Copy under the mount lock; extraction simulations must run after it is released.
                mounts.neoecoae$withMounts(children::addAll);
                for (MEStorage child : children) visit(child, excludeBuses, guards, path, seen, result);
            } else if (inventory instanceof DelegatingMEInventoryAccessor wrapper) {
                var next = new ArrayList<>(guards);
                next.add(inventory);
                visit(wrapper.neoecoae$getDelegate(), excludeBuses, List.copyOf(next), path, seen, result);
            } else {
                MEStorage resolved = TerminalInventorySnapshot.resolve(inventory);
                if (resolved != inventory) visit(resolved, excludeBuses, guards, path, seen, result);
            }
        } finally { path.remove(inventory); }
    }
}
