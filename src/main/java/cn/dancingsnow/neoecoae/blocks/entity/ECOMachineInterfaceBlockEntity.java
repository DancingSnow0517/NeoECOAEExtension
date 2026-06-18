package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageInterfaceUiState;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOMachineInterfaceBlockEntity<C extends NECluster<C>>
        extends NEBlockEntity<C, ECOMachineInterfaceBlockEntity<C>> implements IUIHolder.BlockEntityUI {
    private static final String NBT_STORAGE_INTERFACE_MODE = "storageInterfaceMode";

    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;
    private long exportedLastTick;
    private long exportedTotal;

    public ECOMachineInterfaceBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState, calculator);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.allOf(Direction.class);
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() {
        return storageInterfaceMode;
    }

    public boolean isStorageOutputMode() {
        return storageInterfaceMode == ECOStorageInterfaceMode.OUTPUT;
    }

    public boolean supportsStorageInterfaceUi() {
        return cluster instanceof NEStorageCluster || calculator instanceof NEStorageClusterCalculator;
    }

    public void setStorageInterfaceMode(ECOStorageInterfaceMode mode) {
        if (mode == null) {
            mode = ECOStorageInterfaceMode.STORAGE;
        }
        if (storageInterfaceMode == mode) {
            return;
        }
        storageInterfaceMode = mode;
        exportedLastTick = 0L;
        setChanged();
        markForUpdate();
        notifyStorageControllerModeChanged();
    }

    public void recordStorageInterfaceExport(long amount) {
        exportedLastTick = Math.max(0L, amount);
        if (amount > 0L) {
            exportedTotal = saturatedAdd(exportedTotal, amount);
            setChanged();
        }
    }

    public NEStorageInterfaceUiState createStorageInterfaceUiState() {
        boolean hasController =
                cluster instanceof NEStorageCluster storageCluster && storageCluster.getController() != null;
        boolean targetOnline = getMainNode().isOnline() && getMainNode().getGrid() != null;
        return new NEStorageInterfaceUiState(
                worldPosition,
                formed,
                storageInterfaceMode,
                exportedLastTick,
                exportedTotal,
                targetOnline,
                hasController);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModularUI createUI(Player player) {
        if (supportsStorageInterfaceUi()) {
            return NELDLibUis.createStorageInterface((ECOMachineInterfaceBlockEntity<NEStorageCluster>) this, player);
        }
        return null;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_STORAGE_INTERFACE_MODE, storageInterfaceMode.name());
        tag.putLong("exportedTotal", exportedTotal);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(NBT_STORAGE_INTERFACE_MODE));
        exportedTotal = Math.max(0L, tag.getLong("exportedTotal"));
    }

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putString(NBT_STORAGE_INTERFACE_MODE, storageInterfaceMode.name());
        tag.putLong("exportedLastTick", exportedLastTick);
        tag.putLong("exportedTotal", exportedTotal);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(NBT_STORAGE_INTERFACE_MODE));
        exportedLastTick = Math.max(0L, tag.getLong("exportedLastTick"));
        exportedTotal = Math.max(0L, tag.getLong("exportedTotal"));
    }

    private void notifyStorageControllerModeChanged() {
        if (level == null || level.isClientSide || !(cluster instanceof NEStorageCluster storageCluster)) {
            return;
        }
        if (storageCluster.getController() != null) {
            storageCluster.getController().onStorageInterfaceModeChanged();
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
