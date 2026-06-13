package cn.dancingsnow.neoecoae.mixins;

import appeng.util.inv.AppEngInternalInventory;
import com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AppEngInternalInventory.class)
public abstract class AppEngInternalInventoryMixin implements IContentChangeAware, ValueIOSerializable {
    @Unique
    private Runnable neoECOAEExtension$onContentsChanged;

    @Shadow
    public abstract int size();

    @Shadow
    public abstract void readFromNBT(ValueInput input, String name);

    @Shadow
    public abstract void writeToNBT(ValueOutput output, String name);

    @Override
    public void serialize(ValueOutput output) {
        this.writeToNBT(output, "ae_inventory");
    }

    @Override
    public void deserialize(ValueInput input) {
        this.readFromNBT(input, "ae_inventory");
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
