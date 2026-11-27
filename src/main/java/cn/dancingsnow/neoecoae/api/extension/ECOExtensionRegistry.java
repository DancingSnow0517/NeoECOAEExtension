package cn.dancingsnow.neoecoae.api.extension;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Ordered registrations. Higher priority wins; equal priority is ordered by ID, never load order. */
public final class ECOExtensionRegistry<T> {
    private final Map<ResourceLocation, Entry<T>> entries = new HashMap<>();
    private volatile List<Entry<T>> snapshot = List.of();
    private volatile long revision;

    public synchronized void register(ResourceLocation id, int priority, T extension) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extension, "extension");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate extension: " + id);
        entries.put(id, new Entry<>(id, priority, extension));
        rebuild();
    }

    public synchronized boolean unregister(ResourceLocation id) {
        if (entries.remove(id) == null) return false;
        rebuild();
        return true;
    }

    private void rebuild() {
        snapshot = entries.values().stream().sorted(Comparator
            .<Entry<T>>comparingInt(Entry::priority).reversed()
            .thenComparing(entry -> entry.id().toString())).toList();
        revision++;
    }

    public List<Entry<T>> entries() { return snapshot; }
    public long revision() { return revision; }
    public record Entry<T>(ResourceLocation id, int priority, T extension) {}
}
