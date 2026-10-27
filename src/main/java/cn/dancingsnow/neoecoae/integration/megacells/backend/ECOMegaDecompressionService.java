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
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import gripe._90.megacells.item.part.DecompressionModulePart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.util.ArrayList;
import java.util.List;

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
    // Updated by every dispatched batch and drained every tick; primitive values avoid boxing on both paths.
    private final Object2LongLinkedOpenHashMap<AEKey> pendingOutputs = new Object2LongLinkedOpenHashMap<>();
    private final IGrid grid;
    private final List<IGridNode> moduleNodes = new ArrayList<>();
    private final IGridNode providerNode;
    private int installedModules;
    private int patternPriority;
    private static final String PENDING_TAG = "neoecoae_pending_outputs";

    public ECOMegaDecompressionService(IGrid grid, ICraftingService craftingService) {
        this.grid = grid;
        // AE2 15 exposes node providers. Give the global service a stable provider node whose
        // power/channel state follows its real decompression modules without creating a world node.
        providerNode = (IGridNode) java.lang.reflect.Proxy.newProxyInstance(
            IGridNode.class.getClassLoader(), new Class<?>[] {IGridNode.class}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "ECO MEGA decompression provider";
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
                if (method.getName().equals("getOwner")) return this;
                if (method.getName().equals("getGrid")) return grid;
                if (method.getName().equals("getService") && args[0] == ICraftingProvider.class) return this;
                if (method.getName().equals("isActive")) return moduleNodes.stream().anyMatch(IGridNode::isActive);
                var pivot = grid.getPivot();
                if (pivot == null) throw new IllegalStateException("Decompression provider has no grid pivot");
                try {
                    return method.invoke(pivot, args);
                } catch (java.lang.reflect.InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (savedData != null && savedData.contains(PENDING_TAG, 9)) {
            var entries = savedData.getList(PENDING_TAG, 10);
            for (var raw : entries) {
                CompoundTag entry = (CompoundTag) raw;
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                long amount = entry.getLong("amount");
                if (key != null && amount > 0) pendingOutputs.put(key, NEMath.saturatingAdd(pendingOutputs.getLong(key), amount));
            }
        }
        if (node.getOwner() instanceof IChestOrDrive cellHost) {
            cellHosts.add(cellHost);
        }
        if (node.getOwner() instanceof ECODriveBlockEntity drive) {
            ecoDrives.add(drive);
        }
        if (node.getOwner() instanceof DecompressionModulePart) {
            installedModules++;
            moduleNodes.add(node);
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
            installedModules--;
            moduleNodes.remove(node);
        }
    }

    @Override
    public void onServerStartTick() {
        for (var it = pendingOutputs.object2LongEntrySet().fastIterator(); it.hasNext(); ) {
            var pending = it.next();
            long amount = pending.getLongValue();
            long inserted = grid.getStorageService().getInventory()
                .insert(pending.getKey(), amount, Actionable.MODULATE, IActionSource.empty());
            if (inserted >= amount) {
                it.remove();
                markGridDataDirty();
            } else if (inserted > 0L) {
                pending.setValue(amount - inserted);
                markGridDataDirty();
            }
        }
    }

    @Override
    public void onServerEndTick() {
        boolean hadPatterns = !patterns.isEmpty();
        patterns.clear();

        if (installedModules > 0) {
            // The module UI only writes MEGA's service; ECO patterns follow its priority.
            patternPriority = moduleNodes.stream().filter(IGridNode::isActive)
                .map(IGridNode::getOwner).map(DecompressionModulePart.class::cast)
                .mapToInt(DecompressionModulePart::getPatternPriority).max().orElse(0);
            for (IChestOrDrive host : cellHosts) {
                for (int i = 0; i < host.getCellCount(); i++) {
                    addPatterns(host.getOriginalCellInventory(i));
                }
            }
            for (ECODriveBlockEntity drive : ecoDrives) {
                addPatterns(drive.getCellInventory());
            }
            grid.getCraftingService().refreshNodeCraftingProvider(providerNode);
        } else if (hadPatterns) {
            // Withdraw the published patterns once when the last module is removed.
            grid.getCraftingService().refreshNodeCraftingProvider(providerNode);
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return installedModules > 0 ? patterns : List.of();
    }

    @Override
    public int getPatternPriority() {
        // AE2 snapshots the provider during addGlobalCraftingProvider(), which is called while Grid is still
        // constructing its service container. Do not touch grid.getService() from this early callback.
        return patternPriority;
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
        if (!(details instanceof DecompressionPattern)) {
            return false;
        }

        var output = details.getPrimaryOutput();
        pendingOutputs.addTo(output.what(), output.amount());
        markGridDataDirty();
        return true;
    }

    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        long capacity = batchCapacity(context);
        return capacity <= 0L ? null : new Preparation(capacity, null, false,
            batch -> pushBatch(context, batch.craftCount()));
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        // Inventory-like service state must have one owner. Replicating it onto every node would
        // duplicate pending outputs if the grid split. AE2 keeps the pivot stable and persists it.
        if (node != grid.getPivot()) {
            savedData.remove(PENDING_TAG);
            return;
        }
        var entries = new net.minecraft.nbt.ListTag();
        for (var pending : Object2LongMaps.fastIterable(pendingOutputs)) {
            if (pending.getLongValue() <= 0) continue;
            CompoundTag entry = new CompoundTag();
            entry.put("key", pending.getKey().toTagGeneric());
            entry.putLong("amount", pending.getLongValue());
            entries.add(entry);
        }
        savedData.put(PENDING_TAG, entries);
    }

    private long batchCapacity(ECOBatchDispatchContext context) {
        if (!(context.pattern() instanceof DecompressionPattern) || !patterns.contains(context.pattern()) || !context.containerItems().isEmpty()) return 0;
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
                (Long.MAX_VALUE - pendingOutputs.getLong(output.getKey())) / output.getLongValue());
        }
        return capacity;
    }

    private boolean pushBatch(ECOBatchDispatchContext context, long craftCount) {
        if (craftCount <= 0 || craftCount > batchCapacity(context)) {
            return false;
        }

        var batchOutputs = new Object2LongLinkedOpenHashMap<AEKey>();
        for (var output : context.outputs()) {
            long amount = Math.multiplyExact(output.amount(), craftCount);
            batchOutputs.put(output.what(), Math.addExact(batchOutputs.getLong(output.what()), amount));
        }
        // Validate and aggregate the complete batch before publishing any output. If a malformed
        // pattern overflows, the CPU can still roll back all extracted inputs atomically.
        for (var entry : Object2LongMaps.fastIterable(batchOutputs)) {
            entry.setValue(Math.addExact(pendingOutputs.getLong(entry.getKey()), entry.getLongValue()));
        }
        pendingOutputs.putAll(batchOutputs);
        markGridDataDirty();
        return true;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    private void addPatterns(@Nullable StorageCell cell) {
        if (cell instanceof ECOMegaLongBulkStorageCell bulk) {
            patterns.addAll(bulk.getDecompressionPatterns());
        }
    }

    private void markGridDataDirty() {
        IGridNode pivot = grid.getPivot();
        if (pivot != null && pivot.getOwner() instanceof BlockEntity blockEntity) blockEntity.setChanged();
    }

}
