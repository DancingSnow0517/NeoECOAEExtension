package cn.dancingsnow.neoecoae.mixins;

import appeng.util.inv.AppEngInternalInventory;
import com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AppEngInternalInventory.class)
public abstract class AppEngInternalInventoryMixin implements INBTSerializable<CompoundTag>, IContentChangeAware {
    @Unique
    private Runnable neoECOAEExtension$onContentsChanged;

    @Shadow
    public abstract int size();

    @Shadow
    public abstract void writeToNBT(CompoundTag data, String name, HolderLookup.Provider registries);

    @Shadow
    public abstract void readFromNBT(CompoundTag data, String name, HolderLookup.Provider registries);

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        this.writeToNBT(tag, "inventory", provider);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.readFromNBT(nbt, "inventory", provider);
    }

    @Override
    public void setOnContentsChanged(Runnable onContentChanged) {
        this.neoECOAEExtension$onContentsChanged = onContentChanged;
    }

    @Override
    public Runnable getOnContentsChanged() {
        return neoECOAEExtension$onContentsChanged;
    }
}
