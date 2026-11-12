package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Exact-order inventory. The inherited counter is a saturated compatibility window, never overflow storage. */
public final class ECOExactInventory extends ListCraftingInventory {
    private final Map<AEKey, PlannerAmount> wideAmounts = new LinkedHashMap<>();
    private final ChangeListener listener;
    private boolean enabled;

    public ECOExactInventory(ChangeListener listener) {
        super(listener);
        this.listener = listener;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        // Cancelled big orders still own their leftovers until the network accepts every window.
        this.enabled = enabled || !wideAmounts.isEmpty();
    }

    public BigInteger amount(AEKey key) {
        return quantity(key).toBigInteger();
    }

    public PlannerAmount quantity(AEKey key) {
        PlannerAmount wide = wideAmounts.get(key);
        return wide == null ? PlannerAmount.of(Math.max(0L, list.get(key))) : wide;
    }

    public static BigInteger amount(ListCraftingInventory inventory, AEKey key) {
        return quantity(inventory, key).toBigInteger();
    }

    public static PlannerAmount quantity(ListCraftingInventory inventory, AEKey key) {
        return inventory instanceof ECOExactInventory exact && exact.isEnabled()
            ? exact.quantity(key) : PlannerAmount.of(Math.max(0L, inventory.list.get(key)));
    }

    public Map<AEKey, BigInteger> snapshot() {
        return toBigIntegers(snapshotQuantities());
    }

    public Map<AEKey, PlannerAmount> snapshotQuantities() {
        var result = new LinkedHashMap<AEKey, PlannerAmount>();
        for (var entry : list) if (entry.getLongValue() > 0) result.put(entry.getKey(), quantity(entry.getKey()));
        return result;
    }

    public PlannerAmount totalQuantity() {
        PlannerAmount total = PlannerAmount.ZERO;
        for (var entry : list) if (entry.getLongValue() > 0) total = total.add(quantity(entry.getKey()));
        return total;
    }

    private void set(AEKey key, PlannerAmount amount) {
        long window = amount.longValueSaturated();
        if (window == 0) list.remove(key); else list.set(key, window);
        if (amount.fitsLong()) wideAmounts.remove(key); else wideAmounts.put(key, amount);
    }

    @Override public void insert(AEKey key, long amount, Actionable mode) {
        if (!enabled) { super.insert(key, amount, mode); return; }
        if (amount < 0) throw new IllegalArgumentException("Negative inventory insertion");
        if (mode == Actionable.MODULATE && amount > 0) {
            long current = Math.max(0L, list.get(key));
            if (!wideAmounts.containsKey(key) && amount <= Long.MAX_VALUE - current) list.set(key, current + amount);
            else set(key, quantity(key).add(amount));
            listener.onChange(key);
        }
    }

    @Override public long extract(AEKey key, long requested, Actionable mode) {
        if (!enabled) return super.extract(key, requested, mode);
        if (requested < 0) throw new IllegalArgumentException("Negative inventory extraction");
        long current = Math.max(0L, list.get(key));
        long extracted = Math.min(current, requested);
        if (mode == Actionable.MODULATE && extracted > 0) {
            if (wideAmounts.containsKey(key)) set(key, quantity(key).subtract(extracted));
            else if (extracted == current) list.remove(key);
            else list.set(key, current - extracted);
            listener.onChange(key);
        }
        return extracted;
    }

    /** Debits an acknowledged external transfer. A failing observer must never refund already delivered resources. */
    public void debitTransferred(AEKey key, BigInteger accepted) {
        if (accepted == null || accepted.signum() <= 0) throw new IllegalArgumentException("Invalid transfer debit");
        BigInteger current = amount(key);
        if (current.compareTo(accepted) < 0) throw new IllegalStateException("Transfer exceeds retained inventory");
        set(key, PlannerAmount.of(current).subtract(PlannerAmount.of(accepted)));
        listener.onChange(key);
    }

    /** Validate all keys first, then change all keys before publishing notifications. */
    public boolean debit(Map<AEKey, BigInteger> inputs) {
        return debitQuantities(fromBigIntegers(inputs));
    }

    public boolean debitQuantities(Map<AEKey, PlannerAmount> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var entry : inputs.entrySet()) {
            if (entry.getValue().signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
            if (quantity(entry.getKey()).compareTo(entry.getValue()) < 0) return false;
        }
        inputs.forEach((key, value) -> set(key, quantity(key).subtract(value)));
        try {
            inputs.keySet().forEach(listener::onChange);
        } catch (RuntimeException failure) {
            inputs.forEach((key, value) -> set(key, quantity(key).add(value)));
            throw failure;
        }
        return true;
    }

    public void restore(Map<AEKey, BigInteger> inputs) {
        restoreQuantities(fromBigIntegers(inputs));
    }

    public void restoreQuantities(Map<AEKey, PlannerAmount> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var amount : inputs.values()) if (amount.signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
        inputs.forEach((key, value) -> set(key, quantity(key).add(value)));
        inputs.keySet().forEach(listener::onChange);
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, long copies) {
        return toBigIntegers(totalsQuantities(stacks, PlannerAmount.of(copies)));
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, BigInteger copies) {
        return toBigIntegers(totalsQuantities(stacks, PlannerAmount.of(copies)));
    }

    public static Map<AEKey, PlannerAmount> totalsQuantities(List<GenericStack> stacks, long copies) {
        return totalsQuantities(stacks, PlannerAmount.of(copies));
    }

    public static Map<AEKey, PlannerAmount> totalsQuantities(List<GenericStack> stacks, PlannerAmount copies) {
        var result = new LinkedHashMap<AEKey, PlannerAmount>();
        if (copies.signum() <= 0) throw new IllegalArgumentException("Invalid copies");
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("Invalid stack");
            result.merge(stack.what(), copies.multiply(stack.amount()), PlannerAmount::add);
        }
        return result;
    }

    public static <K extends AEKey> Map<K, PlannerAmount> fromBigIntegers(Map<K, BigInteger> amounts) {
        var result = new LinkedHashMap<K, PlannerAmount>();
        amounts.forEach((key, value) -> result.put(key, PlannerAmount.of(value)));
        return result;
    }

    public static <K extends AEKey> Map<K, BigInteger> toBigIntegers(Map<K, PlannerAmount> amounts) {
        var result = new LinkedHashMap<K, BigInteger>();
        amounts.forEach((key, value) -> result.put(key, value.toBigInteger()));
        return result;
    }

    @Override public void clear() {
        wideAmounts.clear();
        super.clear();
    }

    @Override public void serialize(ValueOutput.ValueOutputList output) {
        if (!enabled) { super.serialize(output); return; }
        for (var entry : list) {
            if (entry.getLongValue() <= 0) continue;
            var child = output.addChild();
            entry.getKey().toTagGeneric(child);
            child.putLong("#", entry.getLongValue());
            if (wideAmounts.containsKey(entry.getKey())) child.putString("ecoExactAmount", quantity(entry.getKey()).toString());
        }
    }

    @Override public void deserialize(ValueInput.ValueInputList data) {
        if (!enabled) {
            boolean exactData = false;
            for (var item : data) exactData |= item.getString("ecoExactAmount").isPresent();
            if (!exactData) { wideAmounts.clear(); super.deserialize(data); return; }
        }
        // Decode into a temporary ledger first. Invalid exact data must not silently become a long window.
        var restored = new LinkedHashMap<AEKey, PlannerAmount>();
        boolean hasExact = false;
        for (var tag : data) {
            var key = ECOKeyFactory.canonicalize(AEKey.fromTagGeneric(tag));
            var exactAmount = tag.getString("ecoExactAmount");
            boolean exact = exactAmount.isPresent();
            if (key == null) {
                if (exact) throw new IllegalArgumentException("Unknown key in exact inventory");
                continue;
            }
            var count = exact ? PlannerAmount.parse(exactAmount.orElseThrow()) : PlannerAmount.of(tag.getLongOr("#", 0L));
            if (count.signum() < 0) throw new IllegalArgumentException("Negative saved inventory");
            restored.merge(key, count, PlannerAmount::add);
            hasExact |= exact;
        }
        list.clear();
        wideAmounts.clear();
        enabled |= hasExact;
        restored.forEach(this::set);
        enabled |= !wideAmounts.isEmpty();
        restored.keySet().forEach(listener::onChange);
    }
}
