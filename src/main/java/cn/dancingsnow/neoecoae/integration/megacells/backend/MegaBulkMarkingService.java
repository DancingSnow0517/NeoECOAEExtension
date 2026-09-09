package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
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
import java.util.HashSet;
import java.util.List;
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

    public static MarkResult autoMark(ECOStorageSystemBlockEntity host, long threshold) {
        if (threshold < 0L) {
            return result(Status.INVALID_THRESHOLD);
        }
        if (!(host.getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread()) {
            return result(Status.BUSY);
        }
        if (host.isFiniteTransferDomainLocked() || host.isInfiniteMode()) {
            return result(Status.BUSY);
        }

        List<ECODriveBlockEntity> drives = host.getStorageDrivesForIntegration();
        List<TargetCell> targets = collectTargetCells(host, drives);
        if (targets.isEmpty()) {
            return result(Status.NO_BULK_CELL);
        }

        KeyCounter available = host.collectLocalStorageStacksForIntegration();
        List<Candidate> candidates = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getLongValue() <= threshold || !(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            CompressionChain chain = CompressionService.getChain(itemKey);
            if (!chain.isEmpty()) {
                candidates.add(new Candidate(itemKey, entry.getLongValue(), chain));
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::amount).reversed());
        return markCandidates(host, drives, targets, candidates);
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
                    || !(stack.getItem() instanceof ECOMegaLongBulkStorageCellItem cellItem)) {
                    return;
                }
                IECOStorageCell inventory = drive.getCellInventory();
                if (!(inventory instanceof ECOMegaLongBulkStorageCell bulkStorage)
                    || host.getTier().compareTo(bulkStorage.getTier()) < 0) {
                    return;
                }
                result.add(new TargetCell(drive, cellItem.getConfigInventory(stack), bulkStorage));
            });
        return result;
    }

    private static MarkResult markCandidates(
        ECOStorageSystemBlockEntity host,
        List<ECODriveBlockEntity> drives,
        List<TargetCell> targets,
        List<Candidate> rawCandidates
    ) {
        List<CompressionChain> occupiedChains = new ArrayList<>();
        List<SlotRef> freeSlots = new ArrayList<>();
        for (TargetCell target : targets) {
            ConfigInventory config = target.config();
            for (int slot = 0; slot < config.size(); slot++) {
                AEKey existing = config.getKey(slot);
                if (existing instanceof AEItemKey itemKey) {
                    CompressionChain chain = CompressionService.getChain(itemKey);
                    if (!chain.isEmpty() && !containsChain(occupiedChains, chain)) {
                        occupiedChains.add(chain);
                    }
                } else if (existing == null) {
                    freeSlots.add(new SlotRef(target.drive(), config, slot));
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
            if (containsChain(occupiedChains, candidate.chain())) {
                alreadyMarked++;
                continue;
            }
            accepted.add(candidate);
        }

        int count = Math.min(freeSlots.size(), accepted.size());
        Set<ECODriveBlockEntity> changedDrives = new HashSet<>();
        for (int index = 0; index < count; index++) {
            SlotRef slot = freeSlots.get(index);
            slot.config().setStack(slot.slot(), new GenericStack(accepted.get(index).key(), 0L));
            changedDrives.add(slot.drive());
        }
        changedDrives.forEach(ECODriveBlockEntity::onCellConfigurationChanged);
        if (!changedDrives.isEmpty()) host.notifyStorageConfigurationChanged();

        long transferred = transferMarkedChains(host, drives, targets);
        if (transferred > 0L) host.notifyStorageConfigurationChanged();
        return new MarkResult(Status.SUCCESS, count, alreadyMarked, 0, accepted.size() - count, transferred);
    }

    private static long transferMarkedChains(
        ECOStorageSystemBlockEntity host,
        List<ECODriveBlockEntity> drives,
        List<TargetCell> targets
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
                    if (entry.getLongValue() <= 0L || !(entry.getKey() instanceof AEItemKey itemKey)) {
                        continue;
                    }
                    CompressionChain chain = CompressionService.getChain(itemKey);
                    ChainTarget target = findTarget(chainTargets, chain);
                    if (target != null) {
                        transferred = saturatingAdd(transferred, transfer(
                            sourceStorage, target.storage(), itemKey, entry.getLongValue(), actionSource));
                    }
                }
            }
        }
        return transferred;
    }

    private static List<ChainTarget> collectChainTargets(List<TargetCell> targets) {
        List<ChainTarget> result = new ArrayList<>();
        for (TargetCell target : targets) {
            for (int slot = 0; slot < target.config().size(); slot++) {
                if (!(target.config().getKey(slot) instanceof AEItemKey itemKey)) continue;
                CompressionChain chain = CompressionService.getChain(itemKey);
                if (!chain.isEmpty() && findTarget(result, chain) == null) {
                    result.add(new ChainTarget(chain, target.storage()));
                }
            }
        }
        return result;
    }

    private static ChainTarget findTarget(List<ChainTarget> targets, CompressionChain chain) {
        if (chain.isEmpty()) return null;
        for (ChainTarget target : targets) {
            if (sameChain(target.chain(), chain)) return target;
        }
        return null;
    }

    private static long transfer(
        IECOStorageCell from,
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

    private static boolean containsChain(List<CompressionChain> chains, CompressionChain candidate) {
        return chains.stream().anyMatch(existing -> sameChain(existing, candidate));
    }

    private static boolean sameChain(CompressionChain first, CompressionChain second) {
        return !first.isEmpty() && first.equals(second);
    }

    private static MarkResult result(Status status) {
        return new MarkResult(status, 0, 0, 0, 0, 0L);
    }

    private record TargetCell(
        ECODriveBlockEntity drive,
        ConfigInventory config,
        ECOMegaLongBulkStorageCell storage
    ) {
    }

    private record ChainTarget(CompressionChain chain, ECOMegaLongBulkStorageCell storage) {
    }

    private record SlotRef(ECODriveBlockEntity drive, ConfigInventory config, int slot) {
    }

    private record Candidate(AEItemKey key, long amount, CompressionChain chain) {
    }
}
