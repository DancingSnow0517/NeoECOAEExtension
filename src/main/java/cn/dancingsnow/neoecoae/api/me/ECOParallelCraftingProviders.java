package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/** Dependency-free registry for integrations supplying an ordinary parallel provider contract. */
public final class ECOParallelCraftingProviders {
    @FunctionalInterface
    public interface Adapter {
        @Nullable ECOParallelCraftingProvider adapt(ICraftingProvider provider);
    }

    private static final Map<Class<?>, Adapter> ADAPTERS = new ConcurrentHashMap<>();
    private static final Map<ICraftingProvider, WeakReference<ECOParallelCraftingProvider>> RESOLVED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ECOParallelCraftingProviders() {}

    public static void register(Class<? extends ICraftingProvider> type, Adapter adapter) {
        ADAPTERS.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
        RESOLVED.clear();
    }

    @Nullable public static ECOParallelCraftingProvider find(@Nullable Object provider) {
        if (provider instanceof ECOParallelCraftingProvider parallel) return parallel;
        if (!(provider instanceof ICraftingProvider crafting)) return null;
        var cached = RESOLVED.get(crafting);
        if (cached != null && cached.get() != null) return cached.get();
        for (var entry : ADAPTERS.entrySet()) {
            if (!entry.getKey().isInstance(crafting)) continue;
            try {
                var adapted = entry.getValue().adapt(crafting);
                if (adapted != null) {
                    RESOLVED.put(crafting, new WeakReference<>(adapted));
                    return adapted;
                }
            } catch (RuntimeException unavailable) {
                return null;
            }
        }
        return null;
    }
}
