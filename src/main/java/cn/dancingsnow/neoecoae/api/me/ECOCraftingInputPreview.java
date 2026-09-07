package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

/** Sparse extraction overlay: AE2 selects concrete substitutes without touching CPU inventory. */
final class ECOCraftingInputPreview implements ICraftingInventory {
    private final ICraftingInventory source;
    private final KeyCounter removed = new KeyCounter();

    ECOCraftingInputPreview(ICraftingInventory source) {
        this.source = source;
    }

    @Override
    public void insert(AEKey key, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) removed.remove(key, amount);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        long available = Math.max(0L, source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) - removed.get(key));
        long extracted = Math.min(amount, available);
        if (mode == Actionable.MODULATE) removed.add(key, extracted);
        return extracted;
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
        return source.findFuzzyTemplates(key);
    }
}
