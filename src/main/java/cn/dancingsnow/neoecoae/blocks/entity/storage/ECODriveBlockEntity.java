package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ECODriveBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster, ECODriveBlockEntity>
    implements NEManagedBlockEntity, IStorageProvider, ICellHost, ISaveProvider {
    private static final String RESTORE_RECEIPTS_TAG = "neoecoae_restore_receipts";

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new CellHostItemHandler(this);

    @Getter
    @DescSynced
    @Persisted
    @RequireRerender
    @Nullable
    private ItemStack cellStack = null;
    @Persisted
    private CompoundTag legacy2042RestoreData = new CompoundTag();

    // UI requests identify this installation, independently of mutable or transient item components.
    // Recreated on load; no persistent disk data or save migration is needed.
    @Getter
    private UUID cellInteractionId = UUID.randomUUID();

    @Getter
    @DescSynced
    private boolean mounted = false;
    @Getter
    @DescSynced
    private boolean online = false;
    @Getter
    @DescSynced
    private CellState cellState = CellState.ABSENT;
    @Nullable
    private ItemStack cachedCellStack;
    @Nullable
    private IECOStorageCell cachedCellInventory;

    public ECODriveBlockEntity(
        BlockEntityType<ECODriveBlockEntity> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator::new);
        getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        if (!canExtractCell()) {
            return;
        }
        if (cellStack != null && !isItemValid(cellStack)) return;
        if (this.cellStack != null && cachedCellInventory instanceof cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell ordinary
            && !ordinary.storageData().flush()) return;
        if (cellStack != null) {
            try { ECOStorageCells.getCellInventory(cellStack, this); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(ECODriveBlockEntity.class).error("Cannot prepare ECO disk installation", failure);
                return;
            }
        }
        ECOStorageSystemBlockEntity controller = getStorageController();
        if (controller != null) controller.rememberInfiniteMembers();
        ECOStorageCells.releaseCellInventory(this.cellStack, this);
        this.cellStack = cellStack;
        cellInteractionId = UUID.randomUUID();
        invalidateCellInventoryCache();
        if (getLevel() != null && !isServerStopping()) {
            BlockState state = getBlockState();
            BlockState newState = state.setValue(ECODriveBlock.HAS_CELL, cellStack != null);
            if (newState != state) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateState();
        this.cellStack = cellStack;
        setChanged();
        if (controller != null) controller.notifyStorageConfigurationChanged();
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        ECOStorageSystemBlockEntity controller = getStorageController();
        if (controller != null && !controller.canInsertStorageCell(stack)) return false;
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public boolean canExtractCell() {
        return getCellExtractionBlockReason() == CellExtractionBlockReason.NONE;
    }

    @Override
    @Nullable
    public Component getCellExtractionBlockReasonText() {
        CellExtractionBlockReason reason = getCellExtractionBlockReason();
        return reason == CellExtractionBlockReason.NONE ? null : Component.translatable(reason.translationKey());
    }

    /** Shared by manual interaction and automation; querying this never releases ownership. */
    public CellExtractionBlockReason getCellExtractionBlockReason() {
        if (cachedCellInventory instanceof cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell ordinary
            && (!ordinary.storageData().canMutate() || ordinary.storageData().writeFailure() != null))
            return CellExtractionBlockReason.PERSISTENCE_FAILED;
        if (ECOInfiniteStorageMember.isMigrating(cellStack)) {
            return CellExtractionBlockReason.INFINITE_MIGRATION;
        }
        if (isLockedByInfiniteMode() && (getStorageController() == null
            || !getStorageController().isFormedInfiniteMode())) {
            return CellExtractionBlockReason.INFINITE_MEMBER;
        }
        return CellExtractionBlockReason.NONE;
    }

    public enum CellExtractionBlockReason {
        NONE(""),
        PERSISTENCE_FAILED("tooltip.neoecoae.storage.persistence_failed"),
        INFINITE_MIGRATION("tooltip.neoecoae.storage.infinite_migration_locked"),
        INFINITE_MEMBER("tooltip.neoecoae.storage.infinite_member_locked");

        private final String translationKey;

        CellExtractionBlockReason(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public boolean isLockedByInfiniteMode() {
        return cluster != null
            && cluster.getController() != null
            && cluster.getController().isInfiniteMode()
            && cellStack != null
            && !cellStack.isEmpty()
            && ECOInfiniteStorageMember.isMember(cellStack);
    }

    private void updateState() {
        if (isServerStopping()) {
            return;
        }
        updateCellState();
        double power = 256;
        if (cluster != null && cluster.getController() != null) {
            IECOTier mainTier = cluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                power += cellInventory.getIdleDrain();
            }
        }
        getMainNode().setIdlePowerUsage(power);
        IStorageProvider.requestUpdate(getMainNode());
    }

    public void scheduleRenderUpdate() {
        markForUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Nullable
    public IECOStorageCell getCellInventory() {
        if (cellStack == null || cellStack.isEmpty()) {
            invalidateCellInventoryCache();
            return null;
        }
        if (cachedCellStack != cellStack) {
            cachedCellStack = cellStack;
            cachedCellInventory = ECOStorageCells.getCellInventory(cellStack, this);
        }
        if (hasLegacyRestoreReceipts() && cachedCellInventory instanceof cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell ordinary) {
            CompoundTag metadata = ordinary.storageData().metadata();
            String key = cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage.RESTORE_BARRIER;
            if (!metadata.contains(key)) {
                metadata.put(key, legacy2042RestoreData.copy()); ordinary.storageData().metadata(metadata);
                ordinary.storageData().flush();
                NEItemData.update(cellStack, tag -> tag.put(key, legacy2042RestoreData.copy()));
            }
        }
        return cachedCellInventory;
    }

    public boolean hasLegacyRestoreReceipts() { return !legacy2042RestoreData.isEmpty()
        || cellStack != null && NEItemData.read(cellStack).contains(cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage.RESTORE_BARRIER); }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains("ecoRestoreReceipts") && (!(data.get("ecoRestoreReceipts") instanceof net.minecraft.nbt.ListTag list)
            || !list.isEmpty())) {
            legacy2042RestoreData.put("receipts", data.get("ecoRestoreReceipts").copy());
            org.slf4j.LoggerFactory.getLogger(ECODriveBlockEntity.class)
                .error("20.4.2 restore receipts at {} require reconciliation; disk and domain will remain locked", worldPosition);
        }
        if (cellStack != null && !legacy2042RestoreData.isEmpty())
            NEItemData.update(cellStack, tag -> tag.put(cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage.RESTORE_BARRIER,
                legacy2042RestoreData.copy()));
        invalidateCellInventoryCache();
    }

    private void invalidateCellInventoryCache() {
        cachedCellStack = null;
        cachedCellInventory = null;
    }

    @Override
    public void onChunkUnloaded() {
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.setRemoved();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (cluster != null && cluster.getController() != null) {
            IECOTier mainTier = cluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null
                && mainTier.compareTo(cellInventory.getTier()) >= 0
                && !cluster.getController().isStorageInterfaceTransferMode()
                && !ECOInfiniteStorageMember.isMigrating(cellStack)
                && !ECOInfiniteStorageMember.isMember(cellStack)) {
                int priority = cn.dancingsnow.neoecoae.gui.storage.StoragePriority.mountPriority(
                    cluster.getController().getStoragePriority(), cellInventory.prioritizesMarkedInserts());
                storageMounts.mount(cellInventory, priority);
                mounted = true;
                updateCellState();
                setChanged();
                return;
            }
        }
        mounted = false;
        updateCellState();
        setChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        updateState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        online = getMainNode().isOnline();
        setChanged();
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                updateCellState();
                setChanged();
                markForUpdate();
            });
        }
    }

    private void updateCellState() {
        if (level == null || level.isClientSide) {
            return;
        }
        IECOStorageCell cellInventory = getCellInventory();
        cellState = cellInventory == null ? CellState.ABSENT : cellInventory.getStatus();
    }

    @Nullable
    public ECOStorageSystemBlockEntity getStorageController() {
        return cluster != null ? cluster.getController() : null;
    }

    /** Persists an in-place CellConfig mutation and refreshes this drive's AE2 storage mount. */
    public void onCellConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        updateCellState();
        setChanged();
        markForUpdate();
        IStorageProvider.requestUpdate(getMainNode());
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
    }

    @Override
    public void saveChanges() {
        if (cachedCellInventory != null) {
            cachedCellInventory.persist();
        }
        notifyPersistence();
    }

    public void convertCellToInfiniteMember(UUID domainId) {
        if (cellStack == null || cellStack.isEmpty()) {
            return;
        }
        // Use the live handler: clearing a separately constructed inventory leaves this drive's cache stale.
        var inventory = getCellInventory();
        if (!(inventory instanceof cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Missing transfer-capable source cell");
        }
        migrationCell.clearMigrationStacks();
        migrationCell.persistMigrationContents((ServerLevel) level);
        ECOInfiniteStorageMember.markMember(cellStack, domainId);
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
        setChanged();
        markForUpdate();
    }

    public boolean convertInfiniteMemberToNormalStorage(UUID domainId) {
        if (cellStack == null || cellStack.isEmpty() || !ECOInfiniteStorageMember.isMemberOf(cellStack, domainId)) {
            return false;
        }
        ECOInfiniteStorageMember.clearMember(cellStack);
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
        clearRestoreReceipts();
        setChanged();
        markForUpdate();
        return true;
    }

    public java.math.BigInteger getRestoreReceiptExact(UUID transactionId) {
        if (cellStack == null || cellStack.isEmpty()) return java.math.BigInteger.ZERO;
        CompoundTag receipts = NEItemData.read(cellStack).getCompound(RESTORE_RECEIPTS_TAG);
        String key = transactionId.toString();
        return receipts.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? java.math.BigInteger.valueOf(receipts.getLong(key))
            : cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount.read(receipts.getCompound(key)).toBigInteger();
    }

    public void putRestoreReceipt(UUID transactionId, java.math.BigInteger amount) {
        if (cellStack == null || cellStack.isEmpty() || amount.signum() <= 0) return;
        CompoundTag custom = NEItemData.copy(cellStack), receipts = custom.getCompound(RESTORE_RECEIPTS_TAG);
        if (amount.bitLength() < 64) receipts.putLong(transactionId.toString(), amount.longValueExact());
        else receipts.put(transactionId.toString(), cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount.of(amount).write());
        custom.put(RESTORE_RECEIPTS_TAG, receipts); NEItemData.write(cellStack, custom); setChanged();
    }

    public long getRestoreReceipt(UUID transactionId) {
        return getRestoreReceiptExact(transactionId).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public void putRestoreReceipt(UUID transactionId, long amount) {
        if (cellStack == null || cellStack.isEmpty() || amount <= 0L) return;
        CompoundTag custom = NEItemData.copy(cellStack);
        CompoundTag receipts = custom.getCompound(RESTORE_RECEIPTS_TAG);
        receipts.putLong(transactionId.toString(), amount);
        custom.put(RESTORE_RECEIPTS_TAG, receipts);
        NEItemData.write(cellStack, custom);
        setChanged();
    }

    public void clearRestoreReceipts() {
        if (cellStack == null || cellStack.isEmpty()) return;
        CompoundTag custom = NEItemData.copy(cellStack);
        custom.remove(RESTORE_RECEIPTS_TAG);
        if (custom.isEmpty()) cellStack.setTag(null);
        else NEItemData.write(cellStack, custom);
        cn.dancingsnow.neoecoae.impl.storage.persistence.ECOStorageRepository.persistMetadata(cellStack, true);
        setChanged();
    }
}
