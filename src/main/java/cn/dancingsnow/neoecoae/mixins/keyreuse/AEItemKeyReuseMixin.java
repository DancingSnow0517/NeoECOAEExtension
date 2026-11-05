package cn.dancingsnow.neoecoae.mixins.keyreuse;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEItemKey.class)
public abstract class AEItemKeyReuseMixin {
    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;",
        at = @At("HEAD"), cancellable = true)
    private static void neoecoae$reuse(ItemStack stack, CallbackInfoReturnable<AEItemKey> cir) {
        if (!stack.isEmpty() && stack.isComponentsPatchEmpty()) {
            AEItemKey key = (AEItemKey) KeyReuse.peek(stack.getItem());
            if (key != null) cir.setReturnValue(key);
        }
    }

    // Keep the upstream creation/constructor call chain intact for component-bearing stacks.
    @ModifyReturnValue(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;", at = @At("RETURN"))
    private static AEItemKey neoecoae$publishFactory(AEItemKey key) {
        if (key == null || !key.getReadOnlyStack().isComponentsPatchEmpty()) return key;
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || (slot = KeyReuse.slot(key.getItem())) == null) return key;
        synchronized (slot) {
            AEItemKey existing = (AEItemKey) slot.get(epoch);
            if (existing != null) return existing;
            // This fresh owned key has not been published; count is not part of its identity/hash.
            key.getReadOnlyStack().setCount(1);
            slot.set(epoch, key);
            return key;
        }
    }

    // GenericStack's map codec invokes this constructor lambda without calling of/fromTag.
    @Inject(method = "lambda$static$4", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoecoae$reuseDecoded(Holder<Item> item, DataComponentPatch patch,
            CallbackInfoReturnable<AEItemKey> cir) {
        if (patch.isEmpty()) {
            AEItemKey key = (AEItemKey) KeyReuse.peek(item.value());
            if (key != null) cir.setReturnValue(key);
        }
    }

    @WrapOperation(method = {"fromPacket", "lambda$static$4"}, at = @At(value = "NEW",
        target = "(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;"))
    private static AEItemKey neoecoae$publish(ItemStack stack, Operation<AEItemKey> original) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot;
        if (epoch == null || stack.isEmpty() || !stack.isComponentsPatchEmpty()
                || (slot = KeyReuse.slot(stack.getItem())) == null) return original.call(stack);
        synchronized (slot) {
            AEItemKey existing = (AEItemKey) slot.get(epoch);
            if (existing != null) return existing;
            // Every intercepted constructor receives a newly owned stack; quantities belong to the ledger.
            stack.setCount(1);
            AEItemKey key = original.call(stack);
            slot.set(epoch, key);
            return key;
        }
    }
}
