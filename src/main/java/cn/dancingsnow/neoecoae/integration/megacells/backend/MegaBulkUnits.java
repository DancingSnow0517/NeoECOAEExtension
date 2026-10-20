package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.storage.bulk.BulkUnits;
import gripe._90.megacells.misc.CompressionChain;

import java.util.ArrayList;
import java.util.List;

/** Imports MEGA's validated recipes; unrepresentable denominations are never clamped. */
final class MegaBulkUnits {
    private MegaBulkUnits() {}

    static BulkUnits compile(CompressionChain chain, AEItemKey fallback) {
        if (chain.isEmpty()) return new BulkUnits(List.of(fallback), List.of(1L));
        var items = new ArrayList<AEItemKey>();
        var factors = new ArrayList<Long>();
        long factor = 1;
        for (int i = 0; i < chain.size(); i++) {
            var stack = chain.getItem(i);
            if (stack.getCount() <= 0 || i == 0 && stack.getCount() != 1) {
                throw new IllegalArgumentException("Invalid MEGA compression ratio");
            }
            try {
                factor = Math.multiplyExact(factor, stack.getCount());
            } catch (ArithmeticException overflow) {
                break; // Higher variants cannot fit even once in a long-range cell.
            }
            items.add(AEItemKey.of(stack));
            factors.add(factor);
        }
        return new BulkUnits(items, factors);
    }
}
