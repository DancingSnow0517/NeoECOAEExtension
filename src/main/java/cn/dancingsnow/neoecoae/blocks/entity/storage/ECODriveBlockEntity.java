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
    implements ISyncPersistRPCBlockEntity, IStorageProvider, ICellHost {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public final IItemHandler HANDLER = new CellHostItemHandler(this);

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
        this.cellStack = cellStack;
        if (cellStack != null) {
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(ECODriveBlock.HAS_CELL, true));
        } else {
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(ECODriveBlock.HAS_CELL, false));
        }
        updateState();
        this.cellStack = cellStack;
        setChanged();
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return ECOStorageCells.isCellHandled(stack);
    }

    private void updateState() {
        double power = 256;
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
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
    public IECOStorageCell getCellInventory() {
        if (cellStack != null) {
            return ECOStorageCells.getCellInventory(cellStack, null);
        }
        return null;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null) {
            IECOTier mainTier = storageCluster.getController().getTier();
            IECOStorageCell cellInventory = getCellInventory();
            if (cellInventory != null && mainTier.compareTo(cellInventory.getTier()) >= 0) {
                storageMounts.mount(cellInventory);
                mounted = true;
                setChanged();
                return;
            }
        }
        mounted = false;
        setChanged();
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
}
