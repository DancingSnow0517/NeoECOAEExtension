package cn.dancingsnow.neoecoae.blocks.entity;

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
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ECOLargeIntegratedWorkingStationOutputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;

    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
            notifyController();
        }
    };

    private final FluidTankResourceHandler drainOnlyHandler = FluidTankResourceHandler.extractOnly(() -> tank);

    /** 26.1 fluid capability ({@code Capabilities.Fluid.BLOCK}): drain-only view of {@link #tank}. */
    public ResourceHandler<FluidResource> getFluidResourceHandler() { return drainOnlyHandler; }

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
            ResourceHandler<FluidResource> target = level.getCapability(
                Capabilities.Fluid.BLOCK, neighborPos, face.getOpposite());
            if (target != null && !tank.isEmpty()) {
                try (Transaction transaction = Transaction.open(null)) {
                    if (ResourceHandlerUtil.move(drainOnlyHandler, target, fluid -> true, tank.getFluidAmount(), transaction) > 0) {
                        transaction.commit();
                        return;
                    }
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(
            holder, tank, "block.neoecoae.large_integrated_working_station_output_hatch", false, true);
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        tank.serialize(data);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        tank.deserialize(data);
    }
}
