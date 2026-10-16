package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.core.network.DeltaSyncChannel;
import java.util.Map;
import java.util.Set;

/** Wire-compatible facade for the core delta contract. Absence and removal stay distinct. */
public record MapDelta<K, V>(Map<K, V> updates, Set<K> removed) {
    public static <K, V> MapDelta<K, V> between(Map<K, V> previous, Map<K, V> current) {
        return from(DeltaSyncChannel.between(previous, current));
    }
    public static <K, V> MapDelta<K, V> from(DeltaSyncChannel.Delta<K, V> delta) {
        return new MapDelta<>(delta.updates(), delta.removed());
    }
    public boolean isEmpty() { return updates.isEmpty() && removed.isEmpty(); }
    public Map<K, V> apply(Map<K, V> previous) {
        return new DeltaSyncChannel.Delta<>(updates, removed).apply(previous);
    }
}
