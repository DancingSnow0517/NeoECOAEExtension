package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Internal ECO entry points and the version-checked default-key factory bridge. Content equality is authoritative. */
public final class ECOKeyFactory {
    private ECOKeyFactory() {}

    public static final com.mojang.serialization.Codec<AEKey> KEY_CODEC =
        AEKey.CODEC.xmap(ECOKeyFactory::canonicalize, java.util.function.Function.identity());
    public static final com.mojang.serialization.Codec<GenericStack> STACK_CODEC =
        GenericStack.CODEC.xmap(ECOKeyFactory::canonicalize, java.util.function.Function.identity());

    public static AEItemKey item(ItemStack stack) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || stack.isEmpty() || !plain(stack)) return AEItemKey.of(stack);
        AEItemKey key = cachedItem(epoch, stack);
        if (key != null) return key;
        // The public AE2 stack overload preserves count. ECO normalizes its own input before that boundary.
        return publishItem(epoch, AEItemKey.of(stack.getCount() == 1 ? stack : stack.copyWithCount(1)));
    }

    public static AEItemKey item(ItemLike item) {
        Item resource = item.asItem();
        if (resource.getClass() == Item.class) {
            AEItemKey cached = cachedItem(KeyReuse.epoch(), resource);
            if (cached != null) return cached;
        }
        return item(resource.getDefaultInstance());
    }

    public static AEItemKey cachedItem(Object epoch, Item item) {
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, item);
        return slot == null ? null : (AEItemKey) slot.get(epoch, item.components());
    }

    public static AEItemKey cachedItem(Object epoch, ItemStack stack) {
        if (epoch == null || stack.isEmpty() || !plain(stack)) return null;
        AEItemKey cached = cachedItem(epoch, stack.getItem());
        // Empty patches created before ModifyDefaultComponentsEvent can still carry an older prototype.
        return cached != null && ItemStack.isSameItemSameComponents(stack, cached.getReadOnlyStack()) ? cached : null;
    }

    /** Only freshly constructed factory results enter the shared slot; borrowed decoder keys do not. */
    public static AEItemKey publishItem(Object epoch, AEItemKey key) {
        if (epoch == null || key == null || key.getReadOnlyStack().getCount() != 1 || !plain(key.getReadOnlyStack())) return key;
        Item item = key.getItem();
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, item);
        if (slot == null) return key;
        synchronized (slot) {
            var prototype = item.components();
            AEItemKey cached = (AEItemKey) slot.get(epoch, prototype);
            if (cached != null) return key.equals(cached) ? cached : key;
            // PatchedDataComponentMap.equals compares both prototype and patch, exactly as ItemStack/AE2 do.
            if (key.getReadOnlyStack().getComponents().equals(new PatchedDataComponentMap(prototype)))
                slot.set(epoch, prototype, key);
            return key;
        }
    }

    private static boolean plain(ItemStack stack) { return stack.isComponentsPatchEmpty(); }
    private static boolean plain(FluidStack stack) { return stack.isComponentsPatchEmpty(); }

    public static AEFluidKey fluid(FluidStack stack) {
        if (KeyReuse.epoch() == null || stack.isEmpty() || !plain(stack)) return AEFluidKey.of(stack);
        return fluid(stack.getFluid());
    }

    public static AEFluidKey fluid(Fluid fluid) {
        Object epoch = KeyReuse.epoch();
        AEFluidKey cached = cachedFluid(epoch, fluid);
        return cached != null ? cached : publishFluid(epoch, AEFluidKey.of(fluid));
    }

    public static AEFluidKey cachedFluid(Object epoch, Fluid fluid) {
        KeyReuseSlot slot = KeyReuse.fluidSlot(epoch, fluid);
        return slot == null ? null : (AEFluidKey) slot.get(epoch);
    }

    /** Caller has already checked the input's empty component patch. */
    public static AEFluidKey publishFluid(Object epoch, AEFluidKey key) {
        if (epoch == null || key == null) return key;
        if ((Object) key instanceof FluidKeyComponents components && components.neoecoae$hasComponentPatch()) return key;
        KeyReuseSlot slot = KeyReuse.fluidSlot(epoch, key.getFluid());
        if (slot == null) return key;
        synchronized (slot) {
            AEFluidKey cached = (AEFluidKey) slot.get(epoch);
            if (cached != null) return key.equals(cached) ? cached : key;
            slot.set(epoch, key);
            return key;
        }
    }

    /** Never publishes an externally owned key. Content equality remains authoritative across generations. */
    public static AEKey canonicalize(AEKey key) {
        if (key == null || KeyReuse.epoch() == null) return key;
        AEKey owned;
        if (key instanceof AEItemKey item) {
            ItemStack stack = item.getReadOnlyStack();
            if (!plain(stack)) return key;
            AEItemKey cached = cachedItem(KeyReuse.epoch(), item.getItem());
            if (cached != null) return key.equals(cached) ? cached : key;
            owned = item(stack);
        } else if (key instanceof AEFluidKey fluid) {
            if ((Object) fluid instanceof FluidKeyComponents components && components.neoecoae$hasComponentPatch()) return key;
            AEFluidKey cached = cachedFluid(KeyReuse.epoch(), fluid.getFluid());
            if (cached != null) return key.equals(cached) ? cached : key;
            if (!((Object) fluid instanceof FluidKeyComponents) && !plain(fluid.toStack(1))) return key;
            owned = fluid(fluid.getFluid());
        } else {
            return OptionalECOKeys.canonicalize(key);
        }
        return key.equals(owned) ? owned : key;
    }

    public static GenericStack canonicalize(GenericStack stack) {
        if (stack == null) return null;
        AEKey key = canonicalize(stack.what());
        return key == stack.what() ? stack : new GenericStack(key, stack.amount());
    }

    public static GenericStack fromItemStack(ItemStack stack) {
        GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        if (wrapped != null) return canonicalize(wrapped);
        AEItemKey key = item(stack);
        return key == null ? null : new GenericStack(key, stack.getCount());
    }

    public static GenericStack fromFluidStack(FluidStack stack) {
        AEFluidKey key = fluid(stack);
        return key == null ? null : new GenericStack(key, stack.getAmount());
    }

    public static AEKey fromTagGeneric(HolderLookup.Provider registries, CompoundTag tag) {
        return canonicalize(AEKey.fromTagGeneric(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, tag)));
    }

    public static AEItemKey itemFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return (AEItemKey) canonicalize(AEItemKey.fromTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, tag)));
    }

    public static AEFluidKey fluidFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return (AEFluidKey) canonicalize(AEFluidKey.fromTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, tag)));
    }

    public static GenericStack readTag(HolderLookup.Provider registries, CompoundTag tag) {
        return canonicalize(GenericStack.readTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, tag)));
    }

    public static AEKey readKey(RegistryFriendlyByteBuf buffer) {
        return canonicalize(AEKey.readKey(buffer));
    }

    public static AEKey readOptionalKey(RegistryFriendlyByteBuf buffer) {
        return canonicalize(AEKey.readOptionalKey(buffer));
    }

    public static GenericStack readBuffer(RegistryFriendlyByteBuf buffer) {
        return canonicalize(GenericStack.readBuffer(buffer));
    }
}
