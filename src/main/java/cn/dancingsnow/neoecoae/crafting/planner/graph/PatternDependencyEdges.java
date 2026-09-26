package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import java.util.ArrayList;
import java.util.List;

/** Shared structural dependencies for both the candidate universe and a selected route. */
public final class PatternDependencyEdges {
    private PatternDependencyEdges() {}

    public static List<CraftingGraphEdge> of(AEKey key, CompiledPattern pattern) {
        List<CraftingGraphEdge> edges = new ArrayList<>();
        for (var input : pattern.inputs()) {
            if (!pattern.specialAnalysis().excludesFromCycleGraph(input)) {
                edges.add(new CraftingGraphEdge(key, input.key(), pattern, input));
            }
        }
        // A byproduct is available only after this producer fires. Its reverse dependency closes
        // feedback through any upstream recipe, without registering it as an independent producer.
        for (var output : pattern.outputs()) {
            if (!output.what().equals(key)) {
                edges.add(new CraftingGraphEdge(output.what(), key, pattern,
                    new CompiledInput(null, key, output.amount(), true, null)));
            }
        }
        for (var feedback : pattern.semantics().feedbackEdges()) {
            if (pattern.specialAnalysis().requirements().stream()
                    .anyMatch(requirement -> feedback.returnedKey().equals(requirement.returnedKey()))) continue;
            var input = pattern.inputs().stream()
                .filter(candidate -> feedback.returnedKey().equals(candidate.remainderKey())
                    || feedback.returnedKey().equals(candidate.key()))
                .findFirst().orElse(pattern.inputs().isEmpty()
                    ? new CompiledInput(null, feedback.dependentOutput(), 1L, true, null)
                    : pattern.inputs().getFirst());
            edges.add(new CraftingGraphEdge(feedback.returnedKey(), feedback.dependentOutput(), pattern, input));
        }
        return List.copyOf(edges);
    }
}
