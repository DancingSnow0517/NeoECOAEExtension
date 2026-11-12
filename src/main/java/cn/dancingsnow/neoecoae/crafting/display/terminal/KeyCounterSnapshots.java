package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/** Copies only changed primary-resource groups; native AE2 counters still own all quantity operations. */
public final class KeyCounterSnapshots {
    private KeyCounterSnapshots() {}

    public static KeyCounter copy(KeyCounter current, KeyCounter previous, Set<AEKey> changed) {
        return copy(current, previous, changed, counter ->
                (Object) counter instanceof KeyCounterSnapshotAccess access ? access : null);
    }

    static KeyCounter copy(KeyCounter current, KeyCounter previous, Set<AEKey> changed,
            Function<KeyCounter, KeyCounterSnapshotAccess> access) {
        var source = access.apply(current);
        if (source == null || previous == null || access.apply(previous) == null) return nativeCopy(current);
        var primaries = new ReferenceOpenHashSet<Object>();
        for (AEKey key : changed) primaries.add(key.getPrimaryKey());
        // Dense updates are faster through AE2's bulk copy. Sparse updates never walk the full outer map.
        if (primaries.size() * 4L >= source.neoecoae$primaryMaps().size()) return nativeCopy(current);
        var old = access.apply(previous);
        Shards base;
        if (old.neoecoae$primaryMaps() instanceof Shards shards) base = shards;
        else {
            base = new Shards(old.neoecoae$primaryMaps());
            // Only representation changes; older viewers retain exactly the same quantities.
            old.neoecoae$primaryMaps(base);
        }
        var selected = new Reference2ObjectOpenHashMap<Object, Object>(primaries.size());
        for (Object primary : primaries) {
            Object group = source.neoecoae$primaryMaps().get(primary);
            if (group != null) selected.put(primary, group);
        }
        var view = new KeyCounter();
        access.apply(view).neoecoae$primaryMaps(selected);
        var copiedGroups = nativeCopy(view); // AE2 clones its own opaque VariantCounter implementations.
        var next = new KeyCounter();
        access.apply(next).neoecoae$primaryMaps(base.with(primaries, access.apply(copiedGroups).neoecoae$primaryMaps()));
        return next;
    }

    private static KeyCounter nativeCopy(KeyCounter source) {
        if (cn.dancingsnow.neoecoae.storage.StoragePerformance.ENABLED)
            cn.dancingsnow.neoecoae.storage.StoragePerformance.add(
                cn.dancingsnow.neoecoae.storage.StoragePerformance.Counter.TERMINAL_COPIED_KEYS, source.size());
        var copy = new KeyCounter();
        copy.addAll(source);
        return copy;
    }

    public static boolean shared(Reference2ObjectMap<Object, ?> maps) { return maps instanceof Shards; }

    /** An ordinary KeyCounter mutation detaches all shared groups before AE2 changes a variant counter. */
    public static Reference2ObjectMap<Object, ?> mutableCopy(KeyCounter counter) {
        return ((KeyCounterSnapshotAccess) (Object) nativeCopy(counter)).neoecoae$primaryMaps();
    }

    /** Immutable reference-keyed directory. Forking copies 256 references and only the affected hash buckets. */
    static final class Shards extends AbstractReference2ObjectMap<Object, Object> {
        private static final int COUNT = 256;
        private final Reference2ObjectOpenHashMap<Object, Object>[] buckets;
        private final int size;

        @SuppressWarnings("unchecked")
        Shards(Reference2ObjectMap<Object, ?> source) {
            buckets = new Reference2ObjectOpenHashMap[COUNT];
            for (var entry : source.reference2ObjectEntrySet()) {
                int index = index(entry.getKey());
                if (buckets[index] == null) buckets[index] = new Reference2ObjectOpenHashMap<>();
                buckets[index].put(entry.getKey(), entry.getValue());
            }
            size = source.size();
        }

        private Shards(Reference2ObjectOpenHashMap<Object, Object>[] buckets, int size) {
            this.buckets = buckets;
            this.size = size;
        }

        Shards with(Collection<Object> changed, Reference2ObjectMap<Object, ?> values) {
            var next = buckets.clone();
            int nextSize = size;
            for (Object primary : changed) {
                int index = index(primary);
                var bucket = next[index];
                if (bucket == null) bucket = next[index] = new Reference2ObjectOpenHashMap<>();
                else if (bucket == buckets[index]) bucket = next[index] = bucket.clone();
                Object value = values.get(primary);
                if (value == null) {
                    if (bucket.remove(primary) != null) nextSize--;
                } else if (bucket.put(primary, value) == null) nextSize++;
                if (bucket.isEmpty()) next[index] = null;
            }
            return new Shards(next, nextSize);
        }

        private static int index(Object key) { return HashCommon.mix(System.identityHashCode(key)) & (COUNT - 1); }
        @Override public int size() { return size; }
        @Override public boolean isEmpty() { return size == 0; }
        @Override public Object get(Object key) {
            var bucket = buckets[index(key)];
            return bucket == null ? null : bucket.get(key);
        }
        @Override public boolean containsKey(Object key) { return get(key) != null; }

        @Override public ObjectSet<Reference2ObjectMap.Entry<Object, Object>> reference2ObjectEntrySet() {
            return new AbstractObjectSet<>() {
                @Override public int size() { return size; }
                @Override public ObjectIterator<Reference2ObjectMap.Entry<Object, Object>> iterator() {
                    return new ObjectIterator<>() {
                        int index;
                        ObjectIterator<Reference2ObjectMap.Entry<Object, Object>> iterator = ObjectIterators.emptyIterator();
                        @Override public boolean hasNext() {
                            while (!iterator.hasNext() && index < COUNT) {
                                var bucket = buckets[index++];
                                if (bucket != null) iterator = bucket.reference2ObjectEntrySet().iterator();
                            }
                            return iterator.hasNext();
                        }
                        @Override public Reference2ObjectMap.Entry<Object, Object> next() {
                            if (!hasNext()) throw new java.util.NoSuchElementException();
                            var entry = iterator.next();
                            return new BasicEntry<>(entry.getKey(), entry.getValue());
                        }
                    };
                }
            };
        }

        @Override public ObjectCollection<Object> values() {
            return new AbstractObjectCollection<>() {
                @Override public int size() { return size; }
                @Override public ObjectIterator<Object> iterator() {
                    return new ObjectIterator<>() {
                        int index;
                        ObjectIterator<Object> iterator = ObjectIterators.emptyIterator();
                        @Override public boolean hasNext() {
                            while (!iterator.hasNext() && index < COUNT) {
                                var bucket = buckets[index++];
                                if (bucket != null) iterator = bucket.values().iterator();
                            }
                            return iterator.hasNext();
                        }
                        @Override public Object next() {
                            if (!hasNext()) throw new java.util.NoSuchElementException();
                            return iterator.next();
                        }
                    };
                }
            };
        }
    }
}
