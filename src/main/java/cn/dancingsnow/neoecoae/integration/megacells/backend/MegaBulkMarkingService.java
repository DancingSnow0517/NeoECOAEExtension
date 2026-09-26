package cn.dancingsnow.neoecoae.integration.megacells.backend;

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
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaLongBulkStorageCellItem;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Writes host-wide compression-chain filters into ECO MEGA long bulk cells. */
public final class MegaBulkMarkingService {
    private MegaBulkMarkingService() {
    }

    public static boolean hasBulkCell(ECOStorageSystemBlockEntity host) {
        for (ECODriveBlockEntity drive : host.getStorageDrivesForIntegration()) {
            ItemStack stack = drive.getCellStack();
            if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ECOMegaLongBulkStorageCellItem) {
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
        CompressionChain leftChain = CompressionService.getChain(leftKey);
        return !leftChain.isEmpty() && leftChain.equals(CompressionService.getChain(rightKey));
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
            if (entry.getLongValue() <= threshold || !(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            CompressionChain chain = CompressionService.getChain(itemKey);
            if (!chain.isEmpty()) {
                candidates.add(new Candidate(itemKey, entry.getLongValue(), chain));
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::amount).reversed());
        return markCandidates(host, drives, targets, candidates, migrate, unboundedKeys);
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
                    || !(stack.getItem() instanceof ECOMegaLongBulkStorageCellItem)) {
                    return;
                }
                IECOStorageCell inventory = drive.getCellInventory();
                if (!(inventory instanceof ECOMegaLongBulkStorageCell bulkStorage)
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
            var config = ((ECOMegaLongBulkStorageCellItem) target.drive().getCellStack().getItem())
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
            var cellItem = (ECOMegaLongBulkStorageCellItem) cellStack.getItem();
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
                    || stack.getItem() instanceof ECOMegaLongBulkStorageCellItem
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
                            transferred = saturatingAdd(transferred, transfer(
                                sourceStorage, target.storage(), itemKey, entry.getLongValue(), actionSource));
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
                            transferred = saturatingAdd(transferred, transfer(
                                network, target.storage(), key, entry.getLongValue(), actionSource));
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
                CompressionChain chain = CompressionService.getChain(itemKey);
                if (result.stream().noneMatch(existing -> existing.storage() == target.storage()
                        && sameMarker(existing.marker(), itemKey))) {
                    result.add(new ChainTarget(itemKey, chain, target.storage()));
                }
            }
        }
        return result;
    }

    private static long transfer(
        MEStorage from,
        ECOMegaLongBulkStorageCell to,
        AEItemKey key,
        long amount,
        IActionSource actionSource
    ) {
        long available = from.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (available <= 0L) return 0L;
        long accepted = to.insert(key, available, Actionable.SIMULATE, actionSource);
        if (accepted <= 0L) return 0L;

        long extracted = from.extract(key, accepted, Actionable.MODULATE, actionSource);
        if (extracted < 0L || extracted > accepted) {
            throw new IllegalStateException("Invalid internal-transfer source acknowledgement");
        }
        long inserted = to.insert(key, extracted, Actionable.MODULATE, actionSource);
        if (inserted < 0L || inserted > extracted) {
            throw new IllegalStateException("Invalid internal-transfer destination acknowledgement");
        }
        if (inserted < extracted) {
            long remainder = extracted - inserted;
            long restored = from.insert(key, remainder, Actionable.MODULATE, actionSource);
            if (restored != remainder) {
                throw new IllegalStateException(
                    "Internal-transfer rollback incomplete: " + restored + "/" + remainder);
            }
        }
        return inserted;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static boolean sameChain(CompressionChain first, CompressionChain second) {
        return !first.isEmpty() && first.equals(second);
    }

    private static boolean sameMarker(AEItemKey first, AEItemKey second) {
        if (first.equals(second)) return true;
        CompressionChain firstChain = CompressionService.getChain(first);
        return !firstChain.isEmpty() && firstChain.equals(CompressionService.getChain(second));
    }

    private static MarkResult result(Status status) {
        return new MarkResult(status, 0, 0, 0, 0, 0L);
    }

    private record TargetCell(
        ECODriveBlockEntity drive,
        ECOMegaLongBulkStorageCell storage
    ) {
    }

    private record SlotTarget(TargetCell target, int slot) {
    }

    private record ChainTarget(AEItemKey marker, CompressionChain chain, ECOMegaLongBulkStorageCell storage) {
    }

    private record Candidate(AEItemKey key, long amount, CompressionChain chain) {
    }
}
