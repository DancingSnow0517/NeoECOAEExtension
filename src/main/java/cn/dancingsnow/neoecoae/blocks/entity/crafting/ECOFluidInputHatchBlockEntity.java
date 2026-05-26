package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.LDLib1MachineUIs;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
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

public class ECOFluidInputHatchBlockEntity extends AbstractCraftingBlockEntity<ECOFluidInputHatchBlockEntity> implements IUIHolder.BlockEntityUI {

    public FluidTank tank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };
    private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(() -> tank);

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(face));
            IFluidHandler sourceHandler = blockEntity == null ? null : blockEntity
                .getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite())
                .orElse(null);
            if (sourceHandler != null) {
                if (!FluidUtil.tryFluidTransfer(tank, sourceHandler, tank.getCapacity(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    @Override
    public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player) {
        return LDLib1MachineUIs.createFluidHatchUI(this, player, "block.neoecoae.input_hatch");
    }

    @Deprecated(forRemoval = true)
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return null;
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

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidHandlerCap.invalidate();
    }
}
