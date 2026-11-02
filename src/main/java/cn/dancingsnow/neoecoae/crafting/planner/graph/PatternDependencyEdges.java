package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Shared structural dependencies for both the candidate universe and a selected route. */
public final class PatternDependencyEdges {
    private PatternDependencyEdges() {}

    public static List<CraftingGraphEdge> of(AEKey key, CompiledPattern pattern,
            Function<AEKey, List<CompiledPattern>> producers) {
        List<CraftingGraphEdge> edges = new ArrayList<>();
        for (var input : pattern.inputs()) {
            if (!pattern.specialAnalysis().excludesFromCycleGraph(input)) {
                edges.add(new CraftingGraphEdge(key, input.key(), pattern, input));
            }
        }
        // Every view of one physical pattern uses the same dependency anchor, so sibling outputs
        // cannot introduce opposite edges. Prefer its primary view when offered, otherwise use the
        // first offered output. Keeping the edges in the latter case still closes real seed feedback.
        AEKey anchor = dependencyAnchor(key, pattern, producers);
        for (var output : pattern.outputs()) {
            if (output != null && output.what() != null && output.amount() > 0
                    && !output.what().equals(anchor)) {
                edges.add(new CraftingGraphEdge(output.what(), anchor, pattern,
                    new CompiledInput(null, anchor, output.amount(), true, null)));
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
        return edges;
    }

    private static AEKey dependencyAnchor(AEKey key, CompiledPattern pattern,
            Function<AEKey, List<CompiledPattern>> producers) {
        try {
            var primary = pattern.details() == null ? null : pattern.details().getPrimaryOutput();
            if (primary != null && primary.what() != null && hasView(primary.what(), pattern, producers)) {
                return primary.what();
            }
        } catch (RuntimeException ignored) {
            // Malformed or unavailable primary metadata must not discard the compiled output contract.
        }
        for (var output : pattern.outputs()) {
            if (output != null && output.what() != null && hasView(output.what(), pattern, producers)) {
                return output.what();
            }
        }
        return key;
    }

    private static boolean hasView(AEKey key, CompiledPattern pattern,
            Function<AEKey, List<CompiledPattern>> producers) {
        return producers.apply(key).stream().anyMatch(candidate -> candidate.details() != null
            && candidate.details().equals(pattern.details()));
    }
}
