package cn.dancingsnow.neoecoae.api.me.bigorder;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Exact-order inventory. The inherited counter is a saturated compatibility window, never overflow storage. */
public final class ECOExactInventory extends ListCraftingInventory {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final Map<AEKey, BigInteger> excess = new LinkedHashMap<>();
    private final ChangeListener listener;
    private boolean enabled;

    public ECOExactInventory(ChangeListener listener) {
        super(listener);
        this.listener = listener;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        // Cancelled big orders still own their leftovers until the network accepts every window.
        this.enabled = enabled || !excess.isEmpty();
    }

    public BigInteger amount(AEKey key) {
        return BigInteger.valueOf(Math.max(0L, list.get(key))).add(excess.getOrDefault(key, BigInteger.ZERO));
    }

    public static BigInteger amount(ListCraftingInventory inventory, AEKey key) {
        return inventory instanceof ECOExactInventory exact && exact.isEnabled()
            ? exact.amount(key) : BigInteger.valueOf(Math.max(0L, inventory.list.get(key)));
    }

    public Map<AEKey, BigInteger> snapshot() {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var entry : list) if (entry.getLongValue() > 0) result.put(entry.getKey(), amount(entry.getKey()));
        return Map.copyOf(result);
    }

    private void set(AEKey key, BigInteger amount) {
        long window = amount.min(MAX).longValueExact();
        if (window == 0) list.remove(key); else list.set(key, window);
        if (amount.compareTo(MAX) > 0) excess.put(key, amount.subtract(MAX)); else excess.remove(key);
    }

    @Override public void insert(AEKey key, long amount, Actionable mode) {
        if (!enabled) { super.insert(key, amount, mode); return; }
        if (amount < 0) throw new IllegalArgumentException("Negative inventory insertion");
        if (mode == Actionable.MODULATE && amount > 0) {
            set(key, amount(key).add(BigInteger.valueOf(amount)));
            listener.onChange(key);
        }
    }

    @Override public long extract(AEKey key, long requested, Actionable mode) {
        if (!enabled) return super.extract(key, requested, mode);
        if (requested < 0) throw new IllegalArgumentException("Negative inventory extraction");
        long extracted = amount(key).min(BigInteger.valueOf(requested)).longValueExact();
        if (mode == Actionable.MODULATE && extracted > 0) {
            set(key, amount(key).subtract(BigInteger.valueOf(extracted)));
            listener.onChange(key);
        }
        return extracted;
    }

    /** Validate all keys first, then change all keys before publishing notifications. */
    public boolean debit(Map<AEKey, BigInteger> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var entry : inputs.entrySet()) {
            if (entry.getValue().signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
            if (amount(entry.getKey()).compareTo(entry.getValue()) < 0) return false;
        }
        inputs.forEach((key, value) -> set(key, amount(key).subtract(value)));
        try {
            inputs.keySet().forEach(listener::onChange);
        } catch (RuntimeException failure) {
            inputs.forEach((key, value) -> set(key, amount(key).add(value)));
            throw failure;
        }
        return true;
    }

    public void restore(Map<AEKey, BigInteger> inputs) {
        if (!enabled) throw new IllegalStateException("Exact inventory is disabled");
        for (var amount : inputs.values()) if (amount.signum() <= 0) throw new IllegalArgumentException("Invalid exact input");
        inputs.forEach((key, value) -> set(key, amount(key).add(value)));
        inputs.keySet().forEach(listener::onChange);
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, long copies) {
        return totals(stacks, BigInteger.valueOf(copies));
    }

    public static Map<AEKey, BigInteger> totals(List<GenericStack> stacks, BigInteger copies) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        if (copies.signum() <= 0) throw new IllegalArgumentException("Invalid copies");
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("Invalid stack");
            result.merge(stack.what(), BigInteger.valueOf(stack.amount()).multiply(copies), BigInteger::add);
        }
        return Map.copyOf(result);
    }

    @Override public void clear() {
        excess.clear();
        super.clear();
    }

    @Override public ListTag writeToNBT(HolderLookup.Provider registries) {
        if (!enabled) return super.writeToNBT(registries);
        var result = new ListTag();
        for (var entry : list) {
            if (entry.getLongValue() <= 0) continue;
            var tag = entry.getKey().toTagGeneric(registries);
            tag.putLong("#", entry.getLongValue());
            if (excess.containsKey(entry.getKey())) tag.putString("ecoExactAmount", amount(entry.getKey()).toString());
            result.add(tag);
        }
        return result;
    }

    @Override public void readFromNBT(ListTag data, HolderLookup.Provider registries) {
        if (!enabled) {
            boolean exactData = false;
            for (int i = 0; i < data.size(); i++) exactData |= data.getCompound(i).contains("ecoExactAmount", Tag.TAG_STRING);
            if (!exactData) { excess.clear(); super.readFromNBT(data, registries); return; }
        }
        // Decode into a temporary ledger first. Invalid exact data must not silently become a long window.
        var restored = new LinkedHashMap<AEKey, BigInteger>();
        boolean hasExact = false;
        for (int i = 0; i < data.size(); i++) {
            var tag = data.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, tag);
            boolean exact = tag.contains("ecoExactAmount", Tag.TAG_STRING);
            if (key == null) {
                if (exact) throw new IllegalArgumentException("Unknown key in exact inventory");
                continue;
            }
            var count = exact ? new BigInteger(tag.getString("ecoExactAmount")) : BigInteger.valueOf(tag.getLong("#"));
            if (count.signum() < 0) throw new IllegalArgumentException("Negative saved inventory");
            restored.merge(key, count, BigInteger::add);
            hasExact |= exact;
        }
        list.clear();
        excess.clear();
        enabled |= hasExact;
        restored.forEach(this::set);
        enabled |= !excess.isEmpty();
        restored.keySet().forEach(listener::onChange);
    }
}
