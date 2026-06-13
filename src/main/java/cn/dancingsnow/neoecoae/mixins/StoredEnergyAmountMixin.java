package cn.dancingsnow.neoecoae.mixins;

import appeng.me.energy.StoredEnergyAmount;
import com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.DoubleTag;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = StoredEnergyAmount.class, remap = false)
public abstract class StoredEnergyAmountMixin implements IContentChangeAware {
    @Shadow public abstract void setStored(double amount);

    @Shadow public abstract double getAmount();

    @Unique private Runnable neoecoae$onContentsChanged = () -> {};

    public @UnknownNullability DoubleTag serializeNBT(HolderLookup.Provider provider) {
        return DoubleTag.valueOf(getAmount());
    }

    public void deserializeNBT(HolderLookup.Provider provider, DoubleTag nbt) {
        setStored(nbt.doubleValue());
    }

    @Override
    public void setOnContentsChanged(Runnable onContentChanged) {
        neoecoae$onContentsChanged = onContentChanged;
    }

    @Override
    public Runnable getOnContentsChanged() {
        return neoecoae$onContentsChanged;
    }
}
