package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.MatterKeyOwner;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {"com.buuz135.replication.api.MatterType", "com.buuz135.replication.integration.kubejs.CustomMatterType"}, remap = false)
public abstract class MatterKeyOwnerMixin implements MatterKeyOwner {
    @Unique private final KeyReuseSlot neoecoae$keyReuse = new KeyReuseSlot();
    @Override public final KeyReuseSlot neoecoae$keyReuseSlot() { return neoecoae$keyReuse; }
}
