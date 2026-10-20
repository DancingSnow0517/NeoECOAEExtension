package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Higher priority wins; equal priorities are ordered by registration ID, independent of load order. */
public final class ECOExecutionOptimizationRegistry {
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile List<Entry> ordered = List.of();
    private static volatile long revision;

    private ECOExecutionOptimizationRegistry() {}

    public static synchronized void register(ResourceLocation id, int priority,
            ECOExecutionOptimizationAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        if (ENTRIES.containsKey(id)) throw new IllegalArgumentException("Duplicate execution optimization ID: " + id);
        ENTRIES.put(id, new Entry(id, priority, adapter));
        changed();
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        if (ENTRIES.remove(Objects.requireNonNull(id, "id")) == null) return false;
        changed();
        return true;
    }

    public static ECOExecutionOptimization resolve(IPatternDetails pattern) {
        if (pattern == null) return ECOExecutionOptimization.NONE;
        for (Entry entry : ordered) {
            try {
                if (entry.adapter().supports(pattern)) {
                    ECOExecutionOptimization result = entry.adapter().describe(pattern);
                    return result == null ? ECOExecutionOptimization.NONE : result;
                }
            } catch (RuntimeException | LinkageError failure) {
                // A failed capability must never silently fall through to a broader authorization.
                return ECOExecutionOptimization.NONE;
            }
        }
        return ECOExecutionOptimization.NONE;
    }

    /** Changes on registration/removal so derived planning caches can include the capability contract. */
    public static long revision() {
        return revision;
    }

    private static void changed() {
        List<Entry> snapshot = new ArrayList<>(ENTRIES.values());
        snapshot.sort(Comparator.comparingInt(Entry::priority).reversed()
            .thenComparing(entry -> entry.id().toString()));
        ordered = List.copyOf(snapshot);
        revision++;
        // Clear positive/negative caches and invalidate credentials issued before this registry change.
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
    }

    private record Entry(ResourceLocation id, int priority, ECOExecutionOptimizationAdapter adapter) {}
}
