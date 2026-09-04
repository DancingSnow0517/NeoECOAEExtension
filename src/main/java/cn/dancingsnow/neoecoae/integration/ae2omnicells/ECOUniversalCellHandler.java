package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellHandler;
import com.wintercogs.ae2omnicells.common.init.OCDataComponents;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ECOUniversalCellHandler implements IECOCellHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOUniversalCellHandler.class);
    private static final String UUID_TAG = "ae_universal_cell_uuid";
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
            delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
        }
        return delegate == null ? null : new ECOUniversalStorageCell(delegate, stack, item);
    }

    @Override
    public synchronized void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
        if (host == null) return;
        UUID id = ownerIds.get(host);
        if (id == null) id = getStorageId(stack);
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

    private synchronized boolean claimUniqueStorage(ItemStack stack, ISaveProvider host) {
        UUID currentId = getStorageId(stack);
        if (currentId == null) return false;
        ISaveProvider owner = owners.get(currentId);
        if (owner == null || owner == host) {
            claim(currentId, host);
            return false;
        }
        UUID originalComponentId = stack.get(OCDataComponents.CELL_UUID.get());
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String originalLegacyId = tag.getString(UUID_TAG);
        if (originalComponentId != null) {
            stack.remove(OCDataComponents.CELL_UUID.get());
        } else {
            tag.remove(UUID_TAG);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        AEUniversalCellData replacement = AEUniversalCellData.computeIfAbsentCellDataForItemStack(stack);
        UUID replacementId = getStorageId(stack);
        if (replacement == null || replacementId == null || replacementId.equals(currentId)) {
            if (originalComponentId != null) {
                stack.set(OCDataComponents.CELL_UUID.get(), originalComponentId);
            } else {
                tag.putString(UUID_TAG, originalLegacyId);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            throw new IllegalStateException("Unable to detach duplicated Omni storage UUID " + currentId);
        }
        replacement.getOriginalStorage().clear();
        replacement.setDirty();
        claim(replacementId, host);
        LOGGER.warn("Detached duplicated Omni storage UUID {} -> {} for a second ECO drive host; replacement starts empty", currentId, replacementId);
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
        try { return UUID.fromString(tag.getString(UUID_TAG)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
