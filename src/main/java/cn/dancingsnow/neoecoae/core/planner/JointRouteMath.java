package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Exact recipe algebra and presence closure. Producer advertisement and physical replay remain adapter policies. */
public final class JointRouteMath {
    private JointRouteMath() {}

    public static <K> Map<K, PlannerAmount> inputs(PatternView<K> pattern) {
        Map<K, PlannerAmount> result = new LinkedHashMap<>();
        for (int i = 0; i < pattern.inputCount(); i++)
            result.merge(pattern.inputKey(i), pattern.inputAmount(i), PlannerAmount::add);
        return result;
    }

    public static <K> Map<K, PlannerAmount> outputs(PatternView<K> pattern) {
        Map<K, PlannerAmount> result = new LinkedHashMap<>();
        for (int i = 0; i < pattern.outputCount(); i++)
            result.merge(pattern.outputKey(i), PlannerAmount.of(pattern.outputAmount(i)), PlannerAmount::add);
        return result;
    }

    /** Counts physical identities once, while all advertised producer views remain available to the adapter. */
    public static <K, P extends PatternView<K>> Set<Object> demandedPatterns(K goal,
            Function<K, ? extends List<P>> producers, Predicate<P> safe, Cancellation cancellation)
            throws InterruptedException {
        Set<K> demanded = new LinkedHashSet<>();
        ArrayDeque<K> pending = new ArrayDeque<>();
        pending.add(goal);
        Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            K key = pending.removeFirst();
            if (!demanded.add(key)) continue;
            for (P pattern : producers.apply(key)) {
                if (safe.test(pattern) && identities.add(pattern.identity()))
                    for (int i = 0; i < pattern.inputCount(); i++) pending.addLast(pattern.inputKey(i));
            }
        }
        return identities;
    }

    /** Mutates the supplied marking exactly as recipe-presence closure does; no quantity feasibility is implied. */
    public static <K, P extends PatternView<K>> Set<P> presenceClosure(List<P> candidates, Set<K> available,
            Cancellation cancellation) throws InterruptedException {
        Set<P> reachable = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (P pattern : candidates) {
                cancellation.checkpoint();
                if (reachable.contains(pattern)) continue;
                boolean ready = true;
                for (int i = 0; i < pattern.inputCount(); i++) {
                    if (!available.contains(pattern.inputKey(i))) { ready = false; break; }
                }
                if (!ready) continue;
                reachable.add(pattern);
                for (int i = 0; i < pattern.outputCount(); i++) available.add(pattern.outputKey(i));
                changed = true;
            }
        } while (changed);
        return reachable;
    }

    /** More output alone is insufficient: primary activation may reduce coproduct production. */
    public static <K> boolean dominates(PatternView<K> left, PatternView<K> right) {
        if (!outputs(left).equals(outputs(right))) return false;
        Map<K, PlannerAmount> needed = inputs(right);
        return inputs(left).entrySet().stream().allMatch(entry ->
            entry.getValue().compareTo(needed.getOrDefault(entry.getKey(), PlannerAmount.ZERO)) <= 0);
    }

    /** Consumption-minus-production columns for the exact material balance solver. */
    public static <K, P extends PatternView<K>> List<Map<Integer, BigInteger>> netConsumptionMatrix(
            List<P> patterns, Map<K, Integer> keys, Cancellation cancellation) throws InterruptedException {
        List<Map<Integer, BigInteger>> net = new ArrayList<>();
        for (P pattern : patterns) {
            cancellation.checkpoint();
            Map<Integer, BigInteger> column = new LinkedHashMap<>();
            for (int i = 0; i < pattern.inputCount(); i++)
                column.merge(keys.get(pattern.inputKey(i)), pattern.inputAmount(i).toBigInteger(), BigInteger::add);
            for (int i = 0; i < pattern.outputCount(); i++)
                column.merge(keys.get(pattern.outputKey(i)), BigInteger.valueOf(pattern.outputAmount(i)).negate(), BigInteger::add);
            column.values().removeIf(value -> value.signum() == 0);
            net.add(column);
        }
        return net;
    }

    public static PlannerAmount firingCost(Iterable<PlannerAmount> counts) {
        PlannerAmount result = PlannerAmount.ZERO;
        for (PlannerAmount count : counts) result = result.add(count);
        return result;
    }
}
