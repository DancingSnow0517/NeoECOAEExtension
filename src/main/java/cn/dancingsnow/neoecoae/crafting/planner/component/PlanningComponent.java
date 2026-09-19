package cn.dancingsnow.neoecoae.crafting.planner.component;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.List;

public sealed interface PlanningComponent permits AcyclicComponent, CycleComponent {
    int componentId();

    List<AEKey> members();

    List<CompiledPattern> patterns();

    default boolean cyclic() {
        return this instanceof CycleComponent;
    }
}
