package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

final class CheckedAmounts {
    private CheckedAmounts() {}
    static long add(long a, long b, String operation) throws AmountOverflowException {
        if (a < 0 || b < 0 || a > Long.MAX_VALUE - b) throw new AmountOverflowException(operation);
        return a + b;
    }
    static long multiply(long a, long b, String operation) throws AmountOverflowException {
        if (a < 0 || b < 0 || (a != 0 && b > Long.MAX_VALUE / a)) throw new AmountOverflowException(operation);
        return a * b;
    }
    static long ceilDiv(long value, long divisor) throws AmountOverflowException {
        if (value < 0 || divisor <= 0) throw new AmountOverflowException("ceilDiv");
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
    static long stackBytes(long amount, long amountPerByte) throws AmountOverflowException {
        if (amount < 0 || amountPerByte <= 0) throw new AmountOverflowException("stack bytes");
        long whole = multiply(amount / amountPerByte, 8, "stack bytes");
        long remainder = amount % amountPerByte;
        long partial = ceilDiv(multiply(remainder, 8, "stack bytes"), amountPerByte);
        return add(whole, partial, "stack bytes");
    }
}
