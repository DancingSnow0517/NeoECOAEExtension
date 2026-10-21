package cn.dancingsnow.neoecoae.platform.nbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.fluids.FluidStack;
public final class NEStacks {
    private NEStacks(){}
    public static ItemStack read(HolderLookup.Provider registries,CompoundTag tag){return ItemStack.of(tag);}
    public static CompoundTag save(ItemStack stack,HolderLookup.Provider registries){return stack.save(new CompoundTag());}
    public static CompoundTag save(FluidStack stack,HolderLookup.Provider registries){return stack.writeToNBT(new CompoundTag());}
    public static FluidStack withAmount(FluidStack stack,int amount){var copy=stack.copy();copy.setAmount(amount);return copy;}
    public static boolean sameFluid(FluidStack left,FluidStack right){return left.isFluidEqual(right);}
}
