package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The running total crosses long, U126 and BigInteger and must equal the exact sum at every snapshot. */
class MutableInfiniteStorageTotalTest {
    @Test void totalMatchesExactSumAcrossTiers() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            var total = new MutableInfiniteStorageTotal();
            BigInteger expected = BigInteger.ZERO;
            for (int step = 0; step < 500; step++) {
                int kind = random.nextInt(6);
                if (kind == 0) {
                    BigInteger amount = new BigInteger(random.nextInt(140) + 1, random);
                    total.add(amount);
                    expected = expected.add(amount);
                } else if (kind == 1 && expected.signum() > 0) {
                    BigInteger amount = new BigInteger(Math.max(1, expected.bitLength()), random).mod(expected).add(BigInteger.ONE);
                    total.subtract(HugeAmount.of(amount));
                    expected = expected.subtract(amount);
                } else if (kind <= 3) {
                    long amount = random.nextBoolean() ? Long.MAX_VALUE - random.nextInt(1000) : random.nextInt(1_000_000);
                    total.add(amount);
                    expected = expected.add(BigInteger.valueOf(amount));
                } else {
                    long amount = expected.min(BigInteger.valueOf(random.nextBoolean() ? Long.MAX_VALUE : 1_000_000))
                        .longValueExact();
                    amount = amount == 0 ? 0 : (random.nextLong() & Long.MAX_VALUE) % amount + 1;
                    if (amount > 0) {
                        total.subtract(amount);
                        expected = expected.subtract(BigInteger.valueOf(amount));
                    }
                }
                if (step % 7 == 0) assertEquals(expected, total.snapshot().toBigInteger(), "run " + run + " step " + step);
            }
            assertEquals(expected, total.snapshot().toBigInteger());
        }
    }

    @Test void subtractingBelowZeroIsRejected() {
        var total = new MutableInfiniteStorageTotal();
        total.add(Long.MAX_VALUE);
        total.add(10);
        total.subtract(Long.MAX_VALUE);
        // Reported either immediately (back in the long tier) or when the pending delta is folded.
        assertThrows(IllegalArgumentException.class, () -> {
            total.subtract(Long.MAX_VALUE);
            total.snapshot();
        });
    }
}
