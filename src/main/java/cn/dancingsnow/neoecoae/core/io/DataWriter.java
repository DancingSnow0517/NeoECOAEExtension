package cn.dancingsnow.neoecoae.core.io;

import java.util.List;
import java.util.function.Consumer;

/** Structured persistence operations; the platform owns key and tag representation. */
public interface DataWriter<K> {
    void writeString(String name, String value);
    void writeKey(String name, K key);
    void writeList(String name, List<Consumer<DataWriter<K>>> entries);
    void writeInt(String name, int value);
    void writeLong(String name, long value);
    void writeBoolean(String name, boolean value);
    void writeIntArray(String name, int[] values);
    void writeLongArray(String name, long[] values);
    void writeCompound(String name, Consumer<DataWriter<K>> contents);
}
