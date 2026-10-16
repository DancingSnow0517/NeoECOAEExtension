package cn.dancingsnow.neoecoae.core.amount;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class PlannerAmountInfinityTest {
    @Test void infinityIsExplicitAndCannotEnterFiniteExecutionQuantities() {
        var infinity = PlannerAmount.unbounded();
        assertTrue(infinity.infinite());
        assertFalse(infinity.fitsLong());
        assertFalse(infinity.isZero());
        assertTrue(infinity.compareTo(PlannerAmount.of(BigInteger.ONE.shiftLeft(500))) > 0);
        assertThrows(ArithmeticException.class, infinity::longValueExact);
        assertThrows(ArithmeticException.class, infinity::toBigInteger);
        assertEquals(PlannerAmount.ONE, infinity.min(PlannerAmount.ONE));
        assertEquals(infinity, PlannerAmount.ONE.max(infinity));
    }

    @Test void definedOperationsRemainUnboundedAndUndefinedOperationsFail() {
        var infinity = PlannerAmount.unbounded();
        assertSame(infinity, infinity.add(3));
        assertSame(infinity, infinity.subtract(3));
        assertSame(infinity, infinity.multiply(3));
        assertSame(infinity, infinity.divide(PlannerAmount.of(3)));
        assertSame(infinity, infinity.ceilDiv(PlannerAmount.of(3)));
        assertSame(infinity, PlannerAmount.stackBytes(infinity, 8));
        assertSame(PlannerAmount.ZERO, infinity.multiply(0));
        assertSame(PlannerAmount.ZERO, PlannerAmount.ONE.divide(infinity));
        assertSame(PlannerAmount.ONE, PlannerAmount.ONE.ceilDiv(infinity));
        assertThrows(ArithmeticException.class, () -> infinity.subtract(infinity));
        assertThrows(ArithmeticException.class, () -> PlannerAmount.ONE.subtract(infinity));
        assertThrows(ArithmeticException.class, () -> infinity.divide(infinity));
        assertThrows(ArithmeticException.class, () -> infinity.remainder(PlannerAmount.ONE));
        assertThrows(ArithmeticException.class, () -> infinity.multiply(-1));
    }

    @Test void finitePromotionDemotionAndCompatibilityDtoStayExact() {
        var overflow = PlannerAmount.of(Long.MAX_VALUE).add(1);
        assertEquals(BigInteger.ONE.shiftLeft(63), overflow.toBigInteger());
        assertEquals(PlannerAmount.of(Long.MAX_VALUE), overflow.subtract(1));
        assertEquals(BigInteger.ONE.shiftLeft(63), PlannerAmount.of(Long.MIN_VALUE)
            .divide(PlannerAmount.of(-1)).toBigInteger());
        assertEquals(PlannerAmount.of(-4), PlannerAmount.ONE.subtract(5));
        var dto = ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)).add(1);
        assertEquals(overflow, dto.plannerAmount());
        assertTrue(dto.add(ExactAmount.unbounded()).infinite());
        assertSame(PlannerAmount.INFINITE, ExactAmount.unbounded().plannerAmount());
        assertThrows(IllegalArgumentException.class, () -> ExactAmount.finite(BigInteger.ZERO).add(-1));
    }
}
