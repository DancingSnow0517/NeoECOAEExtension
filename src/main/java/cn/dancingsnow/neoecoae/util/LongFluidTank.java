package cn.dancingsnow.neoecoae.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** A long-backed single-fluid buffer; Forge's int-sized fluid view is saturated, never wrapped. */
public class LongFluidTank extends FluidTank {
    private long amount;

    public LongFluidTank() { super(Integer.MAX_VALUE); }

    public long getStoredAmount() { return amount; }

    public void add(FluidStack sample, long count) {
        if (sample.isEmpty() || count <= 0 || count > Long.MAX_VALUE - amount
            || (!fluid.isEmpty() && !fluid.isFluidEqual(sample))) {
            throw new IllegalArgumentException("Invalid fluid buffer addition");
        }
        fluid = new FluidStack(sample, (int) Math.min(Integer.MAX_VALUE, amount + count));
        amount += count;
        onContentsChanged();
    }

    public void remove(long count) {
        if (count < 0 || count > amount) throw new IllegalArgumentException("Invalid fluid buffer removal");
        amount -= count;
        fluid = amount == 0 ? FluidStack.EMPTY : new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, amount));
        onContentsChanged();
    }

    @Override
    public void setFluid(FluidStack stack) {
        amount = stack.isEmpty() ? 0 : stack.getAmount();
        fluid = amount == 0 ? FluidStack.EMPTY : stack.copy();
        onContentsChanged();
    }

    @Override
    public int getSpace() { return (int) Math.min(Integer.MAX_VALUE, Long.MAX_VALUE - amount); }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(resource)
            || (!fluid.isEmpty() && !fluid.isFluidEqual(resource))) return 0;
        int accepted = Math.min(resource.getAmount(), getSpace());
        if (action.execute() && accepted > 0) add(resource, accepted);
        return accepted;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int drained = (int) Math.min(Math.max(0, maxDrain), amount);
        if (drained == 0) return FluidStack.EMPTY;
        FluidStack result = new FluidStack(fluid, drained);
        if (action.execute()) remove(drained);
        return result;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return fluid.isFluidEqual(resource)
            ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag tag) {
        super.writeToNBT(tag);
        tag.putLong("StoredAmount", amount);
        return tag;
    }

    @Override
    public FluidTank readFromNBT(CompoundTag tag) {
        super.readFromNBT(tag);
        amount = fluid.isEmpty() ? 0 : tag.contains("StoredAmount")
            ? Math.max(0, tag.getLong("StoredAmount")) : fluid.getAmount();
        fluid = amount == 0 ? FluidStack.EMPTY : new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, amount));
        return this;
    }
}
