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
import cn.dancingsnow.neoecoae.impl.storage.ECOCellStorageManager;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

public class ECODriveBlockEntity extends AbstractStorageBlockEntity<ECODriveBlockEntity>
        implements IStorageProvider, ICellHost {

    public final IItemHandler HANDLER = new CellHostItemHandler(this);
    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> HANDLER);
    private final IBatchedECOCellSaveProvider cellSaveProvider = new IBatchedECOCellSaveProvider() {
        @Override
        public void saveChanges() {
            markCellContentDirty();
        }

        @Override
        public boolean isHostRemoved() {
            return ECODriveBlockEntity.this.isRemoved();
        }
    };

    @Nullable private ItemStack cellStack = null;

    @Nullable private IECOStorageCell cachedCellInventory = null;

    @Nullable private ItemStack cachedCellInventoryStack = null;

    @Getter
    private boolean mounted = false;

    @Getter
    private boolean online = false;

    @Nullable private CellState lastSyncedCellState = null;

    private final Map<UUID, RestoreReceipt> restoreReceipts = new HashMap<>();
    private final Set<UUID> unverifiableRestoreReceipts = new java.util.HashSet<>();

    public ECODriveBlockEntity(BlockEntityType<ECODriveBlockEntity> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        flushPendingCellContent();
        releaseCellBackend();
        this.cellStack = normalizeCellStack(cellStack);
        restoreReceipts.clear();
        unverifiableRestoreReceipts.clear();
        forkDuplicateCellInCurrentHost();
        invalidateCellInventoryCache();
        if (getLevel() != null && getBlockState().hasProperty(ECODriveBlock.HAS_CELL)) {
            boolean oldHasCell = getBlockState().getValue(ECODriveBlock.HAS_CELL);
            boolean newHasCell = this.cellStack != null;
            BlockState newState = getBlockState().setValue(ECODriveBlock.HAS_CELL, newHasCell);
            if (oldHasCell != newHasCell) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateStorageProviderState();
        lastSyncedCellState = getCurrentCellState();
        markForUpdate();
        setChanged();
        notifyControllerRefresh();
    }

    @Nullable @Override
    public ItemStack getCellStack() {
        flushPendingCellContent();
        return cellStack;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        ECOStorageSystemBlockEntity controller = getController();
        if (controller != null && controller.isInfiniteMode() && !ECOInfiniteStorageMember.isMember(stack)) {
            return false;
        }
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public boolean canExtractCell() {
        if (ECOInfiniteStorageMember.isMember(cellStack)) {
            return false;
        }
        ECOStorageSystemBlockEntity controller = getController();
        return controller == null || controller.canExtractDriveCell(this);
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        updateStorageProviderState();
    }

    private void updateStorageProviderState() {
        double power = 256;
        if (cluster != null && cluster.getController() != null) {
            IECOTier mainTier = cluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null
                    && !cluster.getController().isInfiniteMode()
                    && !ECOInfiniteStorageMember.isMember(cellStack)
                    && !cluster.getController().isStorageInterfaceTransferMode()
                    && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                power += cellInventory.getIdleDrain();
            }
        }
        getMainNode().setIdlePowerUsage(power);
        IStorageProvider.requestUpdate(getMainNode());
    }

    /**
     * Public entry point for the storage controller to request a storage provider
     * refresh (e.g. after priority change). Delegates to
     * {@link #updateStorageProviderState}.
     */
    public void requestStorageProviderUpdate() {
        updateStorageProviderState();
    }

    public void scheduleRenderUpdate() {
        markForUpdate();
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() {
        ECOStorageSystemBlockEntity controller = getController();
        return controller == null ? ECOStorageInterfaceMode.STORAGE : controller.getStorageInterfaceMode();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Nullable public IECOStorageCell getCellInventory() {
        if (cellStack == null) {
            invalidateCellInventoryCache();
            return null;
        }
        if (level != null && level.isClientSide) {
            return null;
        }
        if (cachedCellInventory == null || cachedCellInventoryStack != cellStack) {
            cachedCellInventory = ECOStorageCells.getCellInventory(cellStack, cellSaveProvider);
            cachedCellInventoryStack = cellStack;
        }
        return cachedCellInventory;
    }

    @Nullable public CellState getRenderedCellState() {
        return lastSyncedCellState;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (cluster != null && cluster.getController() != null) {
            IECOTier mainTier = cluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null
                    && !cluster.getController().isInfiniteMode()
                    && !ECOInfiniteStorageMember.isMember(cellStack)
                    && !cluster.getController().isStorageInterfaceTransferMode()
                    && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                int priority = cluster.getController().getPriority();
                storageMounts.mount(cellInventory, priority);
                boolean mountedChanged = !mounted;
                mounted = true;
                setChanged();
                lastSyncedCellState = getCurrentCellState();
                if (mountedChanged) {
                    markForUpdate();
                }
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
        notifyControllerRefresh();
    }

    @Override
    public void onReady() {
        super.onReady();
        updateStorageProviderState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        online = getMainNode().isOnline();
        updateStorageProviderState();
        markForUpdate();
        setChanged();
    }

    public void notifyPersistence() {
        markCellContentDirty();
    }

    public void convertCellToInfiniteMember(UUID domainId) {
        flushPendingCellContent();
        releaseCellBackend();
        if (cellStack != null && !cellStack.isEmpty()) {
            ECOInfiniteStorageMember.clearStoredContents(cellStack);
            ECOInfiniteStorageMember.markMember(cellStack, domainId);
        }
        invalidateCellInventoryCache();
        lastSyncedCellState = null;
        markForUpdate();
        setChanged();
        notifyControllerRefresh();
    }

    public boolean convertInfiniteMemberToNormalStorage(UUID domainId) {
        flushPendingCellContent();
        releaseCellBackend();
        if (!ECOInfiniteStorageMember.isMemberOf(cellStack, domainId)) {
            return false;
        }
        ECOInfiniteStorageMember.clearMember(cellStack);
        invalidateCellInventoryCache();
        lastSyncedCellState = null;
        markForUpdate();
        setChanged();
        notifyControllerRefresh();
        return true;
    }

    public long getRestoreReceipt(UUID transactionId) {
        RestoreReceipt receipt = getRestoreReceiptDetails(transactionId);
        return receipt == null ? 0L : receipt.amount();
    }

    @Nullable public RestoreReceipt getRestoreReceiptDetails(UUID transactionId) {
        return transactionId == null ? null : restoreReceipts.get(transactionId);
    }

    public boolean hasRestoreReceipt(UUID transactionId) {
        return transactionId != null
                && (restoreReceipts.containsKey(transactionId) || unverifiableRestoreReceipts.contains(transactionId));
    }

    public boolean hasUnexpectedRestoreReceipts(Set<UUID> expectedTransactionIds) {
        if (!unverifiableRestoreReceipts.isEmpty()) {
            return true;
        }
        for (UUID transactionId : restoreReceipts.keySet()) {
            if (!expectedTransactionIds.contains(transactionId)) {
                return true;
            }
        }
        return false;
    }

    public void putRestoreReceipt(UUID transactionId, long amount, long postAmount) {
        if (transactionId == null || amount <= 0L || postAmount < amount) {
            return;
        }
        restoreReceipts.put(transactionId, new RestoreReceipt(amount, postAmount));
        unverifiableRestoreReceipts.remove(transactionId);
        setChanged();
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        loadDriveVisualState(data);
        restoreReceipts.clear();
        unverifiableRestoreReceipts.clear();
        var receipts = data.getList("ecoRestoreReceipts", Tag.TAG_COMPOUND);
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag receipt = receipts.getCompound(i);
            if (receipt.hasUUID("id")) {
                UUID transactionId = receipt.getUUID("id");
                long amount = receipt.getLong("amount");
                long postAmount = receipt.getLong("post_amount");
                if (receipt.getInt("version") == 2 && amount > 0L && postAmount >= amount) {
                    restoreReceipts.put(transactionId, new RestoreReceipt(amount, postAmount));
                } else {
                    // Retain V1 receipts as a safety barrier: they cannot prove the current cell contents.
                    unverifiableRestoreReceipts.add(transactionId);
                }
            }
        }
        invalidateCellInventoryCache();
        pendingContentSave = false;
        pendingControllerStatsDirty = false;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        flushPendingCellContent();
        super.saveAdditional(data);
        saveDriveVisualState(data);
        var receipts = new net.minecraft.nbt.ListTag();
        for (var entry : restoreReceipts.entrySet()) {
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("id", entry.getKey());
            receipt.putInt("version", 2);
            receipt.putLong("amount", entry.getValue().amount());
            receipt.putLong("post_amount", entry.getValue().postAmount());
            receipts.add(receipt);
        }
        for (UUID transactionId : unverifiableRestoreReceipts) {
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("id", transactionId);
            receipt.putInt("version", 1);
            receipts.add(receipt);
        }
        data.put("ecoRestoreReceipts", receipts);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveDriveUpdateState(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        loadDriveVisualState(tag);
    }

    @Override
    protected void saveVisualState(CompoundTag data) {
        flushPendingCellContent();
        super.saveVisualState(data);
        saveDriveUpdateState(data);
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

    private void saveDriveUpdateState(CompoundTag data) {
        if (cellStack != null && !cellStack.isEmpty()) {
            data.put("cellStack", saveDisplayCellStack(cellStack));
        }
        data.putBoolean("mounted", mounted);
        data.putBoolean("online", online);
        CellState cellState = lastSyncedCellState != null ? lastSyncedCellState : getCurrentCellState();
        if (cellState != null) {
            data.putString("cellState", cellState.name());
        }
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
        this.lastSyncedCellState = readCellState(data);
        if (this.lastSyncedCellState == null && (level == null || !level.isClientSide)) {
            this.lastSyncedCellState = getCurrentCellState();
        }
    }

    @Nullable private CellState getCurrentCellState() {
        IECOStorageCell cellInventory = getCellInventory();
        return cellInventory == null ? null : cellInventory.getStatus();
    }

    private static @Nullable ItemStack normalizeCellStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.copyWithCount(1);
    }

    private static CompoundTag saveDisplayCellStack(ItemStack stack) {
        ItemStack displayStack = new ItemStack(stack.getItem(), 1);
        CompoundTag sourceTag = stack.getTag();
        if (sourceTag != null) {
            CompoundTag displayTag = new CompoundTag();
            copyDisplayTag(sourceTag, displayTag, "display");
            copyDisplayTag(sourceTag, displayTag, "CustomModelData");
            copyDisplayTag(sourceTag, displayTag, "HideFlags");
            copyDisplayTag(sourceTag, displayTag, "Enchantments");
            copyDisplayTag(sourceTag, displayTag, "StoredEnchantments");
            copyDisplayTag(sourceTag, displayTag, "Trim");
            ECOInfiniteStorageMember.copyClientSyncTags(sourceTag, displayTag);
            if (!displayTag.isEmpty()) {
                displayStack.setTag(displayTag);
            }
        }
        return displayStack.save(new CompoundTag());
    }

    @Nullable private static CellState readCellState(CompoundTag data) {
        if (!data.contains("cellState")) {
            return null;
        }
        try {
            return CellState.valueOf(data.getString("cellState"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void copyDisplayTag(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key)) {
            Tag value = source.get(key);
            if (value != null) {
                target.put(key, value.copy());
            }
        }
    }

    public record RestoreReceipt(long amount, long postAmount) {}

    /**
     * Notifies the storage controller (if formed) that storage stats should be
     * recalculated after a cell change. Safe to call on either side; only
     * executes on the server.
     */
    private void notifyControllerRefresh() {
        if (level == null || level.isClientSide) return;
        if (cluster == null || cluster.getController() == null) return;
        cluster.getController().refreshStorageUiState();
    }

    private boolean pendingContentSave = false;
    private boolean pendingControllerStatsDirty = false;

    private void markCellContentDirty() {
        if (!(level instanceof ServerLevel) || isRemoved()) {
            return;
        }

        boolean firstDirty = !pendingContentSave;
        pendingContentSave = true;
        pendingControllerStatsDirty = true;
        if (firstDirty) {
            setChanged();
        }
    }

    private void flushPendingContentChanges() {
        boolean loaded = processDeferredCellLoad();
        if (loaded) {
            updateStorageProviderState();
            IStorageProvider.requestUpdate(getMainNode());
            markForUpdate();
            setChanged();
        }
        flushPendingCellContent();
        if (pendingControllerStatsDirty) {
            pendingControllerStatsDirty = false;
            notifyControllerRefresh();
        }
    }

    private boolean processDeferredCellLoad() {
        if (cachedCellInventory instanceof ECOStorageCell storageCell) {
            boolean loaded = storageCell.processDeferredLoad(500_000L);
            if (loaded) {
                lastSyncedCellState = getCurrentCellState();
                pendingControllerStatsDirty = true;
            }
            return loaded;
        }
        return false;
    }

    private void flushPendingCellContent() {
        if (!pendingContentSave) {
            return;
        }
        if (cachedCellInventory != null) {
            cachedCellInventory.persist();
        }
        pendingContentSave = false;
        CellState currentState = getCurrentCellState();
        if (currentState != lastSyncedCellState) {
            lastSyncedCellState = currentState;
            markForUpdate();
        }
        setChanged();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel) || isRemoved()) {
            return;
        }
        flushPendingContentChanges();
    }

    private void invalidateCellInventoryCache() {
        cachedCellInventory = null;
        cachedCellInventoryStack = null;
    }

    private void forkDuplicateCellInCurrentHost() {
        if (!(level instanceof ServerLevel) || this.cellStack == null || cluster == null) {
            return;
        }
        List<ItemStack> mountedStacks = new ArrayList<>();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (drive == this) {
                continue;
            }
            ItemStack mountedStack = drive.getCellStack();
            if (mountedStack != null && !mountedStack.isEmpty()) {
                mountedStacks.add(mountedStack);
            }
        }
        ECOCellStorageManager.forkIfAlreadyPresent(this.cellStack, mountedStacks);
    }

    public void invalidateCellInventoryForHostChange() {
        flushPendingCellContent();
        invalidateCellInventoryCache();
        lastSyncedCellState = getCurrentCellState();
        markForUpdate();
        setChanged();
        notifyControllerRefresh();
    }

    @Nullable private ECOStorageSystemBlockEntity getController() {
        return cluster == null ? null : cluster.getController();
    }

    @Override
    public void onChunkUnloaded() {
        flushPendingCellContent();
        releaseCellBackend();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        flushPendingCellContent();
        releaseCellBackend();
        super.setRemoved();
    }

    private void releaseCellBackend() {
        ECOStorageCells.releaseCellInventory(cellStack, cellSaveProvider);
        ECOCellStorageManager.release(cellStack, cellSaveProvider);
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
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

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> HANDLER);
    }
}
