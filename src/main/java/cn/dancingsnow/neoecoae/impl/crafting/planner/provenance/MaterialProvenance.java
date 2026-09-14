package cn.dancingsnow.neoecoae.impl.crafting.planner.provenance;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable solve-local ledger for material sources and unconsumed byproduct credit. */
public final class MaterialProvenance {
    private final Map<AEKey, Map<MaterialSource, PlannerAmount>> suppliers = new LinkedHashMap<>();
    private final Map<AEKey, Map<IPatternDetails, PlannerAmount>> creditLedger = new LinkedHashMap<>();

    public void credit(AEKey key, IPatternDetails pattern, PlannerAmount amount) {
        requirePositive(amount);
        creditLedger.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .merge(pattern, amount, PlannerAmount::add);
    }

    /** Consumes at most {@code amount} in insertion order and attributes every consumed portion. */
    public Map<IPatternDetails, PlannerAmount> consumeCredit(AEKey key, PlannerAmount amount) {
        requirePositive(amount);
        Map<IPatternDetails, PlannerAmount> credits = creditLedger.get(key);
        if (credits == null || credits.isEmpty()) return Map.of();
        Map<IPatternDetails, PlannerAmount> consumed = new LinkedHashMap<>();
        PlannerAmount remaining = amount;
        var iterator = credits.entrySet().iterator();
        while (iterator.hasNext() && remaining.signum() > 0) {
            var entry = iterator.next();
            PlannerAmount drawn = remaining.min(entry.getValue());
            if (drawn.signum() <= 0) continue;
            consumed.put(entry.getKey(), drawn);
            supplied(key, new MaterialSource.PatternOutput(entry.getKey(), false), drawn);
            remaining = remaining.subtract(drawn);
            PlannerAmount left = entry.getValue().subtract(drawn);
            if (left.isZero()) iterator.remove(); else entry.setValue(left);
        }
        if (credits.isEmpty()) creditLedger.remove(key);
        if (remaining.signum() > 0) {
            throw new IllegalStateException("Crafted credit and provenance ledger diverged for " + key);
        }
        return Map.copyOf(consumed);
    }

    public void supplied(AEKey key, MaterialSource source, PlannerAmount amount) {
        requirePositive(amount);
        suppliers.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .merge(source, amount, PlannerAmount::add);
    }

    public MaterialProvenance copy() {
        MaterialProvenance copy = new MaterialProvenance();
        copy.mergeFrom(this);
        return copy;
    }

    public void mergeFrom(MaterialProvenance other) {
        mergeSuppliersFrom(other);
        other.creditLedger.forEach((key, credits) -> credits.forEach((pattern, amount) ->
            creditLedger.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .merge(pattern, amount, PlannerAmount::add)));
    }

    public void mergeSuppliersFrom(MaterialProvenance other) {
        other.suppliers.forEach((key, sources) -> sources.forEach((source, amount) ->
            suppliers.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .merge(source, amount, PlannerAmount::add)));
    }

    public void replaceWith(MaterialProvenance other) {
        suppliers.clear();
        creditLedger.clear();
        mergeFrom(other);
    }

    public ExecutionProvenance freeze() {
        return new ExecutionProvenance(suppliers);
    }

    private static void requirePositive(PlannerAmount amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
