package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.stacks.KeyCounter;

/** Shared exact stack arithmetic for all provider dispatch paths. */
final class ECOCraftingDispatchStacks {
    private ECOCraftingDispatchStacks() {
    }

    static KeyCounter[] scaleCounters(KeyCounter[] source, long multiplier) {
        KeyCounter[] result = new KeyCounter[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            KeyCounter scaled = new KeyCounter();
            KeyCounter counter = source[slot];
            if (counter != null) {
                for (var entry : counter) {
                    scaled.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
                }
            }
            result[slot] = scaled;
        }
        return result;
    }
}
