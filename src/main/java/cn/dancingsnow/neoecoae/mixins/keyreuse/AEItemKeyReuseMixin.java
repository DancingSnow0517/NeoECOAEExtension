package cn.dancingsnow.neoecoae.mixins.keyreuse;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.*;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.objectweb.asm.Opcodes;

@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyReuseMixin implements SharedItemKey {
    @Unique private boolean neoecoae$shared;

    @Override public final boolean neoecoae$isShared() { return neoecoae$shared; }
    @Override public final void neoecoae$initializeShared() {
        AEItemKey key = (AEItemKey) (Object) this;
        key.getReadOnlyStack();
        key.getMaxStackSize();
        neoecoae$shared = true;
    }

    // Keep capability serialization and its empty-compound normalization in the upstream public factory.
    // A published key's lazy fields have already been initialized before it becomes visible to other threads.
    @WrapWithCondition(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;",
        at = @At(value = "FIELD", target = "Lappeng/api/stacks/AEItemKey;maxStackSize:I", opcode = Opcodes.PUTFIELD))
    private static boolean neoecoae$preserveSharedStackSize(AEItemKey key, int size) {
        return !((SharedItemKey) (Object) key).neoecoae$isShared();
    }

    @Invoker(value = "of", remap = false)
    public static AEItemKey neoecoae$originalFactory(ItemLike item, CompoundTag tag, CompoundTag caps) {
        throw new AssertionError();
    }

    @Redirect(method = {
        "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;",
        "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;",
        "fromTag"
    }, at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEItemKey;of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;"))
    private static AEItemKey neoecoae$fromIdentity(ItemLike item, CompoundTag tag, CompoundTag caps) {
        // AE2 15 distinguishes null from a non-null empty tag; complex identities retain the upstream factory.
        if (tag != null || caps != null) return neoecoae$originalFactory(item, tag, caps);
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || (slot = KeyReuse.slot(item.asItem())) == null) return neoecoae$originalFactory(item, tag, caps);
        AEItemKey key = (AEItemKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (AEItemKey) slot.get(epoch);
            if (key == null) {
                key = neoecoae$originalFactory(item, tag, caps);
                ((SharedItemKey) (Object) key).neoecoae$initializeShared();
                slot.set(epoch, key);
            }
            return key;
        }
    }

    @WrapOperation(method = "fromPacket", at = @At(value = "NEW", target =
        "(Lnet/minecraft/world/item/Item;Lappeng/api/stacks/AEItemKey$InternedTag;Lappeng/api/stacks/AEItemKey$InternedTag;)Lappeng/api/stacks/AEItemKey;"))
    private static AEItemKey neoecoae$publish(Item item, @Coerce Object tag, @Coerce Object caps,
            Operation<AEItemKey> original) {
        if (((InternedTagData) tag).neoecoae$tag() != null || ((InternedTagData) caps).neoecoae$tag() != null)
            return original.call(item, tag, caps);
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || (slot = KeyReuse.slot(item)) == null) return original.call(item, tag, caps);
        synchronized (slot) {
            AEItemKey existing = (AEItemKey) slot.get(epoch);
            if (existing != null) return existing;
            AEItemKey key = original.call(item, tag, caps);
            ((SharedItemKey) (Object) key).neoecoae$initializeShared();
            slot.set(epoch, key);
            return key;
        }
    }
}
