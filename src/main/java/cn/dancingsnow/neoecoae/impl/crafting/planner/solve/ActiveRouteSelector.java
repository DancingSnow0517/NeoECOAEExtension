package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.SccComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selects one actual producer route before numeric solving; the universe never decides cyclicity. */
public final class ActiveRouteSelector {
    public record Selection(
        boolean acyclic,
        Map<AEKey, Integer> choices,
        CondensationGraph condensation,
        List<CycleComponent> cyclicComponents,
        List<CompiledPattern> deferredCyclicCandidates
    ) {
        public Selection {
            choices = Map.copyOf(choices);
            cyclicComponents = List.copyOf(cyclicComponents);
            deferredCyclicCandidates = List.copyOf(deferredCyclicCandidates);
        }
    }

    private final TarjanSccAnalyzer tarjan = new TarjanSccAnalyzer();

    public Selection select(CraftingDependencyGraph universe, ECOCancellation cancellation)
            throws InterruptedException {
        return select(universe, true, cancellation);
    }

    /**
     * Selects the active producer route. When cycle avoidance is disabled, the first supported producer of
     * every key is analyzed exactly once; cyclic alternatives are not searched.
     */
    public Selection select(CraftingDependencyGraph universe, boolean avoidCycles,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, Integer> choices = new LinkedHashMap<>();
        Map<AEKey, List<CompiledPattern>> candidatesByKey = new LinkedHashMap<>();
        List<CompiledPattern> deferred = new ArrayList<>();
        for (AEKey key : universe.nodes().keySet()) {
            choices.put(key, 0);
            candidatesByKey.put(key, fastCandidates(universe, key));
        }
        if (!avoidCycles) {
            CraftingDependencyGraph active = activeGraph(universe, choices, candidatesByKey, cancellation);
            List<SccComponent> sccs = tarjan.analyze(active, cancellation);
            CondensationGraph condensation = CondensationGraph.build(active, sccs, cancellation);
            List<CycleComponent> cycles = condensation.cycles();
            return new Selection(cycles.isEmpty(), choices, condensation, cycles, List.of());
        }
        int guard = Math.max(1, universe.nodes().size() + universe.edges().size() + 1);
        for (int attempt = 0; attempt < guard; attempt++) {
            cancellation.checkpoint();
            CraftingDependencyGraph active = activeGraph(universe, choices, candidatesByKey, cancellation);
            List<SccComponent> sccs = tarjan.analyze(active, cancellation);
            List<SccComponent> cyclic = sccs.stream().filter(SccComponent::cyclic).toList();
            CondensationGraph condensation = CondensationGraph.build(active, sccs, cancellation);
            if (cyclic.isEmpty()) return new Selection(true, choices, condensation, List.of(), deferred);

            boolean advanced = false;
            for (SccComponent scc : cyclic) {
                cancellation.checkpoint();
                for (AEKey member : scc.members()) {
                    List<CompiledPattern> candidates = candidatesByKey.getOrDefault(member, List.of());
                    int current = choices.getOrDefault(member, 0);
                    if (current + 1 < candidates.size()) {
                        deferred.add(candidates.get(current));
                        choices.put(member, current + 1);
                        advanced = true;
                        break;
                    }
                }
                if (advanced) break;
            }
            if (!advanced) return new Selection(false, choices, condensation, condensation.cycles(), deferred);
        }
        CraftingDependencyGraph active = activeGraph(universe, choices, candidatesByKey, cancellation);
        List<SccComponent> sccs = tarjan.analyze(active, cancellation);
        CondensationGraph condensation = CondensationGraph.build(active, sccs, cancellation);
        return new Selection(false, choices, condensation, condensation.cycles(), deferred);
    }

    private static CraftingDependencyGraph activeGraph(CraftingDependencyGraph universe,
            Map<AEKey, Integer> choices, Map<AEKey, List<CompiledPattern>> candidatesByKey,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        List<CraftingGraphEdge> edges = new ArrayList<>();
        for (AEKey key : universe.nodes().keySet()) {
            cancellation.checkpoint();
            List<CompiledPattern> candidates = candidatesByKey.getOrDefault(key, List.of());
            int choice = choices.getOrDefault(key, 0);
            if (choice >= candidates.size()) choice = candidates.size() - 1;
            List<CompiledPattern> selected = choice >= 0 ? List.of(candidates.get(choice)) : List.of();
            nodes.put(key, new CraftingGraphNode(key, selected));
            if (choice >= 0) {
                CompiledPattern pattern = candidates.get(choice);
                for (var input : pattern.inputs()) {
                    edges.add(new CraftingGraphEdge(key, input.key(), pattern, input));
                }
            }
        }
        return new CraftingDependencyGraph(universe.goal(), nodes, edges);
    }

    private static List<CompiledPattern> fastCandidates(CraftingDependencyGraph graph, AEKey key) {
        return graph.nodes().getOrDefault(key, new CraftingGraphNode(key, List.of())).candidatePatterns().stream()
            .filter(CompiledPattern::fastSupported).toList();
    }
}
