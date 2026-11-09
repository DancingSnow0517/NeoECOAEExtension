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
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.network.FriendlyByteBuf;

/** Internal ECO entry points, sharing generation-scoped default keys with the optional official-AE2 bridge. */
public final class ECOKeyFactory {
    private ECOKeyFactory() {}

    public static AEItemKey item(ItemStack stack) {
        Object epoch = KeyReuse.epoch();
        // The bridge sees AE2's normalized caps directly, avoiding a second serialization in ECO.
        if (epoch != null && stack.getItem() instanceof DefaultKeyOwner) return AEItemKey.of(stack);
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
        Object epoch = KeyReuse.epoch();
        var owner = item.asItem();
        if (epoch != null && owner instanceof DefaultKeyOwner) return AEItemKey.of(owner);
        KeyReuseSlot slot = KeyReuse.itemSlot(epoch, owner);
        if (slot == null) return AEItemKey.of(owner);
        AEItemKey key = (AEItemKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (AEItemKey) slot.get(epoch);
            if (key == null) {
                key = AEItemKey.of(owner);
                key.getReadOnlyStack();
                key.getMaxStackSize();
                slot.set(epoch, key);
            }
            return key;
        }
    }

    private static final java.lang.invoke.MethodHandle SERIALIZE_CAPS = serializeCapsHandle();

    private static java.lang.invoke.MethodHandle serializeCapsHandle() {
        try {
            var method = net.minecraftforge.common.capabilities.CapabilityProvider.class.getDeclaredMethod("serializeCaps");
            method.setAccessible(true);
            return java.lang.invoke.MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static boolean plain(ItemStack stack) {
        if (stack.getTag() != null) return false;
        try {
            CompoundTag caps = (CompoundTag) SERIALIZE_CAPS.invokeExact(
                (net.minecraftforge.common.capabilities.CapabilityProvider) stack);
            // Identical to AE2's normalization; a non-null empty item tag remains a distinct key.
            return caps == null || caps.isEmpty();
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot inspect ECO item key capabilities", failure);
        }
    }

    private static boolean plain(FluidStack stack) { return stack.getTag() == null; }

    public static AEFluidKey fluid(FluidStack stack) {
        if (KeyReuse.epoch() == null || stack.isEmpty() || !plain(stack)) return AEFluidKey.of(stack);
        return fluid(stack.getFluid());
    }

    public static AEFluidKey fluid(Fluid fluid, CompoundTag tag) {
        return tag == null ? fluid(fluid) : AEFluidKey.of(fluid, tag);
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
        cn.dancingsnow.neoecoae.storage.StoragePerformance.count(
            cn.dancingsnow.neoecoae.storage.StoragePerformance.Counter.KEY_CANONICALIZE);
        if (key == null || KeyReuse.epoch() == null) return key;
        AEKey owned;
        if (key instanceof AEItemKey || key instanceof AEFluidKey) {
            // Tagged fluids cannot use a plain owner slot; avoid constructing a temporary FluidStack.
            if (key instanceof AEFluidKey fluid && fluid.hasTag()) return key;
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
            // Tagged keys returned above; no temporary FluidStack is needed for this default key.
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

    public static AEKey fromTagGeneric(CompoundTag tag) {
        return canonicalize(AEKey.fromTagGeneric(tag));
    }

    public static AEItemKey itemFromTag(CompoundTag tag) {
        return (AEItemKey) canonicalize(AEItemKey.fromTag(tag));
    }

    public static AEFluidKey fluidFromTag(CompoundTag tag) {
        return (AEFluidKey) canonicalize(AEFluidKey.fromTag(tag));
    }

    public static GenericStack readTag(CompoundTag tag) {
        return canonicalize(GenericStack.readTag(tag));
    }

    public static AEKey readKey(FriendlyByteBuf buffer) {
        return canonicalize(AEKey.readKey(buffer));
    }

    public static AEKey readOptionalKey(FriendlyByteBuf buffer) {
        return canonicalize(AEKey.readOptionalKey(buffer));
    }

    public static GenericStack readBuffer(FriendlyByteBuf buffer) {
        return canonicalize(GenericStack.readBuffer(buffer));
    }
}
