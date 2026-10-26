package cn.dancingsnow.neoecoae.api.me.diagnostics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Addons may register readers during common setup. Registered readers precede the built-in adapters. */
public final class ECOProcessingStatusRegistry {
    private static final LinkedHashMap<ResourceLocation, ECOProcessingStatusReader> READERS = new LinkedHashMap<>();
    private static volatile List<ECOProcessingStatusReader> snapshot = List.of();
    private ECOProcessingStatusRegistry() {}

    public static synchronized void register(ResourceLocation id, ECOProcessingStatusReader reader) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reader);
        if (READERS.putIfAbsent(id, reader) != null) throw new IllegalArgumentException("Duplicate status reader: " + id);
        snapshot = List.copyOf(READERS.values());
    }

    public static synchronized void unregister(ResourceLocation id) {
        READERS.remove(id);
        snapshot = List.copyOf(READERS.values());
    }

    public static List<ECOProcessingStatusReader> readers() { return snapshot; }
}
