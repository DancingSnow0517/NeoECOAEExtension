package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;

/** Weak handler-to-scheduler bindings plus the short-lived facade observation context. */
public final class ECOSophisticatedSourceRegistry {
    private static final ThreadLocal<Observation> OBSERVING = new ThreadLocal<>();
    private static final Map<Object, Binding> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    private ECOSophisticatedSourceRegistry() {
    }

    public static AutoCloseable observe(SourceChangeSink sink, Runnable handlerObserved, Runnable compatibilityFailure) {
        Observation previous = OBSERVING.get();
        OBSERVING.set(new Observation(sink, handlerObserved, compatibilityFailure));
        return () -> {
            if (previous == null) OBSERVING.remove();
            else OBSERVING.set(previous);
        };
    }

    public static void reportCompatibilityFailure() {
        Observation observation = OBSERVING.get();
        if (observation != null) observation.compatibilityFailure().run();
    }

    public static void observeHandler(Object outerHandler) {
        Observation observation = OBSERVING.get();
        if (observation == null) return;
        SourceChangeSink sink = observation.sink();
        Object base = findBaseHandler(outerHandler);
        if (!(base instanceof ECOSophisticatedHandlerBridge bridge) || bridge.neoecoae$getSlots() <= 0) return;
        boolean newlyBound = false;
        synchronized (BINDINGS) {
            Binding binding = BINDINGS.get(base);
            if (binding == null) {
                binding = new Binding(bridge);
                BINDINGS.put(base, binding);
                newlyBound = true;
            }
            binding.add(sink);
            binding.refreshBaseline(bridge);
            if (newlyBound) {
                for (AEKey key : binding.slotKeys.values()) sink.markDirty(key);
            }
        }
        observation.handlerObserved().run();
    }

    public static void unsubscribe(SourceChangeSink sink) {
        synchronized (BINDINGS) {
            BINDINGS.values().forEach(binding -> binding.remove(sink));
            BINDINGS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    public static void onSlotChanged(Object handler, int slot, ItemStack newStack) {
        Binding binding;
        synchronized (BINDINGS) {
            binding = BINDINGS.get(handler);
            if (binding == null) return;
        }
        AEKey newKey = AEItemKey.of(newStack);
        AEKey oldKey;
        synchronized (binding) {
            oldKey = binding.slotKeys.put(slot, newKey);
            if (newKey == null) binding.slotKeys.remove(slot);
        }
        if (oldKey == null && newKey == null) return;
        ECOSophisticatedMetrics.DIRTY_SLOT_EVENTS.incrementAndGet();
        binding.publish(oldKey, newKey);
    }

    public static ItemStack indexedExtract(Object outerHandler, ItemStack request, boolean simulate) {
        Object current = outerHandler;
        for (int depth = 0; depth < 4 && current instanceof ECOSophisticatedHandlerBridge bridge; depth++) {
            if (bridge.neoecoae$isFilteredExtractor()) {
                ItemStack extracted = bridge.neoecoae$extractMatching(request, simulate);
                ECOSophisticatedMetrics.INDEXED_EXTRACTS.incrementAndGet();
                ECOSophisticatedMetrics.AVOIDED_SLOT_SCANS.addAndGet(Math.max(0, bridge.neoecoae$getSlots() - 1));
                if (!extracted.isEmpty()) {
                    ECOSophisticatedMetrics.MATCHING_SLOTS.incrementAndGet();
                }
                return extracted;
            }
            current = bridge.neoecoae$getDelegate();
        }
        ECOSophisticatedMetrics.FALLBACK_EXTRACTS.incrementAndGet();
        return null;
    }

    private static Object findBaseHandler(Object outerHandler) {
        Object current = outerHandler;
        Object last = null;
        boolean filtered = false;
        for (int depth = 0; depth < 4 && current instanceof ECOSophisticatedHandlerBridge bridge; depth++) {
            last = current;
            filtered |= bridge.neoecoae$isFilteredExtractor();
            Object next = bridge.neoecoae$getDelegate();
            if (next == null || next == current) break;
            current = next;
        }
        if (!filtered) return null;
        return current instanceof ECOSophisticatedHandlerBridge ? current : last;
    }

    private static final class Binding {
        private final Map<Integer, AEKey> slotKeys = new java.util.HashMap<>();
        private final Set<WeakReference<SourceChangeSink>> sinks =
            Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        private Binding(ECOSophisticatedHandlerBridge handler) {
            refreshBaseline(handler);
        }

        private synchronized void refreshBaseline(ECOSophisticatedHandlerBridge handler) {
            if (!slotKeys.isEmpty()) return;
            for (int slot = 0; slot < handler.neoecoae$getSlots(); slot++) {
                AEKey key = AEItemKey.of(handler.neoecoae$getStackInSlot(slot));
                if (key != null) slotKeys.put(slot, key);
            }
        }

        private synchronized void add(SourceChangeSink sink) {
            sinks.removeIf(reference -> reference.get() == null);
            for (WeakReference<SourceChangeSink> reference : sinks) {
                if (reference.get() == sink) return;
            }
            sinks.add(new WeakReference<>(sink));
        }

        private synchronized void remove(SourceChangeSink sink) {
            sinks.removeIf(reference -> reference.get() == null || reference.get() == sink);
        }

        private synchronized boolean isEmpty() {
            sinks.removeIf(reference -> reference.get() == null);
            return sinks.isEmpty();
        }

        private void publish(AEKey oldKey, AEKey newKey) {
            for (WeakReference<SourceChangeSink> reference : new ArrayList<>(sinks)) {
                SourceChangeSink sink = reference.get();
                if (sink == null) continue;
                if (oldKey != null) sink.markDirty(oldKey);
                if (newKey != null && !newKey.equals(oldKey)) sink.markDirty(newKey);
            }
        }
    }

    private record Observation(SourceChangeSink sink, Runnable handlerObserved, Runnable compatibilityFailure) {
    }
}
