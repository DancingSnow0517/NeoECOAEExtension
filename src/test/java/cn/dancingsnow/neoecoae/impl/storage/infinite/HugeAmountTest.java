package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HugeAmountTest {
    @Test
    void upgradesToBigIntegerWithoutLosingAmount() {
        HugeAmount amount = HugeAmount.of(Long.MAX_VALUE).add(HugeAmount.of(42L));

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(42L)), amount.toBigInteger());
        assertEquals(Long.MAX_VALUE, amount.toLongSaturated());
    }

    @Test
    void subtractsAcrossBigAndLongRepresentations() {
        HugeAmount amount = HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100L)));

        assertEquals(HugeAmount.of(Long.MAX_VALUE), amount.subtract(HugeAmount.of(100L)));
    }

    @Test
    void longOverloadsStayCompactUntilTheyOverflow() {
        HugeAmount compact = HugeAmount.of(40L).add(2L);

        assertEquals(HugeAmount.of(42L), compact);
        assertFalse(compact.isBig());
        assertEquals(HugeAmount.of(40L), compact.subtract(2L));

        HugeAmount overflow = HugeAmount.of(Long.MAX_VALUE).add(1L);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), overflow.toBigInteger());
        assertTrue(overflow.isBig());
    }

    @Test
    void refusesNegativeResults() {
        assertThrows(IllegalArgumentException.class, () -> HugeAmount.of(1L).subtract(HugeAmount.of(2L)));
    }

    @Test
    void roundTripsThroughNbt() {
        HugeAmount amount = HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN));

        CompoundTag tag = amount.write();

        assertEquals(amount, HugeAmount.read(tag));
    }
}
