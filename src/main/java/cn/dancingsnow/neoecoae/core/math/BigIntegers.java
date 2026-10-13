package cn.dancingsnow.neoecoae.core.math;

import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

public final class BigIntegers {
    private BigIntegers() {
    }

    public static BigInteger nonNegative(@Nullable BigInteger value) {
        if (value == null || value.signum() < 0) {
            return BigInteger.ZERO;
        }
        return value;
    }
}
