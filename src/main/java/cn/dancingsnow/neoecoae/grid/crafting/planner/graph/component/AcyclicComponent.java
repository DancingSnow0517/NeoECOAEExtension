package cn.dancingsnow.neoecoae.grid.crafting.planner.graph.component;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.grid.crafting.planner.compile.CompiledPattern;
import java.util.List;

/** A singleton SCC with no self-loop. */
public record AcyclicComponent(int componentId, AEKey key, List<CompiledPattern> patterns)
        implements PlanningComponent {
    public AcyclicComponent {

    }

    @Override public List<AEKey> members() { return List.of(key); }
}
