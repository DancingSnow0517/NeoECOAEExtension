package cn.dancingsnow.neoecoae.mixins.ae2.key;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2 has already serialized and normalized capabilities at this boundary. Empty NBT remains distinct. */
@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyFactoryMixin {
    @Inject(method = "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$cached(ItemLike item, CompoundTag tag, CompoundTag caps,
            CallbackInfoReturnable<AEItemKey> callback) {
        if (tag != null || caps != null) return;
        Object epoch = KeyReuse.epoch();
        if (epoch == null) return;
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, item.asItem());
        if (slot != null && slot.get(epoch) instanceof AEItemKey key) callback.setReturnValue(key);
    }

    @Inject(method = "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;",
        at = @At("RETURN"), cancellable = true)
    private static void neoecoae$publish(ItemLike item, CompoundTag tag, CompoundTag caps,
            CallbackInfoReturnable<AEItemKey> callback) {
        if (tag != null || caps != null) return;
        Object epoch = KeyReuse.epoch();
        if (epoch == null) return;
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, item.asItem());
        if (slot == null) return;
        synchronized (slot) {
            if (slot.get(epoch) instanceof AEItemKey key) callback.setReturnValue(key);
            else slot.set(epoch, callback.getReturnValue());
        }
        // Keep AE2's lazy display-stack initialization: creating it here could reenter a mod's Item factory.
    }
}
