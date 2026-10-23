package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ui.NEBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class ECOLargeIntegratedWorkingStationOutputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> implements NEBlockEntityUI {
    public static final int CAPACITY = 1_024_000;

    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
            notifyController();
        }
    };

    public ECOLargeIntegratedWorkingStationOutputHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEIntegratedWorkingStationClusterCalculator::new);
    }

    private void notifyController() {
        if (getCluster() != null && getCluster().getController() != null) {
            getCluster().getController().onChangeTankFromHatch();
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        var cluster = getCluster();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (cluster != null && cluster.containsBlockEntity(level.getBlockEntity(neighborPos))) {
                continue;
            }
            IFluidHandler target = cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, neighborPos, ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
            if (target != null
                && !FluidUtil.tryFluidTransfer(target, tank, tank.getFluidAmount(), true).isEmpty()) {
                return;
            }
        }
    }

    public ModularUI createUI(Player player) {
        return FluidHatchUI.createFluidHatchUI(
            this, player, tank, "block.neoecoae.large_integrated_working_station_output_hatch", false, true);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        tank.readFromNBT(data);
    }
}
