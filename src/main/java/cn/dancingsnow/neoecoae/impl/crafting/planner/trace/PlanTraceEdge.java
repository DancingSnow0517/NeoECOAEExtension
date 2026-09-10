package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;

public record PlanTraceEdge(AEKey from, AEKey to, long amount, BigInteger exactAmount) {
    public PlanTraceEdge(AEKey from, AEKey to, long amount) {
        this(from, to, amount, BigInteger.valueOf(amount));
    }
}
