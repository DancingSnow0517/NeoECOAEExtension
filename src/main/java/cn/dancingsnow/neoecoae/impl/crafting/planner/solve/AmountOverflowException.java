package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

final class AmountOverflowException extends Exception {
    AmountOverflowException(String operation) { super(operation); }
}
