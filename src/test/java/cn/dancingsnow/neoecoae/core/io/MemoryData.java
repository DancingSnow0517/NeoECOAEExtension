package cn.dancingsnow.neoecoae.core.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Independent structured backend for testing the persisted domain schema without Minecraft. */
final class MemoryData<K> implements DataReader<K>, DataWriter<K> {
    private final Map<String, Object> values = new HashMap<>();
    @Override public String readString(String name) { return (String) values.getOrDefault(name, ""); }
    @SuppressWarnings("unchecked")
    @Override public K readKey(String name) { return (K) values.get(name); }
    @Override public boolean contains(String name) { return values.containsKey(name); }
    @Override public int readInt(String name) { return ((Number) values.getOrDefault(name, 0)).intValue(); }
    @Override public long readLong(String name) { return ((Number) values.getOrDefault(name, 0L)).longValue(); }
    @Override public boolean readBoolean(String name) { return (boolean) values.getOrDefault(name, false); }
    @Override public int[] readIntArray(String name) { return ((int[]) values.getOrDefault(name, new int[0])).clone(); }
    @Override public long[] readLongArray(String name) { return ((long[]) values.getOrDefault(name, new long[0])).clone(); }
    @SuppressWarnings("unchecked")
    @Override public DataReader<K> readCompound(String name) {
        return (DataReader<K>) values.getOrDefault(name, new MemoryData<K>());
    }
    @SuppressWarnings("unchecked")
    @Override public List<DataReader<K>> readList(String name) {
        return (List<DataReader<K>>) values.getOrDefault(name, List.of());
    }
    @Override public List<DataReader<K>> readCompoundList(String name) { return readList(name); }
    @Override public void writeString(String name, String value) { values.put(name, value); }
    @Override public void writeKey(String name, K key) { values.put(name, key); }
    @Override public void writeInt(String name, int value) { values.put(name, value); }
    @Override public void writeLong(String name, long value) { values.put(name, value); }
    @Override public void writeBoolean(String name, boolean value) { values.put(name, value); }
    @Override public void writeIntArray(String name, int[] data) { values.put(name, data.clone()); }
    @Override public void writeLongArray(String name, long[] data) { values.put(name, data.clone()); }
    @Override public void writeCompound(String name, Consumer<DataWriter<K>> contents) {
        var child = new MemoryData<K>();
        contents.accept(child);
        values.put(name, child);
    }
    @Override public void writeList(String name, List<Consumer<DataWriter<K>>> entries) {
        List<DataReader<K>> children = new ArrayList<>(entries.size());
        for (var entry : entries) {
            var child = new MemoryData<K>();
            entry.accept(child);
            children.add(child);
        }
        values.put(name, children);
    }
}
