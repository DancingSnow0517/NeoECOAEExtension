package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Per-key amounts and the table total cross long, U126 and BigInteger and must stay exact. */
class StorageAmountsTierTest {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Test void amountsAndTotalMatchBigIntegerReference() {
        Random random = new Random(1260);
        AEKey[] keys = {mock(AEKey.class), mock(AEKey.class), mock(AEKey.class)};
        for (int run = 0; run < 100; run++) {
            var amounts = new StorageAmounts();
            Map<AEKey, BigInteger> expected = new HashMap<>();
            for (int step = 0; step < 400; step++) {
                AEKey key = keys[random.nextInt(keys.length)];
                BigInteger current = expected.getOrDefault(key, BigInteger.ZERO);
                switch (random.nextInt(5)) {
                    case 0 -> {
                        long amount = random.nextBoolean() ? Long.MAX_VALUE - random.nextInt(100) : random.nextInt(10_000) + 1;
                        amounts.add(key, amount);
                        current = current.add(BigInteger.valueOf(amount));
                    }
                    case 1 -> {
                        BigInteger amount = new BigInteger(random.nextInt(140) + 1, random).add(BigInteger.ONE);
                        amounts.add(key, amount);
                        current = current.add(amount);
                    }
                    case 2 -> {
                        long visible = amounts.visible(key);
                        if (visible > 0) {
                            long amount = (random.nextLong() & Long.MAX_VALUE) % visible + 1;
                            amounts.subtract(key, amount);
                            current = current.subtract(BigInteger.valueOf(amount));
                        }
                    }
                    case 3 -> {
                        BigInteger amount = random.nextInt(3) == 0 ? BigInteger.ZERO : new BigInteger(random.nextInt(130) + 1, random);
                        amounts.set(key, HugeAmount.of(amount));
                        current = amount;
                    }
                    default -> {
                        long amount = random.nextInt(1_000);
                        amounts.setLong(key, amount);
                        current = BigInteger.valueOf(amount);
                    }
                }
                if (current.signum() == 0) expected.remove(key);
                else expected.put(key, current);
                BigInteger stored = expected.getOrDefault(key, BigInteger.ZERO);
                assertEquals(stored, amounts.get(key).toBigInteger(), "run " + run + " step " + step);
                assertEquals(stored.min(LONG_MAX).longValueExact(), amounts.visible(key));
                assertEquals(stored.compareTo(LONG_MAX) > 0, amounts.isBig(key));
                BigInteger sum = expected.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
                assertEquals(sum, amounts.totalExact());
                assertEquals(sum.min(LONG_MAX).longValueExact(), amounts.totalVisible());
                assertEquals(expected.size(), amounts.size());
            }
        }
    }
}
