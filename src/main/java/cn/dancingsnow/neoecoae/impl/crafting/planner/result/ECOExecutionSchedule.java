package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;

/** Runtime component phases. The list is explicitly execution (supplier-to-consumer) order. */
public record ECOExecutionSchedule(List<ComponentExecutionPhase> phases) {
    public ECOExecutionSchedule { phases = List.copyOf(phases); }
    public record ComponentExecutionPhase(int componentId, Type type, Set<IPatternDetails> patternSet,
            List<IPatternDetails> cycleWitness) {
        public ComponentExecutionPhase { patternSet = Set.copyOf(patternSet); cycleWitness = List.copyOf(cycleWitness); }
    }
    public enum Type { DAG, CYCLE }

    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder) {
        Map<Integer, ComponentPlanningResult> byId = components.stream().collect(
            java.util.stream.Collectors.toMap(ComponentPlanningResult::componentId, c -> c));
        var phases = new ArrayList<ComponentExecutionPhase>();
        for (int id : executionOrder) {
            var c = byId.get(id);
            if (c == null) continue;
            var witness = c.cycleResult() == null ? List.<IPatternDetails>of()
                : c.cycleResult().executionWitness().stream().map(w -> w.pattern().details()).toList();
            phases.add(new ComponentExecutionPhase(id,
                c.type() == ComponentPlanningResult.Type.CYCLIC ? Type.CYCLE : Type.DAG, c.patterns(), witness));
        }
        return new ECOExecutionSchedule(phases);
    }
}
