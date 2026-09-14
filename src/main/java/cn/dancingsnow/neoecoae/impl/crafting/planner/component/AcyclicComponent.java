package cn.dancingsnow.neoecoae.impl.crafting.planner.component;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.List;

/** A singleton SCC with no self-loop. */
public record AcyclicComponent(int componentId, AEKey key, List<CompiledPattern> patterns)
        implements PlanningComponent {
    public AcyclicComponent {
        patterns = List.copyOf(patterns);
    }

    @Override public List<AEKey> members() { return List.of(key); }
}
