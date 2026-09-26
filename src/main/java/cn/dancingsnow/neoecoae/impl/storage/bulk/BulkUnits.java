package cn.dancingsnow.neoecoae.impl.storage.bulk;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import java.util.List;

/** Immutable denomination table. The first item is one atom; quantities never use saturation. */
public record BulkUnits(List<AEItemKey> items, List<Long> factors) {
    public BulkUnits {
        items = List.copyOf(items);
        factors = List.copyOf(factors);
        if (items.isEmpty() || items.size() != factors.size() || factors.getFirst() != 1L
                || items.stream().distinct().count() != items.size()) {
            throw new IllegalArgumentException("Invalid bulk unit definition");
        }
        for (int i = 1; i < factors.size(); i++) {
            long previous = factors.get(i - 1);
            long factor = factors.get(i);
            if (factor < previous || factor % previous != 0) {
                throw new IllegalArgumentException("Non-integral bulk denomination");
            }
        }
    }

    public long factor(AEItemKey item) {
        int index = items.indexOf(item);
        return index < 0 ? 0 : factors.get(index);
    }

    public int cutoff(AEItemKey item) {
        int index = items.indexOf(item);
        return index < 0 ? items.size() - 1 : index;
    }

    public void publish(long units, int cutoff, KeyCounter out) {
        for (int i = cutoff; i >= 0; i--) {
            long factor = factors.get(i);
            long amount = units / factor;
            if (amount > 0) out.add(items.get(i), amount);
            units %= factor;
        }
    }

    public static long insertable(long stored, long requested, long factor) {
        return requested <= 0 || factor <= 0 ? 0 : Math.min(requested, (Long.MAX_VALUE - stored) / factor);
    }

    public static long extractable(long stored, long requested, long factor) {
        return requested <= 0 || factor <= 0 ? 0 : Math.min(requested, stored / factor);
    }
}
