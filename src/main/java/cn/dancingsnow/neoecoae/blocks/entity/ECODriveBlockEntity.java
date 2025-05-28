package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.client.model.data.ECODriveModelData;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECODriveBlockEntity extends NEBlockEntity<NEStorageCluster, ECODriveBlockEntity>
    implements IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IManaged {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ECODriveBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    @DescSynced
    @Persisted
    @Nullable
    private ItemStack cellStack = null;

    public ECODriveBlockEntity(
        BlockEntityType<ECODriveBlockEntity> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<ECODriveBlockEntity, NEStorageCluster> factory
    ) {
        super(type, pos, blockState, factory.create());
    }

    @Override
    public ModelData getModelData() {
        return ECODriveModelData.create(cellStack == null ? ItemStack.EMPTY : cellStack);
    }

    public void setCellStack(@Nullable ItemStack cellStack) {
        this.cellStack = cellStack;
        markDirty("cellStack");
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public IManagedStorage getSyncStorage() {
        return syncStorage;
    }

    @Override
    public void onSyncChanged(IRef ref, boolean isDirty) {
        IManaged.super.onSyncChanged(ref, isDirty);
        if (level instanceof ClientLevel) {
            onChangedClient();
        }
    }

    @Override
    public void onChanged() {
        setChanged();
    }

    private void onChangedClient() {
        Minecraft.getInstance().execute(this::requestModelDataUpdate);
    }

    @Override
    public IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }
}
