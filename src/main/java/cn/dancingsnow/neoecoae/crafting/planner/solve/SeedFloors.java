package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
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
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleStateEquation;
import com.google.common.collect.MapMaker;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Strict seed retention for self-growing cycles.
 *
 * <p>A cycle depends on its seed when every producer of every member consumes a member: once its member stock is
 * gone it cannot restart. If such a cycle can also grow (some firing vector nets more member material than it
 * consumes), the order must leave its minimal startup seed in the network so later orders can grow again:
 * <ul>
 * <li>stock at least {@code seed + demand}: the demand is served from stock and the seed stays;</li>
 * <li>otherwise the cycle grows first: it may borrow the seed as working stock but must return it;</li>
 * <li>if growth is impossible (missing fuel, or no complete seed), the order reports the missing material instead
 * of consuming the seed.</li>
 * </ul>
 * The floor is reserved from the stock seen by the DAG pass, joint routes and material proofs. A planned cycle
 * component gets the reserved copies back as borrowable stock and the full minimum as an additional output.
 *
 * <p>The minimum is the startup requirement of the cycle solver's own lap from that member alone, computed once
 * per compiled structure. The member kept is the one the stock can restart most often (the form the player holds).
 */
final class SeedFloors {
    record Floor(AEKey key, PlannerAmount minimum, PlannerAmount reserved, Set<AEKey> members) {}

    static final SeedFloors NONE = new SeedFloors(List.of(), List.of());

    /** Extra stock above the component's exact per-firing requirements for a startup probe. */
    private static final long PROBE_STOCK = 1L << 32;
    private static final long PROBE_WORK = 200_000L;
    private static final long PROBE_NANOS = 500_000_000L;
    private static final PlannerAmount UNKNOWN = PlannerAmount.ZERO;
    /** The probe ran out of its allowance; not cached, so a later order measures again. */
    private static final PlannerAmount BUDGET = PlannerAmount.of(-1L);
    /** Structure-only results: whether a component needs a seed, and each member's minimal startup seed. */
    private static final ConcurrentMap<CompiledNetwork, ConcurrentMap<Set<AEKey>, Profile>> PROFILES =
        new MapMaker().weakKeys().makeMap();

    private final List<Floor> floors;
    /** Stocked self-growing cycles whose seed could not be measured within the probe allowance. */
    private final List<Set<AEKey>> undetermined;

    private SeedFloors(List<Floor> floors, List<Set<AEKey>> undetermined) {
        this.floors = List.copyOf(floors);
        this.undetermined = List.copyOf(undetermined);
    }

    List<Floor> floors() { return floors; }

    List<Set<AEKey>> undetermined() { return undetermined; }

    boolean isEmpty() { return floors.isEmpty(); }

    /** Floors whose kept member belongs to this planned cycle component. */
    List<Floor> within(CycleComponent component) {
        if (floors.isEmpty()) return List.of();
        List<Floor> result = new ArrayList<>();
        for (Floor floor : floors) if (component.members().contains(floor.key())) result.add(floor);
        return result;
    }

    Map<AEKey, PlannerAmount> reservations() {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        for (Floor floor : floors) if (floor.reserved().signum() > 0) result.merge(floor.key(), floor.reserved(),
            PlannerAmount::add);
        return result;
    }

    /** Seed that is not yet in stock and must be grown by the order on top of the reserved copies. */
    Map<AEKey, PlannerAmount> shortfallTargets() {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        for (Floor floor : floors) {
            PlannerAmount missing = floor.minimum().subtract(floor.reserved());
            if (missing.signum() > 0) result.merge(floor.key(), missing, PlannerAmount::add);
        }
        return result;
    }

    KeyCounter reduce(KeyCounter inventory) {
        return reduce(PlannerInventorySnapshot.of(inventory)).toKeyCounter();
    }

    PlannerInventorySnapshot reduce(PlannerInventorySnapshot snapshot) {
        return floors.isEmpty() ? snapshot : snapshot.withoutReserved(reservations());
    }

    /** Make an unmeasured seed visible on the published plan instead of silently consuming it. */
    void report(cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace trace) {
        for (Set<AEKey> members : undetermined) {
            trace.addDiagnostic(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic(
                cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED,
                "Startup seed of self-growing cycle " + members + " could not be determined within the probe"
                    + " allowance; this order may consume it"));
        }
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (Floor floor : floors) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(floor.key()).append("=").append(floor.minimum()).append(" (reserved ").append(floor.reserved())
                .append(')');
        }
        return sb.append('}').toString();
    }

    static SeedFloors compute(CompiledNetwork network, List<CycleComponent> components,
            PlannerInventorySnapshot snapshot, CycleSolver solver, ECOCancellation cancellation)
            throws InterruptedException {
        if (components.isEmpty()) return NONE;
        SolveState stock = new SolveState(snapshot);
        List<Floor> floors = new ArrayList<>();
        List<Set<AEKey>> undetermined = new ArrayList<>();
        var profiles = PROFILES.computeIfAbsent(network, ignored -> new ConcurrentHashMap<>());
        for (CycleComponent component : components) {
            cancellation.checkpoint();
            Set<AEKey> members = new HashSet<>(component.members());
            // An unbounded member can always restart the cycle; nothing needs protecting.
            if (members.stream().anyMatch(snapshot::isUnbounded)) continue;
            boolean stocked = false;
            for (AEKey member : members) {
                if (!member.equals(network.goal()) && stock.stored.get(member).signum() > 0) stocked = true;
            }
            if (!stocked) continue;
            Profile profile = profiles.get(members);
            if (profile == null) {
                Boolean seeded = needsSeedToGrow(network, component, members, cancellation);
                // Undecided within the allowance: the probe below only floors a cycle that actually gains. Nothing is
                // cached, so a later order decides again instead of inheriting "needs no seed".
                profile = new Profile(seeded == null || seeded);
                if (seeded != null) {
                    Profile raced = profiles.putIfAbsent(members, profile);
                    if (raced != null) profile = raced;
                }
            }
            if (!profile.seeded()) continue;
            Floor best = null;
            boolean unknown = false;
            for (AEKey member : component.members()) {
                // AE2 crafts the requested amount anew, so a cycle never consumes the goal's own stored copies.
                if (member.equals(network.goal())) continue;
                if (stock.stored.isUnbounded(member)) continue;
                PlannerAmount held = stock.stored.get(member);
                if (held.signum() <= 0) continue;
                PlannerAmount seed = profile.minimum(member, network, component, solver, cancellation);
                if (seed.equals(BUDGET)) unknown = true;
                if (seed.signum() <= 0) continue;
                Floor candidate = new Floor(member, seed, held.min(seed), Set.copyOf(members));
                // Keep the form the stock can restart most often: compare held/minimum without division.
                if (best == null || held.multiply(best.minimum()).compareTo(
                        stock.stored.get(best.key()).multiply(seed)) > 0) {
                    best = candidate;
                }
            }
            if (best != null) floors.add(best);
            else if (unknown) undetermined.add(Set.copyOf(members));
        }
        return floors.isEmpty() && undetermined.isEmpty() ? NONE : new SeedFloors(floors, undetermined);
    }

    /**
     * Every producer of every member consumes a member (no outside bootstrap), and an exact material balance lets
     * some member grow from nothing but the cycle's own firings and boundary imports. {@code null} when the balance
     * could not be decided within the probe allowance.
     */
    private static Boolean needsSeedToGrow(CompiledNetwork network, CycleComponent component, Set<AEKey> members,
            ECOCancellation cancellation) throws InterruptedException {
        for (AEKey member : members) {
            for (CompiledPattern producer : network.producersOf(member)) {
                cancellation.checkpoint();
                if (producer.inputs().stream().noneMatch(input -> members.contains(input.key()))) return false;
            }
        }
        Map<AEKey, Integer> index = new LinkedHashMap<>();
        for (AEKey member : component.members()) index.putIfAbsent(member, index.size());
        List<Map<Integer, PlannerAmount>> columns = new ArrayList<>();
        ReferenceSet<IPatternDetails> seen = new ReferenceOpenHashSet<>();
        for (CompiledPattern pattern : component.patterns()) {
            cancellation.checkpoint();
            if (!seen.add(pattern.details())) continue;
            Map<Integer, PlannerAmount> column = new LinkedHashMap<>();
            for (CompiledInput input : pattern.inputs()) {
                Integer slot = index.get(input.key());
                if (slot == null || input.reusable()) continue;
                column.merge(slot, input.amountPerPattern(), PlannerAmount::add);
            }
            for (GenericStack output : pattern.grossOutputs()) {
                Integer slot = index.get(output.what());
                if (slot != null) column.merge(slot, PlannerAmount.of(output.amount()).negate(), PlannerAmount::add);
            }
            column.values().removeIf(value -> value.signum() == 0);
            columns.add(column);
        }
        if (columns.isEmpty()) return false;
        boolean[] imports = new boolean[index.size()];
        PlannerAmount[] initial = new PlannerAmount[index.size()];
        Arrays.fill(initial, PlannerAmount.ZERO);
        for (int member = 0; member < index.size(); member++) {
            PlannerAmount[] target = new PlannerAmount[index.size()];
            Arrays.fill(target, PlannerAmount.ZERO);
            target[member] = PlannerAmount.ONE;
            CycleStateEquation.Status status;
            try {
                status = CycleStateEquation.solveSparseAmounts(columns, imports, initial, target, true,
                    slice(cancellation)).status();
            } catch (ECOPlanningBudget.Exhausted exhausted) {
                // Only the probe's slice ran out; the session's own allowance decides whether planning goes on.
                cancellation.checkpoint();
                return null;
            }
            if (status == CycleStateEquation.Status.OPTIMAL || status == CycleStateEquation.Status.FEASIBLE) return true;
            if (status == CycleStateEquation.Status.UNKNOWN) return null;
        }
        return false;
    }

    private static ECOCancellation slice(ECOCancellation cancellation) {
        return cancellation instanceof ECOPlanningBudget budget ? budget.slice(PROBE_WORK, PROBE_NANOS)
            : new ECOPlanningBudget(cancellation, PROBE_WORK, PROBE_NANOS, System::nanoTime);
    }

    private static final class Profile {
        private final boolean seeded;
        private final Map<AEKey, PlannerAmount> minimums = new ConcurrentHashMap<>();

        Profile(boolean seeded) { this.seeded = seeded; }

        boolean seeded() { return seeded; }

        /** Startup stock of {@code member} that the solver's lap needs to gain one more copy from that member alone. */
        PlannerAmount minimum(AEKey member, CompiledNetwork network, CycleComponent component, CycleSolver solver,
                ECOCancellation cancellation) throws InterruptedException {
            PlannerAmount known = minimums.get(member);
            if (known != null) return known;
            CycleSolveResult probe;
            PlannerAmount probeStock = PlannerAmount.of(PROBE_STOCK);
            for (CompiledPattern pattern : component.patterns()) {
                for (CompiledInput input : pattern.inputs()) {
                    if (input.key().equals(member)) probeStock = probeStock.add(input.amountPerPattern());
                }
            }
            try {
                probe = solver.solve(new CycleSolveRequest(component, Map.of(member, probeStock.add(1L)),
                    Map.of(member, probeStock), Set.of(), component.outgoingDependencies(),
                    new CycleSolveRequest.PlannerOptions(), Map.of()),
                    slice(cancellation));
            } catch (ECOPlanningBudget.Exhausted exhausted) {
                // Unknown within the probe allowance; retried by a later order instead of cached as "no seed".
                cancellation.checkpoint();
                return BUDGET;
            }
            PlannerAmount minimum = UNKNOWN;
            if (probe.status() == CycleSolveStatus.SUCCESS) {
                PlannerAmount taken = probe.exactRequiredSeed().getOrDefault(member, PlannerAmount.ZERO);
                if (taken.signum() > 0 && taken.compareTo(probeStock) < 0) minimum = taken;
            }
            minimums.put(member, minimum);
            return minimum;
        }
    }
}
