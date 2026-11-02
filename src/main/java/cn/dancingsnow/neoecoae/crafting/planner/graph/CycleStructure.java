package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Structural reading of a strongly connected component: which internal edges move material and how one lap
 * scales it. Amount-free and stock-free; shared by route selection and diagnostics.
 *
 * <p>{@link PatternDependencyEdges} emits three edge shapes. A consumption edge links a product view to one of
 * the pattern's real inputs. A feedback edge links a returned container or reusable item to the output that
 * depends on it. An ordering edge links a byproduct to its pattern's primary output so that a byproduct consumer
 * is scheduled after the pattern. Materially, such a byproduct comes from the inputs that the same firing
 * consumes, so an ordering edge {@code B -> A} of pattern P stands for {@code B -> I} for each input I of P.
 * Two ordering edges of different patterns chained together therefore carry nothing: a byproduct is not made
 * from another pattern's byproduct. A ring made only of such chains (A's pattern also yields B, B's pattern also
 * yields A) is a scheduling artefact, not a recipe loop: it needs no seed and no cycle solver.
 */
public final class CycleStructure {
    public enum EdgeKind { CONSUMPTION, FEEDBACK, ORDERING }

    /**
     * RECIPE: loop through primary outputs. BYPRODUCT_VIEW: the loop needs a byproduct of some pattern.
     * ORDER_ONLY: cycles exist only through chained byproduct-ordering edges; no material circulates.
     */
    public enum Kind { RECIPE, BYPRODUCT_VIEW, ORDER_ONLY }

    /** Sampled multiplier of member material per lap; NO_MATERIAL_LOOP for {@link Kind#ORDER_ONLY}. */
    public enum Growth { GROWTH, CONSERVATIVE, LOSSY, NO_MATERIAL_LOOP, UNKNOWN }

    public record Gain(BigInteger numerator, BigInteger denominator) {
        public int compareToOne() {
            return numerator.compareTo(denominator);
        }

        public boolean greaterThan(Gain other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)) > 0;
        }

        @Override public String toString() {
            return "x" + numerator + (denominator.equals(BigInteger.ONE) ? "" : "/" + denominator);
        }
    }

    /** Edges in dependency direction (product to input); material flows the opposite way. */
    public record Loop(List<CraftingGraphEdge> edges, @Nullable Gain gain) {}

    public record Classification(Kind kind, Growth growth, @Nullable Gain maxGain, List<Loop> loops) {}

    private static final int MAX_LOOP_LENGTH = 12;
    private static final int MAX_LOOP_STEPS = 20_000;

    private CycleStructure() {}

    public static EdgeKind kind(CraftingGraphEdge edge) {
        CompiledPattern pattern = edge.pattern();
        if (edge.input() != null && edge.requiredInput().equals(edge.input().key())
                && pattern.inputs().contains(edge.input())) {
            return EdgeKind.CONSUMPTION;
        }
        if (edge.input() != null && edge.input().source() == null && !pattern.inputs().contains(edge.input())
                && outputs(pattern, edge.producer()) && outputs(pattern, edge.requiredInput())) {
            return EdgeKind.ORDERING;
        }
        return EdgeKind.FEEDBACK;
    }

    /** The edge's product is the pattern's primary output rather than one of its byproducts. */
    public static boolean primaryView(CraftingGraphEdge edge) {
        GenericStack primary;
        try {
            primary = edge.pattern().details() == null ? null : edge.pattern().details().getPrimaryOutput();
        } catch (RuntimeException e) {
            return true;
        }
        if (primary == null || primary.what() == null) return true;
        AEKey key = edge.producer();
        return primary.what().equals(key)
            || primary.what() instanceof AEItemKey a && key instanceof AEItemKey b && a.getItem() == b.getItem();
    }

    /**
     * Material reading of the component's internal edges: consumption and feedback edges as they are, each
     * ordering edge replaced by edges from its byproduct to the same pattern's inputs inside the component.
     */
    public static List<CraftingGraphEdge> materialEdges(Collection<AEKey> members, List<CraftingGraphEdge> edges) {
        Set<AEKey> memberSet = members instanceof Set<AEKey> set ? set : new LinkedHashSet<>(members);
        List<CraftingGraphEdge> material = new ArrayList<>();
        for (CraftingGraphEdge edge : edges) {
            if (!memberSet.contains(edge.producer()) || !memberSet.contains(edge.requiredInput())) continue;
            if (kind(edge) != EdgeKind.ORDERING) {
                material.add(edge);
                continue;
            }
            for (CompiledInput input : edge.pattern().inputs()) {
                if (!memberSet.contains(input.key())) continue;
                if (edge.pattern().specialAnalysis().excludesFromCycleGraph(input)) continue;
                material.add(new CraftingGraphEdge(edge.producer(), input.key(), edge.pattern(), input));
            }
        }
        return material;
    }

    /** True when the edges restricted to {@code members} contain a directed cycle (self-loops included). */
    public static boolean hasCycle(Collection<AEKey> members, List<CraftingGraphEdge> edges) {
        return !cores(members, edges).cyclic().isEmpty();
    }

    /** True when material circulates inside the component, i.e. not every cycle is an ordering artefact. */
    public static boolean materialLoop(Collection<AEKey> members, List<CraftingGraphEdge> edges) {
        return hasCycle(members, materialEdges(members, edges));
    }

    /**
     * Ordering edges that only schedule a byproduct after its pattern without closing a material loop: none of
     * their material expansions stays inside one strongly connected material core. Removing them cannot break a
     * real recipe loop, seed or catalyst feedback; it only removes rings of chained byproduct ordering.
     */
    public static Set<CraftingGraphEdge> redundantOrderingEdges(Collection<AEKey> members,
            List<CraftingGraphEdge> edges) {
        Set<AEKey> memberSet = new LinkedHashSet<>(members);
        List<CraftingGraphEdge> ordering = new ArrayList<>();
        for (CraftingGraphEdge edge : edges) {
            if (memberSet.contains(edge.producer()) && memberSet.contains(edge.requiredInput())
                    && kind(edge) == EdgeKind.ORDERING) {
                ordering.add(edge);
            }
        }
        if (ordering.isEmpty()) return Set.of();
        Map<AEKey, Integer> core = cores(memberSet, materialEdges(memberSet, edges)).core();
        Set<CraftingGraphEdge> redundant = new LinkedHashSet<>();
        for (CraftingGraphEdge edge : ordering) {
            boolean closesLoop = false;
            for (CompiledInput input : edge.pattern().inputs()) {
                if (!memberSet.contains(input.key()) || edge.pattern().specialAnalysis().excludesFromCycleGraph(input))
                    continue;
                if (core.get(input.key()).equals(core.get(edge.producer()))) {
                    closesLoop = true;
                    break;
                }
            }
            if (!closesLoop) redundant.add(edge);
        }
        return redundant;
    }

    public static Classification classify(List<AEKey> members, List<CraftingGraphEdge> edges, int maxLoops) {
        Set<AEKey> memberSet = new LinkedHashSet<>(members);
        List<CraftingGraphEdge> internal = edges.stream()
            .filter(edge -> memberSet.contains(edge.producer()) && memberSet.contains(edge.requiredInput())).toList();
        List<CraftingGraphEdge> material = materialEdges(memberSet, internal);
        boolean loop = hasCycle(memberSet, material);
        Kind kind = !loop ? Kind.ORDER_ONLY
            : hasCycle(memberSet, material.stream().filter(edge -> kind(edge) == EdgeKind.FEEDBACK
                || primaryView(edge)).toList()) ? Kind.RECIPE : Kind.BYPRODUCT_VIEW;
        List<Loop> loops = new ArrayList<>();
        Gain best = null;
        for (List<CraftingGraphEdge> sampled : loops(members, loop ? material : internal, maxLoops)) {
            Gain gain = gain(sampled);
            loops.add(new Loop(sampled, gain));
            if (gain != null && (best == null || gain.greaterThan(best))) best = gain;
        }
        Growth growth = !loop ? Growth.NO_MATERIAL_LOOP : best == null ? Growth.UNKNOWN
            : best.compareToOne() > 0 ? Growth.GROWTH : best.compareToOne() == 0 ? Growth.CONSERVATIVE : Growth.LOSSY;
        return new Classification(kind, growth, best, List.copyOf(loops));
    }

    /** Material multiplier around a loop of consumption edges; null when an edge carries no amount ratio. */
    public static @Nullable Gain gain(List<CraftingGraphEdge> loop) {
        BigInteger numerator = BigInteger.ONE, denominator = BigInteger.ONE;
        for (CraftingGraphEdge edge : loop) {
            if (kind(edge) != EdgeKind.CONSUMPTION) return null;
            BigInteger in = edge.input().amountPerPattern().toBigInteger();
            BigInteger out = outputAmount(edge.pattern(), edge.producer());
            if (in.signum() <= 0 || out.signum() <= 0) return null;
            numerator = numerator.multiply(out);
            denominator = denominator.multiply(in);
        }
        BigInteger gcd = numerator.gcd(denominator);
        return new Gain(numerator.divide(gcd), denominator.divide(gcd));
    }

    /** A few distinct elementary loops, each starting at its earliest member so rotations are not repeated. */
    public static List<List<CraftingGraphEdge>> loops(List<AEKey> members, List<CraftingGraphEdge> edges,
            int maxLoops) {
        Map<AEKey, Integer> order = new HashMap<>();
        for (AEKey member : members) order.putIfAbsent(member, order.size());
        Map<AEKey, List<CraftingGraphEdge>> outgoing = new HashMap<>();
        for (CraftingGraphEdge edge : edges) {
            if (order.containsKey(edge.producer()) && order.containsKey(edge.requiredInput())) {
                outgoing.computeIfAbsent(edge.producer(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        var found = new ArrayList<List<CraftingGraphEdge>>();
        var shapes = new HashSet<List<AEKey>>();
        int[] steps = {MAX_LOOP_STEPS};
        for (AEKey start : order.keySet()) {
            if (found.size() >= maxLoops || steps[0] <= 0) break;
            var onPath = new HashSet<AEKey>();
            onPath.add(start);
            search(start, start, order.get(start), new ArrayList<>(), onPath, outgoing, order, found, shapes, steps,
                maxLoops);
        }
        return found;
    }

    private static void search(AEKey start, AEKey at, int startIndex, List<CraftingGraphEdge> path, Set<AEKey> onPath,
            Map<AEKey, List<CraftingGraphEdge>> outgoing, Map<AEKey, Integer> order,
            List<List<CraftingGraphEdge>> found, Set<List<AEKey>> shapes, int[] steps, int maxLoops) {
        if (path.size() >= MAX_LOOP_LENGTH) return;
        for (CraftingGraphEdge edge : outgoing.getOrDefault(at, List.of())) {
            if (found.size() >= maxLoops || --steps[0] <= 0) return;
            AEKey next = edge.requiredInput();
            Integer nextIndex = order.get(next);
            if (nextIndex == null || nextIndex < startIndex) continue;
            path.add(edge);
            if (next.equals(start)) {
                List<AEKey> shape = path.stream().map(CraftingGraphEdge::producer).toList();
                if (shapes.add(shape)) found.add(List.copyOf(path));
            } else if (onPath.add(next)) {
                search(start, next, startIndex, path, onPath, outgoing, order, found, shapes, steps, maxLoops);
                onPath.remove(next);
            }
            path.removeLast();
        }
    }

    private record Cores(Map<AEKey, Integer> core, List<SccComponent> cyclic) {}

    /** Reuses the planner's Tarjan analysis on a member-restricted edge set. */
    private static Cores cores(Collection<AEKey> members, List<CraftingGraphEdge> edges) {
        Set<AEKey> memberSet = members instanceof Set<AEKey> set ? set : new LinkedHashSet<>(members);
        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        for (AEKey member : memberSet) nodes.put(member, new CraftingGraphNode(member, List.of()));
        List<CraftingGraphEdge> internal = new ArrayList<>();
        for (CraftingGraphEdge edge : edges) {
            if (memberSet.contains(edge.producer()) && memberSet.contains(edge.requiredInput())) internal.add(edge);
        }
        Map<AEKey, Integer> core = new LinkedHashMap<>();
        List<SccComponent> cyclic = new ArrayList<>();
        if (memberSet.isEmpty()) return new Cores(core, cyclic);
        try {
            int id = 0;
            for (SccComponent component : new TarjanSccAnalyzer().analyze(
                    new CraftingDependencyGraph(memberSet.iterator().next(), nodes, internal), ECOCancellation.NONE)) {
                for (AEKey member : component.members()) core.put(member, id);
                if (component.cyclic()) cyclic.add(component);
                id++;
            }
            for (AEKey member : memberSet) core.putIfAbsent(member, id++);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Treat an interrupted analysis as one cyclic core so callers keep every edge.
            for (AEKey member : memberSet) core.put(member, 0);
            cyclic.add(new SccComponent(0, List.copyOf(memberSet), internal, true));
        }
        return new Cores(core, cyclic);
    }

    private static BigInteger outputAmount(CompiledPattern pattern, AEKey key) {
        BigInteger total = BigInteger.ZERO;
        for (GenericStack output : pattern.outputs()) {
            if (output != null && key.equals(output.what())) total = total.add(BigInteger.valueOf(output.amount()));
        }
        return total.signum() > 0 ? total : pattern.outputPerPattern().toBigInteger();
    }

    private static boolean outputs(CompiledPattern pattern, AEKey key) {
        for (GenericStack output : pattern.outputs()) {
            if (output != null && key.equals(output.what())) return true;
        }
        return false;
    }
}
