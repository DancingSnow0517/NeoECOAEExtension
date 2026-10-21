package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** LDLib 1's built-in fluid payload targets its own wrapper, rather than Forge's FluidStack. */
public final class NEFluidStackAccessor extends CustomObjectAccessor<FluidStack> {
    public NEFluidStackAccessor(){super(FluidStack.class,true);}

    @Override public ITypedPayload<?> serialize(AccessorOp op,FluidStack value){
        return NbtTagPayload.of(value.writeToNBT(new CompoundTag()).copy());
    }

    @Override public FluidStack deserialize(AccessorOp op,ITypedPayload<?> payload){
        if(!(payload instanceof NbtTagPayload nbt)||!(nbt.getPayload() instanceof CompoundTag tag)){
            throw new IllegalArgumentException("Expected a Forge fluid stack compound");
        }
        return FluidStack.loadFluidStackFromNBT(tag.copy());
    }
}
