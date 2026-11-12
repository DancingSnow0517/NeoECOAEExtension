package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd;
import static cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingMultiply;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CycleStructure;
import cn.dancingsnow.neoecoae.crafting.planner.graph.SccComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
        var choices = new Object2IntLinkedOpenHashMap<AEKey>(universe.nodes().size());
        Map<AEKey, List<CompiledPattern>> candidatesByKey = new Object2ObjectLinkedOpenHashMap<>(universe.nodes().size());
        List<CompiledPattern> deferred = new ArrayList<>();
        for (AEKey key : universe.nodes().keySet()) {
            choices.put(key, 0);
            candidatesByKey.put(key, fastCandidates(universe, key));
        }
        if (!avoidCycles) {
            Analysis analysis = analyze(activeGraph(universe, choices, candidatesByKey, cancellation), cancellation);
            CondensationGraph condensation = CondensationGraph.build(analysis.graph(), analysis.sccs(), cancellation);
            List<CycleComponent> cycles = condensation.cycles();
            return new Selection(cycles.isEmpty(), choices, condensation, cycles, List.of(), false);
        }

        RouteWorkBudget budget = RouteWorkBudget.create(universe, candidatesByKey);
        CraftingDependencyGraph initialActive = activeGraph(universe, choices, candidatesByKey, cancellation);
        if (!budget.consume(budget.analysisWork())) {
            Analysis exhausted = analyze(initialActive, cancellation);
            return finish(choices, exhausted.sccs(), exhausted.graph(), deferred, true, cancellation);
        }
        Analysis initial = analyze(initialActive, cancellation);
        List<SccComponent> cyclic = cyclicComponents(initial.sccs());
        if (cyclic.isEmpty()) {
            return finish(choices, initial.sccs(), initial.graph(), deferred, false, cancellation);
        }

        Set<List<Integer>> visited = new ObjectOpenHashSet<>();
        visited.add(choiceVector(universe, choices));
        ArrayDeque<SearchFrame> frames = new ArrayDeque<>();
        Analysis current = initial;
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
                return finish(choices, initial.sccs(), initial.graph(), deferred, false, cancellation);
            }

            if (!budget.consume(budget.analysisWork())) {
                SearchFrame appliedFrame = frames.peek();
                if (appliedFrame != null && appliedFrame.applied() != null) {
                    undo(appliedFrame.applied(), choices);
                    appliedFrame.applied(null);
                }
                return finish(choices, current.sccs(), current.graph(), deferred, true, cancellation);
            }
            current = analyze(activeGraph(universe, choices, candidatesByKey, cancellation), cancellation);
        }
    }

    /** Build a route for one explicit producer, including a self-loop that cycle avoidance skipped. */
    public Selection selectWithPattern(CraftingDependencyGraph universe, AEKey key, CompiledPattern pattern,
            ECOCancellation cancellation) throws InterruptedException {
        var choices = new Object2IntLinkedOpenHashMap<AEKey>(universe.nodes().size());
        Map<AEKey, List<CompiledPattern>> candidatesByKey = new Object2ObjectLinkedOpenHashMap<>(universe.nodes().size());
        for (AEKey candidateKey : universe.nodes().keySet()) {
            choices.put(candidateKey, 0);
            candidatesByKey.put(candidateKey, fastCandidates(universe, candidateKey));
        }
        int index = candidatesByKey.getOrDefault(key, List.of()).indexOf(pattern);
        if (index < 0) throw new IllegalArgumentException("Pattern is not a supported producer of " + key);
        choices.put(key, index);
        Analysis analysis = analyze(activeGraph(universe, choices, candidatesByKey, cancellation), cancellation);
        return finish(choices, analysis.sccs(), analysis.graph(), List.of(), false, cancellation);
    }

    public Selection selectWithChoices(CraftingDependencyGraph universe, Map<AEKey, Integer> choices,
            ECOCancellation cancellation) throws InterruptedException {
        return selectWithChoices(universe, choices, cancellation, null);
    }

    Selection selectWithChoices(CraftingDependencyGraph universe, Map<AEKey, Integer> choices,
            ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws InterruptedException {
        long started = metrics == null ? 0L : System.nanoTime();
        CraftingDependencyGraph active;
        try {
            if (metrics != null) metrics.activeGraphBuilds++;
            Map<AEKey, List<CompiledPattern>> candidates = new Object2ObjectLinkedOpenHashMap<>(universe.nodes().size());
            for (AEKey key : universe.nodes().keySet()) candidates.put(key, fastCandidates(universe, key));
            active = activeGraph(universe, choices, candidates, cancellation);
        } finally {
            if (metrics != null) metrics.activeGraphNanos += System.nanoTime() - started;
        }
        Analysis analysis = analyze(active, cancellation, metrics);
        return finish(choices, analysis.sccs(), analysis.graph(), List.of(), false, cancellation, metrics);
    }

    /**
     * Tarjan over the active route, without byproduct-ordering edges that close no material loop.
     *
     * <p>{@link cn.dancingsnow.neoecoae.crafting.planner.graph.PatternDependencyEdges} links every byproduct to its
     * pattern's primary output so a byproduct consumer is scheduled after the pattern. Byproducts that cross-link
     * chains (A's pattern also yields B, B's pattern also yields A) therefore close rings that move no material.
     * Such a ring needs no seed and no cycle solver: the DAG pass still credits byproducts through its joint-output
     * demand queue and rejects a credit that would form a real supplier cycle. An ordering edge whose byproduct is
     * made from inputs inside the same material core is kept, so real feedback (a byproduct converted back into
     * its own pattern's input, seeds, catalysts, returned containers) still forms its cycle component.
     */
    private Analysis analyze(CraftingDependencyGraph active, ECOCancellation cancellation) throws InterruptedException {
        return analyze(active, cancellation, null);
    }

    private Analysis analyze(CraftingDependencyGraph active, ECOCancellation cancellation, AcyclicSolveMetrics metrics)
            throws InterruptedException {
        List<SccComponent> sccs = analyzeSccs(active, cancellation, metrics);
        Set<CraftingGraphEdge> dropped = new ObjectOpenHashSet<>();
        for (SccComponent scc : sccs) {
            cancellation.checkpoint();
            if (scc.cyclic()) dropped.addAll(CycleStructure.redundantOrderingEdges(scc.members(), scc.internalEdges()));
        }
        if (dropped.isEmpty()) return new Analysis(active, sccs, sccs);
        List<CraftingGraphEdge> kept = new ArrayList<>(active.edges().size() - dropped.size());
        for (CraftingGraphEdge edge : active.edges()) if (!dropped.contains(edge)) kept.add(edge);
        long started = metrics == null ? 0L : System.nanoTime();
        CraftingDependencyGraph pruned;
        try {
            if (metrics != null) metrics.prunedGraphBuilds++;
            pruned = new CraftingDependencyGraph(active.goal(), active.nodes(), kept);
        } finally {
            if (metrics != null) metrics.prunedGraphNanos += System.nanoTime() - started;
        }
        return new Analysis(pruned, analyzeSccs(pruned, cancellation, metrics), sccs);
    }

    private List<SccComponent> analyzeSccs(CraftingDependencyGraph graph, ECOCancellation cancellation,
            AcyclicSolveMetrics metrics) throws InterruptedException {
        long started = metrics == null ? 0L : System.nanoTime();
        try {
            if (metrics != null) metrics.sccPasses++;
            return tarjan.analyze(graph, cancellation);
        } finally {
            if (metrics != null) metrics.sccNanos += System.nanoTime() - started;
        }
    }

    private static Selection finish(Map<AEKey, Integer> choices,
            List<SccComponent> sccs, CraftingDependencyGraph active, List<CompiledPattern> deferred,
            boolean budgetExhausted, ECOCancellation cancellation) throws InterruptedException {
        return finish(choices, sccs, active, deferred, budgetExhausted, cancellation, null);
    }

    private static Selection finish(Map<AEKey, Integer> choices,
            List<SccComponent> sccs, CraftingDependencyGraph active, List<CompiledPattern> deferred,
            boolean budgetExhausted, ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws InterruptedException {
        long started = metrics == null ? 0L : System.nanoTime();
        CondensationGraph condensation;
        try {
            if (metrics != null) metrics.condensationBuilds++;
            condensation = CondensationGraph.build(active, sccs, cancellation);
        } finally {
            if (metrics != null) metrics.condensationNanos += System.nanoTime() - started;
        }
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
        return new CandidateBranches(hopeful, false);
    }

    private static Precheck definitelyCyclic(CraftingDependencyGraph active, AEKey member,
            CompiledPattern current, CompiledPattern candidate, RouteWorkBudget budget,
            ECOCancellation cancellation) throws InterruptedException {
        for (CraftingGraphEdge edge : cn.dancingsnow.neoecoae.crafting.planner.graph.PatternDependencyEdges.of(
                member, candidate, key -> key.equals(member) ? List.of(candidate)
                    : active.nodes().getOrDefault(key, new CraftingGraphNode(key, List.of())).candidatePatterns())) {
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
        Set<AEKey> seen = new ObjectOpenHashSet<>();
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
        return vector;
    }

    private static List<CraftingGraphEdge> patternEdges(AEKey key, CompiledPattern pattern,
            Map<AEKey, CraftingGraphNode> nodes) {
        return cn.dancingsnow.neoecoae.crafting.planner.graph.PatternDependencyEdges.of(key, pattern,
            output -> nodes.getOrDefault(output, new CraftingGraphNode(output, List.of())).candidatePatterns());
    }

    private static CraftingDependencyGraph activeGraph(CraftingDependencyGraph universe,
            Map<AEKey, Integer> choices, Map<AEKey, List<CompiledPattern>> candidatesByKey,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, CraftingGraphNode> nodes = new Object2ObjectLinkedOpenHashMap<>(universe.nodes().size());
        List<CraftingGraphEdge> edges = new ArrayList<>();
        for (AEKey key : universe.nodes().keySet()) {
            cancellation.checkpoint();
            List<CompiledPattern> candidates = candidatesByKey.getOrDefault(key, List.of());
            int choice = choices.getOrDefault(key, 0);
            if (choice >= candidates.size()) choice = candidates.size() - 1;
            List<CompiledPattern> selected = choice >= 0 ? List.of(candidates.get(choice)) : List.of();
            nodes.put(key, new CraftingGraphNode(key, selected));
        }
        for (var node : nodes.values()) {
            cancellation.checkpoint();
            for (var pattern : node.candidatePatterns()) {
                edges.addAll(patternEdges(node.key(), pattern, nodes));
            }
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

    /**
     * A solve-local cache of the numeric retry's cycle guard, never an externally published Selection.
     * Rows are indexed by producer view, not edge source: secondary-output edges can start at another key.
     */
    static final class RetryCheck {
        private final CraftingDependencyGraph universe;
        private final Set<IPatternDetails> deferred;
        private final AcyclicSolveMetrics metrics;
        private final ActiveRouteSelector selector = new ActiveRouteSelector();
        private final Map<AEKey, List<CompiledPattern>> candidates = new Object2ObjectLinkedOpenHashMap<>();
        private final Map<AEKey, CraftingGraphNode> nodes = new Object2ObjectLinkedOpenHashMap<>();
        private final Map<AEKey, List<CraftingGraphEdge>> rows = new Object2ObjectLinkedOpenHashMap<>();
        private final Map<IPatternDetails, Set<AEKey>> views = new HashMap<>();
        private final Object2IntOpenHashMap<AEKey> componentByKey = new Object2IntOpenHashMap<>();
        private final Set<AEKey> cyclicKeys = new ObjectOpenHashSet<>();
        private boolean analyzed;
        private boolean ordered;
        private boolean requiresCycle;

        RetryCheck(CraftingDependencyGraph universe, Set<IPatternDetails> deferred,
                AcyclicSolveMetrics metrics, ECOCancellation cancellation) throws InterruptedException {
            this.universe = universe;
            this.deferred = deferred;
            this.metrics = metrics;
            componentByKey.defaultReturnValue(-1);
            for (AEKey key : universe.nodes().keySet()) {
                cancellation.checkpoint();
                List<CompiledPattern> choices = fastCandidates(universe, key);
                candidates.put(key, choices);
                for (CompiledPattern pattern : choices) {
                    views.computeIfAbsent(pattern.details(), ignored -> new ObjectOpenHashSet<>()).add(key);
                }
            }
        }

        boolean requiresCycle(Map<AEKey, Integer> choices, ECOCancellation cancellation) throws InterruptedException {
            try {
                return check(choices, cancellation);
            } catch (InterruptedException | RuntimeException failure) {
                // A checkpoint may interrupt a partially updated row set or SCC analysis.
                analyzed = false;
                nodes.clear();
                rows.clear();
                throw failure;
            }
        }

        private boolean check(Map<AEKey, Integer> choices, ECOCancellation cancellation) throws InterruptedException {
            long started = metrics == null ? 0L : System.nanoTime();
            CraftingDependencyGraph active;
            try {
                Set<AEKey> affected = new ObjectOpenHashSet<>();
                for (var entry : candidates.entrySet()) {
                    cancellation.checkpoint();
                    AEKey key = entry.getKey();
                    List<CompiledPattern> available = entry.getValue();
                    int choice = choices.getOrDefault(key, 0);
                    if (choice >= available.size()) choice = available.size() - 1;
                    CompiledPattern selected = choice < 0 ? null : available.get(choice);
                    CraftingGraphNode oldNode = nodes.get(key);
                    CompiledPattern previous = oldNode == null || oldNode.candidatePatterns().isEmpty()
                        ? null : oldNode.candidatePatterns().get(0);
                    if (oldNode == null || previous != selected) {
                        nodes.put(key, new CraftingGraphNode(key, selected == null ? List.of() : List.of(selected)));
                        affected.add(key);
                        // Changing a physical pattern's offered primary view changes its siblings' anchors too.
                        if (previous != null) affected.addAll(views.get(previous.details()));
                        if (selected != null) affected.addAll(views.get(selected.details()));
                    }
                }
                boolean reusable = analyzed && ordered;
                for (AEKey key : affected) {
                    cancellation.checkpoint();
                    if (cyclicKeys.contains(key)) reusable = false;
                    List<CraftingGraphEdge> previous = rows.getOrDefault(key, List.of());
                    // Preserve every internal edge of the RAW SCCs, including ordering edges later pruned.
                    // Pruned SCCs alone cannot certify that an old ordering ring stays non-material.
                    if (reusable) for (CraftingGraphEdge edge : previous) {
                        if (componentByKey.getInt(edge.producer()) == componentByKey.getInt(edge.requiredInput())) {
                            reusable = false;
                            break;
                        }
                    }
                    CraftingGraphNode node = nodes.get(key);
                    List<CraftingGraphEdge> next = node.candidatePatterns().isEmpty() ? List.of()
                        : patternEdges(key, node.candidatePatterns().get(0), nodes);
                    if (reusable) for (CraftingGraphEdge edge : next) {
                        if (!followsOrder(edge)) {
                            reusable = false;
                            break;
                        }
                    }
                    rows.put(key, next);
                }
                if (metrics != null) {
                    metrics.edgeRowsReused += candidates.size() - affected.size();
                    metrics.edgeRowsRebuilt += affected.size();
                }
                if (analyzed && (affected.isEmpty() || reusable)) {
                    if (metrics != null) metrics.graphCacheHits++;
                    return requiresCycle;
                }
                if (metrics != null) {
                    if (analyzed) metrics.graphInvalidations++;
                    metrics.activeGraphBuilds++;
                }
                Map<AEKey, CraftingGraphNode> activeNodes = new Object2ObjectLinkedOpenHashMap<>(nodes);
                List<CraftingGraphEdge> edges = new ArrayList<>();
                // Match activeGraph's original node/edge order even when rows were updated out of order.
                for (AEKey key : candidates.keySet()) {
                    cancellation.checkpoint();
                    edges.addAll(rows.get(key));
                }
                for (CraftingGraphEdge edge : edges) {
                    activeNodes.putIfAbsent(edge.producer(), new CraftingGraphNode(edge.producer(), List.of()));
                    activeNodes.putIfAbsent(edge.requiredInput(), new CraftingGraphNode(edge.requiredInput(), List.of()));
                }
                active = new CraftingDependencyGraph(universe.goal(), activeNodes, edges);
            } finally {
                if (metrics != null) metrics.activeGraphNanos += System.nanoTime() - started;
            }
            Analysis analysis = selector.analyze(active, cancellation, metrics);
            Selection selection = finish(choices, analysis.sccs(), analysis.graph(), List.of(), false, cancellation, metrics);
            requiresCycle = selection.cyclicComponents().stream()
                .filter(cycle -> cycle.patterns().stream().anyMatch(pattern -> pattern.outputs().stream()
                    .anyMatch(output -> !output.what().equals(pattern.producedKey()))))
                .flatMap(cycle -> cycle.patterns().stream())
                .anyMatch(pattern -> !deferred.contains(pattern.details()));
            componentByKey.clear();
            cyclicKeys.clear();
            for (SccComponent scc : analysis.rawSccs()) {
                cancellation.checkpoint();
                for (AEKey key : scc.members()) componentByKey.put(key, scc.componentId());
                if (scc.cyclic()) cyclicKeys.addAll(scc.members());
            }
            // Tarjan currently numbers SCCs in reverse topological order. Verify that property before reuse.
            ordered = true;
            for (CraftingGraphEdge edge : active.edges()) {
                cancellation.checkpoint();
                if (componentByKey.getInt(edge.producer()) != componentByKey.getInt(edge.requiredInput())
                        && !followsOrder(edge)) ordered = false;
            }
            analyzed = true;
            return requiresCycle;
        }

        private boolean followsOrder(CraftingGraphEdge edge) {
            int from = componentByKey.getInt(edge.producer());
            int to = componentByKey.getInt(edge.requiredInput());
            return to >= 0 && from > to;
        }
    }

    private record Analysis(CraftingDependencyGraph graph, List<SccComponent> sccs, List<SccComponent> rawSccs) { }
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

    }
}
