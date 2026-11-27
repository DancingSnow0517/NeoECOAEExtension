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
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.storage.ECOCreativeSupply;
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
    implements NEManagedBlockEntity, IStorageProvider, ICellHost, ISaveProvider, cn.dancingsnow.neoecoae.storage.DeferredCellPersistence {
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
    @Persisted
    private CompoundTag unresolvedCellStack = new CompoundTag();

    // UI requests identify this installation, independently of mutable or transient item components.
    // Recreated on load; no persistent disk data or save migration is needed.
    @Getter
    private UUID cellInteractionId = UUID.randomUUID();

    @Getter
    @DescSynced
    private boolean mounted = false;
    private boolean infiniteOccupied;
    private boolean infiniteCandidate;
    private boolean unloading;
    @Getter
    @DescSynced
    private boolean online = false;
    @Getter
    @DescSynced
    private CellState cellState = CellState.ABSENT;
    private boolean persistenceNotificationQueued;
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
        if (this.cellStack != null && cachedCellInventory instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
            && !ordinary.storageData().flush()) return;
        ECOStorageSystemBlockEntity controller = getStorageController();
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
        if (!unresolvedCellStack.isEmpty()) return false;
        ECOStorageSystemBlockEntity controller = getStorageController();
        if (controller != null && !controller.canInsertStorageCell(stack)) return false;
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public boolean canExtractCell() {
        return getCellExtractionBlockReason() == CellExtractionBlockReason.NONE;
    }

    static boolean isEmptyInfiniteCandidate(ItemStack stack) {
        return isEmptyInfiniteCandidate(ECOStorageCells.getCellInventory(stack, null));
    }

    static boolean isEmptyInfiniteCandidate(@Nullable IECOStorageCell inventory) {
        if (!cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer.isEligible(inventory)) return false;
        if (inventory.getClass() == cn.dancingsnow.neoecoae.storage.ECOStorageCell.class) {
            var data = ((cn.dancingsnow.neoecoae.storage.ECOStorageCell) inventory).storageData();
            return data.writeFailure() == null && data.amounts().isEmpty();
        }
        var contents = new appeng.api.stacks.KeyCounter();
        ((cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell) inventory).getMigrationStacks(contents);
        return contents.isEmpty();
    }

    @Override
    @Nullable
    public Component getCellExtractionBlockReasonText() {
        CellExtractionBlockReason reason = getCellExtractionBlockReason();
        return reason == CellExtractionBlockReason.NONE ? null : Component.translatable(reason.translationKey());
    }

    /** Shared by manual interaction and automation; querying this never releases ownership. */
    public CellExtractionBlockReason getCellExtractionBlockReason() {
        if (!unresolvedCellStack.isEmpty()) return CellExtractionBlockReason.PERSISTENCE_FAILED;
        if (cachedCellInventory instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary
            && (!ordinary.storageData().canMutate() || ordinary.storageData().writeFailure() != null))
            return CellExtractionBlockReason.PERSISTENCE_FAILED;
        if (ECOInfiniteStorageMember.isSealed(cellStack) && !ECOInfiniteStorageMember.isMember(cellStack) || hasLegacyRestoreReceipts()) {
            return CellExtractionBlockReason.INFINITE_MIGRATION;
        }
        if (ECOInfiniteStorageMember.isMember(cellStack)) return CellExtractionBlockReason.INFINITE_MEMBER;
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

    public boolean isLockedByInfiniteMode() { return infiniteOccupied; }

    boolean isInstalledStorageAvailable() {
        return !unloading && !isRemoved() && level != null && level.hasChunkAt(worldPosition)
            && level.getBlockEntity(worldPosition) == this;
    }
    boolean refreshInfiniteQualification(ECOStorageSystemBlockEntity host) {
        if (getStorageController() != host) return false;
        if (!isInstalledStorageAvailable()) {
            var stack = getCellStack();
            if (unloading || isRemoved() || !(host.getLevel() instanceof ServerLevel server)
                    || level != null && level != server || !host.getMainNode().isReady()
                    || stack == null || stack.isEmpty() || canExtractCell() || getBuiltInCellForDisplay() != stack
                    || hasLegacyRestoreReceipts() || ECOInfiniteStorageMember.isSealed(stack)) return false;
            var pos = host.getBlockPos();
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || chunk.getBlockEntities().get(pos) != host) return false;
            var cell = getCellInventory();
            return cell != null && host.getTier().compareTo(cell.getTier()) >= 0 && isEmptyInfiniteCandidate(cell);
        }
        if (cellStack == null || cellStack.isEmpty() || hasLegacyRestoreReceipts()) {
            infiniteCandidate = false;
            return false;
        }
        var inventory = getCellInventory();
        if (ECOInfiniteStorageMember.isAnonymous(cellStack) && !ECOInfiniteStorageMember.hasMalformedEvidence(cellStack)
                && !NEItemData.read(cellStack).contains(RESTORE_RECEIPTS_TAG)
                && isEmptyInfiniteCandidate(inventory)) {
            clearSettledInfiniteMarkers();
            inventory = getCellInventory();
        }
        infiniteCandidate = !hasLegacyRestoreReceipts() && !ECOInfiniteStorageMember.isSealed(cellStack)
            && isEmptyInfiniteCandidate(inventory);
        return infiniteCandidate;
    }
    void clearInfiniteOccupancy() { infiniteOccupied = false; }
    void setInfiniteOccupied(boolean occupied) {
        if (infiniteOccupied == occupied) return;
        infiniteOccupied = occupied;
        setChanged(); markForUpdate();
    }
    void clearSettledInfiniteMarkers() {
        if (cellStack == null || cellStack.isEmpty()) return;
        CompoundTag original = NEItemData.copy(cellStack);
        CompoundTag clean = original.copy();
        for (String key : java.util.List.of("neoecoae_infinite_member", "neoecoae_infinite_domain", "neoecoae_member_identity",
            "neoecoae_migration_id", "neoecoae_migration_domain", "neoecoae_restore_receipts")) clean.remove(key);
        try {
            NEItemData.write(cellStack, clean);
            cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.persistMetadata(cellStack, true);
        } catch (RuntimeException failure) {
            NEItemData.write(cellStack, original);
            throw failure;
        }
        ECOStorageCells.releaseCellInventory(cellStack, this);
        invalidateCellInventoryCache();
        setChanged();
        updateCellState();
        IStorageProvider.requestUpdate(getMainNode());
        var controller = getStorageController();
        if (controller != null) controller.storageDriveChanged(this, false);
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
        if (hasLegacyRestoreReceipts() && cachedCellInventory instanceof cn.dancingsnow.neoecoae.storage.ECOStorageCell ordinary) {
            CompoundTag metadata = ordinary.storageData().metadata();
            String key = cn.dancingsnow.neoecoae.storage.persistence.Legacy2042Storage.RESTORE_BARRIER;
            if (!metadata.contains(key)) {
                metadata.put(key, legacy2042RestoreData.copy()); ordinary.storageData().metadata(metadata);
                ordinary.storageData().flush();
                NEItemData.update(cellStack, tag -> tag.put(key, legacy2042RestoreData.copy()));
            }
        }
        return cachedCellInventory;
    }

    public boolean hasLegacyRestoreReceipts() { return !legacy2042RestoreData.isEmpty()
        || cellStack != null && NEItemData.read(cellStack).contains(cn.dancingsnow.neoecoae.storage.persistence.Legacy2042Storage.RESTORE_BARRIER); }

    @Nullable
    IECOStorageCell loadedCellInventory() {
        return cachedCellStack == cellStack ? cachedCellInventory : null;
    }

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
            NEItemData.update(cellStack, tag -> tag.put(cn.dancingsnow.neoecoae.storage.persistence.Legacy2042Storage.RESTORE_BARRIER,
                legacy2042RestoreData.copy()));
        invalidateCellInventoryCache();
    }

    @Override
    public void loadCustomPersistedData(CompoundTag data) {
        unresolvedCellStack = cn.dancingsnow.neoecoae.platform.nbt.DriveCellPersistence.unresolved(data, cellStack);
        if (!unresolvedCellStack.isEmpty()) {
            ItemStack recovered = ItemStack.of(unresolvedCellStack);
            if (!recovered.isEmpty()) {
                cellStack = recovered;
                unresolvedCellStack = new CompoundTag();
            } else {
                cellStack = null;
                org.slf4j.LoggerFactory.getLogger(ECODriveBlockEntity.class)
                    .error("Unavailable ECO disk item {} at {}; complete item NBT retained",
                        unresolvedCellStack.getString("id"), worldPosition);
            }
        } else if (cellStack != null && cellStack.isEmpty()) {
            cellStack = null;
        }
        invalidateCellInventoryCache();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag data, boolean forDrop) {
        if (!unresolvedCellStack.isEmpty()) data.put("cellStack", unresolvedCellStack.copy());
    }

    private void invalidateCellInventoryCache() {
        cachedCellStack = null;
        cachedCellInventory = null;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        var controller = getStorageController();
        if (controller != null && !isServerStopping()) { controller.storageDriveChanged(this, true); controller.invalidateInfiniteMembers(); }
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unloading = true;
        var controller = getStorageController();
        if (controller != null) { controller.storageDriveChanged(this, true); controller.invalidateInfiniteMembers(); }
        ECOStorageCells.releaseCellInventory(cellStack, this);
        super.setRemoved();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (isInstalledStorageAvailable() && cluster != null && cluster.getController() != null) {
            IECOTier mainTier = cluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null
                && mainTier.compareTo(cellInventory.getTier()) >= 0
                && !cluster.getController().isStorageInterfaceTransferMode()
                && !infiniteOccupied
                && !ECOInfiniteStorageMember.isSealed(cellStack)) {
                int priority = cn.dancingsnow.neoecoae.gui.storage.StoragePriority.mountPriority(
                    cluster.getController().getStoragePriority(), getConfiguredCellPriority());
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
        unloading = false;
        super.onReady();
        if (level instanceof ServerLevel && getBlockState().hasProperty(ECODriveBlock.HAS_CELL)) {
            boolean hasCell = cellStack != null && !cellStack.isEmpty() || !unresolvedCellStack.isEmpty();
            if (getBlockState().getValue(ECODriveBlock.HAS_CELL) != hasCell)
                level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ECODriveBlock.HAS_CELL, hasCell));
        }
        updateState();
        var controller = getStorageController();
        if (controller != null) { controller.storageDriveChanged(this, false); controller.invalidateInfiniteMembers(); }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        online = getMainNode().isOnline();
        updateCreativeAvailability();
        setChanged();
    }

    @Override
    protected void onMainNodeGridChanged() {
        if (isServerStopping()) return;
        online = getMainNode().isOnline();
        updateCreativeAvailability();
    }

    private void updateCreativeAvailability() {
        if (!(cachedCellInventory instanceof ECOCreativeCell creative)) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        if (grid.getStorageService().getInventory() instanceof ECOCreativeSupply.Source storage) {
            var supply = storage.neoecoae$activeRefillSupply();
            if (supply != null) supply.availabilityChanged(creative, online);
        }
    }

    @Override
    public boolean neoecoae$canDeferCellPersistence() {
        return level != null && !level.isClientSide && level.getServer() != null && level.getServer().isSameThread();
    }

    @Override
    public void neoecoae$onCellContentsChanged(cn.dancingsnow.neoecoae.storage.ECOStorageCell cell) {
        onStorageContentsChanged(cell);
    }

    public void onStorageContentsChanged(cn.dancingsnow.neoecoae.storage.ECOStorageCell cell) {
        var controller = getStorageController();
        if (controller == null) return;
        controller.storageInterfaceTransfer().cellContentsChanged(cell);
        if (cell.getClass() == cn.dancingsnow.neoecoae.storage.ECOStorageCell.class
                && controller.getTier() == cn.dancingsnow.neoecoae.api.ECOTier.L9 && cachedCellInventory == cell
                && isInstalledStorageAvailable() && !ECOInfiniteStorageMember.isSealed(cellStack)
                && infiniteCandidate != isEmptyInfiniteCandidate(cell)) {
            controller.storageCellQualificationChanged();
        }
    }

    @Override
    public void notifyPersistence() {
        // Frequent transfers (for example FE) notify every tick; keep at most one pending notification per drive.
        if (level instanceof ServerLevel serverLevel && !persistenceNotificationQueued) {
            persistenceNotificationQueued = true;
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                persistenceNotificationQueued = false;
                CellState previous = cellState;
                updateCellState();
                setChanged();
                // cellState and cellStack are DescSynced; a block update is only needed for a visible state change.
                if (cellState != previous) markForUpdate();
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

    public int getConfiguredCellPriority() {
        return cellStack == null ? 0 : cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.priority(cellStack);
    }

    public void setConfiguredCellPriority(int priority) {
        if (cellStack == null || !cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.canConfigure(cellStack)) return;
        if (getCellInventory() instanceof cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell bulk) {
            bulk.setConfiguredStoragePriority(priority);
        } else {
            cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.setPriority(cellStack, priority);
        }
        onCellConfigurationChanged();
        var controller = getStorageController();
        if (controller != null) controller.notifyStorageConfigurationChanged();
    }

    /** Flush balances before replacing the handler that cached the old partition and upgrade settings. */
    public void reloadCellConfiguration() {
        if (level == null || level.isClientSide) return;
        if (cachedCellInventory != null) cachedCellInventory.persist();
        ECOStorageCells.releaseCellInventory(cellStack, this);
        invalidateCellInventoryCache();
        onCellConfigurationChanged();
        var controller = getStorageController();
        if (controller != null) controller.notifyStorageConfigurationChanged();
    }

    /** Persists an in-place CellConfig mutation and refreshes this drive's AE2 storage mount. */
    public void onCellConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (cachedCellInventory instanceof ECOCreativeCell) invalidateCellInventoryCache();
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
        if (cellStack == null || cellStack.isEmpty() || !(ECOInfiniteStorageMember.isAnonymous(cellStack)
                || ECOInfiniteStorageMember.isMemberOf(cellStack, domainId))) {
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
            : cn.dancingsnow.neoecoae.storage.infinite.HugeAmount.read(receipts.getCompound(key)).toBigInteger();
    }

    public void putRestoreReceipt(UUID transactionId, java.math.BigInteger amount) {
        if (cellStack == null || cellStack.isEmpty() || amount.signum() <= 0) return;
        CompoundTag custom = NEItemData.copy(cellStack), receipts = custom.getCompound(RESTORE_RECEIPTS_TAG);
        if (amount.bitLength() < 64) receipts.putLong(transactionId.toString(), amount.longValueExact());
        else receipts.put(transactionId.toString(), cn.dancingsnow.neoecoae.storage.infinite.HugeAmount.of(amount).write());
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
        cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository.persistMetadata(cellStack, true);
        setChanged();
    }
}
