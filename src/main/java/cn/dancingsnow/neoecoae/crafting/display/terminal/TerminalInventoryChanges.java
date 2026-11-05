package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collections;
import java.util.Set;

/** Per-menu difference workspace. Published inventory snapshots are never modified by a viewer. */
public final class TerminalInventoryChanges {
    private Object2LongOpenHashMap<AEKey> previousValues = new Object2LongOpenHashMap<>();
    private Object2LongOpenHashMap<AEKey> nextValues = new Object2LongOpenHashMap<>();
    private final ObjectOpenHashSet<AEKey> changed = new ObjectOpenHashSet<>();
    private final Set<AEKey> changedView = Collections.unmodifiableSet(changed);
    private Set<AEKey> result = changedView;
    private KeyCounter lastInventory;

    /** AE2 needs the changed keys, not signed differences. Neither published counter is modified. */
    public Set<AEKey> between(KeyCounter previous, KeyCounter current) {
        Set<AEKey> shared = TerminalInventorySnapshot.changesBetween(previous, current);
        if (shared != null) {
            lastInventory = null;
            result = shared;
            return shared;
        }
        result = changedView;
        if (previous != lastInventory) {
            // A new menu, a full resync or another integration replaced AE2's baseline.
            previousValues.clear();
            copyAmounts(previous, previousValues);
        }
        nextValues.clear();
        changed.clear();
        for (var entry : current) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount == 0) continue;
            nextValues.put(key, amount);
            if (previousValues.getLong(key) != amount) changed.add(key);
        }
        for (var entry : Object2LongMaps.fastIterable(previousValues)) {
            if (!nextValues.containsKey(entry.getKey())) changed.add(entry.getKey());
        }
        var unused = previousValues;
        previousValues = nextValues;
        nextValues = unused;
        lastInventory = current;
        return changedView;
    }

    /** Consumed synchronously by AE2's update helper; no caller may retain or mutate this view. */
    public Set<AEKey> changedKeys() { return result; }

    private static void copyAmounts(KeyCounter from, Object2LongOpenHashMap<AEKey> to) {
        for (var entry : from) {
            if (entry.getLongValue() != 0) to.put(entry.getKey(), entry.getLongValue());
        }
    }
}
