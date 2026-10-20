package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
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

public class ECOFluidOutputHatchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidOutputHatchBlockEntity> implements IUIHolder.BlockEntityUI {

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

    public void tick(Level level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            IFluidHandler targetHandler = cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, pos.relative(face), ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
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
