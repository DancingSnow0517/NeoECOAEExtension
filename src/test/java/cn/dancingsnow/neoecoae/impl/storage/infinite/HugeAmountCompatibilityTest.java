package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HugeAmountCompatibilityTest {
    @Test void canonicalArithmeticPreservesFiniteNonNegativeAndNullSemantics() {
        HugeAmount value = HugeAmount.of(Long.MAX_VALUE);
        HugeAmount huge = value.add(7);
        assertTrue(huge.isBig());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7)), huge.toBigInteger());
        assertEquals(value, huge.subtract(7));
        assertEquals(HugeAmount.ZERO, huge.subtract(huge));
        assertSame(value, value.add((HugeAmount) null));
        assertSame(value, value.subtract((HugeAmount) null));
        assertSame(value, value.min(null));
        assertThrows(IllegalArgumentException.class, () -> HugeAmount.of(-1));
        assertThrows(IllegalArgumentException.class, () -> value.subtract(huge));
        assertThrows(IllegalArgumentException.class, () -> value.add(-1));
        assertThrows(IllegalArgumentException.class, () -> value.subtract(-1));
    }

    @Test void oldLongAndBigNbtPayloadsRoundTripWithoutFormatChange() {
        assertSame(HugeAmount.ZERO, HugeAmount.read(null));
        assertSame(HugeAmount.ZERO, HugeAmount.read(new CompoundTag()));
        CompoundTag small = new CompoundTag(); small.putLong("long", Long.MAX_VALUE);
        assertEquals(small, HugeAmount.read(small).write());
        CompoundTag big = new CompoundTag();
        big.putString("big", BigInteger.valueOf(Long.MAX_VALUE).pow(3).toString());
        assertEquals(big, HugeAmount.read(big).write());
        assertEquals(Long.MAX_VALUE, HugeAmount.read(big).toLongSaturated());
        assertThrows(IllegalArgumentException.class, () -> HugeAmount.read(negative()));
    }
    private static CompoundTag negative() { CompoundTag tag = new CompoundTag(); tag.putString("big", "-1"); return tag; }
}
