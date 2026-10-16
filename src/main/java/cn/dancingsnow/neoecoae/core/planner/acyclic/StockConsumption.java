package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.planner.PlannerCounter;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Exact stock debits, with platform-specific ingredient matching supplied as a predicate. */
public final class StockConsumption {
    private StockConsumption() {}

    public static <K> PlannerAmount consume(PlannerCounter<K> stored, PlannerCounter<K> used,
            K key, PlannerAmount requested, boolean alternatives, Predicate<K> matches,
            BiConsumer<K, PlannerAmount> attribution) {
        if (requested.signum() <= 0) return PlannerAmount.ZERO;
        if (!alternatives) {
            PlannerAmount exact = stored.available(key, requested);
            if (exact.signum() > 0) {
                stored.remove(key, exact);
                used.add(key, exact);
                attribution.accept(key, exact);
            }
            return exact;
        }
        PlannerAmount remaining = requested;
        PlannerAmount consumed = PlannerAmount.ZERO;
        for (K candidate : stored.keysSnapshot()) {
            if (remaining.isZero()) break;
            if (!matches.test(candidate)) continue;
            PlannerAmount take = stored.available(candidate, remaining);
            if (take.signum() <= 0) continue;
            stored.remove(candidate, take);
            used.add(candidate, take);
            attribution.accept(candidate, take);
            consumed = consumed.add(take);
            remaining = remaining.subtract(take);
        }
        return consumed;
    }
}
