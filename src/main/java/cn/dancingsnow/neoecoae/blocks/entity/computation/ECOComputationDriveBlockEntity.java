package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.util.CellHostItemHandler;
import cn.dancingsnow.neoecoae.util.ICellHost;
import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECOComputationDriveBlockEntity
    extends AbstractComputationBlockEntity<ECOComputationDriveBlockEntity>
    implements IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IEnhancedManaged, ICellHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ECOComputationDriveBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    @Setter
    @DescSynced
    @Persisted
    @RequireRerender
    @Nullable
    private ItemStack cellStack = null;

    @Getter
    private final IItemHandler itemHandler = new CellHostItemHandler(this);

    public ECOComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void scheduleRenderUpdate() {
        SectionPos sectionPos = SectionPos.of(worldPosition);
        Minecraft.getInstance().levelRenderer
            .setSectionDirty(sectionPos.x(), sectionPos.y(), sectionPos.z());
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
    public void onChanged() {
        setChanged();
        markForUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (cellStack != null) {
            drops.add(cellStack);
        }
    }

    @Override
    public IManagedStorage getRootStorage() {
        return syncStorage;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof ECOComputationCellItem;
    }
}
