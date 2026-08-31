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
                "Found a firing order of " + first.witness.size() + " step(s) within the stock snapshot"));
            return witnessResult(model, model.stock, first.witness, diagnostics,
                new CycleSolveMetrics(model.keyCount(), model.transitionCount(), visited, expanded,
                    first.witness.size(), 0, false, false));
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
                        attempt.witness.size(), step + 1, false, false));
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
    // Bounded breadth-first marking search
    // ---------------------------------------------------------------------------------------------------

    /**
     * Breadth-first over markings, transitions tried in ascending pattern id.
     *
     * <p>Breadth-first buys two things: the first witness found is one of minimum firing count, and because
     * a marking is therefore first reached at its minimum depth, exact-marking deduplication is enough to
     * keep the search finite without discarding any shorter solution.
     */
    private Search search(Model model, PlannerAmount[] start, int stateBudget, int maxFirings, ECOCancellation cancellation)
            throws InterruptedException {
        Search outcome = new Search();
        int n = model.keyCount();
        int transitionCount = model.transitionCount();
        List<Node> nodes = new ArrayList<>();
        Set<Marking> seen = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        PlannerAmount[] root = Arrays.copyOf(start, n);
        nodes.add(new Node(root, -1, -1, 0));
        seen.add(new Marking(root));
        if (satisfied(root, model.required)) {
            outcome.kind = Search.Kind.REACHED;
            outcome.witness = List.of();
            outcome.statesVisited = 1;
            return outcome;
        }
        queue.addLast(0);

        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            int index = queue.pollFirst();
            Node node = nodes.get(index);
            if (node.depth >= maxFirings) {
                outcome.firingDepthTruncated = true;
                continue;
            }
            outcome.statesExpanded++;
            for (int t = 0; t < transitionCount; t++) {
                if (!enabled(node.marking, model.cons[t], model.suppliable)) {
                    outcome.considerUnblock(model, node.marking, t);
                    continue;
                }
                PlannerAmount[] next = fire(node.marking, model.cons[t], model.prod[t]);
                Marking key = new Marking(next);
                if (seen.contains(key)) continue;
                if (seen.size() >= stateBudget) {
                    outcome.stateBudgetExhausted = true;
                    break;
                }
                seen.add(key);
                int child = nodes.size();
                nodes.add(new Node(next, index, t, node.depth + 1));
                if (satisfied(next, model.required)) {
                    outcome.kind = Search.Kind.REACHED;
                    outcome.witness = witnessOf(nodes, child);
                    outcome.statesVisited = seen.size();
                    return outcome;
                }
                queue.addLast(child);
            }
            if (outcome.stateBudgetExhausted) break;
        }

        outcome.statesVisited = seen.size();
        if (outcome.stateBudgetExhausted) outcome.kind = Search.Kind.STATE_BUDGET;
        else if (outcome.firingDepthTruncated) outcome.kind = Search.Kind.DEPTH_TRUNCATED;
        else outcome.kind = Search.Kind.EXHAUSTED;
        return outcome;
    }

    private static List<Integer> witnessOf(List<Node> nodes, int leaf) {
        ArrayDeque<Integer> reversed = new ArrayDeque<>();
        int cursor = leaf;
        while (cursor > 0) {
            Node node = nodes.get(cursor);
            reversed.addFirst(node.transition);
            cursor = node.parent;
        }
        return List.copyOf(reversed);
    }

    private static boolean enabled(PlannerAmount[] marking, long[] cons, boolean[] suppliable) {
        for (int i = 0; i < cons.length; i++) {
            if (cons[i] > 0 && !suppliable[i] && marking[i].compareTo(PlannerAmount.of(cons[i])) < 0) return false;
        }
        return true;
    }

    private static PlannerAmount[] fire(PlannerAmount[] marking, long[] cons, long[] prod) {
        PlannerAmount[] next = marking.clone();
        for (int i = 0; i < next.length; i++) {
            if (cons[i] > 0) next[i] = next[i].subtract(cons[i]).max(PlannerAmount.ZERO);
            if (prod[i] > 0) next[i] = next[i].add(prod[i]);
        }
        return next;
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

    private CycleSolveResult witnessResult(Model model, PlannerAmount[] start, List<Integer> witness,
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
        List<CycleFiring> firings = new ArrayList<>(witness.size());
        for (int step = 0; step < witness.size(); step++) {
            CompiledPattern pattern = model.transitions.get(witness.get(step));
            firings.add(new CycleFiring(step, pattern));
            patternTimes.merge(pattern.details(), 1L, Long::sum);
        }

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
            List.copyOf(explanation), metrics);
    }

    /**
     * Replays a witness step by step. Deficits on boundary keys are booked as imports, deficits on keys the
     * SCC has to own are booked as seed; both are recorded rather than allowed to go negative, so the pair
     * (seed, import) is exactly what the order needs to run.
     */
    private static Simulation simulate(Model model, PlannerAmount[] start, List<Integer> witness) {
        int n = model.keyCount();
        PlannerAmount[] marking = Arrays.copyOf(start, n);
        Map<AEKey, PlannerAmount> lazySeed = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> lazyImport = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> produced = new LinkedHashMap<>();
        for (int transition : witness) {
            long[] cons = model.cons[transition];
            long[] prod = model.prod[transition];
            for (int i = 0; i < n; i++) {
                if (cons[i] <= 0) continue;
                PlannerAmount required = PlannerAmount.of(cons[i]);
                PlannerAmount deficit = required.subtract(marking[i]);
                if (deficit.signum() > 0) {
                    AEKey key = model.keys.get(i);
                    (model.suppliable[i] ? lazyImport : lazySeed).merge(key, deficit, PlannerAmount::add);
                    marking[i] = marking[i].add(deficit);
                }
                marking[i] = marking[i].subtract(required);
            }
            for (int i = 0; i < n; i++) {
                if (prod[i] <= 0) continue;
                marking[i] = marking[i].add(prod[i]);
                produced.merge(model.keys.get(i), PlannerAmount.of(prod[i]), PlannerAmount::add);
            }
        }
        return new Simulation(marking, lazySeed, lazyImport, produced);
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
        private final int transition;
        private final int depth;

        private Node(PlannerAmount[] marking, int parent, int transition, int depth) {
            this.marking = marking;
            this.parent = parent;
            this.transition = transition;
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
        private List<Integer> witness = List.of();
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
