package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import cn.dancingsnow.neoecoae.blocks.entity.FluidTankResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ECOFluidOutputHatchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidOutputHatchBlockEntity> {

    public static int MAX_CAPACITY = 16000;

    /** @deprecated Use {@link #getFluidTankHandler()}; operate on the buffer without replacing it. */
    @Deprecated
    public FluidTank tank = new FluidTank(MAX_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    private final FluidTankResourceHandler drainOnlyHandler = FluidTankResourceHandler.extractOnly(() -> tank);

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
    }

    /** Stable addon handler view. */
    public FluidTank getFluidTankHandler() { return tank; }

    /** 26.1 fluid capability ({@code Capabilities.Fluid.BLOCK}): drain-only view of the tank. */
    public ResourceHandler<FluidResource> getFluidResourceHandler() { return drainOnlyHandler; }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        // Same layout as 1.21.1 FluidTank#writeToNBT(registries, data): "Fluid" at the block entity root.
        tank.serialize(data);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        tank.deserialize(data);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (tank.isEmpty()) {
            return;
        }
        for (Direction face : Direction.values()) {
            ResourceHandler<FluidResource> targetHandler = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());
            if (targetHandler != null) {
                try (Transaction transaction = Transaction.open(null)) {
                    int moved = ResourceHandlerUtil.move(drainOnlyHandler, targetHandler, fluid -> true, tank.getFluidAmount(), transaction);
                    if (moved > 0) {
                        transaction.commit();
                        return;
                    }
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(holder, tank, "block.neoecoae.output_hatch", false, true);
    }

}
