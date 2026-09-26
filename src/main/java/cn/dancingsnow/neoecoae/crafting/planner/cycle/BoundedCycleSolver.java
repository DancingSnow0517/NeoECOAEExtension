package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cyclic SCC solver: exact integer balance, verified compact circuits, then bounded marking search.
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
 * closed exhaustively, or exact integer material balance is infeasible. Any early stop yields
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
    private static final int MAX_EQUATION_WITNESS_STEPS = 4_096;
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
        } catch (cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget.Exhausted exhausted) {
            throw exhausted;
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

        CycleStateEquation.Result balance = supportsRecipeCircuits(model)
            ? CycleStateEquation.solve(model.cons, model.prod, model.suppliable, model.stock, model.required, cancellation)
            : new CycleStateEquation.Result(CycleStateEquation.Status.UNKNOWN, null);
        if (balance.status() == CycleStateEquation.Status.INFEASIBLE) {
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.STATE_EQUATION_INFEASIBLE,
                "Exact material balance has no nonnegative integer firing vector at current stock"));
            boolean noGrowth = true;
            for (int k = 0; k < model.keyCount(); k++) {
                if (model.required[k].compareTo(model.stock[k]) <= 0) continue;
                for (int t = 0; t < model.transitionCount(); t++)
                    if (model.prod[t][k] > model.cons[t][k]) noGrowth = false;
            }
            if (noGrowth) return witnessResult(model, addMissingTargets(model), List.of(), diagnostics,
                CycleSolveMetrics.NONE).withStartupCandidates(startupCandidates(model));
            return CycleSolveResult.failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, diagnostics,
                CycleSolveMetrics.NONE).withStartupCandidates(startupCandidates(model));
        }
        CycleSolveResult equation = balance.counts() == null ? null : solveEquationWitness(model, balance, cancellation);
        if (equation != null && (equation.status() == CycleSolveStatus.SUCCESS
                || equation.status() == CycleSolveStatus.UNREPRESENTABLE)) return equation;
        if (balance.status() == CycleStateEquation.Status.UNKNOWN || balance.status() == CycleStateEquation.Status.FEASIBLE)
            diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.STATE_EQUATION_BUDGET,
                "Integer balance allowance exhausted; marking search remains responsible for reachability"));

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
            return budgetResult(model, first, visited, expanded, 0).withAdditionalDiagnostics(diagnostics);
        }

        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
            "Explored the complete reachable marking set (" + visited
                + " states) without reaching the required outputs"));

        // A seed deficit in one constructed witness is not a reachability proof. Only return that seed
        // proposal after the original-stock search has actually closed; budget cuts above stay unknown.
        if (equation != null && equation.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
            return equation.withAdditionalDiagnostics(diagnostics).withStartupCandidates(startupCandidates(model));
        }

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
        List<PlannerAmount[]> candidates = new ArrayList<>();
        candidates.add(base);
        for (PlannerAmount[] candidate : first.unblockCandidates) {
            if (!Arrays.equals(base, candidate)) candidates.add(candidate);
        }
        // Explore different concrete AE keys before increasing the amount of one chosen key.
        for (int step = 0; step < limits.maxSeedLadderSteps() && remaining > 1; step++) {
            for (int candidateIndex = 0; candidateIndex < candidates.size() && remaining > 1; candidateIndex++) {
                cancellation.checkpoint();
                PlannerAmount[] extra = scale(candidates.get(candidateIndex), step);
                PlannerAmount[] start = add(model.stock, extra);
                int share = Math.max(2, remaining / (candidates.size() - candidateIndex));
                Search attempt = search(model, start, share, limits.maxFirings(), cancellation);
                visited += attempt.statesVisited;
                expanded += attempt.statesExpanded;
                remaining = (int) Math.max(0, budget - visited);
                if (attempt.kind == Search.Kind.REACHED) {
                    List<CycleSolveDiagnostic> verified = new ArrayList<>(diagnostics);
                    verified.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_LADDER_VERIFIED,
                        "Startup candidate " + candidateIndex + ", ladder " + step + " has a verified firing order"));
                    return witnessResult(model, start, attempt.witness, verified,
                        new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited, expanded,
                            expandedWitnessLength(attempt.witness), step + 1, false, false))
                        .withStartupCandidates(startupCandidates(model));
                }
            }
        }

        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.SEED_ESTIMATE_LOWER_BOUND,
            "No verified seed within the ladder budget; the reported seed is the smallest amount that unblocks"
                + " the deadlock, not a proven sufficient amount"));
        Map<AEKey, PlannerAmount> exactShortfall = new Object2ObjectLinkedOpenHashMap<>();
        Map<AEKey, PlannerAmount> exactSeed = new Object2ObjectLinkedOpenHashMap<>();
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
            List.copyOf(diagnostics), ladderMetrics).withStartupCandidates(startupCandidates(model));
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

        Map<IPatternDetails, CompiledPattern> unique = new Object2ObjectLinkedOpenHashMap<>();
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

        var index = new Object2IntLinkedOpenHashMap<AEKey>();
        index.defaultReturnValue(-1);
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
                int slot = index.getInt(key);
                if (slot >= 0 && !members.contains(key)) suppliable[slot] = true;
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
                int slot = index.getInt(input.key());
                exactCons[slot] = exactCons[slot].add(input.amountPerPattern());
            }
            for (GenericStack output : pattern.grossOutputs()) {
                int slot = index.getInt(output.what());
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
        for (AEKey key : request.component().members()) member[index.getInt(key)] = true;
        TransitionMetadata[] metadata = new TransitionMetadata[t];
        for (int p = 0; p < t; p++) {
            int[] internal = new int[n];
            int[] changed = new int[n];
            int internalCount = 0;
            int changedCount = 0;
            List<OutputTarget> outputs = new ArrayList<>();
            List<UnlockTarget> unlocks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (cons[p][i] > 0 && !suppliable[i]) internal[internalCount++] = i;
                if (cons[p][i] > 0 || prod[p][i] > 0) changed[changedCount++] = i;
                if (prod[p][i] <= cons[p][i]) continue;
                PlannerAmount delta = PlannerAmount.of(prod[p][i] - cons[p][i]);
                if (required[i].signum() > 0) outputs.add(new OutputTarget(i, delta));
                if (suppliable[i]) continue;
                var thresholds = new LongOpenHashSet();
                for (int other = 0; other < t; other++) {
                    long needed = cons[other][i];
                    if (needed > 0 && thresholds.add(needed)) {
                        unlocks.add(new UnlockTarget(i, PlannerAmount.of(needed), delta));
                    }
                }
            }
            metadata[p] = new TransitionMetadata(Arrays.copyOf(internal, internalCount),
                Arrays.copyOf(changed, changedCount), outputs.toArray(OutputTarget[]::new),
                unlocks.toArray(UnlockTarget[]::new));
        }
        // Output-only places cannot enable a transition. Their exact surplus is irrelevant to
        // reachability, but must remain in the actual marking for witness/material accounting.
        boolean[] consumedKeys = new boolean[n];
        for (int p = 0; p < t; p++) {
            for (int i = 0; i < n; i++) consumedKeys[i] |= cons[p][i] > 0L;
        }
        return new Model(keys, transitions, cons, prod, suppliable, member, producesRequired, stock, required,
            metadata, consumedKeys);
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
    // State-equation witness construction
    // ---------------------------------------------------------------------------------------------------

    private static PlannerAmount[] addMissingTargets(Model model) {
        PlannerAmount[] start = model.stock.clone();
        for (int k = 0; k < start.length; k++) start[k] = start[k].max(model.required[k]);
        return start;
    }

    private CycleSolveResult solveEquationWitness(Model model, CycleStateEquation.Result balance,
            ECOCancellation cancellation) throws InterruptedException {
        List<BatchFiring> witness = equationWitness(model, balance.counts(), cancellation);
        if (witness == null) return null;
        Simulation bare = simulate(model, zeroes(model.keyCount()), witness);
        PlannerAmount[] start = model.stock.clone();
        bare.lazySeed.forEach((key, amount) -> {
            int index = model.keys.indexOf(key);
            start[index] = start[index].max(amount);
        });
        var diagnostics = new ArrayList<CycleSolveDiagnostic>();
        diagnostics.add(new CycleSolveDiagnostic(CycleSolveDiagnostic.Code.STATE_EQUATION_WITNESS,
            "Constructed and replayed " + witness.size() + " compact steps from "
                + (balance.status() == CycleStateEquation.Status.OPTIMAL ? "a minimum" : "a feasible")
                + " integer firing vector; material balance alone does not certify startup"));
        return witnessResult(model, start, witness, diagnostics,
            new CycleSolveMetrics(model.keyCount(), model.transitionCount(), witness.size() + 1L,
                witness.size(), expandedWitnessLength(witness), 0, false, false));
    }

    /** One shared prefix-deficit/repetition construction for growth, rings and split/merge circuits. */
    private static List<BatchFiring> equationWitness(Model model, PlannerAmount[] counts,
            ECOCancellation cancellation) throws InterruptedException {
        PlannerAmount[] remaining = counts.clone(), marking = model.stock.clone();
        List<BatchFiring> witness = new ArrayList<>();
        for (int macro = 0; macro < MAX_EQUATION_WITNESS_STEPS; macro++) {
            cancellation.checkpoint();
            if (isZero(remaining)) return List.copyOf(witness);
            int lapStart = witness.size();
            for (int t = 0; t < model.transitionCount(); t++) {
                PlannerAmount safe = maximumSafeBatch(model, marking, t).min(remaining[t]);
                if (safe.signum() <= 0) continue;
                marking = fireBatch(model, marking, t, safe);
                remaining[t] = remaining[t].subtract(safe);
                witness.add(new BatchFiring(t, safe));
            }
            if (lapStart < witness.size()) {
                CircuitSummary lap = summarizePlain(model, witness, lapStart, witness.size());
                PlannerAmount extra = null;
                for (int t = 0; t < remaining.length; t++) {
                    if (lap.counts[t].signum() <= 0) continue;
                    PlannerAmount available = remaining[t].divide(lap.counts[t]);
                    extra = extra == null ? available : extra.min(available);
                }
                extra = safeRepetitions(model, marking, lap, extra == null ? PlannerAmount.ZERO : extra);
                if (extra.signum() > 0) {
                    repeatTail(witness, lapStart, extra);
                    marking = applyCircuit(model, marking, lap.repeated(extra));
                    for (int t = 0; t < remaining.length; t++)
                        remaining[t] = remaining[t].subtract(lap.counts[t].multiply(extra));
                }
                continue;
            }
            // A proposal only. Original-stock search must prove deadlock before adopting missing seed.
            int best = -1;
            PlannerAmount score = null;
            for (int t = 0; t < remaining.length; t++) {
                if (remaining[t].signum() <= 0) continue;
                PlannerAmount missing = PlannerAmount.ZERO;
                for (int k : model.metadata[t].consumedInternalKeys) {
                    PlannerAmount input = PlannerAmount.of(model.cons[t][k]);
                    missing = missing.add(normalizedDeficit(input.subtract(marking[k]).max(PlannerAmount.ZERO), input));
                }
                if (best < 0 || missing.compareTo(score) < 0) { best = t; score = missing; }
            }
            if (best < 0) return null;
            for (int k : model.metadata[best].consumedInternalKeys)
                marking[k] = marking[k].max(PlannerAmount.of(model.cons[best][k]));
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
        Set<Marking> seen = new ObjectOpenHashSet<>();
        var queue = new IntHeapPriorityQueue((left, right) -> {
            int progress = compareProgress(nodes.get(left), nodes.get(right));
            return progress != 0 ? progress : Integer.compare(left, right);
        });

        PlannerAmount[] root = Arrays.copyOf(start, n);
        nodes.add(new Node(root, -1, null, 0, deficitScore(model, root)));
        seen.add(new Marking(model, root));
        if (satisfied(root, model.required)) {
            outcome.kind = Search.Kind.REACHED;
            outcome.witness = List.of();
            outcome.statesVisited = 1;
            return outcome;
        }

        Search greedy = greedySearch(model, root, stateBudget, maxFirings, cancellation, outcome);
        if (greedy != null) return greedy;

        queue.enqueue(0);

        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            int index = queue.dequeueInt();
            Node node = nodes.get(index);
            if (node.depth >= maxFirings) {
                outcome.firingDepthTruncated = true;
                continue;
            }
            outcome.statesExpanded++;
            for (int t = 0; t < transitionCount; t++) {
                long[] batches = candidateBatchCounts(model, node.marking, t, false);
                if (batches.length == 0) {
                    outcome.considerUnblock(model, node.marking, t);
                    continue;
                }
                for (long batch : batches) {
                    PlannerAmount[] next = fireBatch(model, node.marking, t, batch);
                    Marking key = new Marking(model, next);
                    if (seen.contains(key)) continue;
                    if (seen.size() >= stateBudget) {
                        outcome.stateBudgetExhausted = true;
                        break;
                    }
                    seen.add(key);
                    int child = nodes.size();
                    nodes.add(new Node(next, index, new BatchFiring(t, batch), node.depth + 1,
                        deficitScore(model, next)));
                    if (satisfied(next, model.required)) {
                        outcome.kind = Search.Kind.REACHED;
                        outcome.witness = witnessOf(nodes, child);
                        outcome.statesVisited = seen.size();
                        return outcome;
                    }
                    queue.enqueue(child);
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
        Set<Marking> seen = new ObjectOpenHashSet<>();
        List<BatchFiring> witness = new ArrayList<>();
        seen.add(new Marking(model, marking));
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
                    if (seen.contains(new Marking(model, next))) continue;
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
            seen.add(new Marking(model, bestMarking));
            witness.add(bestFiring);
            marking = accelerateRecipeCircuit(model, bestMarking, witness, cancellation);
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
    private static long[] candidateBatchCounts(Model model, PlannerAmount[] marking, int transition,
            boolean greedy) {
        PlannerAmount maximum = maximumSafeBatch(model, marking, transition);
        if (maximum.signum() <= 0) return EMPTY_BATCHES;

        BatchCandidates candidates = new BatchCandidates();
        addBatchCandidate(candidates, PlannerAmount.ONE, maximum);

        TransitionMetadata metadata = model.metadata[transition];
        for (OutputTarget output : metadata.requiredOutputs) {
            int i = output.key;
            if (model.required[i].compareTo(marking[i]) <= 0) continue;
            addBatchCandidate(candidates,
                model.required[i].subtract(marking[i]).ceilDiv(output.delta), maximum);
        }

        // Add counts that make a currently disabled internal input of another transition available. This retains
        // interleavings such as "produce enough catalyst, then switch transition" without enumerating every count.
        for (UnlockTarget target : metadata.unlockTargets) {
            if (marking[target.key].compareTo(target.threshold) >= 0) continue;
            addBatchCandidate(candidates,
                target.threshold.subtract(marking[target.key]).ceilDiv(target.delta), maximum);
        }

        addBatchCandidate(candidates, maximum, maximum);
        return candidates.sorted(greedy, greedy && !hasFiniteInternalBound(model, transition));
    }

    /** Greedy never treats the synthetic unbounded sentinel as a meaningful maximal batch. */
    private static long[] greedyCandidateBatchCounts(Model model, PlannerAmount[] marking, int transition) {
        // Exact target/unblocking boundaries must be considered before an over-producing maximal batch.
        return candidateBatchCounts(model, marking, transition, true);
    }

    private static boolean hasFiniteInternalBound(Model model, int transition) {
        return model.metadata[transition].consumedInternalKeys.length > 0;
    }

    private static PlannerAmount boundaryImportScore(Model model, int transition, long batch) {
        PlannerAmount total = PlannerAmount.ZERO;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.suppliable[i] && model.cons[transition][i] > 0L) {
                long reference = 1L;
                for (int t = 0; t < model.transitionCount(); t++) reference = Math.max(reference, model.cons[t][i]);
                total = total.add(normalizedDeficit(PlannerAmount.of(model.cons[transition][i]).multiply(batch),
                    PlannerAmount.of(reference)));
            }
        }
        return total;
    }

    private static PlannerAmount maximumSafeBatch(Model model, PlannerAmount[] marking, int transition) {
        PlannerAmount maximum = null;
        for (int i : model.metadata[transition].consumedInternalKeys) {
            long consumed = model.cons[transition][i];
            PlannerAmount available = marking[i].divide(PlannerAmount.of(consumed));
            maximum = maximum == null ? available : maximum.min(available);
        }
        // A transition with no internal input is already structurally unconstrained. Permit a target-directed
        // batch, but keep the candidate generator finite by never inventing an unbounded maximal successor.
        return maximum == null ? PlannerAmount.of(Long.MAX_VALUE) : maximum;
    }

    private static void addBatchCandidate(BatchCandidates candidates, PlannerAmount candidate, PlannerAmount maximum) {
        if (candidate == null || candidate.signum() <= 0 || !candidate.fitsLong()
                || candidate.compareTo(maximum) > 0) return;
        candidates.add(candidate.longValueExact());
    }

    private static int compareProgress(Node left, Node right) {
        int deficit = left.deficit.compareTo(right.deficit);
        if (deficit != 0) return deficit;
        return Integer.compare(left.depth, right.depth);
    }

    private static PlannerAmount deficitScore(Model model, PlannerAmount[] marking) {
        PlannerAmount result = PlannerAmount.ZERO;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.required[i].compareTo(marking[i]) > 0) {
                result = result.add(normalizedDeficit(model.required[i].subtract(marking[i]), model.required[i]));
            }
        }
        return result;
    }

    /** Dimensionless fixed-point ratios; rounding only affects queue order, never proofs. */
    private static PlannerAmount normalizedDeficit(PlannerAmount missing, PlannerAmount required) {
        return missing.multiply(1_000_000L).ceilDiv(required.max(PlannerAmount.ONE));
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
        return fireBatch(model, marking, transition, PlannerAmount.of(batch));
    }

    private static PlannerAmount[] fireBatch(Model model, PlannerAmount[] marking, int transition, PlannerAmount count) {
        PlannerAmount[] next = marking.clone();
        long[] cons = model.cons[transition];
        long[] prod = model.prod[transition];
        for (int i : model.metadata[transition].changedKeys) {
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
        Map<AEKey, PlannerAmount> exactRequiredSeed = new Object2ObjectLinkedOpenHashMap<>(bare.lazySeed);
        Map<AEKey, PlannerAmount> exactExternalDemand = Map.copyOf(bare.lazyImport);
        CircuitSummary summary = summarize(model, witness);
        // Sequential prefix replay needs only one returned catalyst, but a planned batch may use more.
        // Reserve those already available copies explicitly so the reported and executable concurrency agree.
        for (BatchFiring firing : witness) {
            int transition = firing.transition();
            for (int key : model.metadata[transition].consumedInternalKeys) {
                long input = model.cons[transition][key];
                if (!summary.delta[key].isZero() || model.prod[transition][key] < input) continue;
                PlannerAmount concurrent = firing.exactCount().multiply(input).min(model.stock[key]);
                if (concurrent.signum() > 0)
                    exactRequiredSeed.merge(model.keys.get(key), concurrent, PlannerAmount::max);
            }
        }

        // A speculative ladder seed can survive the witness and satisfy the target without ever being
        // consumed (including an empty witness). Replay against real stock as well: lazy consumption seed
        // alone would omit that retained material and incorrectly report SUCCESS after an exhausted search.
        Simulation stocked = Arrays.equals(start, model.stock) ? actual : simulate(model, model.stock, witness);
        PlannerAmount[] accountedStart = model.stock.clone();
        for (int i = 0; i < model.keyCount(); i++) {
            AEKey key = model.keys.get(i);
            PlannerAmount retainedShortfall = model.required[i].subtract(stocked.marking[i]);
            if (retainedShortfall.signum() > 0) {
                PlannerAmount needed = model.stock[i]
                    .add(stocked.lazySeed.getOrDefault(key, PlannerAmount.ZERO)).add(retainedShortfall);
                exactRequiredSeed.merge(key, needed, PlannerAmount::max);
            }
            accountedStart[i] = accountedStart[i].max(exactRequiredSeed.getOrDefault(key, PlannerAmount.ZERO));
        }
        // Credit only stock and the seed actually reported, never surplus from a doubled probe.
        actual = Arrays.equals(accountedStart, model.stock) ? stocked : simulate(model, accountedStart, witness);
        if (!actual.lazySeed.isEmpty() || !satisfied(actual.marking, model.required)) {
            return CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET,
                CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED,
                "Internal inconsistency: accounted seed did not reproduce the verified target");
        }

        Map<AEKey, PlannerAmount> exactShortfall = new Object2ObjectLinkedOpenHashMap<>();
        exactRequiredSeed.forEach((key, amount) -> {
            PlannerAmount missing = amount.subtract(model.stockAmountOf(key).max(PlannerAmount.ZERO));
            if (missing.signum() > 0) exactShortfall.put(key, missing);
        });

        Map<IPatternDetails, PlannerAmount> exactPatternTimes = new Object2ObjectLinkedOpenHashMap<>();
        for (int p = 0; p < model.transitionCount(); p++) {
            if (summary.counts[p].signum() > 0) {
                exactPatternTimes.put(model.transitions.get(p).details(), summary.counts[p]);
            }
        }
        Map<IPatternDetails, Long> patternTimes = new Object2ObjectLinkedOpenHashMap<>();
        List<PatternRun> executionPlan = new ArrayList<>(witness.size());
        boolean runtimeCountsRepresentable = true;
        try {
            for (var entry : exactPatternTimes.entrySet()) patternTimes.put(entry.getKey(), entry.getValue().longValueExact());
            for (BatchFiring firing : witness) {
                CompiledPattern pattern = model.transitions.get(firing.transition());
                executionPlan.add(new PatternRun(pattern, firing.count(), firing.repeatWidth(),
                    firing.repetitions().longValueExact()));
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

    /** Exact prefix inventory for AE's concrete input keys and normalized container/catalyst returns. */
    private record CircuitSummary(PlannerAmount[] required, PlannerAmount[] delta,
            PlannerAmount[] produced, PlannerAmount[] counts) {
        CircuitSummary then(CircuitSummary next) {
            PlannerAmount[] r = required.clone(), d = delta.clone(), p = produced.clone(), c = counts.clone();
            for (int i = 0; i < r.length; i++) {
                r[i] = r[i].max(next.required[i].subtract(d[i]));
                d[i] = d[i].add(next.delta[i]);
                p[i] = p[i].add(next.produced[i]);
            }
            for (int i = 0; i < c.length; i++) c[i] = c[i].add(next.counts[i]);
            return new CircuitSummary(r, d, p, c);
        }

        CircuitSummary repeated(PlannerAmount times) {
            if (times.signum() <= 0) throw new IllegalArgumentException("Circuit repetitions must be positive");
            PlannerAmount[] r = required.clone(), d = delta.clone(), p = produced.clone(), c = counts.clone();
            for (int i = 0; i < r.length; i++) {
                if (d[i].signum() < 0) r[i] = r[i].subtract(d[i].multiply(times.subtract(PlannerAmount.ONE)));
                d[i] = d[i].multiply(times);
                p[i] = p[i].multiply(times);
            }
            for (int i = 0; i < c.length; i++) c[i] = c[i].multiply(times);
            return new CircuitSummary(r, d, p, c);
        }
    }

    private static CircuitSummary emptySummary(Model model) {
        return new CircuitSummary(zeroes(model.keyCount()), zeroes(model.keyCount()),
            zeroes(model.keyCount()), zeroes(model.transitionCount()));
    }

    private static boolean dominates(PlannerAmount[] left, PlannerAmount[] right) {
        for (int i = 0; i < left.length; i++) if (left[i].compareTo(right[i]) > 0) return false;
        return true;
    }

    /** Alternative first firings, compared per concrete item/fluid key rather than mixed-unit totals. */
    private static List<Map<AEKey, Long>> startupCandidates(Model model) {
        List<PlannerAmount[]> frontier = new ArrayList<>();
        for (int t = 0; t < model.transitionCount(); t++) {
            PlannerAmount[] missing = zeroes(model.keyCount());
            for (int i = 0; i < missing.length; i++) {
                if (!model.suppliable[i]) missing[i] = PlannerAmount.of(model.cons[t][i]).subtract(model.stock[i]).max(PlannerAmount.ZERO);
            }
            if (isZero(missing) || frontier.stream().anyMatch(existing -> dominates(existing, missing))) continue;
            frontier.removeIf(existing -> dominates(missing, existing));
            if (frontier.size() < 32) frontier.add(missing);
        }
        List<Map<AEKey, Long>> candidates = new ArrayList<>();
        for (PlannerAmount[] missing : frontier) {
            Map<AEKey, Long> candidate = new java.util.LinkedHashMap<>();
            for (int i = 0; i < missing.length; i++) if (missing[i].signum() > 0) candidate.put(model.keys.get(i), missing[i].longValueExact());
            candidates.add(Map.copyOf(candidate));
        }
        return List.copyOf(candidates);
    }

    private static CircuitSummary summarizePlain(Model model, List<BatchFiring> witness, int start, int end) {
        CircuitSummary summary = emptySummary(model);
        for (int index = start; index < end; index++) {
            BatchFiring firing = witness.get(index);
            CircuitSummary run = emptySummary(model);
            int t = firing.transition();
            for (int key = 0; key < model.keyCount(); key++) {
                run.required[key] = batchDeficit(PlannerAmount.ZERO, model.cons[t][key], model.prod[t][key], firing.exactCount());
                run.delta[key] = PlannerAmount.of(model.prod[t][key]).subtract(PlannerAmount.of(model.cons[t][key]))
                    .multiply(firing.exactCount());
                run.produced[key] = PlannerAmount.of(model.prod[t][key]).multiply(firing.exactCount());
            }
            run.counts[t] = firing.exactCount();
            summary = summary.then(run);
        }
        return summary;
    }

    private static CircuitSummary summarize(Model model, List<BatchFiring> witness) {
        int[] ends = new int[witness.size()];
        for (int i = 0; i < ends.length; i++) ends[i] = i;
        int previousEnd = -1;
        for (int end = 0; end < witness.size(); end++) {
            BatchFiring last = witness.get(end);
            if (last.repetitions().compareTo(PlannerAmount.ONE) <= 0) continue;
            int start = end - last.repeatWidth() + 1;
            if (start < 0 || start <= previousEnd) throw new IllegalStateException("Overlapping circuit witness");
            ends[start] = end;
            previousEnd = end;
        }
        CircuitSummary result = emptySummary(model);
        for (int start = 0; start < ends.length;) {
            int end = ends[start];
            result = result.then(summarizePlain(model, witness, start, end + 1).repeated(witness.get(end).repetitions()));
            start = end + 1;
        }
        return result;
    }

    /** Bound all finite inputs, including non-SCC fuel. Boundary supplies are charged during final replay. */
    private static PlannerAmount safeRepetitions(Model model, PlannerAmount[] stock, CircuitSummary lap,
            PlannerAmount requested) {
        PlannerAmount result = requested;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.suppliable[i]) continue;
            if (stock[i].compareTo(lap.required[i]) < 0) return PlannerAmount.ZERO;
            if (lap.delta[i].signum() < 0) {
                PlannerAmount loss = PlannerAmount.ZERO.subtract(lap.delta[i]);
                result = result.min(stock[i].subtract(lap.required[i]).divide(loss).add(PlannerAmount.ONE));
            }
        }
        return result.max(PlannerAmount.ZERO);
    }

    private static PlannerAmount[] applyCircuit(Model model, PlannerAmount[] stock, CircuitSummary summary) {
        PlannerAmount[] next = stock.clone();
        for (int i = 0; i < next.length; i++) {
            if (model.suppliable[i]) next[i] = next[i].max(summary.required[i]);
            else if (next[i].compareTo(summary.required[i]) < 0) throw new IllegalStateException("Unseeded recipe circuit");
            next[i] = next[i].add(summary.delta[i]);
        }
        return next;
    }

    private static void repeatTail(List<BatchFiring> witness, int start, PlannerAmount extra) {
        int end = witness.size() - 1;
        BatchFiring last = witness.get(end);
        witness.set(end, new BatchFiring(last.transition(), last.exactCount(), end - start + 1,
            extra.add(PlannerAmount.ONE)));
    }

    /**
     * Reuse an actually executed recipe circuit, including split/merge recipes and returned containers.
     * This is a verified heuristic: competing seed routes still retain the original bounded search fallback.
     */
    private static PlannerAmount[] accelerateRecipeCircuit(Model model, PlannerAmount[] marking,
            List<BatchFiring> witness, ECOCancellation cancellation) throws InterruptedException {
        if (!supportsRecipeCircuits(model)) return marking;
        int end = witness.size();
        // Restrict this probe to a short suffix; no order-sized or nested witness expansion.
        for (int start = end - 1; start >= Math.max(0, end - 32); start--) {
            cancellation.checkpoint();
            if (witness.get(start).repetitions().compareTo(PlannerAmount.ONE) > 0) break;
            CircuitSummary lap = summarizePlain(model, witness, start, end);
            PlannerAmount requested = PlannerAmount.ZERO;
            for (int key = 0; key < model.keyCount(); key++) {
                if (lap.delta[key].signum() > 0 && model.required[key].compareTo(marking[key]) > 0) {
                    requested = requested.max(model.required[key].subtract(marking[key]).ceilDiv(lap.delta[key]));
                }
            }
            if (requested.signum() <= 0) continue;
            PlannerAmount extra = safeRepetitions(model, marking, lap, requested);
            if (extra.signum() <= 0) continue;
            repeatTail(witness, start, extra);
            return applyCircuit(model, marking, lap.repeated(extra));
        }
        return marking;
    }

    private static boolean supportsRecipeCircuits(Model model) {
        for (CompiledPattern pattern : model.transitions) {
            if (!pattern.semantics().cycleSafeForStaticPlanning()) return false;
            if (pattern.specialAnalysis().requirements().stream().anyMatch(requirement ->
                    requirement.type() == cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Type.DURABILITY)) {
                return false;
            }
        }
        return true;
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
        Map<AEKey, PlannerAmount> lazySeed = new Object2ObjectLinkedOpenHashMap<>();
        Map<AEKey, PlannerAmount> lazyImport = new Object2ObjectLinkedOpenHashMap<>();
        Map<AEKey, PlannerAmount> produced = new Object2ObjectLinkedOpenHashMap<>();
        CircuitSummary summary = summarize(model, witness);
        for (int i = 0; i < n; i++) {
            PlannerAmount deficit = summary.required[i].subtract(marking[i]).max(PlannerAmount.ZERO);
            AEKey key = model.keys.get(i);
            if (deficit.signum() > 0) {
                (model.suppliable[i] ? lazyImport : lazySeed).put(key, deficit);
            }
            marking[i] = marking[i].add(deficit).add(summary.delta[i]);
            if (summary.produced[i].signum() > 0) produced.put(key, summary.produced[i]);
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

    private static List<CycleFiring> expandWitness(Model model, List<BatchFiring> witness) {
        int total = expandedWitnessLength(witness);
        if (total == 0) return List.of();
        List<CycleFiring> result = new ArrayList<>(total);
        long[] laps = new long[witness.size()];
        for (int i = 0; i < laps.length; i++) laps[i] = witness.get(i).repetitions().longValueExact();
        for (int index = 0; index < witness.size();) {
            BatchFiring firing = witness.get(index);
            CompiledPattern pattern = model.transitions.get(firing.transition());
            for (long count = 0; count < firing.count(); count++) result.add(new CycleFiring(result.size(), pattern));
            if (firing.repetitions().compareTo(PlannerAmount.ONE) > 0 && --laps[index] > 0) index -= firing.repeatWidth() - 1;
            else index++;
        }
        return List.copyOf(result);
    }

    private static int expandedWitnessLength(List<BatchFiring> witness) {
        PlannerAmount total = PlannerAmount.ZERO;
        for (int end = 0; end < witness.size(); end++) {
            BatchFiring firing = witness.get(end);
            total = total.add(firing.exactCount());
            if (firing.repetitions().compareTo(PlannerAmount.ONE) > 0) {
                PlannerAmount perLap = PlannerAmount.ZERO;
                for (int i = end - firing.repeatWidth() + 1; i <= end; i++) perLap = perLap.add(witness.get(i).exactCount());
                total = total.add(perLap.multiply(firing.repetitions().subtract(PlannerAmount.ONE)));
            }
            if (total.compareTo(PlannerAmount.of(MAX_EXPANDED_WITNESS)) > 0) return 0;
        }
        return (int) total.longValueExact();
    }

    private static Map<AEKey, Long> deliverable(Model model, PlannerAmount[] marking) {
        return representable(exactDeliverable(model, marking));
    }

    private static Map<AEKey, PlannerAmount> exactDeliverable(Model model, PlannerAmount[] marking) {
        Map<AEKey, PlannerAmount> result = new Object2ObjectLinkedOpenHashMap<>();
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.required[i].signum() > 0) result.put(model.keys.get(i), marking[i]);
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, Long> representable(Map<AEKey, PlannerAmount> amounts) {
        Map<AEKey, Long> result = new Object2ObjectLinkedOpenHashMap<>();
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

    private record BatchFiring(int transition, PlannerAmount exactCount, int repeatWidth, PlannerAmount repetitions) {
        private BatchFiring(int transition, PlannerAmount count) { this(transition, count, 1, PlannerAmount.ONE); }
        private BatchFiring {
            if (transition < 0) throw new IllegalArgumentException("Batch transition must not be negative");
            if (exactCount.signum() <= 0) throw new IllegalArgumentException("Batch firing count must be positive");
        }

        private BatchFiring(int transition, long count) {
            this(transition, PlannerAmount.of(count));
        }

        /** Only bounded-search candidates and the legacy execution adapter require a long projection. */
        private long count() {
            return exactCount.longValueExact();
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
        PlannerAmount[] required,
        TransitionMetadata[] metadata,
        boolean[] consumedKeys
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
        private final PlannerAmount deficit;

        private Node(PlannerAmount[] marking, int parent, BatchFiring firing, int depth, PlannerAmount deficit) {
            this.marking = marking;
            this.parent = parent;
            this.firing = firing;
            this.depth = depth;
            this.deficit = deficit;
        }
    }

    private record OutputTarget(int key, PlannerAmount delta) {}
    private record UnlockTarget(int key, PlannerAmount threshold, PlannerAmount delta) {}
    private record TransitionMetadata(int[] consumedInternalKeys, int[] changedKeys,
            OutputTarget[] requiredOutputs, UnlockTarget[] unlockTargets) {}

    private static final long[] EMPTY_BATCHES = new long[0];

    /** Invocation-local storage also keeps recursive lookahead candidates independent. */
    private static final class BatchCandidates {
        private long[] values = new long[8];
        private int size;

        void add(long value) {
            for (int i = 0; i < size; i++) if (values[i] == value) return;
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }

        long[] sorted(boolean ascending, boolean omitUnbounded) {
            Arrays.sort(values, 0, size);
            if (omitUnbounded && size > 0 && values[size - 1] == Long.MAX_VALUE) size--;
            if (!ascending) {
                for (int left = 0, right = size - 1; left < right; left++, right--) {
                    long value = values[left];
                    values[left] = values[right];
                    values[right] = value;
                }
            }
            return size == 0 ? EMPTY_BATCHES : Arrays.copyOf(values, size);
        }
    }

    private static final class Marking {
        private final PlannerAmount[] cells;
        private final int hash;

        private Marking(Model model, PlannerAmount[] cells) {
            this.cells = cells.clone();
            for (int i = 0; i < cells.length; i++) {
                if (!model.consumedKeys[i]) this.cells[i] = cells[i].min(model.required[i]);
            }
            this.hash = Arrays.hashCode(this.cells);
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
        private final List<PlannerAmount[]> unblockCandidates = new ArrayList<>();
        private int unblockRank = Integer.MAX_VALUE;
        private PlannerAmount unblockTotal = null;
        private int unblockTransition = Integer.MAX_VALUE;

        /**
         * Records the cheapest way to unblock a disabled transition, preferring one that actually produces a
         * required output. That candidate becomes the base vector of the deterministic seed ladder.
         */
        private void considerUnblock(Model model, PlannerAmount[] marking, int transition) {
            int rank = model.producesRequired[transition] ? 0 : 1;

            long[] cons = model.cons[transition];
            PlannerAmount total = PlannerAmount.ZERO;
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                if (cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0) {
                    total = total.add(normalizedDeficit(required.subtract(marking[i]), required));
                }
            }
            if (total.signum() <= 0) return;
            PlannerAmount[] deficit = new PlannerAmount[cons.length];
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                deficit[i] = cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0
                    ? required.subtract(marking[i]) : PlannerAmount.ZERO;
            }
            boolean dominated = false;
            for (PlannerAmount[] existing : unblockCandidates) {
                if (dominates(existing, deficit)) { dominated = true; break; }
            }
            if (!dominated) {
                unblockCandidates.removeIf(existing -> dominates(deficit, existing));
                if (unblockCandidates.size() < 32) unblockCandidates.add(deficit);
            }
            if (rank > unblockRank) return;
            if (rank == unblockRank) {
                if (total.compareTo(unblockTotal) > 0) return;
                if (total.equals(unblockTotal) && transition >= unblockTransition) return;
            }
            unblockRank = rank;
            unblockTotal = total;
            unblockTransition = transition;
            unblockDeficit = deficit;
        }
    }
}
