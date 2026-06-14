package cn.dancingsnow.neoecoae.mixins;

import appeng.me.energy.StoredEnergyAmount;
import com.lowdragmc.lowdraglib2.syncdata.IContentChangeAware;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = StoredEnergyAmount.class, remap = false)
public abstract class StoredEnergyAmountMixin implements IContentChangeAware, ValueIOSerializable {
    @Shadow
    public abstract void setStored(double amount);

    @Shadow
    public abstract double getAmount();

    @Unique
    private Runnable neoecoae$onContentsChanged = () -> {
    };

    @Override
    public void serialize(ValueOutput output) {
        output.putDouble("ae_stored_energt", getAmount());
    }

    @Override
    public void deserialize(ValueInput input) {
        setStored(input.getDoubleOr("ae_stored_energt", 0));
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
