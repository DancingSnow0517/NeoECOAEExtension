package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ECOBigIntegerStorageTest {
    private final AEKey key = new InfiniteStorageTestKey(1);
    private final IActionSource source = IActionSource.empty();

    @Test
    void exactStorageReceivesOneWideOffer() {
        var longCalls = new AtomicInteger();
        var exactCalls = new AtomicInteger();
        class ExactStorage implements MEStorage, ECOBigIntegerStorage {
            @Override
            public Component getDescription() {
                return Component.empty();
            }

            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource actionSource) {
                longCalls.incrementAndGet();
                return amount;
            }

            @Override
            public BigInteger insertBigInteger(
                    AEKey what, BigInteger amount, Actionable mode, IActionSource actionSource) {
                exactCalls.incrementAndGet();
                return amount;
            }
        }
        var storage = new ExactStorage();
        BigInteger wide = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(wide, ECOBigIntegerStorage.insert(storage, key, wide, Actionable.MODULATE, source));
        assertEquals(0, longCalls.get());
        assertEquals(1, exactCalls.get());
        assertEquals(
                BigInteger.valueOf(7),
                ECOBigIntegerStorage.insert(storage, key, BigInteger.valueOf(7), Actionable.MODULATE, source));
        assertEquals(1, longCalls.get());
    }

    @Test
    void ordinaryStorageReceivesAtMostOneLongWindow() {
        var calls = new AtomicInteger();
        MEStorage storage = new MEStorage() {
            @Override
            public Component getDescription() {
                return Component.empty();
            }

            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource actionSource) {
                calls.incrementAndGet();
                assertEquals(Long.MAX_VALUE, amount);
                return amount;
            }
        };
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE),
                ECOBigIntegerStorage.insert(storage, key, BigInteger.TEN.pow(30), Actionable.MODULATE, source));
        assertEquals(1, calls.get());
    }
}
