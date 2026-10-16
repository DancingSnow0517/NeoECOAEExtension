package cn.dancingsnow.neoecoae.core.io;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmountMapCodecTest {
    @Test void domainPersistenceWorksWithStringKeysAndAnInMemoryBackend() {
        var backend = new MemoryData<String>();
        var amounts = Map.of("iron", BigInteger.TEN.pow(1023), "gold", BigInteger.ONE);
        AmountMapCodec.write(backend, "materials", amounts);
        var restored = new HashMap<String, BigInteger>();
        AmountMapCodec.read(backend, "materials", restored);
        assertEquals(amounts, restored);
    }

    @Test void malformedAndOverLimitAmountsAreRejectedWithoutGameClasses() {
        for (var encoded : List.of("", "+1", "-1", "0", " 1", "1.5", "1".repeat(1025))) {
            var backend = new MemoryData<String>();
            backend.writeList("materials", List.of(entry -> {
                entry.writeKey("key", "iron");
                entry.writeString("amount", encoded);
            }));
            assertThrows(IllegalArgumentException.class,
                () -> AmountMapCodec.read(backend, "materials", new HashMap<>()), encoded);
        }
    }

}
