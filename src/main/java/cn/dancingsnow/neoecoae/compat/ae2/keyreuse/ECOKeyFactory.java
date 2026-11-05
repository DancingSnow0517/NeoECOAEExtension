package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Internal ECO entry points. AE2 and addon factories/decoders retain their original behavior. */
public final class ECOKeyFactory {
    private ECOKeyFactory() {}

    public static final com.mojang.serialization.Codec<AEKey> KEY_CODEC =
        AEKey.CODEC.xmap(ECOKeyFactory::canonicalize, java.util.function.Function.identity());
    public static final com.mojang.serialization.Codec<GenericStack> STACK_CODEC =
        GenericStack.CODEC.xmap(ECOKeyFactory::canonicalize, java.util.function.Function.identity());

    public static AEItemKey item(ItemStack stack) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || stack.isEmpty() || !plain(stack)) return AEItemKey.of(stack);
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, stack.getItem());
        if (slot == null) return AEItemKey.of(stack);
        AEItemKey key = (AEItemKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (AEItemKey) slot.get(epoch);
            if (key == null) {
                key = AEItemKey.of(stack);
                key.getReadOnlyStack().setCount(1);
                key.getMaxStackSize();
                slot.set(epoch, key);
            }
            return key;
        }
    }

    public static AEItemKey item(ItemLike item) {
        return item(item.asItem().getDefaultInstance());
    }

    private static boolean plain(ItemStack stack) { return stack.isComponentsPatchEmpty(); }
    private static boolean plain(FluidStack stack) { return stack.isComponentsPatchEmpty(); }

    public static AEFluidKey fluid(FluidStack stack) {
        if (KeyReuse.epoch() == null || stack.isEmpty() || !plain(stack)) return AEFluidKey.of(stack);
        return fluid(stack.getFluid());
    }

    public static AEFluidKey fluid(Fluid fluid) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot = KeyReuse.fluidSlot(epoch, fluid);
        if (slot == null) return AEFluidKey.of(fluid);
        AEFluidKey key = (AEFluidKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (AEFluidKey) slot.get(epoch);
            if (key == null) {
                key = AEFluidKey.of(fluid);
                if (key != null) slot.set(epoch, key);
            }
            return key;
        }
    }

    /** Never publishes an externally owned key. Content equality remains authoritative across generations. */
    public static AEKey canonicalize(AEKey key) {
        if (key == null || KeyReuse.epoch() == null) return key;
        AEKey owned;
        if (key instanceof AEItemKey || key instanceof AEFluidKey) {
            Object epoch = KeyReuse.epoch();
            KeyReuseSlot slot = key instanceof AEItemKey
                    ? KeyReuse.itemSlot(epoch, key.getPrimaryKey()) : KeyReuse.fluidSlot(epoch, key.getPrimaryKey());
            if (slot == null) return key;
            AEKey cached = (AEKey) slot.get(epoch);
            if (cached != null) return key.equals(cached) ? cached : key;
        }
        if (key instanceof AEItemKey item) {
            ItemStack stack = item.getReadOnlyStack();
            if (!plain(stack)) return key;
            owned = item(stack);
        } else if (key instanceof AEFluidKey fluid) {
            FluidStack stack = fluid.toStack(1);
            if (!plain(stack)) return key;
            owned = fluid(stack.getFluid());
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
        return canonicalize(AEKey.fromTagGeneric(registries, tag));
    }

    public static AEItemKey itemFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return (AEItemKey) canonicalize(AEItemKey.fromTag(registries, tag));
    }

    public static AEFluidKey fluidFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return (AEFluidKey) canonicalize(AEFluidKey.fromTag(registries, tag));
    }

    public static GenericStack readTag(HolderLookup.Provider registries, CompoundTag tag) {
        return canonicalize(GenericStack.readTag(registries, tag));
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
