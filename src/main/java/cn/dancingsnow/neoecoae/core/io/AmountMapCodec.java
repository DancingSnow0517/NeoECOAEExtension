package cn.dancingsnow.neoecoae.core.io;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

/** Stable decimal amount encoding, independent of game keys and the persistence backend. */
public final class AmountMapCodec {
    private static final int MAX_AMOUNT_DIGITS = 1024;
    private AmountMapCodec() {}

    public static <K> void write(DataWriter<K> writer, String name, Map<K, BigInteger> amounts) {
        var entries = new ArrayList<Consumer<DataWriter<K>>>(amounts.size());
        amounts.forEach((key, amount) -> entries.add(entry -> {
            entry.writeKey("key", key);
            entry.writeString("amount", amount.toString());
        }));
        writer.writeList(name, entries);
    }

    public static <K> void read(DataReader<K> reader, String name, Map<K, BigInteger> target) {
        for (var entry : reader.readList(name)) {
            K key = entry.readKey("key");
            String encoded = entry.readString("amount");
            if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_AMOUNT_DIGITS
                    || !encoded.chars().allMatch(c -> c >= '0' && c <= '9'))
                throw new IllegalArgumentException("Invalid exact order amount");
            BigInteger amount;
            try {
                amount = new BigInteger(encoded);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid deferred material", exception);
            }
            if (key == null || amount.signum() <= 0)
                throw new IllegalArgumentException("Invalid deferred material");
            target.put(key, amount);
        }
    }
}
