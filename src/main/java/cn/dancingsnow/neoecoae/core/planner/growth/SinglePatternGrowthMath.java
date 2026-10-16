package cn.dancingsnow.neoecoae.core.planner.growth;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure generic arithmetic for a one-pattern growth contract. No AE or loader types. */
public final class SinglePatternGrowthMath {
    private SinglePatternGrowthMath() {}
    public static <K> PlannerAmount requiredFirings(Map<K, PlannerAmount> required,
            Map<K, PlannerAmount> stock, Map<K, PlannerAmount> netDelta) {
        Objects.requireNonNull(required); Objects.requireNonNull(stock); Objects.requireNonNull(netDelta);
        PlannerAmount firings = PlannerAmount.ZERO;
        for (var entry : required.entrySet()) {
            PlannerAmount want = entry.getValue() == null ? PlannerAmount.ZERO : entry.getValue();
            if (want.signum() <= 0) continue;
            PlannerAmount have = stock.getOrDefault(entry.getKey(), PlannerAmount.ZERO);
            PlannerAmount outstanding = want.subtract(have).max(PlannerAmount.ZERO);
            if (outstanding.isZero()) continue;
            PlannerAmount perFiring = netDelta.getOrDefault(entry.getKey(), PlannerAmount.ZERO);
            if (perFiring.signum() <= 0) throw new IllegalArgumentException("required output has no positive net production");
            firings = firings.max(outstanding.ceilDiv(perFiring));
        }
        return firings;
    }
    public static <K> Map<K, PlannerAmount> externalDemand(Map<K, PlannerAmount> consumption,
            Map<K, PlannerAmount> remainder, K feedback, PlannerAmount firings) {
        Map<K, PlannerAmount> result = new LinkedHashMap<>();
        for (K key : consumption.keySet()) {
            if (Objects.equals(feedback, key)) continue;
            PlannerAmount net = consumption.getOrDefault(key, PlannerAmount.ZERO)
                .subtract(remainder.getOrDefault(key, PlannerAmount.ZERO));
            if (net.signum() > 0) result.put(key, net.multiply(firings));
        }
        return result;
    }
}
