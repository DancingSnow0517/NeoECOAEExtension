package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.stacks.AEKey;
import java.util.List;

public record ComponentTrace(int componentId, Type type, List<AEKey> members) {
    public enum Type { ACYCLIC, CYCLIC }
    public ComponentTrace { members = List.copyOf(members); }
}
