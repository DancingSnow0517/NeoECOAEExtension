package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseOwner;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.key.type.EnergyType", remap = false)
public abstract class EnergyKeyOwnerMixin implements KeyReuseOwner {
    @Unique private final KeyReuseSlot neoecoae$keyReuse = new KeyReuseSlot();

    @Override
    public final KeyReuseSlot neoecoae$keyReuseSlot() { return neoecoae$keyReuse; }
}
