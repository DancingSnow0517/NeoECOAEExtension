package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOConfigurableCellDrive;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import com.wintercogs.ae2omnicells.common.init.OCDataComponents;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOUniversalCellHandler implements IECOCellHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOUniversalCellHandler.class);
    private static final String UUID_TAG = "ae_universal_cell_uuid";
    public static final ECOUniversalCellHandler INSTANCE = new ECOUniversalCellHandler();

    // Server-thread only: UUID claims and releases are skipped on the client (they would also rewrite the client's
    // copy of the disk), so no lock is needed.
    private final Map<UUID, ISaveProvider> owners = new HashMap<>();
    private final Map<ISaveProvider, UUID> ownerIds = new IdentityHashMap<>();

    private ECOUniversalCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof ECOUniversalStorageCellItem && stack.getCount() == 1;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!(stack.getItem() instanceof ECOUniversalStorageCellItem item) || stack.getCount() != 1
                || !EffectiveSide.get().isServer()) {
            return null;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || host instanceof BlockEntity block && block.isRemoved()) {
            return null;
        }

        UUID id = getStorageId(stack);
        AEUniversalCellData data;
        if (id != null) {
            // Omni's create-if-absent helper replaces an unreadable UUID with an empty inventory.
            // An existing disk must retain its identity so its original file can still be recovered.
            data = AEUniversalCellData.getCellDataByUUID(id);
            if (data == null) {
                LOGGER.error("Cannot load Omni storage UUID {}; refusing to replace the cell's existing data", id);
                return null;
            }
        } else {
            if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(UUID_TAG)
                    || IAEUniversalCell.getUsedBytes(stack) > 0L || IAEUniversalCell.getUsedTypes(stack) > 0
                    || !IAEUniversalCell.getTooltipShowStacks(stack).isEmpty()) {
                LOGGER.error("Omni cell has stored-content metadata but no readable storage UUID; refusing to initialize it as empty");
                return null;
            }
            data = AEUniversalCellData.computeIfAbsentCellDataForItemStack(stack);
            if (data == null) return null;
            id = getStorageId(stack);
        }
        if (host != null && !claimUniqueStorage(stack, host)) return null;
        // Promote a valid legacy identity only after its data and ownership have been checked.
        stack.set(OCDataComponents.CELL_UUID.get(), id);
        StorageCell delegate = new AEUniversalCellInventory(data, stack, item, host);
        return new ECOUniversalStorageCell(delegate, stack, item);
    }

    @Override
    public void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
        if (host == null || !EffectiveSide.get().isServer()) return;
        UUID id = ownerIds.get(host);
        if (id == null) id = getStorageId(stack);
        if (id != null && owners.get(id) == host) {
            owners.remove(id);
            ownerIds.remove(host);
        }
    }

    @Override
    public void clearRuntimeState() {
        owners.clear();
        ownerIds.clear();
    }

    private boolean claimUniqueStorage(ItemStack stack, ISaveProvider host) {
        UUID currentId = getStorageId(stack);
        if (currentId == null) return false;
        ISaveProvider owner = owners.get(currentId);
        if (owner != null && owner != host && !stillOwnsStorage(owner, currentId)) {
            owners.remove(currentId, owner);
            ownerIds.remove(owner, currentId);
            owner = null;
        }
        if (owner == null || owner == host) {
            claim(currentId, host);
            return true;
        }
        LOGGER.error("Refusing to mount duplicate Omni storage UUID {} for {} while owned by {}; cell identity and contents were preserved",
                currentId, host, owner);
        return false;
    }

    /** A removed/replaced drive or a drive whose slot has changed no longer owns this disk. Never load a chunk. */
    private static boolean stillOwnsStorage(ISaveProvider owner, UUID id) {
        if (owner instanceof BlockEntity block) {
            var level = block.getLevel();
            if (block.isRemoved() || level == null || !level.hasChunkAt(block.getBlockPos())
                    || level.getBlockEntity(block.getBlockPos()) != block) return false;
        }
        if (owner instanceof IECOConfigurableCellDrive drive) {
            return !drive.isRemoved() && id.equals(getStorageId(drive.getCellStack()));
        }
        if (owner instanceof ICellHost cellHost) return id.equals(getStorageId(cellHost.getCellStack()));
        return true;
    }

    private void claim(UUID id, ISaveProvider host) {
        UUID previousId = ownerIds.put(host, id);
        if (previousId != null && !previousId.equals(id) && owners.get(previousId) == host) owners.remove(previousId);
        owners.put(id, host);
    }

    @Nullable private static UUID getStorageId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        UUID componentId = stack.get(OCDataComponents.CELL_UUID.get());
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return resolveStorageId(componentId, tag);
    }

    @Nullable
    static UUID resolveStorageId(@Nullable UUID componentId, CompoundTag tag) {
        if (componentId != null) {
            return componentId;
        }
        if (!tag.contains(UUID_TAG)) return null;
        try { return UUID.fromString(tag.getStringOr(UUID_TAG, "")); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
