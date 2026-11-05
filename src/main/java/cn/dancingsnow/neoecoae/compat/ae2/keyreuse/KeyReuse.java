package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

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
        if (enabled) epoch = new Object();
    }

    public static KeyReuseSlot slot(Object owner) {
        return owner instanceof KeyReuseOwner ? ((KeyReuseOwner) owner).neoecoae$keyReuseSlot() : null;
    }

    public static Object peek(Object owner) {
        Object current = epoch;
        if (current == null) return null;
        KeyReuseSlot slot = slot(owner);
        return slot == null ? null : slot.get(current);
    }
}
