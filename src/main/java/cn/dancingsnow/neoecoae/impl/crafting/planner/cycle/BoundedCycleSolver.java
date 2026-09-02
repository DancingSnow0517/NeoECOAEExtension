package cn.dancingsnow.neoecoae.impl.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionCountKnowledge;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedCycleSolver.class);
    /** Keep the legacy per-firing witness only while it remains cheap to materialize. */
    private static final long MAX_EXPANDED_WITNESS = 100_000L;
    /** The greedy walk is only a fast probe; the bounded search remains responsible for difficult interleavings. */
    private static final int MAX_GREEDY_MACRO_STEPS = 4_096;
    private static final int GREEDY_TOP_K = 6;
    private static final int MAX_GREEDY_CANDIDATE_EVALUATIONS = 8_192;
    private static final int MAX_GREEDY_LOOKAHEAD_NODES = 16_384;
    /** Exact ring counts use exponential pivot growth, so this is independent of the requested craft amount. */
    private static final int MAX_EXACT_RING_PIVOT_STEPS = 128;
    /** Protects compact witness construction for pathologically weak net-growth ratios. */
    private static final int MAX_EXACT_RING_MACRO_STEPS = 8_192;
    private final int greedyTopK;
    private final int maxGreedyCandidateEvaluations;
    private final int maxGreedyLookaheadNodes;
    private final int maxGreedyMacroSteps;

    public BoundedCycleSolver() {
        this(GREEDY_TOP_K, MAX_GREEDY_CANDIDATE_EVALUATIONS, MAX_GREEDY_LOOKAHEAD_NODES,
            MAX_GREEDY_MACRO_STEPS);
    }

    /** Testable heuristic limits; these never change the exact bounded-search budgets or verdicts. */
    public BoundedCycleSolver(int topK, int candidateEvaluations, int lookaheadNodes, int macroSteps) {
        if (topK < 1 || candidateEvaluations < 1 || lookaheadNodes < 1 || macroSteps < 1) {
            throw new IllegalArgumentException("Heuristic limits must be positive");
        }
        this.greedyTopK = topK;
        this.maxGreedyCandidateEvaluations = candidateEvaluations;
        this.maxGreedyLookaheadNodes = lookaheadNodes;
        this.maxGreedyMacroSteps = macroSteps;
    }

    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        cancellation.checkpoint();
        try {
            return run(request, cancellation);
        } catch (RuntimeException failure) {
            LOGGER.error("BoundedCycleSolver failed for componentId={} memberKeys={} patternCount={}",
                request.component().componentId(), request.component().members(),
                request.component().patterns().size(), failure);
            throw failure;
        }
    }

    private CycleSolveResult run(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        CycleSolveLimits limits = request.options().limits();
        Object prepared = prepare(request, limits);
        if (prepared instanceof CycleSolveResult rejected) {
            return rejected;
        }
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

        CycleSolveResult exactRing = solveDeterministicRing(model, cancellation);
        if (exactRing != null) return exactRing;

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
                    expandedWitnessLength(first.witness), 0, false, false, false,
                    first.greedyCandidates, first.lookaheadNodes, first.heuristicMacroSteps,
                    first.heuristicBudgetExhausted));
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
        boolean[] member = new boolean[n];
        for (AEKey key : request.component().members()) member[index.get(key)] = true;
        return new Model(keys, transitions, cons, prod, suppliable, member, producesRequired, stock, required);
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
    // Exact deterministic-ring fast path
    // ---------------------------------------------------------------------------------------------------

    /**
     * Solves a strict one-producer/one-consumer material ring without enumerating markings.
     *
     * <p>For ring key {@code i}, the balance constraint is
     * {@code stock[i] + produced[i-1] * x[i-1] - consumed[i] * x[i] >= required[i]}.
     * Choosing one transition count determines lower bounds for every predecessor around the ring. Because the
     * product of output ratios is strictly growing, exponentially increasing that pivot reaches a feasible exact
     * integer vector in at most logarithmic work. The vector is then turned into a compact, replayable batch order;
     * {@link #witnessResult} remains the final authority for non-negativity, seed and boundary imports.
     */
    private CycleSolveResult solveDeterministicRing(Model model, ECOCancellation cancellation)
            throws InterruptedException {
        ExactRing ring = exactRing(model);
        if (ring == null) return null;

        PlannerAmount[] counts = exactRingCounts(model, ring, cancellation);
        if (counts == null) return null;
        List<BatchFiring> witness = exactRingWitness(model, ring, counts, cancellation);
        if (witness == null) return null;

        Simulation bare = simulate(model, zeroes(model.keyCount()), witness);
        PlannerAmount[] verifiedStart = Arrays.copyOf(model.stock, model.keyCount());
        bare.lazySeed.forEach((key, amount) -> {
            int slot = model.keys.indexOf(key);
            if (slot >= 0) verifiedStart[slot] = verifiedStart[slot].max(amount);
        });

        List<CycleSolveDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.DETERMINISTIC_RING_EXACT,
            "Solved a deterministic " + ring.size() + "-transition net-growth ring by exact integer balance"
                + " and verified it in " + witness.size() + " compact batch step(s)"));
        CycleSolveMetrics metrics = new CycleSolveMetrics(model.keyCount(), model.transitionCount(),
            witness.size() + 1L, witness.size(), expandedWitnessLength(witness), 0, false, false);
        return witnessResult(model, verifiedStart, witness, diagnostics, metrics);
    }

    @Nullable
    private static ExactRing exactRing(Model model) {
        int transitionCount = model.transitionCount();
        // Two-transition loops already have a tuned compact greedy path. This proof targets the multi-stage rings
        // that previously fell through to the 100k-state search.
        if (transitionCount < 3) return null;

        List<Integer> memberKeys = new ArrayList<>();
        for (int key = 0; key < model.keyCount(); key++) if (model.member[key]) memberKeys.add(key);
        if (memberKeys.size() != transitionCount) return null;

        int[] consumedMember = new int[transitionCount];
        int[] producedMember = new int[transitionCount];
        int[] consumer = new int[model.keyCount()];
        int[] producer = new int[model.keyCount()];
        Arrays.fill(consumedMember, -1);
        Arrays.fill(producedMember, -1);
        Arrays.fill(consumer, -1);
        Arrays.fill(producer, -1);

        for (int transition = 0; transition < transitionCount; transition++) {
            for (int key : memberKeys) {
                if (model.cons[transition][key] > 0L) {
                    if (consumedMember[transition] >= 0 || consumer[key] >= 0) return null;
                    consumedMember[transition] = key;
                    consumer[key] = transition;
                }
                if (model.prod[transition][key] > 0L) {
                    if (producedMember[transition] >= 0 || producer[key] >= 0) return null;
                    producedMember[transition] = key;
                    producer[key] = transition;
                }
            }
            if (consumedMember[transition] < 0 || producedMember[transition] < 0
                    || consumedMember[transition] == producedMember[transition]) return null;
        }
        for (int key : memberKeys) if (consumer[key] < 0 || producer[key] < 0) return null;

        // A side key that is both consumed and produced would add another coupled balance equation. Keep this path
        // strict and let the general bounded solver retain responsibility for that structure.
        for (int key = 0; key < model.keyCount(); key++) {
            if (model.member[key]) continue;
            boolean consumed = false;
            boolean produced = false;
            for (int transition = 0; transition < transitionCount; transition++) {
                consumed |= model.cons[transition][key] > 0L;
                produced |= model.prod[transition][key] > 0L;
            }
            if (consumed && produced) return null;
        }

        int[] transitions = new int[transitionCount];
        int[] keys = new int[transitionCount];
        boolean[] visited = new boolean[transitionCount];
        int current = 0;
        for (int position = 0; position < transitionCount; position++) {
            if (current < 0 || visited[current]) return null;
            visited[current] = true;
            transitions[position] = current;
            keys[position] = consumedMember[current];
            current = consumer[producedMember[current]];
        }
        if (current != transitions[0]) return null;
        for (boolean seen : visited) if (!seen) return null;

        long[] consumed = new long[transitionCount];
        long[] produced = new long[transitionCount];
        PlannerAmount totalConsumedRatio = PlannerAmount.ONE;
        PlannerAmount totalProducedRatio = PlannerAmount.ONE;
        for (int position = 0; position < transitionCount; position++) {
            int transition = transitions[position];
            int nextKey = keys[(position + 1) % transitionCount];
            if (producedMember[transition] != nextKey) return null;
            consumed[position] = model.cons[transition][keys[position]];
            produced[position] = model.prod[transition][nextKey];
            totalConsumedRatio = totalConsumedRatio.multiply(consumed[position]);
            totalProducedRatio = totalProducedRatio.multiply(produced[position]);
        }
        if (totalProducedRatio.compareTo(totalConsumedRatio) <= 0) return null;

        PlannerAmount[] base = zeroes(transitionCount);
        for (int key = 0; key < model.keyCount(); key++) {
            if (model.member[key] || model.required[key].compareTo(model.stock[key]) <= 0) continue;
            int producingTransition = -1;
            long amount = 0L;
            for (int transition = 0; transition < transitionCount; transition++) {
                long net = model.prod[transition][key] - model.cons[transition][key];
                if (net < 0L || net > 0L && producingTransition >= 0) return null;
                if (net > 0L) {
                    producingTransition = transition;
                    amount = net;
                }
            }
            if (producingTransition < 0) return null;
            int position = positionOf(transitions, producingTransition);
            PlannerAmount needed = model.required[key].subtract(model.stock[key]).ceilDiv(PlannerAmount.of(amount));
            base[position] = base[position].max(needed);
        }
        return new ExactRing(transitions, keys, consumed, produced, base);
    }

    @Nullable
    private static PlannerAmount[] exactRingCounts(Model model, ExactRing ring, ECOCancellation cancellation)
            throws InterruptedException {
        PlannerAmount pivot = ring.baseCounts[0];
        for (int attempt = 0; attempt < MAX_EXACT_RING_PIVOT_STEPS; attempt++) {
            cancellation.checkpoint();
            PlannerAmount[] counts = ringCountsForPivot(model, ring, pivot);
            PlannerAmount closure = requiredRingProducerCount(model, ring, 1, counts[1]).max(ring.baseCounts[0]);
            if (pivot.compareTo(closure) >= 0) {
                counts[0] = pivot;
                for (PlannerAmount count : counts) if (!count.fitsLong()) return null;
                return counts;
            }
            PlannerAmount doubled = pivot.signum() == 0 ? PlannerAmount.ONE : pivot.multiply(2L);
            pivot = doubled.max(closure);
            if (!pivot.fitsLong()) return null;
        }
        return null;
    }

    private static PlannerAmount[] ringCountsForPivot(Model model, ExactRing ring, PlannerAmount pivot) {
        int size = ring.size();
        PlannerAmount[] counts = zeroes(size);
        counts[0] = pivot.max(ring.baseCounts[0]);
        for (int offset = 0; offset < size - 1; offset++) {
            int keyPosition = offset == 0 ? 0 : size - offset;
            int producerPosition = (keyPosition - 1 + size) % size;
            counts[producerPosition] = requiredRingProducerCount(model, ring, keyPosition,
                counts[keyPosition]).max(ring.baseCounts[producerPosition]);
        }
        return counts;
    }

    private static PlannerAmount requiredRingProducerCount(Model model, ExactRing ring, int keyPosition,
            PlannerAmount consumerCount) {
        int size = ring.size();
        int producerPosition = (keyPosition - 1 + size) % size;
        int key = ring.keys[keyPosition];
        PlannerAmount numerator = model.required[key].subtract(model.stock[key])
            .add(PlannerAmount.of(ring.consumed[keyPosition]).multiply(consumerCount));
        return numerator.signum() <= 0 ? PlannerAmount.ZERO
            : numerator.ceilDiv(PlannerAmount.of(ring.produced[producerPosition]));
    }

    @Nullable
    private static List<BatchFiring> exactRingWitness(Model model, ExactRing ring, PlannerAmount[] exactCounts,
            ECOCancellation cancellation) throws InterruptedException {
        int size = ring.size();
        long[] remaining = new long[size];
        for (int position = 0; position < size; position++) remaining[position] = exactCounts[position].longValueExact();

        PlannerAmount[] marking = Arrays.copyOf(model.stock, model.keyCount());
        List<BatchFiring> witness = new ArrayList<>();
        int cursor = bestRingStart(model, ring, remaining);
        for (int macro = 0; macro < MAX_EXACT_RING_MACRO_STEPS; macro++) {
            cancellation.checkpoint();
            if (allZero(remaining)) return List.copyOf(witness);

            boolean progressed = false;
            for (int offset = 0; offset < size; offset++) {
                int position = (cursor + offset) % size;
                if (remaining[position] <= 0L) continue;
                int transition = ring.transitions[position];
                PlannerAmount safe = maximumSafeBatch(model, marking, transition)
                    .min(PlannerAmount.of(remaining[position]));
                if (safe.signum() <= 0 || !safe.fitsLong()) continue;
                long batch = safe.longValueExact();
                marking = fireBatch(model, marking, transition, batch);
                remaining[position] -= batch;
                appendBatch(witness, transition, batch);
                progressed = true;
            }
            if (progressed) continue;

            // A ring without initial stock needs a finite start-up seed. Add only enough to enable one remaining
            // transition in this construction; witness replay independently derives and reports the exact seed.
            int seedPosition = bestSeedPosition(model, ring, marking, remaining);
            if (seedPosition < 0) return null;
            int transition = ring.transitions[seedPosition];
            boolean added = false;
            for (int key = 0; key < model.keyCount(); key++) {
                long consumed = model.cons[transition][key];
                if (consumed <= 0L || model.suppliable[key]) continue;
                PlannerAmount deficit = PlannerAmount.of(consumed).subtract(marking[key]).max(PlannerAmount.ZERO);
                if (deficit.signum() > 0) {
                    marking[key] = marking[key].add(deficit);
                    added = true;
                }
            }
            if (!added) return null;
            cursor = seedPosition;
        }
        return null;
    }

    private static int bestRingStart(Model model, ExactRing ring, long[] remaining) {
        int best = 0;
        PlannerAmount bestCapacity = PlannerAmount.ZERO;
        for (int position = 0; position < ring.size(); position++) {
            if (remaining[position] <= 0L) continue;
            PlannerAmount capacity = maximumSafeBatch(model, model.stock, ring.transitions[position]);
            if (capacity.compareTo(bestCapacity) > 0) {
                best = position;
                bestCapacity = capacity;
            }
        }
        return best;
    }

    private static int bestSeedPosition(Model model, ExactRing ring, PlannerAmount[] marking, long[] remaining) {
        int best = -1;
        PlannerAmount bestDeficit = null;
        for (int position = 0; position < ring.size(); position++) {
            if (remaining[position] <= 0L) continue;
            int transition = ring.transitions[position];
            PlannerAmount deficit = PlannerAmount.ZERO;
            for (int key = 0; key < model.keyCount(); key++) {
                long consumed = model.cons[transition][key];
                if (consumed > 0L && !model.suppliable[key]) {
                    deficit = deficit.add(PlannerAmount.of(consumed).subtract(marking[key]).max(PlannerAmount.ZERO));
                }
            }
            if (best < 0 || deficit.compareTo(bestDeficit) < 0) {
                best = position;
                bestDeficit = deficit;
            }
        }
        return best;
    }

    private static void appendBatch(List<BatchFiring> witness, int transition, long count) {
        if (!witness.isEmpty() && witness.getLast().transition() == transition) {
            BatchFiring previous = witness.removeLast();
            witness.add(new BatchFiring(transition, Math.addExact(previous.count(), count)));
        } else {
            witness.add(new BatchFiring(transition, count));
        }
    }

    private static boolean allZero(long[] values) {
        for (long value : values) if (value != 0L) return false;
        return true;
    }

    private static int positionOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) if (values[index] == target) return index;
        return -1;
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

        Search greedy = greedySearch(model, root, stateBudget, maxFirings, cancellation, outcome);
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
    private Search greedySearch(Model model, PlannerAmount[] start, int stateBudget, int maxFirings,
            ECOCancellation cancellation, Search accounting) throws InterruptedException {
        PlannerAmount[] marking = Arrays.copyOf(start, start.length);
        Set<Marking> seen = new HashSet<>();
        List<BatchFiring> witness = new ArrayList<>();
        seen.add(new Marking(marking));
        CycleHeuristicBudget budget = new CycleHeuristicBudget(maxGreedyCandidateEvaluations,
            maxGreedyLookaheadNodes, Math.min(maxFirings, maxGreedyMacroSteps));

        int greedyLimit = Math.min(maxFirings, maxGreedyMacroSteps);
        for (int depth = 0; depth < greedyLimit; depth++) {
            cancellation.checkpoint();
            if (!budget.macroStep()) return abandonGreedy(accounting, budget);
            if (satisfied(marking, model.required)) {
                Search result = new Search();
                result.kind = Search.Kind.REACHED;
                result.witness = List.copyOf(witness);
                result.statesVisited = seen.size();
                result.statesExpanded = witness.size();
                copyHeuristicMetrics(result, budget);
                return result;
            }

            List<GreedyCandidate> candidates = new ArrayList<>();
            for (int transition = 0; transition < model.transitionCount(); transition++) {
                for (long batch : greedyCandidateBatchCounts(model, marking, transition)) {
                    if (!budget.candidate()) return abandonGreedy(accounting, budget);
                    PlannerAmount[] next = fireBatch(model, marking, transition, batch);
                    if (seen.contains(new Marking(next))) continue;
                    BatchFiring firing = new BatchFiring(transition, batch);
                    if (satisfied(next, model.required)) {
                        List<BatchFiring> reached = new ArrayList<>(witness);
                        reached.add(firing);
                        Search result = reachedSearch(reached, seen);
                        copyHeuristicMetrics(result, budget);
                        return result;
                    }
                    PlannerAmount score = deficitScore(model, next);
                    candidates.add(new GreedyCandidate(firing, next, score,
                        boundaryImportScore(model, transition, batch)));
                }
            }
            if (isStrictSimpleRing(model) && !candidates.isEmpty()) {
                GreedyCandidate selected = simpleRingCandidate(model, marking, candidates);
                if (seen.size() >= stateBudget) return abandonGreedy(accounting, budget);
                seen.add(new Marking(selected.marking()));
                witness.add(selected.firing());
                marking = selected.marking();
                continue;
            }
            candidates.sort(java.util.Comparator.comparing(GreedyCandidate::score)
                .thenComparing(GreedyCandidate::boundaryImportScore)
                .thenComparingInt(candidate -> candidate.firing().transition())
                .thenComparingLong(candidate -> candidate.firing().count()));

            Lookahead bestLookahead = null;
            PlannerAmount bestScore = null;
            PlannerAmount bestBoundaryImport = null;
            PlannerAmount[] bestMarking = null;
            BatchFiring bestFiring = null;
            int top = Math.min(greedyTopK, candidates.size());
            for (int index = 0; index < top; index++) {
                GreedyCandidate candidate = candidates.get(index);
                Lookahead lookahead = lookaheadScore(model, candidate.marking(), 2, budget, cancellation);
                if (lookahead == null) return abandonGreedy(accounting, budget);
                PlannerAmount score = candidate.score();
                long batch = candidate.firing().count();
                    int futureScore = bestLookahead == null ? -1
                        : lookahead.score().compareTo(bestLookahead.score());
                    if (bestFiring == null || futureScore < 0
                            || futureScore == 0 && score.compareTo(bestScore) < 0
                            || futureScore == 0 && score.equals(bestScore)
                                && candidate.boundaryImportScore().compareTo(bestBoundaryImport) < 0
                            || futureScore == 0 && score.equals(bestScore)
                                && candidate.boundaryImportScore().equals(bestBoundaryImport)
                                && lookahead.steps() < bestLookahead.steps()
                            || futureScore == 0 && score.equals(bestScore)
                                && candidate.boundaryImportScore().equals(bestBoundaryImport)
                                && lookahead.steps() == bestLookahead.steps() && batch < bestFiring.count()) {
                        bestLookahead = lookahead;
                        bestScore = score;
                        bestBoundaryImport = candidate.boundaryImportScore();
                        bestMarking = candidate.marking();
                        bestFiring = candidate.firing();
                    }
            }
            if (bestFiring == null) return abandonGreedy(accounting, budget);
            if (seen.size() >= stateBudget) return abandonGreedy(accounting, budget);
            seen.add(new Marking(bestMarking));
            witness.add(bestFiring);
            marking = bestMarking;
        }
        if (!satisfied(marking, model.required)) {
            budget.markExhausted();
            return abandonGreedy(accounting, budget);
        }
        Search result = reachedSearch(witness, seen);
        copyHeuristicMetrics(result, budget);
        return result;
    }

    private static Search abandonGreedy(Search accounting, CycleHeuristicBudget budget) {
        copyHeuristicMetrics(accounting, budget);
        return null;
    }

    private static void copyHeuristicMetrics(Search search, CycleHeuristicBudget budget) {
        search.greedyCandidates = budget.candidateEvaluations();
        search.lookaheadNodes = budget.lookaheadNodes();
        search.heuristicMacroSteps = budget.macroSteps();
        search.heuristicBudgetExhausted = budget.exhausted();
    }

    private static Lookahead lookaheadScore(Model model, PlannerAmount[] marking, int steps,
            CycleHeuristicBudget budget, ECOCancellation cancellation) throws InterruptedException {
        if (!budget.lookahead()) return null;
        cancellation.checkpoint();
        Lookahead best = new Lookahead(deficitScore(model, marking), 0);
        if (best.score().isZero() || steps <= 0) return best;
        for (int transition = 0; transition < model.transitionCount(); transition++) {
            for (long batch : greedyCandidateBatchCounts(model, marking, transition)) {
                Lookahead child = lookaheadScore(model,
                    fireBatch(model, marking, transition, batch), steps - 1, budget, cancellation);
                if (child == null) return null;
                Lookahead candidate = new Lookahead(child.score(), child.steps() + 1);
                if (compareLookahead(candidate, best) < 0) best = candidate;
                if (best.score().isZero()) return best;
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

    /** Greedy never treats the synthetic unbounded sentinel as a meaningful maximal batch. */
    private static List<Long> greedyCandidateBatchCounts(Model model, PlannerAmount[] marking, int transition) {
        List<Long> candidates = candidateBatchCounts(model, marking, transition);
        java.util.stream.Stream<Long> usable = candidates.stream();
        if (!hasFiniteInternalBound(model, transition)) {
            usable = usable.filter(count -> count != Long.MAX_VALUE);
        }
        // Exact target/unblocking boundaries must be considered before an over-producing maximal batch.
        return usable.sorted().toList();
    }

    private static boolean hasFiniteInternalBound(Model model, int transition) {
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.cons[transition][i] > 0L && !model.suppliable[i]) return true;
        }
        return false;
    }

    private static PlannerAmount boundaryImportScore(Model model, int transition, long batch) {
        PlannerAmount total = PlannerAmount.ZERO;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.suppliable[i] && model.cons[transition][i] > 0L) {
                total = total.add(PlannerAmount.of(model.cons[transition][i]).multiply(batch));
            }
        }
        return total;
    }

    /** Strict deterministic two-member ring: one internal consumer/producer per member and no route branching. */
    private static boolean isStrictSimpleRing(Model model) {
        if (model.transitionCount() != 2) return false;
        int[] consumed = {-1, -1};
        for (int transition = 0; transition < 2; transition++) {
            for (int key = 0; key < model.keyCount(); key++) {
                if (model.cons[transition][key] <= 0L || model.suppliable[key]) continue;
                if (consumed[transition] >= 0) return false;
                consumed[transition] = key;
            }
            if (consumed[transition] < 0) return false;
        }
        return consumed[0] != consumed[1]
            && model.prod[0][consumed[1]] > 0L && model.prod[1][consumed[0]] > 0L;
    }

    private static GreedyCandidate simpleRingCandidate(Model model, PlannerAmount[] marking,
            List<GreedyCandidate> candidates) {
        GreedyCandidate best = null;
        boolean onlyNeedsOneRequiredFiring = deficitScore(model, marking)
            .compareTo(maxRequiredProductionPerFiring(model)) <= 0;
        for (GreedyCandidate candidate : candidates) {
            boolean requiredProducer = model.producesRequired[candidate.firing().transition()];
            if (best == null) {
                best = candidate;
                continue;
            }
            boolean bestRequired = model.producesRequired[best.firing().transition()];
            if (requiredProducer != bestRequired) {
                if (requiredProducer) best = candidate;
                continue;
            }
            if (requiredProducer) {
                int progress = candidate.score().compareTo(best.score());
                if (progress < 0 || progress == 0 && candidate.firing().count() < best.firing().count()) {
                    best = candidate;
                }
            } else if (onlyNeedsOneRequiredFiring
                    ? candidate.firing().count() < best.firing().count()
                    : candidate.firing().count() > best.firing().count()) {
                best = candidate;
            }
        }
        return best;
    }

    private static PlannerAmount maxRequiredProductionPerFiring(Model model) {
        PlannerAmount best = PlannerAmount.ONE;
        for (int transition = 0; transition < model.transitionCount(); transition++) {
            if (!model.producesRequired[transition]) continue;
            PlannerAmount produced = PlannerAmount.ZERO;
            for (int key = 0; key < model.keyCount(); key++) {
                if (model.required[key].signum() > 0 && model.prod[transition][key] > model.cons[transition][key]) {
                    produced = produced.add(model.prod[transition][key] - model.cons[transition][key]);
                }
            }
            best = best.max(produced);
        }
        return best;
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
            expanded, 0, ladderSteps, search.stateBudgetExhausted, search.firingDepthTruncated, false,
            search.greedyCandidates, search.lookaheadNodes, search.heuristicMacroSteps,
            search.heuristicBudgetExhausted);
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

        Map<IPatternDetails, PlannerAmount> exactPatternTimes = new LinkedHashMap<>();
        for (BatchFiring firing : witness) {
            IPatternDetails details = model.transitions.get(firing.transition()).details();
            exactPatternTimes.merge(details, PlannerAmount.of(firing.count()), PlannerAmount::add);
        }
        Map<IPatternDetails, Long> patternTimes = new LinkedHashMap<>();
        List<PatternRun> executionPlan = new ArrayList<>(witness.size());
        boolean runtimeCountsRepresentable = true;
        try {
            for (BatchFiring firing : witness) {
                CompiledPattern pattern = model.transitions.get(firing.transition());
                long previous = patternTimes.getOrDefault(pattern.details(), 0L);
                patternTimes.put(pattern.details(), Math.addExact(previous, firing.count()));
                appendRun(executionPlan, pattern, firing.count());
            }
        } catch (ArithmeticException overflow) {
            runtimeCountsRepresentable = false;
            patternTimes.clear();
            executionPlan.clear();
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                "Batch firing count exceeds AE2 long range"));
        }

        // Keep the old per-firing witness for ordinary-sized cycles. Large bottom-of-tree cycles use the exact
        // ordered batch plan instead, avoiding an allocation proportional to the number of crafts.
        List<CycleFiring> firings = expandWitness(model, witness);

        Map<AEKey, PlannerAmount> exactProduced = Map.copyOf(bare.produced);
        Map<AEKey, PlannerAmount> exactDeliverable = exactDeliverable(model, actual.marking);
        // Produced/deliverable totals are theoretical cycle bookkeeping. Only seed, external demand and
        // shortfall become AE2-facing material counters at this boundary.
        boolean unrepresentable = !runtimeCountsRepresentable
            || hasUnrepresentable(exactRequiredSeed, exactExternalDemand, exactShortfall);
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
            ExecutionCountKnowledge.EXACT, Map.copyOf(exactPatternTimes), Map.copyOf(patternTimes),
            externalDemand, requiredSeed, Map.copyOf(shortfall),
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
                + " witness=" + metrics.witnessLength() + " ladder=" + metrics.seedLadderSteps()
                + " greedyCandidates=" + metrics.greedyCandidates()
                + " lookaheadNodes=" + metrics.lookaheadNodes()
                + " heuristicMacroSteps=" + metrics.heuristicMacroSteps()
                + " heuristicBudgetExhausted=" + metrics.heuristicBudgetExhausted());
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
    private record GreedyCandidate(BatchFiring firing, PlannerAmount[] marking, PlannerAmount score,
            PlannerAmount boundaryImportScore) {}

    private record Model(
        List<AEKey> keys,
        List<CompiledPattern> transitions,
        long[][] cons,
        long[][] prod,
        boolean[] suppliable,
        boolean[] member,
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

    private record ExactRing(int[] transitions, int[] keys, long[] consumed, long[] produced,
            PlannerAmount[] baseCounts) {
        int size() { return transitions.length; }
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
        private long greedyCandidates;
        private long lookaheadNodes;
        private long heuristicMacroSteps;
        private boolean heuristicBudgetExhausted;
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
