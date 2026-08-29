package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.stacks.AEKey;

public record PlanTraceEdge(AEKey from, AEKey to, long amount) {
}
