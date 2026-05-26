package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.mojang.logging.LogUtils;
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
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ECODriveBlockEntity extends AbstractStorageBlockEntity<ECODriveBlockEntity>
    implements ISyncPersistRPCBlockEntity, IStorageProvider, ICellHost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_UPDATE_TAGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_STORAGE_UPDATES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MOUNT_ATTEMPTS = ConcurrentHashMap.newKeySet();

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new CellHostItemHandler(this);
    private final LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> HANDLER);

    @Getter
    @DescSynced
    @Persisted
    @RequireRerender
    @Nullable
    private ItemStack cellStack = null;

    @Getter
    @DescSynced
    private boolean mounted = false;
    @Getter
    @DescSynced
    private boolean online = false;

    public ECODriveBlockEntity(
        BlockEntityType<ECODriveBlockEntity> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState);
        getMainNode().addService(IStorageProvider.class, this);
    }

    @Override
    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = normalizeCellStack(cellStack);
        if (getLevel() != null && getBlockState().hasProperty(ECODriveBlock.HAS_CELL)) {
            boolean oldHasCell = getBlockState().getValue(ECODriveBlock.HAS_CELL);
            boolean newHasCell = this.cellStack != null;
            BlockState newState = getBlockState().setValue(ECODriveBlock.HAS_CELL, newHasCell);
            if (!FMLEnvironment.production) {
                LOGGER.info(
                    "ECODrive setCellStack: pos={}, oldHasCell={}, newHasCell={}, cellItem={}, resultingState={}",
                    getBlockPos(),
                    oldHasCell,
                    newHasCell,
                    this.cellStack == null ? "empty" : ForgeRegistries.ITEMS.getKey(this.cellStack.getItem()),
                    newState
                );
            }
            if (oldHasCell != newHasCell) {
                getLevel().setBlockAndUpdate(getBlockPos(), newState);
            }
        }
        updateStorageProviderState("setCellStack");
        markForUpdate();
        setChanged();
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
        logStorageProviderUpdate(source, power);
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
        if (cellStack != null) {
            return ECOStorageCells.getCellInventory(cellStack, this::saveChanges);
        }
        return null;
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
                mounted = true;
                setChanged();
                markForUpdate();
                logMountResult(true);
                return;
            }
        }
        mounted = false;
        setChanged();
        markForUpdate();
        logMountResult(false);
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
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        loadDriveVisualState(data);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
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
        super.saveVisualState(data);
        saveDriveVisualState(data);
    }

    @Override
    protected void loadVisualState(CompoundTag data) {
        super.loadVisualState(data);
        loadDriveVisualState(data);
    }

    private void saveDriveVisualState(CompoundTag data) {
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
        this.mounted = data.getBoolean("mounted");
        this.online = data.getBoolean("online");
    }

    private void logVisualSync(String source, CompoundTag data) {
        if (FMLEnvironment.production) {
            return;
        }
        String cellItem = cellStack == null
            ? "empty"
            : String.valueOf(ForgeRegistries.ITEMS.getKey(cellStack.getItem()));
        String key = source
            + "|" + getBlockPos()
            + "|" + cellItem
            + "|" + data.contains("cellStack")
            + "|" + (level != null && level.isClientSide());
        if (LOGGED_UPDATE_TAGS.add(key)) {
            LOGGER.info(
                "ECODrive visual sync {}: pos={}, cellItem={}, tagHasCellStack={}, mounted={}, online={}, clientSide={}",
                source,
                getBlockPos(),
                cellItem,
                data.contains("cellStack"),
                mounted,
                online,
                level != null && level.isClientSide()
            );
        }
    }

    private static @Nullable ItemStack normalizeCellStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.copyWithCount(1);
    }

    private void logStorageProviderUpdate(String source, double power) {
        if (FMLEnvironment.production) {
            return;
        }
        String key = source
            + "|" + getBlockPos()
            + "|" + (cluster != null)
            + "|" + (cluster != null && cluster.getController() != null)
            + "|" + (cellStack == null ? "empty" : ForgeRegistries.ITEMS.getKey(cellStack.getItem()))
            + "|" + getMainNode().isOnline()
            + "|" + getMainNode().isActive()
            + "|" + power;
        if (LOGGED_STORAGE_UPDATES.add(key)) {
            LOGGER.info(
                "ECODrive storage provider update: source={}, pos={}, hasCluster={}, hasController={}, cellItem={}, idlePower={}, nodeOnline={}, nodeActive={}",
                source,
                getBlockPos(),
                cluster != null,
                cluster != null && cluster.getController() != null,
                cellStack == null ? "empty" : ForgeRegistries.ITEMS.getKey(cellStack.getItem()),
                power,
                getMainNode().isOnline(),
                getMainNode().isActive()
            );
        }
    }

    private void logMountAttempt(
        boolean hasCluster,
        boolean hasController,
        @Nullable IECOTier mainTier,
        @Nullable IECOStorageCell cellInventory,
        @Nullable IECOTier cellTier,
        boolean tierSupported,
        boolean willMount
    ) {
        if (FMLEnvironment.production) {
            return;
        }
        String key = getBlockPos()
            + "|" + hasCluster
            + "|" + hasController
            + "|" + mainTier
            + "|" + (cellStack == null ? "empty" : ForgeRegistries.ITEMS.getKey(cellStack.getItem()))
            + "|" + (cellInventory != null)
            + "|" + cellTier
            + "|" + tierSupported
            + "|" + willMount
            + "|" + getMainNode().isOnline()
            + "|" + getMainNode().isActive();
        if (LOGGED_MOUNT_ATTEMPTS.add(key)) {
            LOGGER.info(
                "ECODrive mountInventories: pos={}, hasCluster={}, hasController={}, controllerTier={}, cellItem={}, cellInventory={}, cellTier={}, tierSupported={}, willMount={}, nodeOnline={}, nodeActive={}",
                getBlockPos(),
                hasCluster,
                hasController,
                mainTier,
                cellStack == null ? "empty" : ForgeRegistries.ITEMS.getKey(cellStack.getItem()),
                cellInventory != null,
                cellTier,
                tierSupported,
                willMount,
                getMainNode().isOnline(),
                getMainNode().isActive()
            );
        }
    }

    private void logMountResult(boolean mounted) {
        if (FMLEnvironment.production) {
            return;
        }
        LOGGER.info(
            "ECODrive mountInventories result: pos={}, mounted={}, online={}, nodeOnline={}, nodeActive={}",
            getBlockPos(),
            mounted,
            online,
            getMainNode().isOnline(),
            getMainNode().isActive()
        );
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
