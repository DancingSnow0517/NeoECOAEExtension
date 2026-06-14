package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class NEForgeFluidStorage implements IFluidStorage {
    private final FluidTank tank;

    public NEForgeFluidStorage(FluidTank tank) {
        this.tank = tank;
    }

    @Override
    public FluidStack getFluid() {
        return toLdFluid(tank.getFluid());
    }

    @Override
    public void setFluid(FluidStack fluidStack) {
        tank.setFluid(toForgeFluid(fluidStack));
    }

    @Override
    public long getCapacity() {
        return tank.getCapacity();
    }

    @Override
    public boolean isFluidValid(FluidStack fluidStack) {
        return tank.isFluidValid(toForgeFluid(fluidStack));
    }

    @Override
    public long fill(int tankIndex, FluidStack resource, boolean simulate, boolean notifyChanges) {
        return tank.fill(
                toForgeFluid(resource),
                simulate
                        ? net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                        : net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public boolean supportsFill(int tankIndex) {
        return true;
    }

    @Override
    public FluidStack drain(int tankIndex, FluidStack resource, boolean simulate, boolean notifyChanges) {
        return toLdFluid(tank.drain(
                toForgeFluid(resource),
                simulate
                        ? net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                        : net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE));
    }

    @Override
    public boolean supportsDrain(int tankIndex) {
        return true;
    }

    @Override
    public Object createSnapshot() {
        return tank.writeToNBT(new CompoundTag());
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        if (snapshot instanceof CompoundTag tag) {
            tank.readFromNBT(tag);
        }
    }

    public static FluidStack toLdFluid(net.minecraftforge.fluids.FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.empty();
        }
        CompoundTag tag = stack.getTag();
        return tag == null
                ? FluidStack.create(stack.getFluid(), stack.getAmount())
                : FluidStack.create(stack.getFluid(), stack.getAmount(), tag.copy());
    }

    public static net.minecraftforge.fluids.FluidStack toForgeFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return net.minecraftforge.fluids.FluidStack.EMPTY;
        }
        net.minecraftforge.fluids.FluidStack forge = new net.minecraftforge.fluids.FluidStack(
                stack.getFluid(), Math.toIntExact(Math.min(Integer.MAX_VALUE, stack.getAmount())));
        if (stack.hasTag()) {
            forge.setTag(stack.getTag().copy());
        }
        return forge;
    }
}
