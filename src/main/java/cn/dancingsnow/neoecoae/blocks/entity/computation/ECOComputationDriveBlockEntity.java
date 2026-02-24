package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;

import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECOComputationDriveBlockEntity
    extends AbstractComputationBlockEntity<ECOComputationDriveBlockEntity> implements ISyncPersistRPCBlockEntity, ICellHost {

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

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = cellStack;
        if (this.cluster != null) {
            this.cluster.recalculateRemainingStorage();
        }
        setChanged();
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
        this.formedState = formed;
        setChanged();
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        formedState = cluster != null;
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
        setChanged();
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof ECOComputationCellItem;
    }
}
