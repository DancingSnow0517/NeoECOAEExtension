package cn.dancingsnow.neoecoae.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** An immutable delta; absence and removal are deliberately different operations. */
public record MapDelta<K, V>(Map<K, V> updates, Set<K> removed) {
    public MapDelta {
        updates = Map.copyOf(updates);
        removed = Set.copyOf(removed);
    }

    public static <K, V> MapDelta<K, V> between(Map<K, V> previous, Map<K, V> current) {
        Map<K, V> updates = new HashMap<>();
        current.forEach((key, value) -> {
            if (!value.equals(previous.get(key))) updates.put(key, value);
        });
        Set<K> removed = new HashSet<>(previous.keySet());
        removed.removeAll(current.keySet());
        return new MapDelta<>(updates, removed);
    }

    public boolean isEmpty() { return updates.isEmpty() && removed.isEmpty(); }

    public Map<K, V> apply(Map<K, V> previous) {
        Map<K, V> next = new HashMap<>(previous);
        removed.forEach(next::remove);
        next.putAll(updates);
        return Map.copyOf(next);
    }
}
