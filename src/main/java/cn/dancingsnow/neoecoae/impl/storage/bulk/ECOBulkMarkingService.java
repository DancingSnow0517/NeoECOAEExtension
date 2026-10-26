package cn.dancingsnow.neoecoae.impl.storage.bulk;

import appeng.api.config.Actionable;
import appeng.api.storage.MEStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageExtractionExclusions;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MarkResult;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.Status;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Writes host-wide compression-chain filters into ECO bulk cells. */
public final class ECOBulkMarkingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOBulkMarkingService.class);

    private ECOBulkMarkingService() {
    }

    public static boolean hasBulkCell(ECOStorageSystemBlockEntity host) {
        for (ECODriveBlockEntity drive : host.getStorageDrivesForIntegration()) {
            ItemStack stack = drive.getCellStack();
            if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ECOBulkStorageCellItem) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack normalizeMarker(ItemStack stack) {
        AEItemKey key = stack == null || stack.isEmpty() ? null : AEItemKey.of(stack);
        return key != null ? stack.copyWithCount(1) : ItemStack.EMPTY;
    }

    public static boolean isSameMarkerChain(ItemStack left, ItemStack right) {
        AEItemKey leftKey = left == null || left.isEmpty() ? null : AEItemKey.of(left);
        AEItemKey rightKey = right == null || right.isEmpty() ? null : AEItemKey.of(right);
        if (leftKey == null || rightKey == null) {
            return false;
        }
        if (leftKey.equals(rightKey)) {
            return true;
        }
        BulkUnits leftChain = ECOCompressionService.units(leftKey);
        return leftChain.compressible() && leftChain.equals(ECOCompressionService.units(rightKey));
    }

    public static MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold, boolean migrate) {
        if (threshold < 0L) {
            return result(Status.INVALID_THRESHOLD);
        }
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread()) {
            return result(Status.BUSY);
        }
        if (host.isInfiniteMode()) {
            return result(Status.BUSY);
        }

        List<ECODriveBlockEntity> drives = host.getStorageDrivesForIntegration();
        List<TargetCell> targets = collectTargetCells(host, drives);
        if (targets.isEmpty()) {
            return result(Status.NO_BULK_CELL);
        }
        var grid = host.getMainNode().getGrid();
        Set<AEKey> unboundedKeys = new HashSet<>();
        if (grid != null) unboundedKeys.addAll(ECOPlannerInventory.collectUnboundedKeys(grid));
        collectUnboundedKeys(drives, unboundedKeys);
        // Mark-only is the convenience scan governed by the configured threshold. Once the
        // player explicitly requests migration, include every positive-stock recognised chain;
        // otherwise a small but valid stock (for example 512 copper ingots) is silently skipped.
        List<Candidate> candidates = collectCandidates(host, migrate ? 0L : threshold);
        return markCandidates(host, drives, targets, candidates, migrate, unboundedKeys);
    }

    /** Marks candidates into one selected bulk cell and immediately transfers all matching chains. */
    public static MarkResult markAndMigrateCell(
        ECOStorageSystemBlockEntity host, BlockPos position, long threshold
    ) {
        if (threshold < 0L) return result(Status.INVALID_THRESHOLD);
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread()) return result(Status.BUSY);
        if (host.isInfiniteMode()) return result(Status.BUSY);

        List<ECODriveBlockEntity> drives = host.getStorageDrivesForIntegration();
        TargetCell selected = findTargetCell(host, position, drives);
        if (selected == null) return result(Status.NO_BULK_CELL);

        var grid = host.getMainNode().getGrid();
        Set<AEKey> unboundedKeys = new HashSet<>();
        if (grid != null) unboundedKeys.addAll(ECOPlannerInventory.collectUnboundedKeys(grid));
        collectUnboundedKeys(drives, unboundedKeys);
        return markCandidates(host, drives, List.of(selected),
            collectCandidates(host, threshold), true, unboundedKeys);
    }

    /**
     * Moves the resources matching one already configured bulk cell immediately. The cell's
     * filters are authoritative; this operation never creates or changes marker slots.
     */
    public static cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MigrationResult migrateMarkedCell(
        ECOStorageSystemBlockEntity host, BlockPos position
    ) {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread()) {
            return new cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MigrationResult(
                Status.BUSY, 0L);
        }
        if (host.isInfiniteMode()) {
            return new cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MigrationResult(
                Status.BUSY, 0L);
        }
        List<ECODriveBlockEntity> drives = host.getStorageDrivesForIntegration();
        TargetCell selected = findTargetCell(host, position, drives);
        if (selected == null) {
            return new cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MigrationResult(
                Status.NO_BULK_CELL, 0L);
        }
        Set<AEKey> unboundedKeys = new HashSet<>();
        var grid = host.getMainNode().getGrid();
        if (grid != null) unboundedKeys.addAll(ECOPlannerInventory.collectUnboundedKeys(grid));
        collectUnboundedKeys(drives, unboundedKeys);
        long transferred = transferMarkedChains(host, drives, List.of(selected), unboundedKeys);
        if (transferred > 0L) host.notifyStorageConfigurationChanged();
        return new cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration.MigrationResult(
            Status.SUCCESS, transferred);
    }

    /** Read-only preview: probes the same sources and filters without moving any resources. */
    public static List<ItemStack> previewMarkedCell(ECOStorageSystemBlockEntity host, BlockPos position) {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread() || host.isInfiniteMode()) return List.of();
        var drives = host.getStorageDrivesForIntegration();
        TargetCell selected = findTargetCell(host, position, drives);
        if (selected == null) return List.of();
        Set<AEKey> unboundedKeys = new HashSet<>();
        var grid = host.getMainNode().getGrid();
        if (grid != null) unboundedKeys.addAll(ECOPlannerInventory.collectUnboundedKeys(grid));
        collectUnboundedKeys(drives, unboundedKeys);
        Set<AEItemKey> items = new HashSet<>();
        IActionSource source = IActionSource.ofMachine(host);
        for (var drive : drives) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell inventory = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || stack.getItem() instanceof ECOBulkStorageCellItem
                || host.isInfiniteMemberCell(stack) || inventory == null
                || host.getTier().compareTo(inventory.getTier()) < 0) continue;
            collectPreviewItems(inventory, selected.storage(), source, unboundedKeys, items);
        }
        if (grid != null) {
            try (var ignored = StorageExtractionExclusions.open(List.of(selected.storage()))) {
                collectPreviewItems(grid.getStorageService().getInventory(), selected.storage(), source,
                    unboundedKeys, items);
            }
        }
        return items.stream().sorted(Comparator.comparing(key -> key.getId().toString()))
            .map(AEItemKey::toStack).toList();
    }

    /** Read-only preview for the selected-cell mark-and-migrate operation. */
    public static List<ItemStack> previewMarkAndMigrateCell(
        ECOStorageSystemBlockEntity host, BlockPos position, long threshold
    ) {
        if (threshold < 0L || !(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread() || host.isInfiniteMode()) return List.of();
        List<ECODriveBlockEntity> drives = host.getStorageDrivesForIntegration();
        TargetCell selected = findTargetCell(host, position, drives);
        if (selected == null) return List.of();

        Set<AEKey> unboundedKeys = new HashSet<>();
        var grid = host.getMainNode().getGrid();
        if (grid != null) unboundedKeys.addAll(ECOPlannerInventory.collectUnboundedKeys(grid));
        collectUnboundedKeys(drives, unboundedKeys);
        Set<AEItemKey> items = new HashSet<>();
        IActionSource source = IActionSource.ofMachine(host);
        for (var drive : drives) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell inventory = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || stack.getItem() instanceof ECOBulkStorageCellItem
                || host.isInfiniteMemberCell(stack) || inventory == null
                || host.getTier().compareTo(inventory.getTier()) < 0) continue;
            collectPreviewItems(inventory, selected.storage(), source, unboundedKeys, items);
        }
        if (grid != null) {
            try (var ignored = StorageExtractionExclusions.open(List.of(selected.storage()))) {
                collectPreviewItems(grid.getStorageService().getInventory(), selected.storage(), source,
                    unboundedKeys, items);
            }
        }

        int freeSlots = freeSlotCount(selected);
        List<AEItemKey> occupied = new ArrayList<>(selected.storage().getEffectiveConfiguredFilters());
        for (Candidate candidate : collectCandidates(host, threshold)) {
            if (freeSlots <= 0) break;
            if (occupied.stream().anyMatch(existing -> sameMarker(existing, candidate.key()))) continue;
            items.add(candidate.key());
            occupied.add(candidate.key());
            freeSlots--;
        }
        return items.stream().sorted(Comparator.comparing(key -> key.getId().toString()))
            .map(AEItemKey::toStack).toList();
    }

    private static List<Candidate> collectCandidates(
        ECOStorageSystemBlockEntity host, long threshold
    ) {
        var grid = host.getMainNode().getGrid();
        KeyCounter available = host.collectLocalStorageStacksForIntegration();
        if (grid != null && !host.isStorageInterfaceTransferMode()) {
            available = new KeyCounter();
            grid.getStorageService().getInventory().getAvailableStacks(available);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            // Unbounded sources may still be marked so the player can see/use the chain. They
            // are filtered only from physical migration below; explicit decompression inserts
            // into the bulk cell continue to work normally.
            if (entry.getLongValue() <= threshold || !(entry.getKey() instanceof AEItemKey itemKey)) continue;
            BulkUnits chain = ECOCompressionService.units(itemKey);
            if (chain.compressible()) candidates.add(new Candidate(itemKey, entry.getLongValue(), chain));
        }
        candidates.sort(Comparator.comparingLong(Candidate::amount).reversed());
        return candidates;
    }

    private static int freeSlotCount(TargetCell target) {
        var config = ((ECOBulkStorageCellItem) target.drive().getCellStack().getItem())
            .getConfigInventory(target.drive().getCellStack());
        int free = 0;
        for (int slot = 0; slot < config.size(); slot++) if (config.getKey(slot) == null) free++;
        return free;
    }

    private static void collectPreviewItems(MEStorage from, ECOBulkStorageCell to, IActionSource source,
        Set<AEKey> unboundedKeys, Set<AEItemKey> items) {
        KeyCounter available = new KeyCounter();
        from.getAvailableStacks(available);
        for (var entry : available) {
            if (entry.getLongValue() <= 0L || !(entry.getKey() instanceof AEItemKey key)
                || unboundedKeys.contains(key) || items.contains(key)) continue;
            if (to.insert(key, 1L, Actionable.SIMULATE, source) > 0L
                && from.extract(key, 1L, Actionable.SIMULATE, source) > 0L) items.add(key);
        }
    }

    private static TargetCell findTargetCell(ECOStorageSystemBlockEntity host, BlockPos position,
        List<ECODriveBlockEntity> drives) {
        for (ECODriveBlockEntity drive : drives) {
            if (!drive.getBlockPos().equals(position)) continue;
            ItemStack stack = drive.getCellStack();
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ECOBulkStorageCellItem
                && drive.getCellInventory() instanceof ECOBulkStorageCell bulk
                && host.getTier().compareTo(bulk.getTier()) >= 0) {
                return new TargetCell(drive, bulk);
            }
            break;
        }
        return null;
    }

    private static void collectUnboundedKeys(List<ECODriveBlockEntity> drives, Set<AEKey> target) {
        for (ECODriveBlockEntity drive : drives) {
            var cell = drive.getCellInventory();
            if (cell instanceof ECOCreativeCell creative) target.addAll(creative.configuredKeys());
            if (cell instanceof ExactAmountSource source) {
                source.neoecoae$visitExactAmounts((key, amount) -> {
                    if (amount.infinite()) target.add(key);
                });
            }
        }
    }

    private static List<TargetCell> collectTargetCells(
        ECOStorageSystemBlockEntity host,
        List<ECODriveBlockEntity> drives
    ) {
        List<TargetCell> result = new ArrayList<>();
        drives.stream()
            .sorted(Comparator.comparingLong(drive -> drive.getBlockPos().asLong()))
            .forEach(drive -> {
                ItemStack stack = drive.getCellStack();
                if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof ECOBulkStorageCellItem)) {
                    return;
                }
                IECOStorageCell inventory = drive.getCellInventory();
                if (!(inventory instanceof ECOBulkStorageCell bulkStorage)
                    || host.getTier().compareTo(bulkStorage.getTier()) < 0) {
                    return;
                }
                result.add(new TargetCell(drive, bulkStorage));
            });
        return result;
    }

    private static MarkResult markCandidates(
        ECOStorageSystemBlockEntity host,
        List<ECODriveBlockEntity> drives,
        List<TargetCell> targets,
        List<Candidate> rawCandidates,
        boolean migrate,
        Set<AEKey> unboundedKeys
    ) {
        List<AEItemKey> occupiedMarkers = new ArrayList<>();
        for (TargetCell target : targets) {
            for (AEItemKey itemKey : target.storage().getEffectiveConfiguredFilters()) {
                if (occupiedMarkers.stream().noneMatch(existing -> sameMarker(existing, itemKey))) {
                    occupiedMarkers.add(itemKey);
                }
            }
        }
        List<SlotTarget> freeSlots = new ArrayList<>();
        for (TargetCell target : targets) {
            var config = ((ECOBulkStorageCellItem) target.drive().getCellStack().getItem())
                .getConfigInventory(target.drive().getCellStack());
            for (int slot = 0; slot < config.size(); slot++) {
                if (config.getKey(slot) == null) {
                    freeSlots.add(new SlotTarget(target, slot));
                }
            }
        }

        List<Candidate> uniqueCandidates = new ArrayList<>();
        for (Candidate candidate : rawCandidates) {
            if (uniqueCandidates.stream().noneMatch(
                existing -> sameChain(existing.chain(), candidate.chain()))) {
                uniqueCandidates.add(candidate);
            }
        }

        List<Candidate> accepted = new ArrayList<>();
        int alreadyMarked = 0;
        for (Candidate candidate : uniqueCandidates) {
            if (occupiedMarkers.stream().anyMatch(existing -> sameMarker(existing, candidate.key()))) {
                alreadyMarked++;
                continue;
            }
            accepted.add(candidate);
        }

        int count = Math.min(freeSlots.size(), accepted.size());
        for (int index = 0; index < count; index++) {
            SlotTarget slotTarget = freeSlots.get(index);
            ItemStack cellStack = slotTarget.target().drive().getCellStack();
            var cellItem = (ECOBulkStorageCellItem) cellStack.getItem();
            cellItem.getConfigInventory(cellStack).setStack(
                slotTarget.slot(), new GenericStack(accepted.get(index).key(), 0L));
            slotTarget.target().drive().onCellConfigurationChanged();
        }

        long transferred = migrate ? transferMarkedChains(host, drives, targets, unboundedKeys) : 0L;
        if (count > 0 || transferred > 0L) host.notifyStorageConfigurationChanged();
        return new MarkResult(Status.SUCCESS, count, alreadyMarked, 0, accepted.size() - count, transferred);
    }

    private static long transferMarkedChains(
        ECOStorageSystemBlockEntity host,
        List<ECODriveBlockEntity> drives,
        List<TargetCell> targets,
        Set<AEKey> unboundedKeys
    ) {
        List<ChainTarget> chainTargets = collectChainTargets(targets);
        if (chainTargets.isEmpty()) return 0L;

        IActionSource actionSource = IActionSource.ofMachine(host);
        long transferred = 0L;
        try (ECOCellMutationBatch ignored = ECOCellMutationBatch.open()) {
            for (ECODriveBlockEntity drive : drives.stream()
                .sorted(Comparator.comparingLong(value -> value.getBlockPos().asLong()))
                .toList()) {
                ItemStack stack = drive.getCellStack();
                if (stack == null || stack.isEmpty()
                    || stack.getItem() instanceof ECOBulkStorageCellItem
                    || host.isInfiniteMemberCell(stack)) {
                    continue;
                }
                IECOStorageCell sourceStorage = drive.getCellInventory();
                if (sourceStorage == null || host.getTier().compareTo(sourceStorage.getTier()) < 0) {
                    continue;
                }

                KeyCounter available = new KeyCounter();
                sourceStorage.getAvailableStacks(available);
                for (Object2LongMap.Entry<AEKey> entry : available) {
                    if (entry.getLongValue() <= 0L || unboundedKeys.contains(entry.getKey())
                        || !(entry.getKey() instanceof AEItemKey itemKey)) {
                        continue;
                    }
                    for (ChainTarget target : chainTargets) {
                        if (sameMarker(target.marker(), itemKey)) {
                            // One misbehaving inventory must not abandon the remaining chains.
                            try {
                                transferred = saturatingAdd(transferred, transfer(
                                    sourceStorage, target.storage(), itemKey, entry.getLongValue(), actionSource));
                            } catch (RuntimeException failure) {
                                LOGGER.error("Bulk migration failed for {} from drive {}",
                                    itemKey, drive.getBlockPos(), failure);
                            }
                        }
                    }
                }
            }
        }
        var grid = host.getMainNode().getGrid();
        if (grid != null) {
            var network = grid.getStorageService().getInventory();
            KeyCounter available = new KeyCounter();
            network.getAvailableStacks(available);
            try (var ignored = StorageExtractionExclusions.open(
                    targets.stream().map(TargetCell::storage).toList());
                 var batch = ECOCellMutationBatch.open()) {
                for (var entry : available) {
                    if (entry.getLongValue() <= 0L || unboundedKeys.contains(entry.getKey())
                        || !(entry.getKey() instanceof AEItemKey key)) continue;
                    for (ChainTarget target : chainTargets) {
                        if (sameMarker(target.marker(), key)) {
                            try {
                                transferred = saturatingAdd(transferred, transfer(
                                    network, target.storage(), key, entry.getLongValue(), actionSource));
                            } catch (RuntimeException failure) {
                                LOGGER.error("Bulk migration failed for {} from the network", key, failure);
                            }
                        }
                    }
                }
            }
        }
        return transferred;
    }

    private static List<ChainTarget> collectChainTargets(List<TargetCell> targets) {
        List<ChainTarget> result = new ArrayList<>();
        for (TargetCell target : targets) {
            for (AEItemKey itemKey : target.storage().getEffectiveConfiguredFilters()) {
                BulkUnits chain = ECOCompressionService.units(itemKey);
                if (result.stream().noneMatch(existing -> existing.storage() == target.storage()
                        && sameMarker(existing.marker(), itemKey))) {
                    result.add(new ChainTarget(itemKey, chain, target.storage()));
                }
            }
        }
        return result;
    }

    /**
     * Moves as much of {@code amount} as the destination accepts. The destination is filled
     * before the source is drained so that a short source extraction rolls back inside the
     * destination, where the vacated capacity is guaranteed, instead of inserting into an
     * aggregate source that may answer from a different inventory than it was drained from.
     */
    private static long transfer(
        MEStorage from,
        ECOBulkStorageCell to,
        AEItemKey key,
        long amount,
        IActionSource actionSource
    ) {
        if (amount <= 0L) return 0L;
        long available = from.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (available <= 0L) return 0L;
        long accepted = to.insert(key, Math.min(available, amount), Actionable.SIMULATE, actionSource);
        if (accepted <= 0L) return 0L;

        long inserted = acknowledge(to.insert(key, accepted, Actionable.MODULATE, actionSource), accepted);
        if (inserted <= 0L) return 0L;
        long extracted = acknowledge(from.extract(key, inserted, Actionable.MODULATE, actionSource), inserted);
        if (extracted < inserted) {
            long remainder = inserted - extracted;
            long restored = acknowledge(to.extract(key, remainder, Actionable.MODULATE, actionSource), remainder);
            if (restored != remainder) {
                LOGGER.error("Bulk migration could not return {} of {} {} to the source",
                    remainder - restored, remainder, key);
            }
        }
        return extracted;
    }

    /** Clamps an inventory acknowledgement instead of aborting a batch that already moved items. */
    private static long acknowledge(long ack, long requested) {
        if (ack < 0L) {
            LOGGER.error("Storage inventory acknowledged {} of {} moved units", ack, requested);
            return 0L;
        }
        if (ack > requested) {
            LOGGER.error("Storage inventory acknowledged {} of {} moved units", ack, requested);
            return requested;
        }
        return ack;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static boolean sameChain(BulkUnits first, BulkUnits second) {
        return first.compressible() && first.equals(second);
    }

    private static boolean sameMarker(AEItemKey first, AEItemKey second) {
        if (first.equals(second)) return true;
        BulkUnits firstChain = ECOCompressionService.units(first);
        return firstChain.compressible() && firstChain.equals(ECOCompressionService.units(second));
    }

    private static MarkResult result(Status status) {
        return new MarkResult(status, 0, 0, 0, 0, 0L);
    }

    private record TargetCell(
        ECODriveBlockEntity drive,
        ECOBulkStorageCell storage
    ) {
    }

    private record SlotTarget(TargetCell target, int slot) {
    }

    private record ChainTarget(AEItemKey marker, BulkUnits chain, ECOBulkStorageCell storage) {
    }

    private record Candidate(AEItemKey key, long amount, BulkUnits chain) {
    }
}
