package cn.dancingsnow.neoecoae.mixins.ae2.key;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.DefaultKeyOwner;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** No cache or key creation while Minecraft constructs and registers the resource. */
@Mixin({Item.class, Fluid.class})
public abstract class DefaultKeyOwnerMixin implements DefaultKeyOwner {
    @Unique private volatile KeyReuseSlot neoecoae$defaultKeySlot;

    @Override public KeyReuseSlot neoecoae$defaultKeySlot() {
        KeyReuseSlot slot = neoecoae$defaultKeySlot;
        if (slot != null) return slot;
        synchronized (this) {
            if (neoecoae$defaultKeySlot == null) neoecoae$defaultKeySlot = new KeyReuseSlot();
            return neoecoae$defaultKeySlot;
        }
    }
}
