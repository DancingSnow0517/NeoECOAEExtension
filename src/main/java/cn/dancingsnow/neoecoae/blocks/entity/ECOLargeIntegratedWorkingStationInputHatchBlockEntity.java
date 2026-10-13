package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;

    public final FluidTank tank = new FluidTank(CAPACITY, this::isCoolant) {
        @Override
        protected void onContentsChanged() {
            persistAndSync();
            notifyController();
        }
    };

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEIntegratedWorkingStationClusterCalculator::new);
    }

    private boolean isCoolant(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) return false;
        for (var holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            if (holder.value().input().ingredient().test(fluid)) return true;
        }
        return false;
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
            IFluidHandler source = level.getCapability(
                Capabilities.FluidHandler.BLOCK, neighborPos, face.getOpposite());
            if (source != null
                && !FluidUtil.tryFluidTransfer(tank, source, tank.getCapacity(), true).isEmpty()) {
                return;
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(
            holder, tank, "block.neoecoae.large_integrated_working_station_input_hatch", true, true);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
    }
}
