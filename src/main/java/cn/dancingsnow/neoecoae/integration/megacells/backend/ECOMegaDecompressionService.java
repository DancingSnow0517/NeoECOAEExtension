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
import cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.util.NEMath;
import gripe._90.megacells.item.part.DecompressionModulePart;
import gripe._90.megacells.misc.DecompressionPattern;
import gripe._90.megacells.misc.DecompressionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Adds ECO long bulk cells to MEGA Cells' decompression module. */
public final class ECOMegaDecompressionService
        implements IGridService, IGridServiceProvider, ICraftingProvider, ECOBatchCapacityProvider {
    private final List<IChestOrDrive> cellHosts = new ArrayList<>();
    private final List<ECODriveBlockEntity> ecoDrives = new ArrayList<>();
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final Map<AEKey, Long> pendingOutputs = new LinkedHashMap<>();
    private final IGrid grid;
    private int installedModules;
    private int patternPriority;

    public ECOMegaDecompressionService(IGrid grid, ICraftingService craftingService) {
        this.grid = grid;
        // AE2 15.4.x no longer exposes the old global-provider registration API.
        // Keep construction side-effect free; the service is registered through
        // GridServices and its provider is attached by the compatible bridge.
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (node.getOwner() instanceof IChestOrDrive cellHost) cellHosts.add(cellHost);
        if (node.getOwner() instanceof ECODriveBlockEntity drive) ecoDrives.add(drive);
        if (node.getOwner() instanceof DecompressionModulePart) installedModules++;
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof IChestOrDrive cellHost) cellHosts.remove(cellHost);
        if (node.getOwner() instanceof ECODriveBlockEntity drive) ecoDrives.remove(drive);
        if (node.getOwner() instanceof DecompressionModulePart) installedModules = Math.max(0, installedModules - 1);
    }

    @Override
    public void onServerStartTick() {
        for (Iterator<Map.Entry<AEKey, Long>> it = pendingOutputs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<AEKey, Long> pending = it.next();
            long inserted = grid.getStorageService()
                    .getInventory()
                    .insert(pending.getKey(), pending.getValue(), Actionable.MODULATE, IActionSource.empty());
            if (inserted >= pending.getValue()) it.remove();
            else if (inserted > 0L) pending.setValue(pending.getValue() - inserted);
        }
    }

    @Override
    public void onServerEndTick() {
        syncPatternPriority();
        patterns.clear();
        if (installedModules > 0) {
            Set<StorageCell> seenCells = Collections.newSetFromMap(new IdentityHashMap<>());
            for (IChestOrDrive host : cellHosts) {
                for (int i = 0; i < host.getCellCount(); i++) {
                    addPatterns(host.getOriginalCellInventory(i), seenCells);
                }
            }
            for (ECODriveBlockEntity drive : ecoDrives) addPatterns(drive.getCellInventory(), seenCells);
        }
        refreshCraftingProvider();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return installedModules > 0 ? List.copyOf(patterns) : List.of();
    }

    @Override
    public int getPatternPriority() {
        return patternPriority;
    }

    private void syncPatternPriority() {
        try {
            DecompressionService megaService = grid.getService(DecompressionService.class);
            if (megaService == null) {
                patternPriority = 0;
                return;
            }
            var method = megaService.getClass().getMethod("getPatternPriority");
            patternPriority = ((Number) method.invoke(megaService)).intValue();
        } catch (IllegalArgumentException | NullPointerException notReady) {
            // The native service may not be visible during an early grid transition.
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            patternPriority = 0;
        }
    }

    private void refreshCraftingProvider() {
        // This method exists only in older AE2 builds. Newer builds rebuild
        // providers from grid nodes; invoking it reflectively keeps the optional
        // integration binary-compatible with both layouts.
        try {
            var method = grid.getCraftingService().getClass().getMethod(
                    "refreshGlobalCraftingProvider", ICraftingProvider.class);
            method.invoke(grid.getCraftingService(), this);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Provider refresh is picked up by the next grid rebuild on 15.4.x.
        }
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
        if (installedModules <= 0 || !(details instanceof DecompressionPattern)) return false;
        for (var output : details.getOutputs()) {
            pendingOutputs.merge(output.what(), output.amount(), NEMath::saturatingAdd);
        }
        return true;
    }

    @Override
    public long eco$getBatchCapacity(ECOBatchDispatchContext context) {
        if (installedModules <= 0
                || !(context.pattern() instanceof DecompressionPattern)
                || !patterns.contains(context.pattern())
                || !context.containerItems().isEmpty()) return 0;
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
            capacity = Math.min(
                    capacity,
                    (Long.MAX_VALUE - pendingOutputs.getOrDefault(output.getKey(), 0L)) / output.getLongValue());
        }
        return capacity;
    }

    @Override
    public boolean eco$pushBatch(ECOBatchDispatchContext context, long craftCount) {
        if (craftCount <= 0 || craftCount > eco$getBatchCapacity(context)) return false;
        Map<AEKey, Long> batchOutputs = new LinkedHashMap<>();
        for (var output : context.outputs()) {
            long amount = Math.multiplyExact(output.amount(), craftCount);
            batchOutputs.merge(output.what(), amount, Math::addExact);
        }
        batchOutputs.replaceAll((key, amount) -> Math.addExact(pendingOutputs.getOrDefault(key, 0L), amount));
        pendingOutputs.putAll(batchOutputs);
        return true;
    }

    @Override
    public boolean isBusy() {
        return installedModules <= 0;
    }

    private void addPatterns(@Nullable StorageCell cell, Set<StorageCell> seenCells) {
        if (!(cell instanceof ECOMegaLongBulkStorageCell bulk) || !seenCells.add(cell)) return;
        for (IPatternDetails pattern : bulk.getDecompressionPatterns()) {
            if (!patterns.contains(pattern)) patterns.add(pattern);
        }
    }
}
