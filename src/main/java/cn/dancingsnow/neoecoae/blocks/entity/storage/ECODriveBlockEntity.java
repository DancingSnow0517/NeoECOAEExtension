package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECODriveBlockEntity extends AbstractStorageBlockEntity<ECODriveBlockEntity>
        implements IStorageProvider, ICellHost {

    public final IItemHandler HANDLER = new CellHostItemHandler(this);
    private final LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> HANDLER);
    private final IBatchedECOCellSaveProvider cellSaveProvider = this::markCellContentDirty;

    @Nullable
    private ItemStack cellStack = null;
    @Nullable
    private IECOStorageCell cachedCellInventory = null;
    @Nullable
    private ItemStack cachedCellInventoryStack = null;

    @Getter
    private boolean mounted = false;
    @Getter
    private boolean online = false;
    @Nullable
    private CellState lastSyncedCellState = null;

    public ECODriveBlockEntity(
            BlockEntityType<ECODriveBlockEntity> type,
            BlockPos pos,
            BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        flushPendingCellContent();
        this.cellStack = normalizeCellStack(cellStack);
        invalidateCellInventoryCache();
        if (getLevel() != null && getBlockState().hasProperty(ECODriveBlock.HAS_CELL)) {
            boolean oldHasCell = getBlockState().getValue(ECODriveBlock.HAS_CELL);
            boolean newHasCell = this.cellStack != null;
            BlockState newState = getBlockState().setValue(ECODriveBlock.HAS_CELL, newHasCell);
            if (oldHasCell != newHasCell) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateStorageProviderState("setCellStack");
        lastSyncedCellState = getCurrentCellState();
        markForUpdate();
        setChanged();
        notifyControllerRefresh();
    }

    @Nullable
    @Override
    public ItemStack getCellStack() {
        flushPendingCellContent();
        return cellStack;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        updateStorageProviderState("updateState");
    }

    private void updateStorageProviderState(String source) {
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
        if (cellStack == null) {
            invalidateCellInventoryCache();
            return null;
        }
        if (cachedCellInventory == null || cachedCellInventoryStack != cellStack) {
            cachedCellInventory = ECOStorageCells.getCellInventory(cellStack, cellSaveProvider);
            cachedCellInventoryStack = cellStack;
        }
        return cachedCellInventory;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        boolean hasCluster = cluster != null;
        boolean hasController = hasCluster && cluster.getController() != null;
        IECOTier mainTier = hasController ? cluster.getController().getTier() : null;
        IECOStorageCell cellInventory = getCellInventory();
        IECOTier cellTier = cellInventory == null ? null : cellInventory.getTier();
        boolean tierSupported = mainTier != null && cellTier != null && mainTier.compareTo(cellTier) >= 0;
        boolean willMount = hasController && cellInventory != null && tierSupported;
        logMountAttempt(hasCluster, hasController, mainTier, cellInventory, cellTier, tierSupported, willMount);
        if (cluster != null && cluster.getController() != null) {
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                storageMounts.mount(cellInventory);
                boolean mountedChanged = !mounted;
                mounted = true;
                setChanged();
                lastSyncedCellState = getCurrentCellState();
                if (mountedChanged) {
                    markForUpdate();
                }
                logMountResult(true);
                notifyControllerRefresh();
                return;
            }
        }
        boolean mountedChanged = mounted;
        mounted = false;
        setChanged();
        lastSyncedCellState = getCurrentCellState();
        if (mountedChanged) {
            markForUpdate();
        }
        logMountResult(false);
        notifyControllerRefresh();
    }

    @Override
    public void onReady() {
        super.onReady();
        updateStorageProviderState("onReady");
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        online = getMainNode().isOnline();
        updateStorageProviderState("onMainNodeStateChanged:" + reason);
        markForUpdate();
        setChanged();
    }

    public void notifyPersistence() {
        markCellContentDirty();
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        loadDriveVisualState(data);
        invalidateCellInventoryCache();
        pendingContentSave = false;
        pendingPersistenceFlush = false;
        pendingControllerStatsDirty = false;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        flushPendingCellContent();
        super.saveAdditional(data);
        saveDriveVisualState(data);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveDriveVisualState(tag);
        logVisualSync("saveUpdateTag", tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        loadDriveVisualState(tag);
        logVisualSync("handleUpdateTag", tag);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveVisualState(CompoundTag data) {
        flushPendingCellContent();
        super.saveVisualState(data);
        saveDriveVisualState(data);
    }

    @Override
    protected void loadVisualState(CompoundTag data) {
        super.loadVisualState(data);
        loadDriveVisualState(data);
    }

    private void saveDriveVisualState(CompoundTag data) {
        flushPendingCellContent();
        if (cellStack != null && !cellStack.isEmpty()) {
            data.put("cellStack", cellStack.save(new CompoundTag()));
        }
        data.putBoolean("mounted", mounted);
        data.putBoolean("online", online);
    }

    private void loadDriveVisualState(CompoundTag data) {
        if (data.contains("cellStack")) {
            this.cellStack = normalizeCellStack(ItemStack.of(data.getCompound("cellStack")));
        } else {
            this.cellStack = null;
        }
        invalidateCellInventoryCache();
        this.mounted = data.getBoolean("mounted");
        this.online = data.getBoolean("online");
        this.lastSyncedCellState = getCurrentCellState();
    }

    @Nullable
    private CellState getCurrentCellState() {
        IECOStorageCell cellInventory = getCellInventory();
        return cellInventory == null ? null : cellInventory.getStatus();
    }

    private void logVisualSync(String source, CompoundTag data) {
        // No-op: verbose debug logging removed.
    }

    private static @Nullable ItemStack normalizeCellStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.copyWithCount(1);
    }

    private void logStorageProviderUpdate(String source, double power) {
        // No-op: verbose debug logging removed.
    }

    private void logMountAttempt(
            boolean hasCluster,
            boolean hasController,
            @Nullable IECOTier mainTier,
            @Nullable IECOStorageCell cellInventory,
            @Nullable IECOTier cellTier,
            boolean tierSupported,
            boolean willMount) {
        // No-op: debug logging removed.
    }

    private void logMountResult(boolean mounted) {
        // No-op: debug logging removed.
    }

    /**
     * Notifies the storage controller (if formed) that storage stats should be
     * recalculated after a cell change. Safe to call on either side; only
     * executes on the server.
     */
    private void notifyControllerRefresh() {
        if (level == null || level.isClientSide)
            return;
        if (cluster == null || cluster.getController() == null)
            return;
        cluster.getController().refreshStorageUiState();
    }

    private boolean pendingContentSave = false;
    private boolean pendingPersistenceFlush = false;
    private boolean pendingControllerStatsDirty = false;

    private void markCellContentDirty() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }

        boolean firstDirty = !pendingContentSave;
        pendingContentSave = true;
        pendingControllerStatsDirty = true;
        if (firstDirty) {
            setChanged();
        }

        CellState currentState = getCurrentCellState();
        if (currentState != lastSyncedCellState) {
            lastSyncedCellState = currentState;
            markForUpdate();
        }

        if (!pendingPersistenceFlush) {
            pendingPersistenceFlush = true;
            serverLevel.getServer().executeIfPossible(this::flushPendingContentChanges);
        }
    }

    private void flushPendingContentChanges() {
        pendingPersistenceFlush = false;
        flushPendingCellContent();
        if (pendingControllerStatsDirty) {
            pendingControllerStatsDirty = false;
            notifyControllerRefresh();
        }
    }

    private void flushPendingCellContent() {
        if (!pendingContentSave) {
            return;
        }
        if (cachedCellInventory != null) {
            cachedCellInventory.persist();
        }
        pendingContentSave = false;
        setChanged();
    }

    private void invalidateCellInventoryCache() {
        cachedCellInventory = null;
        cachedCellInventoryStack = null;
    }

    @Override
    public void onChunkUnloaded() {
        flushPendingCellContent();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        flushPendingCellContent();
        super.setRemoved();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }
}
