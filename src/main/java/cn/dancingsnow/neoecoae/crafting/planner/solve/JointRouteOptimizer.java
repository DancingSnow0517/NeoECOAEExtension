package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleStateEquation;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded joint firing optimization followed by physical, demand-attributed DAG replay. */
public final class JointRouteOptimizer {
    private boolean budgetExhausted;
    private Map<AEKey, Long> prefixRequired = Map.of();
    private Map<IPatternDetails, PlannerAmount> prefixExecuted = Map.of();

    public boolean budgetExhausted() { return budgetExhausted; }

    public ComponentPlanner.Outcome optimize(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long amount, ComponentPlanner.Outcome preferred, ECOCancellation cancellation) throws InterruptedException {
        return optimize(network, inventory, amount, preferred, false, cancellation);
    }

    public ComponentPlanner.Outcome optimize(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long amount, ComponentPlanner.Outcome preferred, boolean allowOrdered,
            ECOCancellation cancellation) throws InterruptedException {
        budgetExhausted = false;
        if (amount <= 0) return preferred;
        boolean completed = preferred != null && preferred.status() == PlanningStatus.SUCCESS;
        // Feasibility recovery gets a useful share; optional improvement remains short and keeps its incumbent.
        ECOCancellation bounded = new ECOPlanningBudget(cancellation, completed ? 50_000 : 1_000_000,
            completed ? 100_000_000L : 3_000_000_000L, System::nanoTime);
        try {
            ComponentPlanner.Outcome proposal = null;
            if (!completed && allowOrdered) {
                // Earliest startup routes are a small candidate problem, never an exclusion proof.
                // A failed candidate still falls through to the complete advertised network.
                var startup = bootstrapNetwork(network, inventory, Set.of(network.goal()), bounded);
                if (!startup.producersOf(network.goal()).isEmpty())
                    proposal = propose(startup, inventory, amount, true, true, bounded);
            }
            if (proposal == null) proposal = propose(network, inventory, amount, !completed, allowOrdered, bounded);
            if (proposal == null) return preferred;
            if (preferred != null && preferred.status() == PlanningStatus.SUCCESS
                    && cost(proposal.state()).compareTo(cost(preferred.state())) >= 0) return preferred;
            return proposal;
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            budgetExhausted = true;
            if (preferred != null) preferred.trace().addDiagnostic(new PlannerDiagnostic(
                    PlannerDiagnostic.Code.ROUTE_OPTIMIZATION_BUDGET,
                    "Joint route search allowance exhausted; retaining the completed plan"));
            return preferred;
        }
    }

    private ComponentPlanner.Outcome propose(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long amount, boolean firstFeasible, boolean allowOrdered, ECOCancellation cancellation) throws InterruptedException {
        Set<AEKey> demanded = new LinkedHashSet<>();
        var pending = new java.util.ArrayDeque<AEKey>(); pending.add(network.goal());
        Set<IPatternDetails> demandedPatterns = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = pending.removeFirst();
            if (!demanded.add(key)) continue;
            for (CompiledPattern pattern : network.producersOf(key)) {
                if (safe(pattern, allowOrdered) && demandedPatterns.add(pattern.details()))
                    pattern.inputs().forEach(input -> pending.addLast(input.key()));
            }
        }
        List<CompiledPattern> candidates = new ArrayList<>();
        Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean competition = false;
        for (var producers : network.producers().values()) {
            cancellation.checkpoint();
            competition |= producers.size() > 1;
            for (CompiledPattern pattern : producers) {
                cancellation.checkpoint();
                if (demandedPatterns.contains(pattern.details()) && safe(pattern, allowOrdered) && seen.add(pattern.details())) {
                    candidates.add(pattern);
                    competition |= pattern.outputs().stream().map(output -> output.what()).distinct().count() > 1;
                }
            }
        }
        if (!competition || candidates.isEmpty()) return null;
        // Presence closure removes seed-free and unavailable branches without asserting numeric feasibility.
        Set<AEKey> available = new LinkedHashSet<>(network.emittable());
        available.remove(network.goal());
        for (var entry : inventory.toKeyCounter()) if (!entry.getKey().equals(network.goal())) available.add(entry.getKey());
        Set<CompiledPattern> reachable = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (CompiledPattern pattern : candidates) {
                cancellation.checkpoint();
                if (reachable.contains(pattern) || pattern.inputs().stream().anyMatch(input -> !available.contains(input.key())))
                    continue;
                reachable.add(pattern);
                pattern.outputs().forEach(output -> available.add(output.what()));
                changed = true;
            }
        } while (changed);
        candidates.removeIf(pattern -> !reachable.contains(pattern));
        if (!available.contains(network.goal())) return null;
        // Dominance preserves all exact products and never increases consumption, at the same firing cost.
        List<CompiledPattern> patterns = new ArrayList<>();
        for (CompiledPattern candidate : candidates) {
            cancellation.checkpoint();
            boolean dominated = false;
            for (CompiledPattern other : patterns) {
                cancellation.checkpoint();
                if (dominates(network, other, candidate)) { dominated = true; break; }
            }
            if (!dominated) patterns.add(candidate);
        }
        Map<AEKey, Integer> keys = new LinkedHashMap<>();
        keys.put(network.goal(), 0);
        for (CompiledPattern pattern : patterns) {
            pattern.inputs().forEach(input -> keys.computeIfAbsent(input.key(), ignored -> keys.size()));
            pattern.outputs().forEach(output -> keys.computeIfAbsent(output.what(), ignored -> keys.size()));
        }
        List<Map<Integer, BigInteger>> net = new ArrayList<>();
        for (CompiledPattern pattern : patterns) {
            cancellation.checkpoint();
            Map<Integer, BigInteger> column = new LinkedHashMap<>();
            for (var input : inputs(pattern).entrySet())
                column.merge(keys.get(input.getKey()), input.getValue().toBigInteger(), BigInteger::add);
            for (var output : outputs(pattern).entrySet())
                column.merge(keys.get(output.getKey()), output.getValue().toBigInteger().negate(), BigInteger::add);
            column.values().removeIf(value -> value.signum() == 0);
            net.add(column);
        }
        SolveState stock = new SolveState(inventory);
        boolean[] imports = new boolean[keys.size()];
        PlannerAmount[] initial = new PlannerAmount[keys.size()];
        PlannerAmount[] target = new PlannerAmount[keys.size()];
        Arrays.fill(target, PlannerAmount.ZERO);
        target[0] = PlannerAmount.of(amount);
        for (var key : keys.entrySet()) {
            boolean goal = key.getKey().equals(network.goal());
            imports[key.getValue()] = !goal && (network.emittable().contains(key.getKey()) || inventory.isUnbounded(key.getKey()));
            initial[key.getValue()] = goal ? PlannerAmount.ZERO : stock.stored.get(key.getKey());
        }
        // Reserve the rest of this phase for concrete replay even if quantity optimization exhausts its slice.
        ECOCancellation quantities = new ECOPlanningBudget(cancellation, firstFeasible ? 700_000 : 30_000,
            firstFeasible ? 2_000_000_000L : 60_000_000L, System::nanoTime);
        var solution = CycleStateEquation.solveSparseMaterialBalance(net, imports, initial, target, false, quantities);
        if (solution.counts() == null) return null;
        SolveState state = replay(network, inventory, amount, patterns, solution.counts(), cancellation);
        if (state == null && allowOrdered) {
            Set<Map<Integer, PlannerAmount>> attempted = new LinkedHashSet<>();
            Map<Integer, PlannerAmount> lowerBounds = new LinkedHashMap<>();
            while (true) {
                prefixRequired = Map.of(); prefixExecuted = Map.of();
                var ordered = orderedProposal(network, inventory, amount, patterns, solution.counts(), cancellation);
                if (ordered != null) return ordered;
                if (prefixRequired.isEmpty()) return null;
                var cycle = new CycleComponent(0, List.of(network.goal()), List.of(), List.of(), List.of(), List.of());
                Set<IPatternDetails> allowedStartup = Collections.newSetFromMap(new IdentityHashMap<>());
                var bootstrap = bootstrapNetwork(network, inventory, prefixRequired.keySet(), cancellation);
                bootstrap.producers().values().forEach(views -> views.forEach(pattern -> allowedStartup.add(pattern.details())));
                Map<Integer, PlannerAmount> repaired = new LinkedHashMap<>();
                for (int i = 0; i < patterns.size(); i++) if (allowedStartup.contains(patterns.get(i).details()))
                    repaired.put(i, prefixExecuted.getOrDefault(patterns.get(i).details(), PlannerAmount.ZERO));
                var external = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(bootstrap, cycle,
                    prefixRequired, inventory.toKeyCounter(), new SolveState(inventory), Map.of(), Set.of(), cancellation);
                if (!external.solved()) return null;
                for (SolveState supply : external.states()) {
                    for (var firing : supply.patternTimes.entrySet()) {
                        int index = -1;
                        for (int i = 0; i < patterns.size(); i++) if (patterns.get(i).details() == firing.getKey()) { index = i; break; }
                        if (index < 0) return null;
                        repaired.merge(index, firing.getValue(), PlannerAmount::add);
                    }
                }
                repaired.forEach((index, count) -> lowerBounds.merge(index, count, PlannerAmount::max));
                if (lowerBounds.isEmpty() || !attempted.add(Map.copyOf(lowerBounds))) return null;
                // This restricted model is a recovery heuristic, not an original-graph infeasibility proof.
                solution = CycleStateEquation.solveSparseMaterialBalance(net, imports, initial, target, lowerBounds, false, quantities);
                if (solution.counts() == null) return null;
            }
        }
        if (state == null || !state.executionAmountIssues().isEmpty()
                || ECOPlanMaterialValidator.firstDeficit(state, network.goal(), amount, inventory.toKeyCounter(), network) != null)
            return null;
        state.executionProvenance().requireComplete();
        if (state.executionProvenance().hasPatternCycle(state.patternTimes.keySet(), cancellation)) return null;
        if (!activatedFiringsCovered(network, state, patterns, cancellation)) return null;
        ECOPlanTrace trace = new ECOPlanTrace();
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.JOINT_ROUTE_OPTIMIZED,
                "Joint exact-pattern plan verified; firings=" + cost(state) + " candidates=" + candidates.size()
                        + " retained=" + patterns.size() + " quantityStatus=" + solution.status()));
        List<ComponentPlanningResult> components = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (var task : state.patternTimes.entrySet()) {
            CompiledPattern pattern = patterns.stream().filter(p -> p.details() == task.getKey()).findFirst().orElseThrow();
            int id = components.size();
            order.add(id);
            components.add(new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
                    ComponentPlanningResult.Status.PLANNED, Map.of(), Set.of(task.getKey()), Set.of(task.getKey()),
                    null, null, Map.of(), "JOINT_ROUTE", null, CycleExecutionDisposition.NOT_REQUIRED, Map.of()));
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, pattern.producedKey(), task.getKey(),
                    0, 0, 0, 0, task.getValue().longValueExact(), PlanTraceNode.Selection.SELECTED, "JOINT_ROUTE"));
        }
        return new ComponentPlanner.Outcome(PlanningStatus.SUCCESS, state, trace, List.of(), components, order);
    }

    /** A candidate-only startup DAG: earliest supply cannot depend on the same loop's later returns. */
    private static CompiledNetwork bootstrapNetwork(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Set<AEKey> goals, ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, Integer> ranks = new LinkedHashMap<>();
        network.emittable().forEach(key -> ranks.put(key, 0));
        for (var entry : inventory.toKeyCounter()) if (entry.getLongValue() > 0) ranks.put(entry.getKey(), 0);
        boolean changed;
        do {
            changed = false;
            for (var views : network.producers().values()) for (var pattern : views) {
                cancellation.checkpoint();
                if (!safe(pattern, true) || inputs(pattern).keySet().stream().anyMatch(key -> !ranks.containsKey(key))) continue;
                int rank = inputs(pattern).keySet().stream().mapToInt(ranks::get).max().orElse(0) + 1;
                for (var output : pattern.outputs()) {
                    if (!ranks.containsKey(output.what()) || ranks.get(output.what()) > rank) {
                        ranks.put(output.what(), rank); changed = true;
                    }
                }
            }
        } while (changed);
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        var pending = new java.util.ArrayDeque<>(goals);
        Set<AEKey> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = pending.removeFirst();
            if (!visited.add(key)) continue;
            List<CompiledPattern> views = network.producersOf(key).stream().filter(pattern -> safe(pattern, true)
                && inputs(pattern).keySet().stream().allMatch(input -> ranks.containsKey(input)
                    && ranks.get(input) < ranks.getOrDefault(key, 0))).toList();
            producers.put(key, views);
            for (var pattern : views) pending.addAll(inputs(pattern).keySet());
        }
        // Restrict physical candidates without discarding their real advertised output views.
        // The ordered verifier must see the same AE activation contract as the original graph.
        Set<IPatternDetails> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        producers.values().forEach(views -> views.forEach(pattern -> retained.add(pattern.details())));
        network.producers().forEach((key, views) -> {
            var advertised = views.stream().filter(pattern -> retained.contains(pattern.details())).toList();
            if (!advertised.isEmpty()) producers.put(key, advertised);
        });
        return new CompiledNetwork(goals.iterator().next(), producers, network.emittable(),
            producers.values().stream().mapToInt(List::size).sum(),
            producers.values().stream().flatMap(List::stream).mapToInt(pattern -> pattern.inputs().size()).sum());
    }

    /** Selected-support feedback uses a verified flat execution trace; failed replay remains unknown. */
    private ComponentPlanner.Outcome orderedProposal(CompiledNetwork network, PlannerInventorySnapshot inventory,
            long amount, List<CompiledPattern> patterns, PlannerAmount[] counts, ECOCancellation cancellation)
            throws InterruptedException {
        List<CompiledPattern> selected = new ArrayList<>();
        Map<IPatternDetails, PlannerAmount> firings = new LinkedHashMap<>();
        Set<AEKey> keys = new LinkedHashSet<>(); keys.add(network.goal());
        Map<AEKey, PlannerAmount> demand = new LinkedHashMap<>();
        demand.put(network.goal(), PlannerAmount.of(amount));
        for (int i = 0; i < patterns.size(); i++) {
            cancellation.checkpoint();
            if (counts[i].isZero()) continue;
            if (counts[i].signum() < 0 || !counts[i].fitsLong()) return null;
            CompiledPattern pattern = patterns.get(i);
            selected.add(pattern); firings.put(pattern.details(), counts[i]);
            for (var input : inputs(pattern).entrySet()) {
                // Unlimited imports are already supported by DAG replay; this verifier requires finite marking.
                if (inventory.isUnbounded(input.getKey()) || network.emittable().contains(input.getKey())) return null;
                keys.add(input.getKey());
                demand.merge(input.getKey(), input.getValue().multiply(counts[i]), PlannerAmount::add);
            }
            outputs(pattern).keySet().forEach(keys::add);
        }
        // CompiledNetwork contains queried views, so an unused output need not be indexed.
        // Consumed hidden coproduct feedback still needs the existing per-slot component path.
        for (var pattern : selected) {
            if (outputs(pattern).keySet().stream().anyMatch(key -> demand.getOrDefault(key, PlannerAmount.ZERO).signum() > 0
                    && network.producersOf(key).stream().noneMatch(view -> view.details() == pattern.details()))) return null;
        }
        if (selected.isEmpty() || !orderedActivationsCovered(network, selected, firings, amount, Map.of(), cancellation)) return null;
        var stock = inventory.toKeyCounter();
        Map<AEKey, Long> initial = new LinkedHashMap<>();
        for (AEKey key : keys) initial.put(key, stock.get(key));
        PlannerAmount required = PlannerAmount.of(amount).add(stock.get(network.goal()));
        var component = new CycleComponent(0, List.copyOf(keys), selected, List.of(), List.of(), List.of());
        var witness = new BoundedCycleSolver().verifyFirings(new CycleSolveRequest(component,
            required.fitsLong() ? Map.of(network.goal(), required.longValueExact()) : Map.of(),
            Map.of(network.goal(), required), initial, List.of(), new CycleSolveRequest.PlannerOptions()), firings, cancellation);
        if (witness == null) return null;
        if (witness.status() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT) {
            prefixRequired = Map.copyOf(witness.seedShortfall());
            prefixExecuted = verifiedPrefixFirings(witness.executionPlan(), initial, cancellation);
            return null;
        }
        if (witness.status() != CycleSolveStatus.SUCCESS || witness.executionPlan().isEmpty()) return null;
        if (!orderedActivationsCovered(network, selected, firings, amount, witness.requiredSeed(), cancellation)) return null;
        SolveState state = new SolveState(inventory);
        state.patternTimes.putAll(firings); state.demand.putAll(demand);
        for (var seed : witness.requiredSeed().entrySet()) {
            if (seed.getValue() > stock.get(seed.getKey())) return null;
            state.used.add(seed.getKey(), seed.getValue());
            if (seed.getValue() > 0) {
                var boundary = MaterialDemand.boundary(0, seed.getKey(), PlannerAmount.of(seed.getValue()));
                state.provenance.register(boundary);
                state.provenance.allocate(boundary, seed.getKey(), MaterialSource.Stock.INSTANCE, boundary.amount());
            }
        }
        var goal = MaterialDemand.goal(network.goal(), PlannerAmount.of(amount));
        state.provenance.register(goal);
        state.provenance.allocate(goal, goal.key(), new MaterialSource.CycleOutput(0), goal.amount());
        state.bytes = PlannerAmount.of(demand.size()).multiply(8).add(cost(state));
        demand.forEach((key, count) -> state.bytes = state.bytes.add(PlannerAmount.stackBytes(count, key.getAmountPerByte())));
        if (!state.executionAmountIssues().isEmpty()
                || ECOPlanMaterialValidator.firstDeficit(state, network.goal(), amount, stock, network) != null) return null;
        state.executionProvenance().requireComplete();
        Map<AEKey, Long> reservations = new LinkedHashMap<>();
        for (var entry : state.usedItems()) reservations.put(entry.getKey(), entry.getLongValue());
        var result = new ComponentPlanningResult(0, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(network.goal(), amount), firings.keySet(), firings.keySet(),
            CyclePlanningStatus.SOLVED, CycleExternalDemandStatus.SOLVED, Map.of(), "JOINT_ORDERED", witness,
            CycleExecutionDisposition.ORDERED_EXECUTION, reservations);
        ECOPlanTrace trace = new ECOPlanTrace();
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.JOINT_ROUTE_OPTIMIZED,
            "Joint firing vector verified with ordered prefix; firings=" + cost(state) + " steps=" + witness.executionPlan().size()));
        Map<AEKey, PlannerAmount> netOutputs = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> totalOutputs = new LinkedHashMap<>();
        for (var pattern : selected) {
            var net = outputs(pattern);
            inputs(pattern).forEach((key, used) -> net.merge(key, used.multiply(-1), PlannerAmount::add));
            net.forEach((key, output) -> {
                netOutputs.merge(key, output, PlannerAmount::add);
                totalOutputs.merge(key, output.multiply(firings.get(pattern.details())), PlannerAmount::add);
            });
        }
        var diagnostic = new CycleDiagnostic(List.copyOf(keys), List.copyOf(firings.keySet()), netOutputs, totalOutputs,
            initial, witness.executionCountKnowledge(), witness.status());
        return new ComponentPlanner.Outcome(PlanningStatus.SUCCESS, state, trace, List.of(diagnostic), List.of(result), List.of(0));
    }

    private static Map<IPatternDetails, PlannerAmount> verifiedPrefixFirings(
            List<cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun> runs, Map<AEKey, Long> initial,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, PlannerAmount> stock = new LinkedHashMap<>();
        initial.forEach((key, amount) -> stock.put(key, PlannerAmount.of(amount)));
        Map<IPatternDetails, PlannerAmount> executed = new LinkedHashMap<>();
        for (var run : runs) {
            cancellation.checkpoint();
            if (run.repetitions() != 1) break;
            var in = inputs(run.pattern()); var out = outputs(run.pattern());
            PlannerAmount count = PlannerAmount.of(run.count());
            for (var input : in.entrySet()) {
                PlannerAmount available = stock.getOrDefault(input.getKey(), PlannerAmount.ZERO);
                if (available.compareTo(input.getValue()) < 0) { count = PlannerAmount.ZERO; break; }
                PlannerAmount net = input.getValue().subtract(out.getOrDefault(input.getKey(), PlannerAmount.ZERO));
                if (net.signum() > 0) count = count.min(available.subtract(input.getValue()).divide(net).add(1));
            }
            if (count.signum() > 0) {
                for (var input : in.entrySet()) stock.merge(input.getKey(), input.getValue().multiply(count).multiply(-1), PlannerAmount::add);
                for (var output : out.entrySet()) stock.merge(output.getKey(), output.getValue().multiply(count), PlannerAmount::add);
                executed.merge(run.details(), count, PlannerAmount::add);
            }
            if (count.compareTo(PlannerAmount.of(run.count())) < 0) break;
        }
        return executed;
    }

    private static boolean orderedActivationsCovered(CompiledNetwork network, List<CompiledPattern> selected,
            Map<IPatternDetails, PlannerAmount> firings, long amount, Map<AEKey, Long> stockCredits,
            ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, PlannerAmount> demand = new LinkedHashMap<>();
        demand.put(network.goal(), PlannerAmount.of(amount));
        for (var pattern : selected) inputs(pattern).forEach((key, input) ->
            demand.merge(key, input.multiply(firings.get(pattern.details())), PlannerAmount::add));
        stockCredits.forEach((key, held) -> demand.computeIfPresent(key,
            (ignored, needed) -> needed.subtract(PlannerAmount.of(held)).max(PlannerAmount.ZERO)));
        Set<AEKey> useful = new LinkedHashSet<>(); useful.add(network.goal());
        Set<IPatternDetails> activated = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean progress;
        do {
            progress = false;
            for (CompiledPattern pattern : selected) {
                cancellation.checkpoint();
                if (activated.contains(pattern.details())) continue;
                boolean justified = useful.stream().anyMatch(key -> demand.getOrDefault(key, PlannerAmount.ZERO).signum() > 0
                    && network.producersOf(key).stream().anyMatch(view -> view.details() == pattern.details()));
                if (justified) {
                    activated.add(pattern.details()); inputs(pattern).keySet().forEach(useful::add); progress = true;
                }
            }
        } while (progress);
        if (activated.size() != selected.size()) return false;
        Map<AEKey, PlannerAmount> remaining = new LinkedHashMap<>(demand);
        for (CompiledPattern pattern : selected) {
            cancellation.checkpoint();
            AEKey activationKey = null; PlannerAmount allocation = null;
            for (AEKey key : useful) {
                if (network.producersOf(key).stream().noneMatch(view -> view.details() == pattern.details())) continue;
                PlannerAmount output = outputs(pattern).getOrDefault(key, PlannerAmount.ZERO);
                if (output.signum() <= 0) continue;
                PlannerAmount needed = output.multiply(firings.get(pattern.details()).subtract(PlannerAmount.ONE)).add(1);
                if (remaining.getOrDefault(key, PlannerAmount.ZERO).compareTo(needed) >= 0
                        && (allocation == null || needed.compareTo(allocation) < 0)) {
                    activationKey = key; allocation = needed;
                }
            }
            if (activationKey == null) return false;
            remaining.put(activationKey, remaining.get(activationKey).subtract(allocation));
        }
        // Consumed-output advertised support was checked by orderedProposal. Hidden coproduct feedback
        // stays on the existing component path, whose input-slot attribution is more expressive.
        return true;
    }

    private static boolean safe(CompiledPattern pattern, boolean allowOrdered) {
        return pattern.fastSupported() && (pattern.unsupportedReason() == null || pattern.unsupportedReason().isEmpty())
                && pattern.semantics().completeForStaticPlanning() && !pattern.specialAnalysis().special()
                && pattern.semantics().returnedOutputs().isEmpty()
                && (allowOrdered || pattern.semantics().feedbackEdges().isEmpty())
                && pattern.inputs().stream().allMatch(input -> input.fastSupported() && !input.ignoresComponents()
                    && input.remainderKey() == null && input.amountPerPattern().signum() > 0 && input.amountPerPattern().fitsLong())
                && !pattern.outputs().isEmpty()
                && pattern.outputs().stream().allMatch(output -> output != null && output.what() != null && output.amount() > 0);
    }

    /** AE2 may credit every output, but only an advertised producer view may activate physical firings. */
    private static boolean activatedFiringsCovered(CompiledNetwork network, SolveState state,
            List<CompiledPattern> patterns, ECOCancellation cancellation) throws InterruptedException {
        var provenance = state.executionProvenance();
        for (var task : state.patternTimes.entrySet()) {
            cancellation.checkpoint();
            Map<AEKey, PlannerAmount> allocated = new LinkedHashMap<>();
            for (var allocation : provenance.allocations()) {
                cancellation.checkpoint();
                if (allocation.source() instanceof MaterialSource.PatternOutput source && source.pattern() == task.getKey())
                    allocated.merge(allocation.material(), allocation.amount(), PlannerAmount::add);
            }
            CompiledPattern physical = patterns.stream().filter(p -> p.details() == task.getKey()).findFirst().orElseThrow();
            Map<AEKey, PlannerAmount> output = outputs(physical);
            PlannerAmount activations = PlannerAmount.ZERO;
            for (var view : network.producers().entrySet()) {
                if (view.getValue().stream().noneMatch(p -> p.details() == task.getKey())) continue;
                PlannerAmount supplied = allocated.getOrDefault(view.getKey(), PlannerAmount.ZERO);
                PlannerAmount perFiring = output.getOrDefault(view.getKey(), PlannerAmount.ZERO);
                if (supplied.signum() > 0 && perFiring.signum() > 0)
                    activations = activations.max(supplied.ceilDiv(perFiring));
            }
            if (task.getValue().compareTo(activations) > 0) return false;
        }
        return true;
    }

    private static Map<AEKey, PlannerAmount> inputs(CompiledPattern pattern) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        pattern.inputs().forEach(input -> result.merge(input.key(), input.amountPerPattern(), PlannerAmount::add));
        return result;
    }

    private static Map<AEKey, PlannerAmount> outputs(CompiledPattern pattern) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        pattern.outputs().forEach(output -> result.merge(output.what(), PlannerAmount.of(output.amount()), PlannerAmount::add));
        return result;
    }

    private static boolean dominates(CompiledNetwork network, CompiledPattern left, CompiledPattern right) {
        Map<AEKey, PlannerAmount> li = inputs(left), ri = inputs(right), lo = outputs(left), ro = outputs(right);
        // More output is not automatically better under AE's advertised activation contract:
        // fewer primary firings may then provide too little of an unadvertised coproduct.
        return lo.equals(ro)
                && network.producers().values().stream().allMatch(views ->
                    views.stream().noneMatch(view -> view.details() == right.details())
                    || views.stream().anyMatch(view -> view.details() == left.details()))
                && li.entrySet().stream().allMatch(e -> e.getValue().compareTo(ri.getOrDefault(e.getKey(), PlannerAmount.ZERO)) <= 0);
    }

    private static SolveState replay(CompiledNetwork network, PlannerInventorySnapshot inventory, long amount,
            List<CompiledPattern> patterns, PlannerAmount[] counts, ECOCancellation cancellation) throws InterruptedException {
        SolveState state = new SolveState(inventory);
        state.stored.set(network.goal(), PlannerAmount.ZERO);
        boolean[] completed = new boolean[patterns.size()];
        int remaining = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i].signum() < 0) return null;
            completed[i] = counts[i].isZero();
            if (!completed[i]) remaining++;
        }
        while (remaining > 0) {
            boolean progress = false;
            for (int p = 0; p < patterns.size(); p++) {
                cancellation.checkpoint();
                if (completed[p]) continue;
                CompiledPattern pattern = patterns.get(p);
                boolean ready = true;
                for (var input : inputs(pattern).entrySet()) {
                    PlannerAmount needed = input.getValue().multiply(counts[p]);
                    if (state.stored.isUnbounded(input.getKey()) || network.emittable().contains(input.getKey())
                            && !input.getKey().equals(network.goal())) continue;
                    if (state.stored.get(input.getKey()).add(state.craftedAmount(input.getKey())).compareTo(needed) < 0) {
                        ready = false; break;
                    }
                }
                if (!ready) continue;
                for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                    var input = pattern.inputs().get(slot);
                    MaterialDemand demand = MaterialDemand.input(pattern.details(), slot, input.key(), input.amountPerPattern().multiply(counts[p]));
                    state.provenance.register(demand);
                    state.demand.merge(demand.key(), demand.amount(), PlannerAmount::add);
                    PlannerAmount stored = state.stored.available(demand.key(), demand.amount());
                    if (stored.signum() > 0) {
                        state.stored.remove(demand.key(), stored);
                        state.used.add(demand.key(), stored);
                        state.provenance.allocate(demand, demand.key(), MaterialSource.Stock.INSTANCE, stored);
                    }
                    PlannerAmount crafted = state.craftedAmount(demand.key()).min(state.provenance.remaining(demand));
                    if (crafted.signum() > 0) state.consumeCrafted(demand, demand.key(), crafted);
                    PlannerAmount emitted = state.provenance.remaining(demand);
                    if (emitted.signum() > 0) {
                        if (demand.key().equals(network.goal()) || !network.emittable().contains(demand.key())) return null;
                        state.emitted.add(demand.key(), emitted);
                        state.provenance.allocate(demand, demand.key(), MaterialSource.Emitted.INSTANCE, emitted);
                    }
                }
                PlannerAmount times = counts[p];
                outputs(pattern).forEach((key, output) -> state.creditCrafted(key, pattern.details(), output.multiply(times)));
                state.patternTimes.put(pattern.details(), counts[p]);
                completed[p] = true;
                remaining--;
                progress = true;
            }
            // A balance vector needing repeated seed circulation belongs to the existing cycle solver.
            if (!progress) return null;
        }
        MaterialDemand goal = MaterialDemand.goal(network.goal(), PlannerAmount.of(amount));
        if (state.craftedAmount(goal.key()).compareTo(goal.amount()) < 0) return null;
        state.provenance.register(goal);
        state.consumeCrafted(goal, goal.key(), goal.amount());
        state.demand.merge(goal.key(), goal.amount(), PlannerAmount::add);
        state.bytes = PlannerAmount.of(state.demand.size()).multiply(8L).add(cost(state));
        state.demand.forEach((key, count) -> state.bytes = state.bytes.add(PlannerAmount.stackBytes(count, key.getAmountPerByte())));
        return state;
    }

    private static PlannerAmount cost(SolveState state) {
        return state.plannerPatternTimes().values().stream().reduce(PlannerAmount.ZERO, PlannerAmount::add);
    }
}
