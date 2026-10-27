package cn.dancingsnow.neoecoae.integration.mekanism;

import java.util.function.BooleanSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.infuse.IInfusionTank;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Fill-only view of the existing infusion tank: one mB of milk is one unit of milk infusion. */
public final class MilkInfusionFluidHandler implements IFluidHandler {
    private final IInfusionTank tank;
    private final InfuseType milk;
    private final Fluid milkFluid;
    private final BooleanSupplier canFill;
    private final Runnable markDirty;

    public MilkInfusionFluidHandler(IInfusionTank tank, InfuseType milk, Fluid milkFluid,
            BooleanSupplier canFill, Runnable markDirty) {
        this.tank = tank;
        this.milk = milk;
        this.milkFluid = milkFluid;
        this.canFill = canFill;
        this.markDirty = markDirty;
    }

    @Override public int getTanks() { return 1; }

    @Override public FluidStack getFluidInTank(int index) {
        return index == 0 && !tank.isEmpty() && tank.isTypeEqual(milk)
            ? new FluidStack(milkFluid, (int) Math.min(Integer.MAX_VALUE, tank.getStored())) : FluidStack.EMPTY;
    }

    @Override public int getTankCapacity(int index) {
        return index == 0 ? (int) Math.min(Integer.MAX_VALUE, tank.getCapacity()) : 0;
    }

    @Override public boolean isFluidValid(int index, FluidStack stack) {
        return index == 0 && !stack.isEmpty()
            && (stack.getFluid() == milkFluid || stack.getFluid().is(Tags.Fluids.MILK));
    }

    @Override public int fill(FluidStack stack, FluidAction action) {
        if (!canFill.getAsBoolean() || !isFluidValid(0, stack)
                || !tank.isEmpty() && !tank.isTypeEqual(milk)) return 0;
        InfusionStack remainder = tank.insert(new InfusionStack(milk, stack.getAmount()),
            action.execute() ? Action.EXECUTE : Action.SIMULATE, AutomationType.EXTERNAL);
        int accepted = stack.getAmount() - (int) remainder.getAmount();
        if (accepted > 0 && action.execute()) markDirty.run();
        return accepted;
    }

    @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
    @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
}
