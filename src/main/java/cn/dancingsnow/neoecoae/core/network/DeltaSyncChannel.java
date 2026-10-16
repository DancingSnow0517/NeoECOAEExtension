package cn.dancingsnow.neoecoae.core.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A transactional snapshot baseline: only commit after the adapter queues a complete transfer. */
public final class DeltaSyncChannel<K, V> {
    public record Delta<K, V>(Map<K, V> updates, Set<K> removed) {
        public boolean isEmpty() { return updates.isEmpty() && removed.isEmpty(); }
        public Map<K, V> apply(Map<K, V> previous) {
            Map<K, V> next = new HashMap<>(previous);
            removed.forEach(next::remove);
            next.putAll(updates);
            return next;
        }
    }
    public record Pending<K, V>(boolean full, Delta<K, V> delta, Map<K, V> snapshot) {}
    private Map<K, V> previous;
    public Pending<K, V> prepare(Map<K, V> current, boolean reset) {
        Map<K, V> snapshot = Map.copyOf(current);
        boolean full = reset || previous == null;
        return new Pending<>(full, between(full ? Map.of() : previous, snapshot), snapshot);
    }
    public void commit(Pending<K, V> pending) { previous = pending.snapshot(); }
    public static <K, V> Delta<K, V> between(Map<K, V> previous, Map<K, V> current) {
        Map<K, V> updates = new HashMap<>();
        current.forEach((key, value) -> { if (!value.equals(previous.get(key))) updates.put(key, value); });
        Set<K> removed = new HashSet<>(previous.keySet());
        removed.removeAll(current.keySet());
        return new Delta<>(updates, removed);
    }
}
