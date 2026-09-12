package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.util.NEMath;
import gripe._90.megacells.item.part.DecompressionModulePart;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gripe._90.megacells.misc.DecompressionPattern;
import gripe._90.megacells.misc.DecompressionService;

/**
 * Adds ECO long bulk cells to MEGA Cells' decompression module without modifying MEGA's own
 * service. The existing MEGA service only recognises its own BulkCellInventory implementation.
 */
public final class ECOMegaDecompressionService implements IGridService, IGridServiceProvider, ICraftingProvider,
    ECOFastPathDispatchProvider {
    private final List<IChestOrDrive> cellHosts = new ArrayList<>();
    private final List<ECODriveBlockEntity> ecoDrives = new ArrayList<>();
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final Map<AEKey, Long> pendingOutputs = new LinkedHashMap<>();
    private final IGrid grid;
    private int installedModules;
    private int patternPriority;

    public ECOMegaDecompressionService(IGrid grid, ICraftingService craftingService) {
        this.grid = grid;
        craftingService.addGlobalCraftingProvider(this);
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (node.getOwner() instanceof IChestOrDrive cellHost) {
            cellHosts.add(cellHost);
        }
        if (node.getOwner() instanceof ECODriveBlockEntity drive) {
            ecoDrives.add(drive);
        }
        if (node.getOwner() instanceof DecompressionModulePart) {
            installedModules++;
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof IChestOrDrive cellHost) {
            cellHosts.remove(cellHost);
        }
        if (node.getOwner() instanceof ECODriveBlockEntity drive) {
            ecoDrives.remove(drive);
        }
        if (node.getOwner() instanceof DecompressionModulePart) {
            installedModules = Math.max(0, installedModules - 1);
        }
    }

    @Override
    public void onServerStartTick() {
        for (Iterator<Map.Entry<AEKey, Long>> it = pendingOutputs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<AEKey, Long> pending = it.next();
            long inserted = grid.getStorageService().getInventory()
                .insert(pending.getKey(), pending.getValue(), Actionable.MODULATE, IActionSource.empty());
            if (inserted >= pending.getValue()) {
                it.remove();
            } else if (inserted > 0L) {
                pending.setValue(pending.getValue() - inserted);
            }
        }
    }

    @Override
    public void onServerEndTick() {
        syncPatternPriority();
        patterns.clear();
        if (installedModules <= 0) {
            grid.getCraftingService().refreshGlobalCraftingProvider(this);
            return;
        }

        Set<StorageCell> seenCells = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IChestOrDrive host : cellHosts) {
            for (int i = 0; i < host.getCellCount(); i++) {
                addPatterns(host.getOriginalCellInventory(i), seenCells);
            }
        }
        for (ECODriveBlockEntity drive : ecoDrives) {
            addPatterns(drive.getCellInventory(), seenCells);
        }

        grid.getCraftingService().refreshGlobalCraftingProvider(this);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return installedModules > 0 ? List.copyOf(patterns) : List.of();
    }

    @Override
    public int getPatternPriority() {
        // AE2 snapshots the provider during addGlobalCraftingProvider(), which is called while Grid is still
        // constructing its service container. Do not touch grid.getService() from this early callback.
        return patternPriority;
    }

    private void syncPatternPriority() {
        try {
            DecompressionService megaService = grid.getService(DecompressionService.class);
            patternPriority = megaService == null ? 0 : megaService.getPatternPriority();
        } catch (IllegalArgumentException | NullPointerException notReady) {
            // The native MEGA service may not be visible during an early grid transition. Keep the last
            // usable value and retry on the next server-end tick.
        }
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
        if (installedModules <= 0 || !(details instanceof DecompressionPattern)) {
            return false;
        }

        for (var output : details.getOutputs()) {
            pendingOutputs.merge(output.what(), output.amount(), NEMath::saturatingAdd);
        }
        return true;
    }

    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        long capacity = batchCapacity(context);
        return capacity <= 0L ? null : new Preparation(capacity, null, false,
            batch -> pushBatch(context, batch.craftCount()));
    }

    private long batchCapacity(ECOBatchDispatchContext context) {
        if (installedModules <= 0 || !(context.pattern() instanceof DecompressionPattern)
                || !patterns.contains(context.pattern()) || !context.containerItems().isEmpty()) return 0;
        var perCopy = new KeyCounter();
        for (var output : context.pattern().getOutputs()) {
            if (output.amount() <= 0) return 0;
            perCopy.add(output.what(), output.amount());
        }
        var expected = new KeyCounter();
        for (var output : context.outputs()) expected.add(output.what(), output.amount());
        for (var output : perCopy) {
            if (expected.get(output.getKey()) != output.getLongValue()) return 0;
        }
        for (var output : expected) {
            if (perCopy.get(output.getKey()) != output.getLongValue()) return 0;
        }
        long capacity = Long.MAX_VALUE;
        for (var output : perCopy) {
            capacity = Math.min(capacity,
                (Long.MAX_VALUE - pendingOutputs.getOrDefault(output.getKey(), 0L)) / output.getLongValue());
        }
        return capacity;
    }

    private boolean pushBatch(ECOBatchDispatchContext context, long craftCount) {
        if (craftCount <= 0 || craftCount > batchCapacity(context)) {
            return false;
        }

        Map<AEKey, Long> batchOutputs = new LinkedHashMap<>();
        for (var output : context.outputs()) {
            long amount = Math.multiplyExact(output.amount(), craftCount);
            batchOutputs.merge(output.what(), amount, Math::addExact);
        }
        // Validate and aggregate the complete batch before publishing any output. If a malformed
        // pattern overflows, the CPU can still roll back all extracted inputs atomically.
        batchOutputs.replaceAll((key, amount) -> Math.addExact(pendingOutputs.getOrDefault(key, 0L), amount));
        pendingOutputs.putAll(batchOutputs);
        return true;
    }

    @Override
    public boolean isBusy() {
        return installedModules <= 0;
    }

    private void addPatterns(@Nullable StorageCell cell, Set<StorageCell> seenCells) {
        if (!(cell instanceof ECOMegaLongBulkStorageCell bulk) || !seenCells.add(cell)) {
            return;
        }
        for (IPatternDetails pattern : bulk.getDecompressionPatterns()) {
            if (!patterns.contains(pattern)) {
                patterns.add(pattern);
            }
        }
    }

}
