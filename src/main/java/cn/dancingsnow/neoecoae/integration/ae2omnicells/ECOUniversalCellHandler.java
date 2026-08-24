package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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
        String uuidTagBefore = stack.hasTag() ? stack.getTag().getString(AEUniversalCellData.UUID_TAG) : "";
        StorageCell delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
        if (delegate == null) {
            return null;
        }

        // ae2omnicells silently re-points the item at a fresh, EMPTY storage domain when the SavedData record
        // for its UUID is missing or fails strict loading. Mounting such a cell would strand the real contents
        // behind the orphaned UUID and present an empty disk. Restore the tag and refuse to mount so the data
        // stays recoverable instead of being overwritten by later inserts.
        String uuidTagAfter = stack.hasTag() ? stack.getTag().getString(AEUniversalCellData.UUID_TAG) : "";
        if (!isStorageIdStable(uuidTagBefore, uuidTagAfter)) {
            LOGGER.error(
                    "Refusing to mount universal cell whose storage domain {} is missing (ae2omnicells re-pointed"
                            + " it at empty domain {}); restored the original UUID so the contents stay reachable",
                    uuidTagBefore,
                    uuidTagAfter);
            stack.getOrCreateTag().putString(AEUniversalCellData.UUID_TAG, uuidTagBefore);
            return null;
        }

        if (host != null && claimUniqueStorage(stack, host)) {
            // The first delegate was created for the duplicate UUID. Reopen it after the replacement domain exists.
            delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
            if (delegate == null) {
                return null;
            }
        }
        return new ECOUniversalStorageCell(delegate, stack, item);
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
     *
     * <p>The ownership maps are JVM-runtime state only. If a host disappears without releasing (abnormal teardown,
     * crashed integrated-server session, exception mid-swap), its stale claim must never make the only physical cell
     * look duplicated: dead hosts are evicted and their claim stolen. A genuinely live duplicate is detached to an
     * empty domain; if even that fails, the caller keeps the original delegate instead of throwing, so one bad cell
     * cannot break the whole storage read chain until the next restart.
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
        if (isDeadHost(owner)) {
            LOGGER.warn(
                    "Universal-cell claim {} was held by a removed host; stealing it instead of detaching data",
                    currentId);
            ownerIds.remove(owner);
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
            // Deliberately no throw: this runs inside drive reads (stats, mounts). Keep the delegate bound to
            // the original domain so the cell stays readable; ownership just stays with the other live host.
            LOGGER.error(
                    "Unable to detach duplicated Omni storage UUID {}; mounting against the live domain without"
                            + " claiming it",
                    currentId);
            return false;
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

    static boolean isDeadHost(ISaveProvider owner) {
        return owner instanceof BlockEntity blockEntity && blockEntity.isRemoved()
                || owner instanceof IBatchedECOCellSaveProvider batchedHost && batchedHost.isHostRemoved();
    }

    static boolean isStorageIdStable(String before, String after) {
        return before.isEmpty() || before.equals(after);
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
