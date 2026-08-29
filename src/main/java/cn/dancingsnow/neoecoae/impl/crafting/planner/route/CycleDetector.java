package cn.dancingsnow.neoecoae.impl.crafting.planner.route;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleDiagnostic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Iterative WHITE/GRAY/BLACK DFS; it cannot overflow the Java stack on large recipe chains. */
public final class CycleDetector {
    private enum Color { GRAY, BLACK }
    private record Edge(AEKey to, CompiledPattern pattern) {}
    private record Parent(AEKey key, CompiledPattern pattern) {}
    private static final class Frame {
        final AEKey key;
        final List<Edge> edges;
        int next;
        Frame(AEKey key, List<Edge> edges) { this.key = key; this.edges = edges; }
    }
    public record Result(AcyclicRoutePlan route, List<CycleDiagnostic> cycles) {
        public boolean cyclic() { return !cycles.isEmpty(); }
    }

    public Result detect(CompiledNetwork network, Set<AEKey> reachable, ECOCancellation cancellation)
            throws InterruptedException {
        Map<AEKey, Color> colors = new HashMap<>();
        Map<AEKey, Parent> parents = new HashMap<>();
        List<AEKey> finished = new ArrayList<>();
        List<CycleDiagnostic> cycles = new ArrayList<>();
        Set<String> seenCycles = new LinkedHashSet<>();

        for (AEKey root : reachable) {
            if (colors.containsKey(root)) continue;
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            colors.put(root, Color.GRAY);
            stack.push(new Frame(root, edges(network, root)));
            while (!stack.isEmpty()) {
                cancellation.checkpoint();
                Frame frame = stack.peek();
                if (frame.next >= frame.edges.size()) {
                    stack.pop();
                    colors.put(frame.key, Color.BLACK);
                    finished.add(frame.key);
                    continue;
                }
                Edge edge = frame.edges.get(frame.next++);
                Color color = colors.get(edge.to);
                if (color == null) {
                    parents.put(edge.to, new Parent(frame.key, edge.pattern));
                    colors.put(edge.to, Color.GRAY);
                    stack.push(new Frame(edge.to, edges(network, edge.to)));
                } else if (color == Color.GRAY) {
                    CycleDiagnostic cycle = buildCycle(frame.key, edge, parents);
                    String signature = cycle.keys().stream().map(Object::toString).sorted().toList().toString();
                    if (seenCycles.add(signature)) cycles.add(cycle);
                }
            }
        }
        java.util.Collections.reverse(finished);
        return new Result(new AcyclicRoutePlan(finished), List.copyOf(cycles));
    }

    private static List<Edge> edges(CompiledNetwork network, AEKey key) {
        List<Edge> result = new ArrayList<>();
        for (CompiledPattern pattern : network.producersOf(key)) {
            for (CompiledInput input : pattern.inputs()) result.add(new Edge(input.key(), pattern));
        }
        return result;
    }

    private static CycleDiagnostic buildCycle(AEKey from, Edge closing, Map<AEKey, Parent> parents) {
        List<AEKey> keys = new ArrayList<>();
        List<CompiledPattern> compiledPatterns = new ArrayList<>();
        keys.add(closing.to);
        compiledPatterns.add(closing.pattern);
        AEKey cursor = from;
        while (!cursor.equals(closing.to)) {
            keys.add(cursor);
            Parent parent = parents.get(cursor);
            if (parent == null) break;
            compiledPatterns.add(parent.pattern);
            cursor = parent.key;
        }
        java.util.Collections.reverse(keys);
        java.util.Collections.reverse(compiledPatterns);
        List<IPatternDetails> patterns = compiledPatterns.stream().map(CompiledPattern::details).toList();
        return new CycleDiagnostic(keys, patterns, calculateNetOutputs(keys, compiledPatterns), Map.of());
    }

    private static Map<AEKey, Long> calculateNetOutputs(List<AEKey> keys, List<CompiledPattern> patterns) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey key : keys) {
            result.putIfAbsent(key, 0L);
        }
        for (CompiledPattern pattern : patterns) {
            for (var output : pattern.outputs()) {
                if (result.containsKey(output.what())) {
                    result.compute(output.what(), (key, amount) -> addSaturated(amount, output.amount()));
                }
            }
            for (CompiledInput input : pattern.inputs()) {
                if (result.containsKey(input.key())) {
                    result.compute(input.key(), (key, amount) -> addSaturated(amount, -input.amountPerPattern()));
                }
            }
        }
        return result;
    }

    private static long addSaturated(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}
