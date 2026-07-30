package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellHandler;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOUniversalCellHandler implements IECOCellHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOUniversalCellHandler.class);

    public static final ECOUniversalCellHandler INSTANCE = new ECOUniversalCellHandler();

    private final Map<UUID, ISaveProvider> owners = new HashMap<>();
    private final Map<ISaveProvider, UUID> ownerIds = new IdentityHashMap<>();

    private ECOUniversalCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof ECOUniversalStorageCellItem && stack.getCount() == 1;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!(stack.getItem() instanceof ECOUniversalStorageCellItem item) || stack.getCount() != 1) {
            return null;
        }
        StorageCell delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
        if (delegate != null && host != null && claimUniqueStorage(stack, host)) {
            // The first delegate was created for the duplicate UUID. Reopen it after the replacement domain exists.
            delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
        }
        return delegate == null ? null : new ECOUniversalStorageCell(delegate, stack, item);
    }

    @Override
    public synchronized void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
        if (host == null) {
            return;
        }
        UUID id = ownerIds.get(host);
        if (id == null) {
            id = getStorageId(stack);
        }
        if (id != null && owners.get(id) == host) {
            owners.remove(id);
            ownerIds.remove(host);
        }
    }

    @Override
    public synchronized void clearRuntimeState() {
        owners.clear();
        ownerIds.clear();
    }

    /**
     * ae2omnicells keeps contents in world SavedData and puts only its UUID on the item. Two physical matrices with
     * that UUID would both mutate the same storage. Detach the later mount into an empty domain rather than copying
     * the source: copying is unsafe for worlds that were already affected by the shared-UUID bug.
     */
    private synchronized boolean claimUniqueStorage(ItemStack stack, ISaveProvider host) {
        UUID currentId = getStorageId(stack);
        if (currentId == null) {
            return false;
        }
        ISaveProvider owner = owners.get(currentId);
        if (owner == null || owner == host) {
            claim(currentId, host);
            return false;
        }

        CompoundTag tag = stack.getOrCreateTag();
        String originalId = tag.getString(AEUniversalCellData.UUID_TAG);
        tag.remove(AEUniversalCellData.UUID_TAG);
        AEUniversalCellData replacement = AEUniversalCellData.computeIfAbsentCellDataForItemStack(stack);
        UUID replacementId = getStorageId(stack);
        if (replacement == null || replacementId == null || replacementId.equals(currentId)) {
            tag.putString(AEUniversalCellData.UUID_TAG, originalId);
            throw new IllegalStateException("Unable to detach duplicated Omni storage UUID " + currentId);
        }

        replacement.getOriginalStorage().clear();
        replacement.setDirty();
        claim(replacementId, host);
        LOGGER.warn(
                "Detached duplicated Omni storage UUID {} -> {} for a second ECO drive host; the replacement starts"
                        + " empty to prevent shared-storage multiplication",
                currentId,
                replacementId);
        return true;
    }

    private void claim(UUID id, ISaveProvider host) {
        UUID previousId = ownerIds.put(host, id);
        if (previousId != null && !previousId.equals(id) && owners.get(previousId) == host) {
            owners.remove(previousId);
        }
        owners.put(id, host);
    }

    @Nullable private static UUID getStorageId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(AEUniversalCellData.UUID_TAG)) {
            return null;
        }
        try {
            return UUID.fromString(tag.getString(AEUniversalCellData.UUID_TAG));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
