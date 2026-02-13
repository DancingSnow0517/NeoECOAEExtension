package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECODriveBlockEntity extends AbstractStorageBlockEntity<ECODriveBlockEntity>
    implements ISyncPersistRPCBlockEntity, IStorageProvider {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new IItemHandler() {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return cellStack != null ? cellStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (cellStack != null) {
                return stack;
            } else {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    setCellStack(stack.copyWithCount(1));
                }
                ItemStack copy = stack.copy();
                copy.shrink(1);
                return copy;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (cellStack == null) {
                return ItemStack.EMPTY;
            }
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack copy = cellStack.copyWithCount(1);
            if (!simulate) {
                setCellStack(null);
            }
            return copy;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof ECOStorageCellItem;
        }
    };

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

    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = cellStack;
        if (cellStack != null) {
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(ECODriveBlock.HAS_CELL, true));
        } else {
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(ECODriveBlock.HAS_CELL, false));
        }
        updateState();
        this.cellStack = cellStack;
    }

    private void updateState() {
        double power = 256;
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            ECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                power += cellInventory.getIdleDrain();
            }
        }
        getMainNode().setIdlePowerUsage(power);
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    public void scheduleRenderUpdate() {
        markForClientUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Nullable
    public ECOStorageCell getCellInventory() {
        if (cellStack != null) {
            return ECOStorageCellItem.getCellInventory(cellStack);
        }
        return null;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            ECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                storageMounts.mount(cellInventory);
                mounted = true;
                return;
            }
        }
        mounted = false;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        online = getMainNode().isOnline();
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }
}
