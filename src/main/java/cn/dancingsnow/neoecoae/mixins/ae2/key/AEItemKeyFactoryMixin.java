package cn.dancingsnow.neoecoae.mixins.ae2.key;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve AE2's observable stack count and all component patches. */
@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyFactoryMixin {
    @WrapMethod(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;")
    private static AEItemKey neoecoae$stack(ItemStack stack, Operation<AEItemKey> original) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || stack.getCount() != 1 || !stack.isComponentsPatchEmpty()
                || stack.getItem().getClass() != Item.class) return original.call(stack);
        // NeoForge subclasses can compute damage or maximum stack size from state outside the components.
        // Keep their public constructor callbacks; ECO's pre-existing internal reuse remains separate.
        AEItemKey cached = ECOKeyFactory.cachedItem(epoch, stack);
        return cached != null ? cached : ECOKeyFactory.publishItem(epoch, original.call(stack));
    }

    @WrapMethod(method = "of(Lnet/minecraft/world/level/ItemLike;)Lappeng/api/stacks/AEItemKey;")
    private static AEItemKey neoecoae$item(ItemLike source, Operation<AEItemKey> original) {
        // Subclasses may override getDefaultInstance or component verification; execute their factory normally.
        if (source instanceof Item item && item.getClass() == Item.class) {
            AEItemKey cached = ECOKeyFactory.cachedItem(KeyReuse.epoch(), item);
            if (cached != null) return cached;
        }
        return original.call(source);
    }
}
