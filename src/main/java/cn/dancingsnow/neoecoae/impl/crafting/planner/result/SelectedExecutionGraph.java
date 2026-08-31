package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final physical dependency graph. It is built from selected task semantics, never structural candidates. */
public record SelectedExecutionGraph(Set<IPatternDetails> tasks, List<ExecutionDependency> dependencies) {
    public SelectedExecutionGraph {
        tasks = Set.copyOf(tasks);
        dependencies = List.copyOf(dependencies);
    }

    public static SelectedExecutionGraph build(Map<IPatternDetails, PatternSemantics> selected) {
        List<IPatternDetails> patterns = new ArrayList<>(selected.keySet());
        List<ExecutionDependency> edges = new ArrayList<>();
        for (int c = 0; c < patterns.size(); c++) {
            IPatternDetails consumer = patterns.get(c);
            PatternSemantics cs = selected.get(consumer);
            for (PatternSemantics.Input input : cs.consumedInputs()) {
                AEKey key = input.key();
                for (int p = 0; p < patterns.size(); p++) {
                    if (p == c) continue;
                    IPatternDetails producer = patterns.get(p);
                    PatternSemantics ps = selected.get(producer);
                    if (ps.producedKeys().contains(key) || ps.returnedKeys().contains(key)) {
                        edges.add(new ExecutionDependency(producer, consumer, key));
                    }
                }
            }
        }
        return new SelectedExecutionGraph(new LinkedHashSet<>(patterns), edges);
    }
}
