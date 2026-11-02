package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
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

import java.util.ArrayList;
import java.util.List;

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
        // LDLib sends a complete snapshot, while AE2's reader only overlays the slots present in the NBT
        // and writes the backing list directly. A slot the snapshot omits therefore has to be cleared
        // explicitly; remember the old contents so the hosts can be told what actually changed.
        List<ItemStack> before = new ArrayList<>(stacks);
        // Clear silently: receiving or loading a snapshot must not publish intermediate empty inventories.
        stacks.clear();
        this.readFromNBT(nbt, "inventory", provider);
        // The guard also keeps this method honest when the mixin body is exercised directly, as the
        // unit test does: there is no host to tell, so there is nothing to do.
        Object self = this;
        if (!(self instanceof AppEngInternalInventory inventory)) {
            return;
        }
        notifyChangedSlots(inventory, before, stacks);
    }

    /**
     * Tells the host which slots a snapshot actually changed.
     *
     * <p>A snapshot is how every LDLib2 GUI edit reaches the server, so without this notification the block
     * entity keeps every cache derived from the inventory and goes on advertising patterns that are gone.</p>
     *
     * <p>Calls {@link InternalInventoryHost#onChangeInventory} directly instead of going through AE2's
     * {@code onContentsChanged} / {@code notifyContentsChanged}: those route through the dirty listener this
     * mixin installs below, so a receiver would immediately re-broadcast what it had just received. For the
     * same reason there is no {@code saveChangedInventory} call — modifying events on the receiving side are
     * not modifications, and the hosts that need persisting already do it from {@code onChangeInventory}.
     * Reporting per changed slot matches how AE2 announces ordinary writes, and doing it only once the whole
     * snapshot is in place keeps intermediate states unpublished.</p>
     */
    @Unique
    private static void notifyChangedSlots(AppEngInternalInventory inventory, List<ItemStack> before,
                                           List<ItemStack> after) {
        InternalInventoryHost host = inventory.getHost();
        if (host == null) {
            return;
        }
        // NonNullList keeps its length across clear(), so both lists match; the guard is defensive only.
        int slots = Math.min(before.size(), after.size());
        for (int slot = 0; slot < slots; slot++) {
            if (!ItemStack.matches(before.get(slot), after.get(slot))) {
                host.onChangeInventory(inventory, slot);
            }
        }
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
