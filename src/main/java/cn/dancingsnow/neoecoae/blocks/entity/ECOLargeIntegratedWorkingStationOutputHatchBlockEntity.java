package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.util.AdjacentFluidHandlers;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ECOLargeIntegratedWorkingStationOutputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;
    private final AdjacentFluidHandlers adjacentFluids = new AdjacentFluidHandlers();
    private NEIntegratedWorkingStationCluster fluidTopology;
    private int internalFluidFaces;

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
            getCluster().getController().onOutputTankChanged();
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || tank.isEmpty()) return;
        var cluster = getCluster();
        if (cluster != null && cluster.isDestroyed()) cluster = null;
        if (fluidTopology != cluster) {
            fluidTopology = cluster;
            internalFluidFaces = cluster == null ? 0 : AdjacentFluidHandlers.mask(cluster.getInternalConnections(this));
        }
        for (int faces = adjacentFluids.candidateFaces(serverLevel) & ~internalFluidFaces; faces != 0; faces &= faces - 1) {
            var face = AdjacentFluidHandlers.firstFace(faces);
            IFluidHandler target = adjacentFluids.get(serverLevel, pos, face);
            if (target != null
                && !FluidUtil.tryFluidTransfer(target, tank, tank.getFluidAmount(), true).isEmpty()) {
                return;
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(
            holder, tank, "block.neoecoae.large_integrated_working_station_output_hatch", false, true);
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
