package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.util.inv.AppEngInternalInventory;
import com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AppEngInternalInventory.class)
public abstract class AppEngInternalInventoryMixin implements INBTSerializable<CompoundTag>, IContentChangeAware {
    @Unique
    private Runnable neoECOAEExtension$onContentsChanged;

    @Shadow @Final
    private NonNullList<ItemStack> stacks;

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
        // LDLib sends a complete snapshot, while AE2's reader only overlays occupied slots.
        // Clear silently: receiving/loading a snapshot must not publish intermediate empty inventories.
        stacks.clear();
        this.readFromNBT(nbt, "inventory", provider);
    }

    @Inject(method = "onContentsChanged", at = @At("RETURN"))
    private void neoecoae$markInventorySyncDirty(int slot, CallbackInfo ci) {
        if (neoECOAEExtension$onContentsChanged != null) {
            neoECOAEExtension$onContentsChanged.run();
        }
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
