package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.ui.NEBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public class ECOFluidOutputHatchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidOutputHatchBlockEntity> implements NEBlockEntityUI {

    public FluidTank tank = new FluidTank(NECraftingCluster.fluidHatchCapacity(null)) {
        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return getSpace() == 0 ? 0 : super.fill(resource, action);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
    }

    /** Stable addon handler view, matching the 1.21.1 accessor. */
    public FluidTank getFluidTankHandler() { return tank; }

    @Override
    public void updateCluster(@Nullable NECraftingCluster cluster) {
        tank.setCapacity(NECraftingCluster.fluidHatchCapacity(cluster));
        super.updateCluster(cluster);
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

    private final cn.dancingsnow.neoecoae.util.AdjacentFluidHandlers adjacentFluidHandlers =
        new cn.dancingsnow.neoecoae.util.AdjacentFluidHandlers();

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || tank.isEmpty()) return;
        for (Direction face : Direction.values()) {
            IFluidHandler targetHandler = adjacentFluidHandlers.get(level, pos, face);
            if (targetHandler != null) {
                if (!FluidUtil.tryFluidTransfer(targetHandler, tank, tank.getFluidAmount(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    public ModularUI createUI(Player player) {
        return FluidHatchUI.createFluidHatchUI(this, player, tank, "block.neoecoae.output_hatch", false, true);
    }

}
