package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;

import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOCellWorkbenchDrive;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.storage.DeferredCellPersistence;
import cn.dancingsnow.neoecoae.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.storage.ECOCreativeSupply;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.LazyManaged;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ECODriveBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster, ECODriveBlockEntity>
    implements ISyncPersistRPCBlockEntity, IStorageProvider, ICellHost, ISaveProvider, DeferredCellPersistence,
        IECOCellWorkbenchDrive {
    private static final String RESTORE_RECEIPTS_TAG = "neoecoae_restore_receipts";
    private static final Logger LOGGER = LoggerFactory.getLogger(ECODriveBlockEntity.class);

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new CellHostItemHandler(this);

    @Persisted
    @LazyManaged
    @Nullable
    private ItemStack cellStack = null;

    // Publish a replacement rather than letting LDLib inspect the live inventory from its worker thread.
    @DescSynced
    @RequireRerender
    private volatile ItemStack renderedCell = ItemStack.EMPTY;
    @Getter
    @DescSynced
    private volatile boolean infiniteMember;

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
    // At most one persistence notification is pending per drive.
    private final java.util.concurrent.atomic.AtomicBoolean persistenceNotifyQueued =
            new java.util.concurrent.atomic.AtomicBoolean();
    @Nullable
    private ItemStack cachedCellStack;
    @Nullable
    private IECOStorageCell cachedCellInventory;
    private boolean chunkUnloaded;

    public ECODriveBlockEntity(
        BlockEntityType<ECODriveBlockEntity> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator::new);
        getMainNode().addService(IStorageProvider.class, this);
    }

    /**
     * The installed cell. Server-side, contents whose component write was deferred to the end of the tick are
     * written first, so callers always see current components and may copy or move the stack.
     */
    @Override
    @Nullable
    public ItemStack getCellStack() {
        if (level != null && level.isClientSide) return renderedCell.isEmpty() ? null : renderedCell;
        flushPendingCellWrites();
        return cellStack;
    }

    @Nullable
    ItemStack getInstalledCellStack() {
        return cellStack;
    }

    boolean isInstalledStorageAvailable() {
        return !chunkUnloaded && !isRemoved();
    }

    private void onTransferAvailabilityChanged() {
        if (!(level instanceof ServerLevel)) return;
        var controller = getStorageController();
        if (controller != null) controller.driveStorageAvailabilityChanged();
    }

    private void updateRenderedCell() {
        var item = cellStack == null || cellStack.isEmpty() ? null : cellStack.getItem();
        if (item == null) renderedCell = ItemStack.EMPTY;
        else if (renderedCell.isEmpty() || !renderedCell.is(item)) renderedCell = new ItemStack(item);
        infiniteMember = ECOInfiniteStorageMember.isMember(cellStack);
    }

    @Override
    public void setChanged() {
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer().isSameThread()) {
            boolean previouslyMember = infiniteMember;
            updateRenderedCell();
            if (previouslyMember != infiniteMember) {
                var controller = getStorageController();
                if (controller != null) controller.storageInterfaceTransfer().driveConfigurationChanged(this);
            }
        }
        super.setChanged();
    }

    /** Writes cell contents deferred to the end of the tick; required before the stack is read, saved or moved. */
    public void flushPendingCellWrites() {
        if (level != null && !level.isClientSide) {
            ECOCellMutationBatch.flushDeferred(this);
        }
    }

    @Override
    public boolean neoecoae$canDeferCellPersistence() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null || isRemoved()) {
            return false;
        }
        return !isServerStopping() && serverLevel.getServer().isSameThread();
    }

    @Override
    public void neoecoae$onCellContentsChanged(cn.dancingsnow.neoecoae.storage.ECOStorageCell cell) {
        if (cell != cachedCellInventory || !neoecoae$canDeferCellPersistence()) return;
        var controller = getStorageController();
        if (controller != null) controller.storageInterfaceTransfer().cellContentsChanged(cell);
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        if (!canExtractCell()) {
            return;
        }
        if (cellStack != null && !isItemValid(cellStack)) return;
        // The outgoing stack must carry every accepted transfer before it leaves this drive.
        flushPendingCellWrites();
        ECOStorageSystemBlockEntity controller = getStorageController();
        ECOStorageCells.releaseCellInventory(this.cellStack, this);
        // Anonymous members are empty; they leave as ordinary cells.
        if (ECOInfiniteStorageMember.isAnonymous(this.cellStack)) ECOInfiniteStorageMember.clearMember(this.cellStack);
        if (cellStack != null) {
            if (controller != null && controller.isFormedInfiniteMode()
                    && !ECOInfiniteStorageMember.isMember(cellStack) && !ECOInfiniteStorageMember.isMigrating(cellStack)
                    && isEmptyInfiniteCandidate(cellStack)) {
                ECOInfiniteStorageMember.markAnonymous(cellStack);
            } else if (ECOInfiniteStorageMember.isAnonymous(cellStack)
                    && (controller == null || !controller.isInfiniteMode())) {
                ECOInfiniteStorageMember.clearMember(cellStack);
            }
        }
        this.cellStack = cellStack;
        cellInteractionId = UUID.randomUUID();
        invalidateCellInventoryCache();
        if (controller != null) controller.bulkDriveChanged(this, false);
        if (controller != null) controller.storageInterfaceTransfer().driveConfigurationChanged(this);
        if (getLevel() != null && !isServerStopping()) {
            BlockState state = getBlockState();
            BlockState newState = state.setValue(ECODriveBlock.HAS_CELL, cellStack != null);
            if (newState != state) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateState();
        if (controller != null) controller.invalidateInfiniteMembers();
        setChanged();
        if (controller != null) controller.refreshStorageConfiguration();
    }

    public boolean replaceCellFromHostMenu(@Nullable ItemStack expected, @Nullable ItemStack replacement) {
        if (!isInstalledStorageAvailable() || cellStack != expected || expected == replacement) return false;
        try {
            if (expected != null && !expected.isEmpty()) {
                flushPendingCellWrites();
                var inventory = getCellInventory();
                if (inventory != null) inventory.persist();
            }
            setCellStack(replacement);
        } catch (RuntimeException failure) {
            if (NEConfig.ecoCraftingOutputDeliveryDebug) LOGGER.error("Failed to update the storage disk at {}", getBlockPos(), failure);
        }
        return cellStack == replacement;
    }

    /** Only empty cells may become anonymous members: nothing is migrated on insertion. */
    static boolean isEmptyInfiniteCandidate(ItemStack stack) {
        var cell = ECOStorageCells.getCellInventory(stack, null);
        if (!cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer.isEligible(cell)) return false;
        var contents = new appeng.api.stacks.KeyCounter();
        ((cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell) cell).getMigrationStacks(contents);
        return contents.isEmpty();
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
        return cluster instanceof NEStorageCluster storageCluster
            && storageCluster.getController() != null
            && storageCluster.getController().isInfiniteMode()
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
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                power += cellInventory.getIdleDrain();
            }
        }
        applyIdlePower(ECOPowerRole.STORAGE_DRIVE, 256, power - 256);
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    public void scheduleRenderUpdate() {
        markForClientUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        flushPendingCellWrites();
        if (cellStack != null) {
            if (ECOInfiniteStorageMember.isAnonymous(cellStack)) ECOInfiniteStorageMember.clearMember(cellStack);
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
        return cachedCellInventory;
    }

    private void invalidateCellInventoryCache() {
        cachedCellStack = null;
        cachedCellInventory = null;
    }

    @Override
    public void onChunkUnloaded() {
        flushPendingCellWrites();
        var controller = getStorageController();
        if (controller != null) controller.bulkDriveChanged(this, true);
        ECOStorageCells.releaseCellInventory(cellStack, this);
        invalidateCellInventoryCache();
        chunkUnloaded = true;
        super.onChunkUnloaded();
        onTransferAvailabilityChanged();
    }

    @Override
    public void setRemoved() {
        flushPendingCellWrites();
        var controller = getStorageController();
        if (controller != null) controller.bulkDriveChanged(this, true);
        ECOStorageCells.releaseCellInventory(cellStack, this);
        invalidateCellInventoryCache();
        super.setRemoved();
        onTransferAvailabilityChanged();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        // Chunk saves serialize the cell stack; deferred contents must be in its components first.
        flushPendingCellWrites();
        super.saveAdditional(data, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        flushPendingCellWrites();
        updateRenderedCell();
        return super.getUpdateTag(registries);
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null
                && mainTier.compareTo(cellInventory.getTier()) >= 0
                && !storageCluster.getController().isStorageInterfaceTransferMode()
                && (!storageCluster.getController().isInfiniteMode()
                    || storageCluster.getController().canUseHostDomainStorage())
                && !ECOInfiniteStorageMember.isMigrating(cellStack)
                && !ECOInfiniteStorageMember.isMember(cellStack)) {
                int priority = cn.dancingsnow.neoecoae.gui.storage.StoragePriority.mountPriority(
                    storageCluster.getController().getStoragePriority(), getConfiguredCellPriority());
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
        chunkUnloaded = false;
        super.onReady();
        var controller = getStorageController();
        if (controller != null) controller.bulkDriveChanged(this, false);
        onTransferAvailabilityChanged();
        updateState();
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
    public void notifyPersistence() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var server = serverLevel.getServer();
        // Shutdown still saves dirty chunks. Never submit the server thread's own final write to a stopped executor.
        if (server.isSameThread()) {
            CellState previous = cellState;
            if (!server.isStopped()) updateCellState();
            setChanged();
            if (!server.isStopped() && previous != cellState) markForUpdate();
            return;
        }
        if (server.isStopped() || !persistenceNotifyQueued.compareAndSet(false, true)) return;
        try {
            server.executeIfPossible(() -> {
                persistenceNotifyQueued.set(false);
                notifyPersistence();
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            persistenceNotifyQueued.set(false);
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
        return cluster instanceof NEStorageCluster storageCluster ? storageCluster.getController() : null;
    }

    @Override
    public int getConfiguredCellPriority() {
        return cellStack == null ? 0 : cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.priority(cellStack);
    }

    @Override
    public void setConfiguredCellPriority(int priority) {
        if (cellStack == null || !cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.canConfigure(cellStack)) return;
        if (getCellInventory() instanceof cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell bulk) {
            bulk.setConfiguredStoragePriority(priority);
        } else {
            cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings.setPriority(cellStack, priority);
        }
        onCellConfigurationChanged();
        var controller = getStorageController();
        if (controller != null) controller.refreshStorageConfiguration();
    }

    /** Flush balances before replacing the handler that cached the old partition and upgrade settings. */
    public void reloadCellConfiguration() {
        if (level == null || level.isClientSide) return;
        flushPendingCellWrites();
        if (cachedCellInventory != null) cachedCellInventory.persist();
        ECOStorageCells.releaseCellInventory(cellStack, this);
        invalidateCellInventoryCache();
        onCellConfigurationChanged();
        var controller = getStorageController();
        if (controller != null) controller.refreshStorageConfiguration();
    }

    /** Persists an in-place CellConfig mutation and refreshes this drive's AE2 storage mount. */
    public void onCellConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (cachedCellInventory instanceof ECOCreativeCell) invalidateCellInventoryCache();
        var controller = getStorageController();
        if (getCellInventory() instanceof cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell bulk) {
            bulk.invalidateConversionPatterns();
        }
        if (controller != null) controller.bulkDriveChanged(this, false);
        if (controller != null) controller.storageInterfaceTransfer().driveConfigurationChanged(this);
        updateCellState();
        setChanged();
        markForUpdate();
        IStorageProvider.requestUpdate(getMainNode());
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
        ECOInfiniteStorageMember.markAnonymous(cellStack);
        var controller = getStorageController();
        if (controller != null) controller.invalidateInfiniteMembers();
        setChanged();
        markForUpdate();
    }

    public boolean convertInfiniteMemberToNormalStorage(UUID domainId) {
        if (cellStack == null || cellStack.isEmpty() || !(ECOInfiniteStorageMember.isAnonymous(cellStack) || ECOInfiniteStorageMember.isMemberOf(cellStack, domainId))) {
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

    /** Gives an anonymous member a restore identity; restore receipts and plans are keyed by it. */
    public void bindCellForRestore(UUID domainId) {
        if (!ECOInfiniteStorageMember.isAnonymous(cellStack)) return;
        ECOInfiniteStorageMember.markMember(cellStack, domainId);
        setChanged();
        markForUpdate();
    }

    /** Drops a restore identity once no restore references it. */
    public void releaseRestoreBinding(UUID domainId) {
        if (!ECOInfiniteStorageMember.isMemberOf(cellStack, domainId)) return;
        ECOInfiniteStorageMember.markAnonymous(cellStack);
        clearRestoreReceipts();
        setChanged();
        markForUpdate();
    }

    @SuppressWarnings("deprecation")
    public long getRestoreReceipt(UUID transactionId) {
        if (cellStack == null || cellStack.isEmpty()) return 0L;
        CompoundTag custom = cellStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        return custom.getCompound(RESTORE_RECEIPTS_TAG).getLong(transactionId.toString());
    }

    public void putRestoreReceipt(UUID transactionId, long amount) {
        if (cellStack == null || cellStack.isEmpty() || amount <= 0L) return;
        CompoundTag custom = cellStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag receipts = custom.getCompound(RESTORE_RECEIPTS_TAG);
        receipts.putLong(transactionId.toString(), amount);
        custom.put(RESTORE_RECEIPTS_TAG, receipts);
        cellStack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        setChanged();
    }

    public void clearRestoreReceipts() {
        if (cellStack == null || cellStack.isEmpty()) return;
        CompoundTag custom = cellStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.remove(RESTORE_RECEIPTS_TAG);
        if (custom.isEmpty()) cellStack.remove(DataComponents.CUSTOM_DATA);
        else cellStack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        setChanged();
    }
}
