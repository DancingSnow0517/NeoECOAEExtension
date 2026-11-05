package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseOwner;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({Item.class, Fluid.class})
public abstract class ResourceKeyOwnerMixin implements KeyReuseOwner {
    @Unique private final KeyReuseSlot neoecoae$keyReuse = new KeyReuseSlot();

    @Override
    public final KeyReuseSlot neoecoae$keyReuseSlot() {
        return neoecoae$keyReuse;
    }
}
