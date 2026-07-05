package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class ECOComputationDriveBlockEntity extends AbstractComputationBlockEntity<ECOComputationDriveBlockEntity>
        implements ICellHost {
    @Getter
    @Nullable private ItemStack cellStack = null;

    private boolean formedState;

    @Getter
    private boolean isLowerDrive = false;

    @Setter
    @Getter
    @Nullable private BlockPos ownerBlockPos;

    @Setter
    @Getter
    @Nullable private IECOTier tier = null;

    @Getter
    private final IItemHandler itemHandler = new CellHostItemHandler(this);

    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> itemHandler);

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = normalizeCellStack(cellStack);
        if (getLevel() != null && getBlockState().hasProperty(ECOComputationDrive.HAS_CELL)) {
            boolean oldHasCell = getBlockState().getValue(ECOComputationDrive.HAS_CELL);
            boolean newHasCell = this.cellStack != null;
            BlockState newState = getBlockState().setValue(ECOComputationDrive.HAS_CELL, newHasCell);
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
    public boolean shouldNotifyPersistenceOnRead() {
        return true;
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

    public void setLowerDrive(boolean lowerDrive) {
        if (this.isLowerDrive != lowerDrive) {
            this.isLowerDrive = lowerDrive;
            markForUpdate();
            setChanged();
        }
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
        markForUpdate();
        setChanged();
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
    public boolean canExtractCell() {
        return cluster == null || !cluster.hasActiveCraftingJobs();
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
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        loadDriveVisualState(tag);
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
        if (tier != null) {
            data.putInt("tier", tier.getTier());
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
        this.tier = data.contains("tier") ? tierFromId(data.getInt("tier")) : null;
    }

    private static @Nullable IECOTier tierFromId(int tier) {
        return switch (tier) {
            case 1 -> ECOTier.L4;
            case 2 -> ECOTier.L6;
            case 3 -> ECOTier.L9;
            default -> null;
        };
    }

    private static @Nullable ItemStack normalizeCellStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.copyWithCount(1);
    }

    private void syncFormedBlockState(boolean newFormed, String source) {
        if (level == null || isRemoved() || !getBlockState().hasProperty(ECOComputationDrive.FORMED)) {
            return;
        }
        boolean oldFormed = getBlockState().getValue(ECOComputationDrive.FORMED);
        BlockState newState = getBlockState().setValue(ECOComputationDrive.FORMED, newFormed);
        if (oldFormed != newFormed) {
            level.setBlock(getBlockPos(), newState, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
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

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> itemHandler);
    }
}
