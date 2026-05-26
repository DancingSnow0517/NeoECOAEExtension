package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class ECOFluidOutputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidOutputHatchBlockEntity> {

    public FluidTank tank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    public ECOFluidOutputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
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
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(face));
            IFluidHandler targetHandler = blockEntity == null ? null : blockEntity
                .getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite())
                .orElse(null);
            if (targetHandler != null) {
                if (!FluidUtil.tryFluidTransfer(targetHandler, tank, tank.getFluidAmount(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return CraftingUIHelper.createFluidHatchUI(holder, tank, "block.neoecoae.output_hatch", false, true);
    }

}
