package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKeyType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Runtime-only generations. Keys and storage formats always retain their original value semantics. */
public final class KeyReuse {
    private static final List<WeakReference<KeyReuseSlot>> SLOTS = new ArrayList<>();
    // Null until the local common config has loaded, or while reuse is disabled/being invalidated.
    private static volatile Object epoch;
    private static boolean enabled;

    private KeyReuse() {}

    public static Object epoch() {
        return epoch;
    }

    static synchronized void register(KeyReuseSlot slot) {
        SLOTS.add(new WeakReference<>(slot));
    }

    public static synchronized void configure(boolean value) {
        enabled = value;
        invalidate();
    }

    public static synchronized void invalidate() {
        epoch = null;
        SLOTS.removeIf(reference -> {
            KeyReuseSlot slot = reference.get();
            if (slot == null) return true;
            slot.clear();
            return false;
        });
        if (enabled) epoch = new Generation();
    }

    /** Tables are the fallback for untransformed resources; transformed owners use the same epoch and slots. */
    private static final class Generation {
        final OwnerSlots items = new OwnerSlots();
        final OwnerSlots fluids = new OwnerSlots();
        final java.util.concurrent.ConcurrentMap<Object, OwnerSlots> tables =
            new java.util.concurrent.ConcurrentHashMap<>();
    }

    public static KeyReuseSlot itemSlot(Object generation, Object owner) {
        if (!(generation instanceof Generation active) || generation != epoch || owner == null) return null;
        return owner instanceof DefaultKeyOwner resource ? resource.neoecoae$defaultKeySlot() : active.items.get(owner);
    }

    public static KeyReuseSlot fluidSlot(Object generation, Object owner) {
        if (!(generation instanceof Generation active) || generation != epoch || owner == null) return null;
        return owner instanceof DefaultKeyOwner resource ? resource.neoecoae$defaultKeySlot() : active.fluids.get(owner);
    }

    static KeyReuseSlot slot(Object generation, Object type, Object owner) {
        if (!(generation instanceof Generation active) || generation != epoch || owner == null) return null;
        if (type == AEKeyType.items()) return itemSlot(generation, owner);
        if (type == AEKeyType.fluids()) return fluidSlot(generation, owner);
        OwnerSlots table = active.tables.get(type);
        if (table == null) table = active.tables.computeIfAbsent(type, ignored -> new OwnerSlots());
        return table.get(owner);
    }
}
