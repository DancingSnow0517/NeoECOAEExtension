package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Stage-one cyclic SCC solver: bounded state search over an explicit, inventory-aware marking.
 *
 * <h2>Model</h2>
 * The SCC is read as a small Petri net. A <em>place</em> is one relevant key (SCC member, pattern input or
 * pattern output). A <em>transition</em> is one distinct {@link IPatternDetails} inside the SCC; several
 * {@link CompiledPattern} views of the same physical pattern are deduplicated so a single firing can never
 * be double-counted. A transition fires only when every input it consumes from inside the SCC is actually on
 * hand, so no marking can ever go negative and nothing is produced from nothing.
 *
 * <h2>Boundary</h2>
 * Keys named by {@code externalResourceBoundary} are supplied by the rest of the condensation DAG, so a
 * deficit on them is imported and booked as external demand instead of blocking the firing. Members and
 * unlisted keys have no outside producer on the active route: their deficit is a start-up seed, which only
 * stock can cover.
 *
 * <h2>Honesty rules</h2>
 * {@link CycleSolveStatus#INSUFFICIENT_EXTERNAL_INPUT} is returned only when the reachable marking set was
 * closed exhaustively — no state cap, no firing-depth cut. Any early stop yields
 * {@link CycleSolveStatus#UNKNOWN_BUDGET}. Nothing here ever reports plain missing items, and no result is
 * cached: every answer belongs to the one stock snapshot it was computed from.
 */
public final class BoundedCycleSolver implements CycleSolver {
    /** Keep the legacy per-firing witness only while it remains cheap to materialize. */
    private static final long MAX_EXPANDED_WITNESS = 100_000L;
    /** The greedy walk is only a fast probe; the bounded search remains responsible for difficult interleavings. */
    private static final int MAX_GREEDY_MACRO_STEPS = 4_096;

    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        cancellation.checkpoint();
        return run(request, cancellation);
    }

    private CycleSolveResult run(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        CycleSolveLimits limits = request.options().limits();
        Object prepared = prepare(request, limits);
        if (prepared instanceof CycleSolveResult rejected) return rejected;
        Model model = (Model) prepared;

        List<CycleSolveDiagnostic> diagnostics = new ArrayList<>();
        if (satisfied(model.stock, model.required)) {
            CycleSolveMetrics stockMetrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(), 1, 0,
                0, 0, false, false);
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SATISFIED_FROM_STOCK,
                "Relevant stock already covers every required output; the structural cycle is cut by inventory"));
            diagnostics.add(metrics(stockMetrics));
            return new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                deliverable(model, model.stock), List.of(), List.copyOf(diagnostics), stockMetrics);
        }

        int budget = limits.maxStates();
        Search first = search(model, model.stock, budget, limits.maxFirings(), cancellation);
        long visited = first.statesVisited;
        long expanded = first.statesExpanded;

        if (first.kind == Search.Kind.REACHED) {
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.WITNESS_FOUND,
                "Found a verified batch firing order of " + first.witness.size()
                    + " macro-step(s) within the stock snapshot"));
            return witnessResult(model, model.stock, first.witness, diagnostics,
                new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited, expanded,
                    expandedWitnessLength(first.witness), 0, false, false));
        }
        if (first.kind != Search.Kind.EXHAUSTED) {
            return budgetResult(model, first, visited, expanded, 0);
        }

        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
            "Explored the complete reachable marking set (" + visited
                + " states) without reaching the required outputs"));

        PlannerAmount[] base = first.unblockDeficit;
        if (base == null || isZero(base)) {
            CycleSolveMetrics deadMetrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(),
                visited, expanded, 0, 0, false, false);
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.NO_PRODUCTIVE_FIRING,
                "Every SCC pattern stays fireable yet no reachable marking increases the required outputs"));
            diagnostics.add(metrics(deadMetrics));
            return CycleSolveResult.failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, List.copyOf(diagnostics),
                deadMetrics);
        }

        int remaining = (int) Math.max(0, budget - visited);
        for (int step = 0; step < limits.maxSeedLadderSteps(); step++) {
            cancellation.checkpoint();
            if (remaining <= 1) break;
            PlannerAmount[] extra = scale(base, step);
            PlannerAmount[] start = add(model.stock, extra);
            Search attempt = search(model, start, remaining, limits.maxFirings(), cancellation);
            visited += attempt.statesVisited;
            expanded += attempt.statesExpanded;
            remaining = (int) Math.max(0, remaining - attempt.statesVisited);
            if (attempt.kind == Search.Kind.REACHED) {
                diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_LADDER_VERIFIED,
                    "Ladder step " + step + " (extra seed " + describe(model, extra)
                        + ") produces a verified firing order"));
                return witnessResult(model, start, attempt.witness, diagnostics,
                    new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited, expanded,
                        expandedWitnessLength(attempt.witness), step + 1, false, false));
            }
            if (attempt.kind != Search.Kind.EXHAUSTED) break;
        }

        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_ESTIMATE_LOWER_BOUND,
            "No verified seed within the ladder budget; the reported seed is the smallest amount that unblocks"
                + " the deadlock, not a proven sufficient amount"));
        Map<AEKey, PlannerAmount> exactShortfall = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> exactSeed = new LinkedHashMap<>();
        for (int i = 0; i < model.keyCount(); i++) {
            if (base[i].signum() > 0) {
                PlannerAmount exact = model.stock[i].max(PlannerAmount.ZERO).add(base[i]);
                exactSeed.put(model.keys.get(i), exact);
                exactShortfall.put(model.keys.get(i), base[i]);
            }
        }
        boolean unrepresentable = hasUnrepresentable(exactShortfall, exactSeed);
        if (unrepresentable) addUnrepresentableDiagnostics(diagnostics, model, "cycle seed/shortfall",
            exactShortfall, exactSeed);
        Map<AEKey, Long> shortfall = representable(exactShortfall);
        Map<AEKey, Long> seed = representable(exactSeed);
        CycleSolveMetrics ladderMetrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited,
            expanded, 0, limits.maxSeedLadderSteps(), false, false);
        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_SHORTFALL,
            "Short of " + describe(model, base) + " to start the loop"));
        diagnostics.add(metrics(ladderMetrics));
        return new CycleSolveResult(unrepresentable ? CycleSolveStatus.UNREPRESENTABLE
                : CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, Map.of(), Map.of(),
            Map.copyOf(seed), shortfall, Map.of(), deliverable(model, model.stock), List.of(),
            List.copyOf(diagnostics), ladderMetrics);
    }

    // ---------------------------------------------------------------------------------------------------
    // Structural preparation
    // ---------------------------------------------------------------------------------------------------

    /** Returns a {@link Model}, or a {@link CycleSolveResult} when the component is out of scope. */
    private static Object prepare(CycleSolveRequest request, CycleSolveLimits limits) {
        List<CompiledPattern> declared = request.component().patterns();
        if (declared.isEmpty()) {
            return CycleSolveResult.failure(CycleSolveStatus.UNSUPPORTED_PATTERN,
                CycleSolveDiagnostic.Code.NO_TRANSITIONS, "Cyclic component carries no pattern");
        }
        if (declared.size() > limits.maxPatterns()) {
            return CycleSolveResult.failure(CycleSolveStatus.TOO_COMPLEX,
                CycleSolveDiagnostic.Code.PATTERN_LIMIT_EXCEEDED,
                "Cyclic component declares " + declared.size() + " patterns, limit is " + limits.maxPatterns());
        }

        Map<IPatternDetails, CompiledPattern> unique = new LinkedHashMap<>();
        for (CompiledPattern pattern : declared.stream()
                .sorted(Comparator.comparingInt(CompiledPattern::id)).toList()) {
            unique.putIfAbsent(pattern.details(), pattern);
        }
        List<CompiledPattern> transitions = List.copyOf(unique.values());
        if (transitions.size() > limits.maxPatterns()) {
            return CycleSolveResult.failure(CycleSolveStatus.TOO_COMPLEX,
                CycleSolveDiagnostic.Code.PATTERN_LIMIT_EXCEEDED,
                "Cyclic component has " + transitions.size() + " transitions, limit is " + limits.maxPatterns());
        }
        for (CompiledPattern pattern : transitions) {
            String reason = unsupportedReason(pattern);
            if (reason != null) {
                if (reason.startsWith("AMOUNT_UNREPRESENTABLE:")) {
                    return CycleSolveResult.failure(CycleSolveStatus.UNREPRESENTABLE,
                        CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                        "Pattern " + pattern.id() + " " + reason);
                }
                return CycleSolveResult.failure(CycleSolveStatus.UNSUPPORTED_PATTERN,
                    CycleSolveDiagnostic.Code.UNSUPPORTED_PATTERN,
                    "Pattern " + pattern.id() + " is not batch-safe inside a cycle: " + reason);
            }
        }

        LinkedHashMap<AEKey, Integer> index = new LinkedHashMap<>();
        for (AEKey member : request.component().members()) index.putIfAbsent(member, index.size());
        for (CompiledPattern pattern : transitions) {
            for (CompiledInput input : pattern.inputs()) index.putIfAbsent(input.key(), index.size());
            for (GenericStack output : pattern.grossOutputs()) index.putIfAbsent(output.what(), index.size());
        }
        for (AEKey required : request.plannerRequiredOutputs().keySet()) index.putIfAbsent(required, index.size());
        if (index.size() > limits.maxKeys()) {
            return CycleSolveResult.failure(CycleSolveStatus.TOO_COMPLEX,
                CycleSolveDiagnostic.Code.KEY_LIMIT_EXCEEDED,
                "Cyclic component touches " + index.size() + " keys, limit is " + limits.maxKeys());
        }

        int n = index.size();
        int t = transitions.size();
        List<AEKey> keys = List.copyOf(index.keySet());
        Set<AEKey> members = new LinkedHashSet<>(request.component().members());
        boolean[] suppliable = new boolean[n];
        for (ComponentDependency dependency : request.externalResourceBoundary()) {
            for (var relationship : dependency.relationships()) {
                AEKey key = relationship.requiredInput();
                Integer slot = index.get(key);
                if (slot != null && !members.contains(key)) suppliable[slot] = true;
            }
        }

        long[][] cons = new long[t][n];
        long[][] prod = new long[t][n];
        for (int p = 0; p < t; p++) {
            CompiledPattern pattern = transitions.get(p);
            PlannerAmount[] exactCons = new PlannerAmount[n];
            PlannerAmount[] exactProd = new PlannerAmount[n];
            Arrays.fill(exactCons, PlannerAmount.ZERO);
            Arrays.fill(exactProd, PlannerAmount.ZERO);
            for (CompiledInput input : pattern.inputs()) {
                int slot = index.get(input.key());
                exactCons[slot] = exactCons[slot].add(input.amountPerPattern());
            }
            for (GenericStack output : pattern.grossOutputs()) {
                int slot = index.get(output.what());
                exactProd[slot] = exactProd[slot].add(output.amount());
            }
            for (int i = 0; i < n; i++) {
                if (!exactCons[i].fitsLong()) {
                    return CycleSolveResult.failure(CycleSolveStatus.UNREPRESENTABLE,
                        CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                        "Pattern " + pattern.id() + " input total for " + keys.get(i)
                            + " amount=" + exactCons[i] + " max=" + Long.MAX_VALUE);
                }
                if (!exactProd[i].fitsLong()) {
                    return CycleSolveResult.failure(CycleSolveStatus.UNREPRESENTABLE,
                        CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                        "Pattern " + pattern.id() + " output total for " + keys.get(i)
                            + " amount=" + exactProd[i] + " max=" + Long.MAX_VALUE);
                }
                cons[p][i] = exactCons[i].longValueExact();
                prod[p][i] = exactProd[i].longValueExact();
            }
        }

        PlannerAmount[] stock = new PlannerAmount[n];
        PlannerAmount[] required = new PlannerAmount[n];
        for (int i = 0; i < n; i++) {
            stock[i] = PlannerAmount.of(request.stockOf(keys.get(i)));
            required[i] = request.requiredOutputAmount(keys.get(i));
        }

        boolean[] producesRequired = new boolean[t];
        for (int p = 0; p < t; p++) {
            for (int i = 0; i < n; i++) {
                if (required[i].signum() > 0 && prod[p][i] > cons[p][i]) {
                    producesRequired[p] = true;
                    break;
                }
            }
        }
        return new Model(keys, transitions, cons, prod, suppliable, producesRequired, stock, required);
    }

    private static @Nullable String unsupportedReason(CompiledPattern pattern) {
        if (!pattern.fastSupported()) {
            return pattern.unsupportedReason() == null || pattern.unsupportedReason().isEmpty()
                ? "NOT_FAST_SUPPORTED" : pattern.unsupportedReason();
        }
        if (pattern.outputPerPattern().signum() <= 0) return "PRIMARY_OUTPUT_MISMATCH";
        for (CompiledInput input : pattern.inputs()) {
            if (!input.fastSupported()) {
                return input.unsupportedReason() == null || input.unsupportedReason().isEmpty()
                    ? "UNSUPPORTED_INPUT" : input.unsupportedReason();
            }
            if (input.amountPerPattern().signum() <= 0) return "INVALID_INPUT_AMOUNT";
            if (!input.amountPerPattern().fitsLong()) {
                return "AMOUNT_UNREPRESENTABLE: input=" + input.key() + " amount="
                    + input.amountPerPattern() + " max=" + Long.MAX_VALUE;
            }
        }
        if (pattern.outputs().isEmpty()) return "NO_OUTPUTS";
        for (GenericStack output : pattern.outputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) return "INVALID_OUTPUT";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------
    // Bounded batch marking search
    // ---------------------------------------------------------------------------------------------------

    /**
     * Best-first over markings, with each edge representing a safe batch of one transition.
     *
     * <p>The old search treated every execution as a separate edge. That is needlessly expensive for the common
     * bottom-of-the-tree growth loop where one pattern consumes a batch of an intermediate and another pattern
     * converts the whole batch back into a growing raw-material stock. A successor now carries a positive batch
     * count. The count is bounded by the current marking for internal inputs; boundary inputs remain lazily
     * importable exactly as they were in the single-firing model.
     *
     * <p>One-firing, target-boundary, dependency-unblocking and maximal-safe batches are all retained as candidate
     * edges. A returned path is still replayed exactly before it is accepted, so batching can improve search cost
     * without weakening the non-negative-material invariant. The depth limit is therefore a limit on search macro
     * steps, while the exact execution counts remain in the result's pattern map.
     */
    private Search search(Model model, PlannerAmount[] start, int stateBudget, int maxFirings, ECOCancellation cancellation)
            throws InterruptedException {
        Search outcome = new Search();
        int n = model.keyCount();
        int transitionCount = model.transitionCount();
        List<Node> nodes = new ArrayList<>();
        Set<Marking> seen = new HashSet<>();
        java.util.PriorityQueue<Integer> queue = new java.util.PriorityQueue<>((left, right) -> {
            int progress = compareProgress(model, nodes.get(left), nodes.get(right));
            return progress != 0 ? progress : Integer.compare(left, right);
        });

        PlannerAmount[] root = Arrays.copyOf(start, n);
        nodes.add(new Node(root, -1, null, 0));
        seen.add(new Marking(root));
        if (satisfied(root, model.required)) {
            outcome.kind = Search.Kind.REACHED;
            outcome.witness = List.of();
            outcome.statesVisited = 1;
            return outcome;
        }

        Search greedy = greedySearch(model, root, stateBudget, maxFirings, cancellation);
        if (greedy != null) return greedy;

        queue.add(0);

        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            int index = queue.poll();
            Node node = nodes.get(index);
            if (node.depth >= maxFirings) {
                outcome.firingDepthTruncated = true;
                continue;
            }
            outcome.statesExpanded++;
            for (int t = 0; t < transitionCount; t++) {
                List<Long> batches = candidateBatchCounts(model, node.marking, t);
                if (batches.isEmpty()) {
                    outcome.considerUnblock(model, node.marking, t);
                    continue;
                }
                for (long batch : batches) {
                    PlannerAmount[] next = fireBatch(model, node.marking, t, batch);
                    Marking key = new Marking(next);
                    if (seen.contains(key)) continue;
                    if (seen.size() >= stateBudget) {
                        outcome.stateBudgetExhausted = true;
                        break;
                    }
                    seen.add(key);
                    int child = nodes.size();
                    nodes.add(new Node(next, index, new BatchFiring(t, batch), node.depth + 1));
                    if (satisfied(next, model.required)) {
                        outcome.kind = Search.Kind.REACHED;
                        outcome.witness = witnessOf(nodes, child);
                        outcome.statesVisited = seen.size();
                        return outcome;
                    }
                    queue.add(child);
                }
                if (outcome.stateBudgetExhausted) break;
            }
            if (outcome.stateBudgetExhausted) break;
        }

        outcome.statesVisited = seen.size();
        if (outcome.stateBudgetExhausted) outcome.kind = Search.Kind.STATE_BUDGET;
        else if (outcome.firingDepthTruncated) outcome.kind = Search.Kind.DEPTH_TRUNCATED;
        else outcome.kind = Search.Kind.EXHAUSTED;
        return outcome;
    }

    /**
     * Cheap maximal-batch walk used before the general search. Bottom-of-tree material loops usually have only one
     * enabled transition at each wave; taking its largest safe batch then reaches the next wave in logarithmic time.
     * The walk is deliberately heuristic: if it gets stuck, the exact bounded search below still receives the
     * original root and all smaller candidates.
     */
    private static Search greedySearch(Model model, PlannerAmount[] start, int stateBudget, int maxFirings,
            ECOCancellation cancellation) throws InterruptedException {
        PlannerAmount[] marking = Arrays.copyOf(start, start.length);
        Set<Marking> seen = new HashSet<>();
        List<BatchFiring> witness = new ArrayList<>();
        seen.add(new Marking(marking));

        int greedyLimit = Math.min(maxFirings, MAX_GREEDY_MACRO_STEPS);
        for (int depth = 0; depth < greedyLimit; depth++) {
            cancellation.checkpoint();
            if (satisfied(marking, model.required)) {
                Search result = new Search();
                result.kind = Search.Kind.REACHED;
                result.witness = List.copyOf(witness);
                result.statesVisited = seen.size();
                result.statesExpanded = witness.size();
                return result;
            }

            Lookahead bestLookahead = null;
            PlannerAmount bestScore = null;
            PlannerAmount[] bestMarking = null;
            BatchFiring bestFiring = null;
            for (int transition = 0; transition < model.transitionCount(); transition++) {
                for (long batch : candidateBatchCounts(model, marking, transition)) {
                    PlannerAmount[] next = fireBatch(model, marking, transition, batch);
                    if (seen.contains(new Marking(next))) continue;
                    PlannerAmount score = deficitScore(model, next);
                    Lookahead lookahead = lookaheadScore(model, next, 2);
                    if (bestFiring == null || compareLookahead(lookahead, bestLookahead) < 0
                            || compareLookahead(lookahead, bestLookahead) == 0 && score.compareTo(bestScore) < 0
                            || compareLookahead(lookahead, bestLookahead) == 0 && score.equals(bestScore)
                                && batch < bestFiring.count()) {
                        bestLookahead = lookahead;
                        bestScore = score;
                        bestMarking = next;
                        bestFiring = new BatchFiring(transition, batch);
                    }
                }
            }
            if (bestFiring == null) return null;
            if (seen.size() >= stateBudget) return null;
            seen.add(new Marking(bestMarking));
            witness.add(bestFiring);
            marking = bestMarking;
        }
        return satisfied(marking, model.required) ? reachedSearch(witness, seen) : null;
    }

    private static Lookahead lookaheadScore(Model model, PlannerAmount[] marking, int steps) {
        Lookahead best = new Lookahead(deficitScore(model, marking), 0);
        if (steps <= 0) return best;
        for (int transition = 0; transition < model.transitionCount(); transition++) {
            for (long batch : candidateBatchCounts(model, marking, transition)) {
                Lookahead child = lookaheadScore(model,
                    fireBatch(model, marking, transition, batch), steps - 1);
                Lookahead candidate = new Lookahead(child.score(), child.steps() + 1);
                if (compareLookahead(candidate, best) < 0) best = candidate;
            }
        }
        return best;
    }

    private static int compareLookahead(Lookahead left, Lookahead right) {
        if (right == null) return -1;
        int score = left.score().compareTo(right.score());
        return score != 0 ? score : Integer.compare(left.steps(), right.steps());
    }

    private static Search reachedSearch(List<BatchFiring> witness, Set<Marking> seen) {
        Search result = new Search();
        result.kind = Search.Kind.REACHED;
        result.witness = List.copyOf(witness);
        result.statesVisited = seen.size();
        result.statesExpanded = witness.size();
        return result;
    }

    /**
     * Returns deterministic batch sizes for one transition at one marking.
     *
     * <p>The maximal safe batch is the important fast path. The smaller candidates preserve useful alternate
     * interleavings when another transition needs an intermediate before the maximal batch would consume it all.
     */
    private static List<Long> candidateBatchCounts(Model model, PlannerAmount[] marking, int transition) {
        PlannerAmount maximum = maximumSafeBatch(model, marking, transition);
        if (maximum.signum() <= 0) return List.of();

        Set<Long> candidates = new LinkedHashSet<>();
        addBatchCandidate(candidates, PlannerAmount.ONE, maximum);

        long[] consumes = model.cons[transition];
        long[] produces = model.prod[transition];
        for (int i = 0; i < model.keyCount(); i++) {
            PlannerAmount delta = PlannerAmount.of(produces[i]).subtract(consumes[i]);
            if (delta.signum() <= 0 || model.required[i].compareTo(marking[i]) <= 0) continue;
            addBatchCandidate(candidates,
                model.required[i].subtract(marking[i]).ceilDiv(delta), maximum);
        }

        // Add counts that make a currently disabled internal input of another transition available. This retains
        // interleavings such as "produce enough catalyst, then switch transition" without enumerating every count.
        for (int other = 0; other < model.transitionCount(); other++) {
            for (int i = 0; i < model.keyCount(); i++) {
                long needed = model.cons[other][i];
                if (needed <= 0L || model.suppliable[i] || marking[i].compareTo(PlannerAmount.of(needed)) >= 0) {
                    continue;
                }
                PlannerAmount delta = PlannerAmount.of(produces[i]).subtract(consumes[i]);
                if (delta.signum() <= 0) continue;
                addBatchCandidate(candidates,
                    PlannerAmount.of(needed).subtract(marking[i]).ceilDiv(delta), maximum);
            }
        }

        addBatchCandidate(candidates, maximum, maximum);
        return candidates.stream().sorted(java.util.Comparator.reverseOrder()).toList();
    }

    private static PlannerAmount maximumSafeBatch(Model model, PlannerAmount[] marking, int transition) {
        PlannerAmount maximum = null;
        for (int i = 0; i < model.keyCount(); i++) {
            long consumed = model.cons[transition][i];
            if (consumed <= 0L || model.suppliable[i]) continue;
            PlannerAmount available = marking[i].divide(PlannerAmount.of(consumed));
            maximum = maximum == null ? available : maximum.min(available);
        }
        // A transition with no internal input is already structurally unconstrained. Permit a target-directed
        // batch, but keep the candidate generator finite by never inventing an unbounded maximal successor.
        return maximum == null ? PlannerAmount.of(Long.MAX_VALUE) : maximum;
    }

    private static void addBatchCandidate(Set<Long> candidates, PlannerAmount candidate, PlannerAmount maximum) {
        if (candidate == null || candidate.signum() <= 0 || !candidate.fitsLong()
                || candidate.compareTo(maximum) > 0) return;
        candidates.add(candidate.longValueExact());
    }

    private static int compareProgress(Model model, Node left, Node right) {
        PlannerAmount leftDeficit = deficitScore(model, left.marking);
        PlannerAmount rightDeficit = deficitScore(model, right.marking);
        int deficit = leftDeficit.compareTo(rightDeficit);
        if (deficit != 0) return deficit;
        return Integer.compare(left.depth, right.depth);
    }

    private static PlannerAmount deficitScore(Model model, PlannerAmount[] marking) {
        PlannerAmount result = PlannerAmount.ZERO;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.required[i].compareTo(marking[i]) > 0) {
                result = result.add(model.required[i].subtract(marking[i]));
            }
        }
        return result;
    }

    private static List<BatchFiring> witnessOf(List<Node> nodes, int leaf) {
        ArrayDeque<BatchFiring> reversed = new ArrayDeque<>();
        int cursor = leaf;
        while (cursor > 0) {
            Node node = nodes.get(cursor);
            reversed.addFirst(node.firing);
            cursor = node.parent;
        }
        return List.copyOf(reversed);
    }

    private static PlannerAmount[] fireBatch(Model model, PlannerAmount[] marking, int transition, long batch) {
        PlannerAmount[] next = marking.clone();
        PlannerAmount count = PlannerAmount.of(batch);
        long[] cons = model.cons[transition];
        long[] prod = model.prod[transition];
        for (int i = 0; i < next.length; i++) {
            if (cons[i] <= 0L && prod[i] <= 0L) continue;
            if (model.suppliable[i]) {
                next[i] = advanceWithBoundarySupply(marking[i], cons[i], prod[i], count);
            } else {
                PlannerAmount consumed = PlannerAmount.of(cons[i]).multiply(count);
                PlannerAmount produced = PlannerAmount.of(prod[i]).multiply(count);
                next[i] = marking[i].subtract(consumed).add(produced);
            }
        }
        return next;
    }

    /** Final marking after a repeated transition whose boundary inputs may be imported on demand. */
    private static PlannerAmount advanceWithBoundarySupply(PlannerAmount marking, long consumed, long produced,
            PlannerAmount count) {
        if (count.signum() <= 0) return marking;
        PlannerAmount c = PlannerAmount.of(consumed);
        PlannerAmount p = PlannerAmount.of(produced);
        if (consumed <= 0L) return marking.add(p.multiply(count));
        if (produced >= consumed) {
            PlannerAmount first = marking.subtract(c).max(PlannerAmount.ZERO).add(p);
            return first.add(p.subtract(c).multiply(count.subtract(PlannerAmount.ONE)));
        }
        return marking.subtract(c.multiply(count)).add(p.multiply(count)).max(p);
    }

    private static boolean satisfied(PlannerAmount[] marking, PlannerAmount[] required) {
        for (int i = 0; i < required.length; i++) {
            if (required[i].signum() > 0 && marking[i].compareTo(required[i]) < 0) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------
    // Result assembly
    // ---------------------------------------------------------------------------------------------------

    private CycleSolveResult budgetResult(Model model, Search search, long visited, long expanded,
            int ladderSteps) {
        List<CycleSolveDiagnostic> diagnostics = new ArrayList<>();
        if (search.stateBudgetExhausted) {
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED,
                "Reached the marking budget after " + visited + " states; nothing is proven"));
        }
        if (search.firingDepthTruncated) {
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.FIRING_DEPTH_TRUNCATED,
                "Reached the firing-depth budget; nothing is proven"));
        }
        CycleSolveMetrics metrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited,
            expanded, 0, ladderSteps, search.stateBudgetExhausted, search.firingDepthTruncated, false);
        diagnostics.add(metrics(metrics));
        return CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET, List.copyOf(diagnostics), metrics);
    }

    private CycleSolveResult witnessResult(Model model, PlannerAmount[] start, List<BatchFiring> witness,
            List<CycleSolveDiagnostic> diagnostics, CycleSolveMetrics metrics) {
        Simulation actual = simulate(model, start, witness);
        if (!actual.lazySeed.isEmpty()) {
            return CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET,
                CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED,
                "Internal inconsistency: witness replay needed unbooked seed " + describeRaw(model, actual.lazySeed));
        }
        if (!satisfied(actual.marking, model.required)) {
            return CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET,
                CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED,
                "Internal inconsistency: witness replay did not reach the required outputs");
        }

        Simulation bare = simulate(model, zeroes(model.keyCount()), witness);
        Map<AEKey, PlannerAmount> exactRequiredSeed = Map.copyOf(bare.lazySeed);
        Map<AEKey, PlannerAmount> exactExternalDemand = Map.copyOf(bare.lazyImport);

        Map<AEKey, PlannerAmount> exactShortfall = new LinkedHashMap<>();
        exactRequiredSeed.forEach((key, amount) -> {
            PlannerAmount missing = amount.subtract(model.stockAmountOf(key).max(PlannerAmount.ZERO));
            if (missing.signum() > 0) exactShortfall.put(key, missing);
        });

        Map<IPatternDetails, Long> patternTimes = new LinkedHashMap<>();
        List<PatternRun> executionPlan = new ArrayList<>(witness.size());
        try {
            for (BatchFiring firing : witness) {
                CompiledPattern pattern = model.transitions.get(firing.transition());
                long previous = patternTimes.getOrDefault(pattern.details(), 0L);
                patternTimes.put(pattern.details(), Math.addExact(previous, firing.count()));
                appendRun(executionPlan, pattern, firing.count());
            }
        } catch (ArithmeticException overflow) {
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                "Batch firing count exceeds AE2 long range"));
            CycleSolveMetrics overflowMetrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(),
                metrics.statesVisited(), metrics.statesExpanded(), 0, metrics.seedLadderSteps(),
                metrics.stateBudgetExhausted(), metrics.firingDepthTruncated(), true);
            return CycleSolveResult.failure(CycleSolveStatus.UNREPRESENTABLE, List.copyOf(diagnostics),
                overflowMetrics);
        }

        // Keep the old per-firing witness for ordinary-sized cycles. Large bottom-of-tree cycles use the exact
        // ordered batch plan instead, avoiding an allocation proportional to the number of crafts.
        List<CycleFiring> firings = expandWitness(model, witness);

        Map<AEKey, PlannerAmount> exactProduced = Map.copyOf(bare.produced);
        Map<AEKey, PlannerAmount> exactDeliverable = exactDeliverable(model, actual.marking);
        // Produced/deliverable totals are theoretical cycle bookkeeping. Only seed, external demand and
        // shortfall become AE2-facing material counters at this boundary.
        boolean unrepresentable = hasUnrepresentable(exactRequiredSeed, exactExternalDemand, exactShortfall);
        if (unrepresentable) addUnrepresentableDiagnostics(diagnostics, model, "cycle witness boundary",
            exactRequiredSeed, exactExternalDemand, exactShortfall);
        Map<AEKey, Long> requiredSeed = representable(exactRequiredSeed);
        Map<AEKey, Long> externalDemand = representable(exactExternalDemand);
        Map<AEKey, Long> shortfall = representable(exactShortfall);

        List<CycleSolveDiagnostic> explanation = new ArrayList<>(diagnostics);
        explanation.add(new CycleSolveDiagnostic(
            exactShortfall.isEmpty() ? CycleSolveDiagnostic.Code.SEED_COVERED_BY_STOCK
                : CycleSolveDiagnostic.Code.SEED_SHORTFALL,
            exactShortfall.isEmpty()
                ? "Start-up seed " + describeRaw(model, exactRequiredSeed) + " is covered by relevant stock"
                : "Start-up seed " + describeRaw(model, exactRequiredSeed) + " exceeds stock by "
                    + describeRaw(model, exactShortfall)));
        explanation.add(metrics(metrics));

        return new CycleSolveResult(
            unrepresentable ? CycleSolveStatus.UNREPRESENTABLE
                : exactShortfall.isEmpty() ? CycleSolveStatus.SUCCESS : CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT,
            Map.copyOf(patternTimes), externalDemand, requiredSeed, Map.copyOf(shortfall),
            representable(exactProduced), representable(exactDeliverable), List.copyOf(firings),
            List.copyOf(executionPlan), List.copyOf(explanation), metrics);
    }

    /**
     * Replays a witness in verified batches. Deficits on boundary keys are booked as imports, deficits on keys the
     * SCC has to own are booked as seed; both are recorded rather than allowed to go negative, so the pair
     * (seed, import) is exactly what the order needs to run. The deficit calculation is closed-form for a repeated
     * transition, so replay remains proportional to the number of macro-steps rather than the firing count.
     */
    private static Simulation simulate(Model model, PlannerAmount[] start, List<BatchFiring> witness) {
        int n = model.keyCount();
        PlannerAmount[] marking = Arrays.copyOf(start, n);
        Map<AEKey, PlannerAmount> lazySeed = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> lazyImport = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> produced = new LinkedHashMap<>();
        for (BatchFiring firing : witness) {
            int transition = firing.transition();
            PlannerAmount count = PlannerAmount.of(firing.count());
            long[] cons = model.cons[transition];
            long[] prod = model.prod[transition];
            for (int i = 0; i < n; i++) {
                if (cons[i] <= 0) continue;
                PlannerAmount deficit = batchDeficit(marking[i], cons[i], prod[i], count);
                if (deficit.signum() > 0) {
                    AEKey key = model.keys.get(i);
                    (model.suppliable[i] ? lazyImport : lazySeed).merge(key, deficit, PlannerAmount::add);
                    marking[i] = marking[i].add(deficit);
                }
                marking[i] = marking[i].subtract(PlannerAmount.of(cons[i]).multiply(count));
            }
            for (int i = 0; i < n; i++) {
                if (prod[i] <= 0) continue;
                PlannerAmount total = PlannerAmount.of(prod[i]).multiply(count);
                marking[i] = marking[i].add(total);
                produced.merge(model.keys.get(i), total, PlannerAmount::add);
            }
        }
        return new Simulation(marking, lazySeed, lazyImport, produced);
    }

    /** Minimum extra stock needed before a repeated transition can run without a deficit on this key. */
    private static PlannerAmount batchDeficit(PlannerAmount marking, long consumed, long produced,
            PlannerAmount count) {
        if (consumed <= 0L || count.signum() <= 0) return PlannerAmount.ZERO;
        PlannerAmount c = PlannerAmount.of(consumed);
        PlannerAmount p = PlannerAmount.of(produced);
        PlannerAmount requiredBeforeLast = produced >= consumed
            ? c
            : c.multiply(count).subtract(p.multiply(count.subtract(PlannerAmount.ONE)));
        return requiredBeforeLast.subtract(marking).max(PlannerAmount.ZERO);
    }

    private static void appendRun(List<PatternRun> runs, CompiledPattern pattern, long count) {
        if (count <= 0L) return;
        if (!runs.isEmpty() && runs.getLast().pattern().details() == pattern.details()) {
            PatternRun previous = runs.removeLast();
            runs.add(new PatternRun(pattern, Math.addExact(previous.count(), count)));
        } else {
            runs.add(new PatternRun(pattern, count));
        }
    }

    private static List<CycleFiring> expandWitness(Model model, List<BatchFiring> witness) {
        long total = 0L;
        for (BatchFiring firing : witness) {
            if (Long.MAX_VALUE - total < firing.count() || total + firing.count() > MAX_EXPANDED_WITNESS) {
                return List.of();
            }
            total += firing.count();
        }
        List<CycleFiring> result = new ArrayList<>((int) total);
        int step = 0;
        for (BatchFiring firing : witness) {
            CompiledPattern pattern = model.transitions.get(firing.transition());
            for (long count = 0; count < firing.count(); count++) {
                result.add(new CycleFiring(step++, pattern));
            }
        }
        return List.copyOf(result);
    }

    private static int expandedWitnessLength(List<BatchFiring> witness) {
        long total = 0L;
        for (BatchFiring firing : witness) {
            if (Long.MAX_VALUE - total < firing.count() || total + firing.count() > MAX_EXPANDED_WITNESS) {
                return 0;
            }
            total += firing.count();
        }
        return (int) total;
    }

    private static Map<AEKey, Long> deliverable(Model model, PlannerAmount[] marking) {
        return representable(exactDeliverable(model, marking));
    }

    private static Map<AEKey, PlannerAmount> exactDeliverable(Model model, PlannerAmount[] marking) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.required[i].signum() > 0) result.put(model.keys.get(i), marking[i]);
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> representable(Map<AEKey, PlannerAmount> amounts) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            if (amount.fitsLong()) result.put(key, amount.longValueExact());
        });
        return Map.copyOf(result);
    }

    @SafeVarargs
    private static boolean hasUnrepresentable(Map<AEKey, PlannerAmount>... maps) {
        for (Map<AEKey, PlannerAmount> map : maps) {
            for (PlannerAmount amount : map.values()) if (!amount.fitsLong()) return true;
        }
        return false;
    }

    @SafeVarargs
    private static void addUnrepresentableDiagnostics(List<CycleSolveDiagnostic> diagnostics, Model model,
            String stage, Map<AEKey, PlannerAmount>... maps) {
        for (Map<AEKey, PlannerAmount> map : maps) {
            for (var entry : map.entrySet()) {
                if (!entry.getValue().fitsLong()) {
                    diagnostics.add(new CycleSolveDiagnostic(
                        CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                        "Execution amount exceeds AE2 long range: key=" + entry.getKey()
                            + " producer=cycle pattern=" + model.transitions.stream()
                                .map(pattern -> Integer.toString(pattern.id())).collect(java.util.stream.Collectors.joining(","))
                            + " amount=" + entry.getValue() + " max=" + Long.MAX_VALUE + " stage=" + stage));
                }
            }
        }
    }

    private static CycleSolveDiagnostic metrics(CycleSolveMetrics metrics) {
        return new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEARCH_METRICS,
            "keys=" + metrics.relevantKeys() + " transitions=" + metrics.transitions()
                + " states=" + metrics.statesVisited() + " expanded=" + metrics.statesExpanded()
                + " witness=" + metrics.witnessLength() + " ladder=" + metrics.seedLadderSteps());
    }

    private static String describe(Model model, PlannerAmount[] amounts) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i < model.keyCount(); i++) {
            if (amounts[i].signum() <= 0) continue;
            if (!first) builder.append(", ");
            builder.append(amounts[i]).append(" x ").append(model.keys.get(i));
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String describeRaw(Model model, Map<AEKey, PlannerAmount> amounts) {
        if (amounts.isEmpty()) return "{}";
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i < model.keyCount(); i++) {
            AEKey key = model.keys.get(i);
            PlannerAmount amount = amounts.get(key);
            if (amount == null || amount.signum() <= 0) continue;
            if (!first) builder.append(", ");
            builder.append(amount).append(" x ").append(key);
            first = false;
        }
        return builder.append('}').toString();
    }

    private static PlannerAmount[] scale(PlannerAmount[] base, int step) {
        PlannerAmount[] result = new PlannerAmount[base.length];
        for (int i = 0; i < base.length; i++) {
            PlannerAmount value = base[i];
            for (int doubling = 0; doubling < step && value.signum() > 0; doubling++) {
                value = value.multiply(2L);
            }
            result[i] = value;
        }
        return result;
    }

    private static PlannerAmount[] add(PlannerAmount[] left, PlannerAmount[] right) {
        PlannerAmount[] result = new PlannerAmount[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = left[i].add(right[i]);
        }
        return result;
    }

    private static boolean isZero(PlannerAmount[] values) {
        for (PlannerAmount value : values) if (value.signum() > 0) return false;
        return true;
    }

    private static PlannerAmount[] zeroes(int length) {
        PlannerAmount[] result = new PlannerAmount[length];
        Arrays.fill(result, PlannerAmount.ZERO);
        return result;
    }

    // ---------------------------------------------------------------------------------------------------
    // Internal data
    // ---------------------------------------------------------------------------------------------------

    private record BatchFiring(int transition, long count) {
        private BatchFiring {
            if (transition < 0) throw new IllegalArgumentException("Batch transition must not be negative");
            if (count <= 0L) throw new IllegalArgumentException("Batch firing count must be positive");
        }
    }

    private record Lookahead(PlannerAmount score, int steps) {}

    private record Model(
        List<AEKey> keys,
        List<CompiledPattern> transitions,
        long[][] cons,
        long[][] prod,
        boolean[] suppliable,
        boolean[] producesRequired,
        PlannerAmount[] stock,
        PlannerAmount[] required
    ) {
        int keyCount() { return keys.size(); }
        int transitionCount() { return transitions.size(); }
        PlannerAmount stockAmountOf(AEKey key) {
            int slot = keys.indexOf(key);
            return slot < 0 ? PlannerAmount.ZERO : stock[slot];
        }
    }

    private record Simulation(
        PlannerAmount[] marking,
        Map<AEKey, PlannerAmount> lazySeed,
        Map<AEKey, PlannerAmount> lazyImport,
        Map<AEKey, PlannerAmount> produced
    ) {}

    private static final class Node {
        private final PlannerAmount[] marking;
        private final int parent;
        private final BatchFiring firing;
        private final int depth;

        private Node(PlannerAmount[] marking, int parent, BatchFiring firing, int depth) {
            this.marking = marking;
            this.parent = parent;
            this.firing = firing;
            this.depth = depth;
        }
    }

    /** Value wrapper so an exact marking can be deduplicated in a hash set. */
    private static final class Marking {
        private final PlannerAmount[] cells;
        private final int hash;

        private Marking(PlannerAmount[] cells) {
            this.cells = cells;
            this.hash = Arrays.hashCode(cells);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Marking marking && Arrays.equals(cells, marking.cells);
        }

        @Override public int hashCode() { return hash; }
    }

    private static final class Search {
        private enum Kind { REACHED, EXHAUSTED, STATE_BUDGET, DEPTH_TRUNCATED }

        private Kind kind = Kind.EXHAUSTED;
        private List<BatchFiring> witness = List.of();
        private long statesVisited;
        private long statesExpanded;
        private boolean stateBudgetExhausted;
        private boolean firingDepthTruncated;
        private PlannerAmount[] unblockDeficit;
        private int unblockRank = Integer.MAX_VALUE;
        private PlannerAmount unblockTotal = null;
        private int unblockTransition = Integer.MAX_VALUE;

        /**
         * Records the cheapest way to unblock a disabled transition, preferring one that actually produces a
         * required output. That candidate becomes the base vector of the deterministic seed ladder.
         */
        private void considerUnblock(Model model, PlannerAmount[] marking, int transition) {
            int rank = model.producesRequired[transition] ? 0 : 1;
            if (rank > unblockRank) return;
            long[] cons = model.cons[transition];
            PlannerAmount total = PlannerAmount.ZERO;
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                if (cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0) {
                    total = total.add(required.subtract(marking[i]));
                }
            }
            if (total.signum() <= 0) return;
            if (rank == unblockRank) {
                if (total.compareTo(unblockTotal) > 0) return;
                if (total.equals(unblockTotal) && transition >= unblockTransition) return;
            }
            PlannerAmount[] deficit = new PlannerAmount[cons.length];
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                deficit[i] = cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0
                    ? required.subtract(marking[i]) : PlannerAmount.ZERO;
            }
            unblockRank = rank;
            unblockTotal = total;
            unblockTransition = transition;
            unblockDeficit = deficit;
        }
    }
}
