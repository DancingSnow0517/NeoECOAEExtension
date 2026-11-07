package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import java.math.BigInteger;
import java.util.List;
import java.util.function.BiConsumer;

/** Denomination table. The first item is one atom; quantities never use saturation. */
public record BulkUnits(List<AEItemKey> items, List<BigInteger> factors) {
    public static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    public BulkUnits {
        items = List.copyOf(items);
        factors = List.copyOf(factors);
        if (items.isEmpty() || items.size() != factors.size() || !factors.get(0).equals(BigInteger.ONE)
                || items.stream().distinct().count() != items.size()) {
            throw new IllegalArgumentException("Invalid bulk unit definition");
        }
        for (int i = 1; i < factors.size(); i++) {
            PlannerAmount previous = PlannerAmount.of(factors.get(i - 1)), factor = PlannerAmount.of(factors.get(i));
            if (factor.compareTo(previous) <= 0 || factor.remainder(previous).signum() != 0) {
                throw new IllegalArgumentException("Non-integral bulk denomination");
            }
        }
    }

    public static BulkUnits single(AEItemKey key) { return new BulkUnits(List.of(key), List.of(BigInteger.ONE)); }

    public BigInteger factor(AEItemKey item) {
        int index = items.indexOf(item);
        return index < 0 ? BigInteger.ZERO : factors.get(index);
    }

    public PlannerAmount factorQuantity(AEItemKey item) {
        return PlannerAmount.of(factor(item));
    }

    public PlannerAmount factorQuantity(int index) {
        return PlannerAmount.of(factors.get(index));
    }

    public int cutoff(AEItemKey item) {
        int index = items.indexOf(item);
        return index < 0 ? items.size() - 1 : index;
    }

    public boolean contains(AEItemKey item) { return items.contains(item); }
    public boolean compressible() { return items.size() > 1; }

    /** Disjoint denominations; equivalent forms are never counted more than once. */
    public void visit(BigInteger units, int cutoff, BiConsumer<AEItemKey, BigInteger> visitor) {
        visitQuantities(PlannerAmount.of(units), cutoff, (key, amount) -> visitor.accept(key, amount.toBigInteger()));
    }

    public void visitQuantities(PlannerAmount units, int cutoff, BiConsumer<AEItemKey, PlannerAmount> visitor) {
        if (units.signum() < 0 || cutoff < 0 || cutoff >= items.size()) throw new IllegalArgumentException("Invalid projection");
        for (int i = cutoff; i >= 0; i--) {
            PlannerAmount[] division = units.divideAndRemainder(factorQuantity(i));
            if (division[0].signum() > 0) visitor.accept(items.get(i), division[0]);
            units = division[1];
        }
    }

    public void publish(BigInteger units, int cutoff, KeyCounter out) {
        visitQuantities(PlannerAmount.of(units), cutoff, (key, amount) -> addSaturated(out, key, amount));
    }
    public static long clamp(BigInteger amount) { return clamp(PlannerAmount.of(amount)); }
    public static long clamp(PlannerAmount amount) { return amount.min(PlannerAmount.of(Long.MAX_VALUE)).longValueExact(); }
    public static void addSaturated(KeyCounter out, AEItemKey key, BigInteger amount) {
        addSaturated(out, key, PlannerAmount.of(amount));
    }
    public static void addSaturated(KeyCounter out, AEItemKey key, PlannerAmount amount) {
        long current = out.get(key), delta = clamp(amount);
        out.set(key, current < 0 || Long.MAX_VALUE - current < delta ? Long.MAX_VALUE : current + delta);
    }
}
