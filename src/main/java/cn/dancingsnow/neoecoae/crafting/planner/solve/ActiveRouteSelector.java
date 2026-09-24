package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.crafting.planner.graph.SccComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects one actual producer route before numeric solving; the universe never decides cyclicity. */
public final class ActiveRouteSelector {
    private static final long MAX_ROUTE_WORK = 4_000_000L;
    private static final long MIN_ROUTE_WORK = 4_096L;

    public record Selection(
        boolean acyclic,
        Map<AEKey, Integer> choices,
        CondensationGraph condensation,
        List<CycleComponent> cyclicComponents,
        List<CompiledPattern> deferredCyclicCandidates,
        boolean budgetExhausted
    ) {
        public Selection {
            choices = Map.copyOf(choices);
            cyclicComponents = List.copyOf(cyclicComponents);
            deferredCyclicCandidates = List.copyOf(deferredCyclicCandidates);
        }

        /** Compatibility constructor for callers that do not need budget diagnostics. */
        public Selection(boolean acyclic, Map<AEKey, Integer> choices, CondensationGraph condensation,
                List<CycleComponent> cyclicComponents, List<CompiledPattern> deferredCyclicCandidates) {
            this(acyclic, choices, condensation, cyclicComponents, deferredCyclicCandidates, false);
        }

        public Status status() {
            return budgetExhausted ? Status.BUDGET_EXHAUSTED : acyclic ? Status.ACYCLIC : Status.CYCLIC;
        }

        public enum Status { ACYCLIC, CYCLIC, BUDGET_EXHAUSTED }
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
            return new Selection(cycles.isEmpty(), choices, condensation, cycles, List.of(), false);
        }

        RouteWorkBudget budget = RouteWorkBudget.create(universe, candidatesByKey);
        CraftingDependencyGraph initialActive = activeGraph(universe, choices, candidatesByKey, cancellation);
        if (!budget.consume(budget.analysisWork())) {
            List<SccComponent> sccs = tarjan.analyze(initialActive, cancellation);
            return finish(choices, sccs, initialActive, deferred, true, cancellation);
        }
        List<SccComponent> initialSccs = tarjan.analyze(initialActive, cancellation);
        List<SccComponent> cyclic = cyclicComponents(initialSccs);
        if (cyclic.isEmpty()) {
            return finish(choices, initialSccs, initialActive, deferred, false, cancellation);
        }

        Set<List<Integer>> visited = new HashSet<>();
        visited.add(choiceVector(universe, choices));
        ArrayDeque<SearchFrame> frames = new ArrayDeque<>();
        Analysis current = new Analysis(initialActive, initialSccs);
        while (true) {
            cancellation.checkpoint();
            cyclic = cyclicComponents(current.sccs());
            if (cyclic.isEmpty()) {
                // Every state that reaches this point has passed a complete Tarjan analysis.
                return finish(choices, current.sccs(), current.graph(), deferred, false, cancellation);
            }

            CandidateBranches branches = candidateBranches(current.graph(), cyclic, choices, candidatesByKey,
                budget, cancellation);
            if (branches.budgetExhausted()) {
                return finish(choices, current.sccs(), current.graph(), deferred, true, cancellation);
            }
            if (!branches.changes().isEmpty()) {
                frames.push(new SearchFrame(branches.changes()));
            }
            if (!advanceSearch(frames, choices, candidatesByKey, deferred, universe, visited)) {
                // All bounded combinations were exhausted. The root state is restored by advanceSearch.
                return finish(choices, initialSccs, initialActive, deferred, false, cancellation);
            }

            if (!budget.consume(budget.analysisWork())) {
                SearchFrame appliedFrame = frames.peek();
                if (appliedFrame != null && appliedFrame.applied() != null) {
                    undo(appliedFrame.applied(), choices);
                    appliedFrame.applied(null);
                }
                return finish(choices, current.sccs(), current.graph(), deferred, true, cancellation);
            }
            CraftingDependencyGraph active = activeGraph(universe, choices, candidatesByKey, cancellation);
            List<SccComponent> sccs = tarjan.analyze(active, cancellation);
            current = new Analysis(active, sccs);
        }
    }

    /** Build a route for one explicit producer, including a self-loop that cycle avoidance skipped. */
    public Selection selectWithPattern(CraftingDependencyGraph universe, AEKey key, CompiledPattern pattern,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, Integer> choices = new LinkedHashMap<>();
        Map<AEKey, List<CompiledPattern>> candidatesByKey = new LinkedHashMap<>();
        for (AEKey candidateKey : universe.nodes().keySet()) {
            choices.put(candidateKey, 0);
            candidatesByKey.put(candidateKey, fastCandidates(universe, candidateKey));
        }
        int index = candidatesByKey.getOrDefault(key, List.of()).indexOf(pattern);
        if (index < 0) throw new IllegalArgumentException("Pattern is not a supported producer of " + key);
        choices.put(key, index);
        CraftingDependencyGraph active = activeGraph(universe, choices, candidatesByKey, cancellation);
        return finish(choices, tarjan.analyze(active, cancellation), active, List.of(), false, cancellation);
    }

    public Selection selectWithChoices(CraftingDependencyGraph universe, Map<AEKey, Integer> choices,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, List<CompiledPattern>> candidates = new LinkedHashMap<>();
        for (AEKey key : universe.nodes().keySet()) candidates.put(key, fastCandidates(universe, key));
        CraftingDependencyGraph active = activeGraph(universe, choices, candidates, cancellation);
        return finish(choices, tarjan.analyze(active, cancellation), active, List.of(), false, cancellation);
    }

    private static Selection finish(Map<AEKey, Integer> choices,
            List<SccComponent> sccs, CraftingDependencyGraph active, List<CompiledPattern> deferred,
            boolean budgetExhausted, ECOCancellation cancellation) throws InterruptedException {
        CondensationGraph condensation = CondensationGraph.build(active, sccs, cancellation);
        List<CycleComponent> cycles = condensation.cycles();
        return new Selection(cycles.isEmpty() && !budgetExhausted, choices, condensation,
            cycles, deferred, budgetExhausted);
    }

    private static List<SccComponent> cyclicComponents(List<SccComponent> sccs) {
        return sccs.stream().filter(SccComponent::cyclic).toList();
    }

    private static CandidateBranches candidateBranches(CraftingDependencyGraph active,
            List<SccComponent> cyclic, Map<AEKey, Integer> choices,
            Map<AEKey, List<CompiledPattern>> candidatesByKey, RouteWorkBudget budget,
            ECOCancellation cancellation) throws InterruptedException {
        List<ChoiceChange> hopeful = new ArrayList<>();
        List<ChoiceChange> definiteCycle = new ArrayList<>();
        for (SccComponent scc : cyclic) {
            cancellation.checkpoint();
            for (AEKey member : scc.members()) {
                List<CompiledPattern> candidates = candidatesByKey.getOrDefault(member, List.of());
                int current = choices.getOrDefault(member, 0);
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                    if (candidateIndex == current) continue;
                    CompiledPattern candidate = candidates.get(candidateIndex);
                    Precheck precheck = definitelyCyclic(active, member, candidates.get(current), candidate,
                        budget, cancellation);
                    if (precheck.budgetExhausted()) return new CandidateBranches(List.of(), true);
                    ChoiceChange change = new ChoiceChange(member, current, candidateIndex);
                    if (precheck.definitelyCyclic()) definiteCycle.add(change);
                    else hopeful.add(change);
                }
            }
        }
        // A candidate that does not close a currently reachable path is tried first. Definite-cycle candidates
        // remain in the list so a later change in another member can make their old path disappear; this preserves
        // complete bounded combination search instead of turning the precheck into an unsafe pruning rule.
        hopeful.addAll(definiteCycle);
        return new CandidateBranches(List.copyOf(hopeful), false);
    }

    private static Precheck definitelyCyclic(CraftingDependencyGraph active, AEKey member,
            CompiledPattern current, CompiledPattern candidate, RouteWorkBudget budget,
            ECOCancellation cancellation) throws InterruptedException {
        for (CraftingGraphEdge edge : patternEdges(member, candidate)) {
            cancellation.checkpoint();
            if (!budget.consume(1L)) return new Precheck(false, true);
            if (edge.producer().equals(edge.requiredInput())) return new Precheck(true, false);
            if (canReach(active, edge.requiredInput(), edge.producer(), current, budget, cancellation)) {
                return new Precheck(true, false);
            }
        }
        return new Precheck(false, false);
    }

    private static boolean canReach(CraftingDependencyGraph graph, AEKey start, AEKey goal,
            CompiledPattern removedPattern, RouteWorkBudget budget, ECOCancellation cancellation)
            throws InterruptedException {
        if (start.equals(goal)) return true;
        ArrayDeque<AEKey> queue = new ArrayDeque<>();
        Set<AEKey> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            AEKey node = queue.removeFirst();
            for (CraftingGraphEdge edge : graph.outgoing(node)) {
                if (edge.pattern() == removedPattern) continue;
                if (!budget.consume(1L)) return false;
                AEKey target = edge.requiredInput();
                if (target.equals(goal)) return true;
                if (seen.add(target)) queue.addLast(target);
            }
        }
        return false;
    }

    private static boolean advanceSearch(ArrayDeque<SearchFrame> frames, Map<AEKey, Integer> choices,
            Map<AEKey, List<CompiledPattern>> candidatesByKey, List<CompiledPattern> deferred,
            CraftingDependencyGraph universe, Set<List<Integer>> visited) {
        while (!frames.isEmpty()) {
            SearchFrame frame = frames.peek();
            if (frame.applied() != null) {
                undo(frame.applied(), choices);
                frame.applied(null);
            }
            while (frame.next() < frame.changes().size()) {
                ChoiceChange change = frame.changes().get(frame.next());
                frame.next(frame.next() + 1);
                apply(change, choices, candidatesByKey, deferred);
                frame.applied(change);
                if (visited.add(choiceVector(universe, choices))) return true;
                undo(change, choices);
                frame.applied(null);
            }
            frames.pop();
        }
        return false;
    }

    private static void apply(ChoiceChange change, Map<AEKey, Integer> choices,
            Map<AEKey, List<CompiledPattern>> candidatesByKey, List<CompiledPattern> deferred) {
        choices.put(change.key(), change.nextChoice());
        List<CompiledPattern> candidates = candidatesByKey.getOrDefault(change.key(), List.of());
        if (change.previousChoice() >= 0 && change.previousChoice() < candidates.size()) {
            deferred.add(candidates.get(change.previousChoice()));
        }
    }

    private static void undo(ChoiceChange change, Map<AEKey, Integer> choices) {
        choices.put(change.key(), change.previousChoice());
    }

    private static List<Integer> choiceVector(CraftingDependencyGraph universe, Map<AEKey, Integer> choices) {
        List<Integer> vector = new ArrayList<>(universe.nodes().size());
        for (AEKey key : universe.nodes().keySet()) vector.add(choices.getOrDefault(key, 0));
        return List.copyOf(vector);
    }

    private static List<CraftingGraphEdge> patternEdges(AEKey key, CompiledPattern pattern) {
        return cn.dancingsnow.neoecoae.crafting.planner.graph.PatternDependencyEdges.of(key, pattern);
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
            if (choice >= 0) edges.addAll(patternEdges(key, candidates.get(choice)));
        }
        for (CraftingGraphEdge edge : edges) {
            nodes.putIfAbsent(edge.producer(), new CraftingGraphNode(edge.producer(), List.of()));
            nodes.putIfAbsent(edge.requiredInput(), new CraftingGraphNode(edge.requiredInput(), List.of()));
        }
        return new CraftingDependencyGraph(universe.goal(), nodes, edges);
    }

    private static List<CompiledPattern> fastCandidates(CraftingDependencyGraph graph, AEKey key) {
        return graph.nodes().getOrDefault(key, new CraftingGraphNode(key, List.of())).candidatePatterns().stream()
            .filter(CompiledPattern::fastSupported).toList();
    }

    private record Analysis(CraftingDependencyGraph graph, List<SccComponent> sccs) { }
    private record CandidateBranches(List<ChoiceChange> changes, boolean budgetExhausted) { }
    private record Precheck(boolean definitelyCyclic, boolean budgetExhausted) { }
    private record ChoiceChange(AEKey key, int previousChoice, int nextChoice) { }

    private static final class SearchFrame {
        private final List<ChoiceChange> changes;
        private int next;
        private ChoiceChange applied;

        private SearchFrame(List<ChoiceChange> changes) {
            this.changes = changes;
        }

        private List<ChoiceChange> changes() { return changes; }
        private int next() { return next; }
        private void next(int value) { next = value; }
        private ChoiceChange applied() { return applied; }
        private void applied(ChoiceChange value) { applied = value; }
    }

    private static final class RouteWorkBudget {
        private final long limit;
        private final long analysisWork;
        private long used;

        private RouteWorkBudget(long limit, long analysisWork) {
            this.limit = limit;
            this.analysisWork = analysisWork;
        }

        private static RouteWorkBudget create(CraftingDependencyGraph universe,
                Map<AEKey, List<CompiledPattern>> candidatesByKey) {
            long structuralWork = saturatingAdd(universe.nodes().size(), universe.edges().size());
            long analysisWork = Math.max(1L, saturatingMultiply(structuralWork, 2L));
            long candidateCount = 0L;
            for (List<CompiledPattern> candidates : candidatesByKey.values()) {
                candidateCount = saturatingAdd(candidateCount, candidates.size());
            }
            long requested = saturatingAdd(saturatingMultiply(analysisWork, 8L),
                saturatingMultiply(candidateCount, 4L));
            long limit = Math.max(analysisWork, Math.min(MAX_ROUTE_WORK,
                Math.max(MIN_ROUTE_WORK, requested)));
            return new RouteWorkBudget(limit, analysisWork);
        }

        private long analysisWork() { return analysisWork; }

        private boolean consume(long amount) {
            if (amount <= 0L) return true;
            if (used > limit - amount) return false;
            used += amount;
            return true;
        }

        private static long saturatingAdd(long left, long right) {
            return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
        }

        private static long saturatingMultiply(long left, long right) {
            return left != 0L && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
        }
    }
}
