package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Chooses which firing vector of one cyclic component is handed to the external supply check: the solved vector first,
 * then, when its boundary supply fails, other vectors found by capping the boundary imports that the external planner
 * proved unsuppliable. Seed retention is part of the request targets (see {@code SeedFloors}), not a re-solve here.
 */
final class CycleCandidateSearch {
    private static final int MAX_SUPPLY_ROUNDS = 8;
    private static final int MAX_CAP_PROBES = 16;
    private static final long SLICE_WORK = 1_000_000L;
    private static final long SLICE_NANOS = 3_000_000_000L;

    record Candidate(CycleSolveResult result, Map<AEKey, PlannerAmount> targets) {}

    /** external.solved() means accepted; otherwise this is the first candidate's failure and complete says every alternative branch was closed by proof. */
    record Supplied(CycleSolveResult result, Map<AEKey, PlannerAmount> targets, Map<AEKey, Long> initialReservations,
            Map<AEKey, Long> additionalReservations, ExternalDemandPlanner.Outcome external,
            Map<AEKey, PlannerAmount> caps, boolean searched, boolean complete) {}

    private final CompiledNetwork network;
    private final CondensationGraph condensation;
    private final Map<AEKey, Integer> choices;
    private final CycleComponent cycle;
    private final Map<AEKey, PlannerAmount> exactRequiredOutputs;
    private final Map<AEKey, Long> stock;
    private final Map<AEKey, Long> solveStock;
    private final Map<AEKey, PlannerAmount> stockReservations;
    private final Map<AEKey, Long> plannedCycleInputs;
    private final KeyCounter inventory;
    private final SolveState base;
    private final boolean ignorePatternSubstitutions;
    private final CycleFailureCache failures;
    private final ExternalDemandPlanner externalDemandPlanner;
    private final ECOCancellation cancellation;
    private final Deque<Map<AEKey, PlannerAmount>> pending = new ArrayDeque<>();
    private final Map<AEKey, PlannerAmount> knownCaps = new LinkedHashMap<>();
    private boolean complete;

    CycleCandidateSearch(CompiledNetwork network, CondensationGraph condensation, Map<AEKey, Integer> choices,
            CycleComponent cycle, Map<AEKey, PlannerAmount> exactRequiredOutputs, Map<AEKey, Long> stock,
            Map<AEKey, Long> solveStock, Map<AEKey, PlannerAmount> stockReservations, Map<AEKey, Long> plannedCycleInputs,
            KeyCounter inventory, SolveState base, boolean ignorePatternSubstitutions, CycleFailureCache failures,
            ExternalDemandPlanner externalDemandPlanner, ECOCancellation cancellation) {
        this.network = network;
        this.condensation = condensation;
        this.choices = choices;
        this.cycle = cycle;
        this.exactRequiredOutputs = exactRequiredOutputs;
        this.stock = stock;
        this.solveStock = solveStock;
        this.stockReservations = stockReservations;
        this.plannedCycleInputs = plannedCycleInputs;
        this.inventory = inventory;
        this.base = base;
        this.ignorePatternSubstitutions = ignorePatternSubstitutions;
        this.failures = failures;
        this.externalDemandPlanner = externalDemandPlanner;
        this.cancellation = cancellation;
    }

    /** Checks the boundary supply of a candidate; on a supply failure, searches other firing vectors by import caps. */
    Supplied supply(Candidate candidate) throws InterruptedException {
        Supplied root = attempt(candidate.result(), candidate.targets(), Map.of(), cancellation);
        if (root.external().solved() || !closable(root.external().status())) return root;
        ECOPlanningBudget slice = slice();
        Set<Map<AEKey, PlannerAmount>> visited = new HashSet<>();
        visited.add(Map.of());
        pending.clear();
        knownCaps.clear();
        complete = true;
        int rounds = 0;
        try {
            derive(root, Map.of(), slice);
            while (!pending.isEmpty()) {
                if (rounds >= MAX_SUPPLY_ROUNDS) {
                    complete = false;
                    break;
                }
                Map<AEKey, PlannerAmount> caps = new LinkedHashMap<>(pending.pop());
                knownCaps.forEach((key, cap) -> caps.merge(key, cap, PlannerAmount::min));
                if (!visited.add(caps)) continue;
                rounds++;
                // Only the verdict of a capped probe is read here, so a dominated earlier proof may answer it.
                CycleSolveResult result = failures.solveVerdict(request(candidate.targets()).withBoundaryImportCaps(caps), slice);
                if (result.status() != CycleSolveStatus.SUCCESS) {
                    if (!result.status().proven()) complete = false;
                    continue;
                }
                Supplied current = attempt(result, candidate.targets(), caps, slice);
                if (current.external().solved()) {
                    return new Supplied(current.result(), current.targets(), current.initialReservations(),
                            current.additionalReservations(), current.external(), caps, true, true);
                }
                if (!closable(current.external().status())) {
                    complete = false;
                    continue;
                }
                derive(current, caps, slice);
            }
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            cancellation.checkpoint();
            complete = false;
        }
        return new Supplied(root.result(), root.targets(), root.initialReservations(), root.additionalReservations(),
                root.external(), root.caps(), true, complete);
    }

    private Supplied attempt(CycleSolveResult result, Map<AEKey, PlannerAmount> targets,
            Map<AEKey, PlannerAmount> caps, ECOCancellation cancel) throws InterruptedException {
        Map<AEKey, Long> initial = ComponentPlanner.cycleInitialReservations(exactRequiredOutputs, stock, result);
        Map<AEKey, Long> additional = ComponentPlanner.reservationRemainder(initial, stockReservations);
        Map<AEKey, Long> demands = ComponentPlanner.mergeDemands(result.positiveExternalDemand(), plannedCycleInputs);
        Set<AEKey> delegated = ComponentPlanner.delegatedCycleInputs(network, condensation, choices, cycle,
                demands.keySet());
        var external = externalDemandPlanner.solveDemands(network, cycle, demands, inventory, base, additional,
                delegated, ignorePatternSubstitutions, cancel);
        return new Supplied(result, targets, initial, additional, external, caps, false, false);
    }

    /** Turns one failed vector into tighter global caps, or into one child branch per failing boundary key. */
    private void derive(Supplied failed, Map<AEKey, PlannerAmount> branch, ECOCancellation cancel)
            throws InterruptedException {
        Map<AEKey, Long> demands = ComponentPlanner.mergeDemands(failed.result().positiveExternalDemand(),
                plannedCycleInputs);
        Set<AEKey> delegated = ComponentPlanner.delegatedCycleInputs(network, condensation, choices, cycle,
                demands.keySet());
        Map<AEKey, Long> failing = new LinkedHashMap<>();
        failed.result().positiveExternalDemand().forEach((key, amount) -> {
            if (delegated.contains(key) || plannedCycleInputs.containsKey(key)) return;
            // Uncapped demand includes all boundary fuel; a capped witness splits it into owned stock and
            // extra imports. Probe both portions together before subtracting owned stock from the cap.
            long owned = Math.min(solveStock.getOrDefault(key, 0L),
                    failed.result().requiredSeed().getOrDefault(key, 0L));
            PlannerAmount total = PlannerAmount.of(amount).add(owned);
            if (total.fitsLong()) failing.put(key, total.longValueExact());
            else complete = false;
        });
        if (failing.isEmpty()) {
            complete = false;
            return;
        }
        boolean tightened = false;
        for (var entry : failing.entrySet()) {
            AEKey key = entry.getKey();
            long demand = entry.getValue();
            var single = probe(Map.of(key, demand), cancel);
            if (single.solved()) continue;
            if (!closable(single.status())) {
                complete = false;
                continue;
            }
            long lo = 0L;
            long hi = demand;
            int probes = 0;
            while (hi - lo > 1 && probes++ < MAX_CAP_PROBES) {
                long mid = lo + (hi - lo) / 2;
                var half = probe(Map.of(key, mid), cancel);
                if (half.solved()) lo = mid;
                else if (closable(half.status())) hi = mid;
                else {
                    complete = false;
                    break;
                }
            }
            knownCaps.merge(key, importLimit(key, hi - 1), PlannerAmount::min);
            tightened = true;
        }
        if (tightened) {
            pending.push(branch);
            return;
        }
        if (probe(failing, cancel).solved()) {
            complete = false;
            return;
        }
        var keys = failing.keySet().toArray(new AEKey[0]);
        for (int i = keys.length - 1; i >= 0; i--) {
            Map<AEKey, PlannerAmount> child = new LinkedHashMap<>(branch);
            child.merge(keys[i], importLimit(keys[i], failing.get(keys[i]) - 1), PlannerAmount::min);
            pending.push(child);
        }
    }

    /** CycleSolveRequest caps only imports beyond the stock already present in its marking. */
    private PlannerAmount importLimit(AEKey key, long totalSupply) {
        return PlannerAmount.of(totalSupply).subtract(PlannerAmount.of(solveStock.getOrDefault(key, 0L)))
                .max(PlannerAmount.ZERO);
    }

    /** The external verdict for these demands alone: no cycle-owned reservations and no delegated suppliers. */
    private ExternalDemandPlanner.Outcome probe(Map<AEKey, Long> demands, ECOCancellation cancel)
            throws InterruptedException {
        return externalDemandPlanner.solveDemands(network, cycle, demands, inventory, base, Map.of(), Set.of(),
                ignorePatternSubstitutions, cancel);
    }

    private static boolean closable(CycleExternalDemandStatus status) {
        return status == CycleExternalDemandStatus.MISSING || status == CycleExternalDemandStatus.FORBIDDEN_ROUTE;
    }

    private PlannerAmount demand(AEKey key) {
        return exactRequiredOutputs.getOrDefault(key, PlannerAmount.ZERO);
    }

    private CycleSolveRequest request(Map<AEKey, PlannerAmount> targets) {
        return new CycleSolveRequest(cycle, ComponentPlanner.representable(targets), targets, solveStock,
                cycle.outgoingDependencies(), ComponentPlanner.cycleSolveOptions(cycle));
    }

    private ECOPlanningBudget slice() {
        return cancellation instanceof ECOPlanningBudget budget ? budget.slice(SLICE_WORK, SLICE_NANOS)
                : new ECOPlanningBudget(cancellation, SLICE_WORK, SLICE_NANOS, System::nanoTime);
    }
}
