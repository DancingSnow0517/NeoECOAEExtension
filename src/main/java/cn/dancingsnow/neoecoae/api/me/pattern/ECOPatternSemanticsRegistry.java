package cn.dancingsnow.neoecoae.api.me.pattern;

import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Register optional semantics during common initialization. Higher priority wins, then lexicographic ID.
 * Ordinary AE2 patterns need no registration. Changes increment the revision used by planner caches;
 * each analysis uses an immutable registration snapshot.
 */
public final class ECOPatternSemanticsRegistry {
    private static final Map<ResourceLocation, Registration> ADAPTERS = new LinkedHashMap<>();
    private static long revision;

    private ECOPatternSemanticsRegistry() {
    }

    public static void register(ResourceLocation id, int priority, ECOPatternSemanticAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        synchronized (ECOPatternSemanticsRegistry.class) {
            if (ADAPTERS.containsKey(id)) {
                throw new IllegalArgumentException("Pattern semantics already registered: " + id);
            }
            ADAPTERS.put(id, new Registration(id, priority, adapter));
            revision++;
            // Publish the new snapshot only after invalidating all older contract credentials.
            AE2PatternIntrospection.onRecipeReloadOrServerReload();
        }
    }

    public static boolean unregister(ResourceLocation id) {
        synchronized (ECOPatternSemanticsRegistry.class) {
            if (ADAPTERS.remove(id) == null) return false;
            revision++;
            AE2PatternIntrospection.onRecipeReloadOrServerReload();
        }
        return true;
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized List<Registration> snapshot() {
        return ADAPTERS.values().stream()
            .sorted(Comparator.comparingInt(Registration::priority).reversed()
                .thenComparing(entry -> entry.id().toString()))
            .toList();
    }

    public record Registration(ResourceLocation id, int priority, ECOPatternSemanticAdapter adapter) {
    }
}
