package cn.dancingsnow.neoecoae.core.io;

import java.util.List;

/** Read counterpart of {@link DataWriter}; missing or malformed keys are rejected by the platform. */
public interface DataReader<K> {
    String readString(String name);
    K readKey(String name);
    List<DataReader<K>> readList(String name);
    boolean contains(String name);
    int readInt(String name);
    long readLong(String name);
    boolean readBoolean(String name);
    int[] readIntArray(String name);
    long[] readLongArray(String name);
    DataReader<K> readCompound(String name);
    /** A list constrained to compounds; a list of a different element type is treated as absent. */
    List<DataReader<K>> readCompoundList(String name);
}
