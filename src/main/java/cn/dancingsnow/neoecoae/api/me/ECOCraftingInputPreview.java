package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extraction overlay for an ECO execution plan. The planner commits each substitution slot to its primary
 * concrete member, so the overlay must not let AE2 silently consume a different member from the CPU inventory.
 * Native AE2 jobs do not use this overlay and retain AE2's normal substitution behavior.
 */
final class ECOCraftingInputPreview implements ICraftingInventory {
    private final ICraftingInventory source;
    private final KeyCounter removed = new KeyCounter();
    private final Set<AEKey> primaryInputs;
    private final Set<AEKey> possibleInputs;

    /** Native AE2 dispatch keeps substitution/fuzzy selection unrestricted while extraction stays virtual. */
    ECOCraftingInputPreview(ICraftingInventory source) {
        this.source = source;
        this.primaryInputs = Set.of();
        this.possibleInputs = Set.of();
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern) {
        this.source = source;
        this.primaryInputs = new HashSet<>();
        this.possibleInputs = new HashSet<>();
        for (var input : pattern.getInputs()) {
            if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0) continue;
            var possible = input.getPossibleInputs();
            if (possible[0] == null || possible[0].what() == null) continue;
            primaryInputs.add(possible[0].what());
            for (var candidate : possible) {
                if (candidate != null && candidate.what() != null) possibleInputs.add(candidate.what());
            }
        }
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
        // Restrict only keys that belong to this pattern's substitution slots. Other keys are left untouched so
        // a provider integration can still inspect unrelated templates while resolving the same pattern.
        if (possibleInputs.contains(key) && !primaryInputs.contains(key)) return List.of();
        if (!possibleInputs.contains(key)) return source.findFuzzyTemplates(key);

        // The planner's reservation is keyed by the primary concrete key. Filtering the result also prevents a
        // fuzzy inventory match from changing that concrete key after planning.
        var result = new java.util.ArrayList<AEKey>();
        for (var candidate : source.findFuzzyTemplates(key)) {
            if (key.equals(candidate) && primaryInputs.contains(candidate)) result.add(candidate);
        }
        return List.copyOf(result);
    }
}
