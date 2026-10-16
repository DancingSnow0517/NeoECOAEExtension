package cn.dancingsnow.neoecoae.core.network;

/** Version-independent buffer operations used by domain codecs. Key encoding belongs to the adapter. */
public interface PacketBuf<K> {
    void writeVarInt(int value);
    int readVarInt();
    void writeVarLong(long value);
    long readVarLong();
    void writeUtf(String value);
    String readUtf();
    default void writeEnum(Enum<?> value) { writeVarInt(value.ordinal()); }
    default <E extends Enum<E>> E readEnum(Class<E> type) {
        int index = readVarInt();
        E[] values = type.getEnumConstants();
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Invalid enum ordinal: " + index);
        return values[index];
    }
    void writeBoolean(boolean value);
    boolean readBoolean();
    void writeBytes(byte[] value);
    byte[] readBytes(int maximumLength);
    void writeKey(K key);
    K readKey();
}
