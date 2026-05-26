package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;

import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

public class ECOComputationDriveBlockEntity
    extends AbstractComputationBlockEntity<ECOComputationDriveBlockEntity> implements ISyncPersistRPCBlockEntity, ICellHost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_UPDATE_TAGS = ConcurrentHashMap.newKeySet();

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    @DescSynced
    @Persisted
    @RequireRerender
    @Nullable
    private ItemStack cellStack = null;

    @DescSynced
    @RequireRerender
    private boolean formedState;

    @Getter
    @Setter
    @DescSynced
    @RequireRerender
    private boolean isLowerDrive = false;

    @Setter
    @Getter
    @DescSynced
    @RequireRerender
    @Nullable
    private BlockPos ownerBlockPos;

    @Setter
    @Getter
    @DescSynced
    @Nullable
    private IECOTier tier = null;

    @Getter
    private final IItemHandler itemHandler = new CellHostItemHandler(this);
    private final LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> itemHandler);

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = normalizeCellStack(cellStack);
        if (getLevel() != null && getBlockState().hasProperty(ECOComputationDrive.HAS_CELL)) {
            boolean oldHasCell = getBlockState().getValue(ECOComputationDrive.HAS_CELL);
            boolean newHasCell = this.cellStack != null;
            BlockState newState = getBlockState().setValue(ECOComputationDrive.HAS_CELL, newHasCell);
            if (!FMLEnvironment.production) {
                LOGGER.info(
                    "ECOComputationDrive setCellStack: pos={}, oldHasCell={}, newHasCell={}, cellItem={}, resultingState={}",
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
        if (this.cluster != null) {
            this.cluster.recalculateRemainingStorage();
        }
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
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Override
    public boolean isFormed() {
        return formedState;
    }

    @Override
    public void setFormed(boolean formed) {
        this.formed = formed;
        this.formedState = formed;
        syncFormedBlockState(formed, "setFormed");
        setChanged();
    }

    @Override
    public void updateState(boolean updateExposed) {
        boolean newFormed = cluster != null;
        this.formed = newFormed;
        this.formedState = newFormed;
        syncFormedBlockState(newFormed, "updateState");
        if (updateExposed) {
            onGridConnectableSidesChanged();
        }
        if (cluster != null) {
            ECOComputationSystemBlockEntity controller = cluster.getController();
            if (controller != null) {
                tier = controller.getTier();
                ownerBlockPos = controller.getBlockPos();
            } else {
                tier = null;
                ownerBlockPos = null;
            }
        } else {
            tier = null;
            ownerBlockPos = null;
        }
    }

    @Override
    public void disconnect(boolean update) {
        super.disconnect(update);
        isLowerDrive = false;
        this.formed = false;
        this.formedState = false;
        syncFormedBlockState(false, "disconnect");
        setChanged();
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof ECOComputationCellItem;
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
        data.putBoolean("formedState", formedState);
        data.putBoolean("isLowerDrive", isLowerDrive);
        if (ownerBlockPos != null) {
            data.putLong("ownerBlockPos", ownerBlockPos.asLong());
        }
    }

    private void loadDriveVisualState(CompoundTag data) {
        if (data.contains("cellStack")) {
            this.cellStack = normalizeCellStack(ItemStack.of(data.getCompound("cellStack")));
        } else {
            this.cellStack = null;
        }
        this.formedState = data.getBoolean("formedState");
        this.isLowerDrive = data.getBoolean("isLowerDrive");
        this.ownerBlockPos = data.contains("ownerBlockPos") ? BlockPos.of(data.getLong("ownerBlockPos")) : null;
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
                "ECOComputationDrive visual sync {}: pos={}, cellItem={}, tagHasCellStack={}, formedState={}, lowerDrive={}, clientSide={}",
                source,
                getBlockPos(),
                cellItem,
                data.contains("cellStack"),
                formedState,
                isLowerDrive,
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

    private void syncFormedBlockState(boolean newFormed, String source) {
        if (level == null || notLoaded() || isRemoved() || !getBlockState().hasProperty(ECOComputationDrive.FORMED)) {
            return;
        }
        boolean oldFormed = getBlockState().getValue(ECOComputationDrive.FORMED);
        BlockState newState = getBlockState().setValue(ECOComputationDrive.FORMED, newFormed);
        if (!FMLEnvironment.production && oldFormed != newFormed) {
            LOGGER.info(
                "ECOComputationDrive formed state update: source={}, pos={}, oldBlockFormed={}, newBlockFormed={}, formedState={}, hasCluster={}, resultingState={}",
                source,
                getBlockPos(),
                oldFormed,
                newFormed,
                formedState,
                cluster != null,
                newState
            );
        }
        if (oldFormed != newFormed) {
            level.setBlock(getBlockPos(), newState, Block.UPDATE_ALL_IMMEDIATE);
        }
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
