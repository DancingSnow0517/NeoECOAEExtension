package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
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

public class ECOFluidOutputHatchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidOutputHatchBlockEntity> {

    /** @deprecated Use {@link #getFluidTankHandler()}; operate on the buffer without replacing it. */
    @Deprecated
    public FluidTank tank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
    }

    /** Stable addon handler view. */
    public FluidTank getFluidTankHandler() { return tank; }

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

    /**
     * After a pass over every face moves nothing, wait this long before probing neighbours again. Not
     * persisted: a reload simply probes on its first tick.
     */
    private static final long IDLE_TRANSFER_BACKOFF_TICKS = 20L;
    private long nextTransferTick;

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (tank.isEmpty()) return;
        long now = level.getGameTime();
        if (now < nextTransferTick) return;
        for (Direction face : Direction.values()) {
            IFluidHandler targetHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(face), face.getOpposite());
            if (targetHandler != null) {
                if (!FluidUtil.tryFluidTransfer(targetHandler, tank, tank.getFluidAmount(), true).isEmpty()) {
                    return;
                }
            }
        }
        nextTransferTick = now + IDLE_TRANSFER_BACKOFF_TICKS;
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(holder, tank, "block.neoecoae.output_hatch", false, true);
    }

}
