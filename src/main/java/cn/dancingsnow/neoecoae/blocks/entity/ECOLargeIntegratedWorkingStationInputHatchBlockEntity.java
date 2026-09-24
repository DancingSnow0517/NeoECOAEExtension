package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Fluid intake for the formed workstation. */
public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
        extends NEBlockEntity<
                NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;
    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
            if (cluster != null && cluster.getController() != null)
                cluster.getController().onChangeTankFromHatch();
        }
    };
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> tank);

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NEIntegratedWorkingStationClusterCalculator::new);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!isFormed()) return;
        for (Direction face : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(face));
            if (neighbor == null || cluster != null && clusterContains(neighbor)) continue;
            IFluidHandler source = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite())
                    .orElse(null);
            if (source != null
                    && !FluidUtil.tryFluidTransfer(tank, source, tank.getCapacity(), true)
                            .isEmpty()) return;
        }
    }

    private boolean clusterContains(BlockEntity entity) {
        var members = cluster.getBlockEntities();
        while (members.hasNext()) if (members.next() == entity) return true;
        return entity == cluster.getController();
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put("largeWorkstationTank", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.contains("largeWorkstationTank")) tank.readFromNBT(data.getCompound("largeWorkstationTank"));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.FLUID_HANDLER ? fluidCapability.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> tank);
    }
}
