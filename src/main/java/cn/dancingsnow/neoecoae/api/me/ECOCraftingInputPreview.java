package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

import java.util.List;
import java.util.Set;

/**
 * Extraction overlay for an ECO execution plan. The planner commits each substitution slot to its primary
 * concrete member for ordinary materials. Reusable and durability inputs are resolved separately by the planner:
 * they may use an unchanged alternative or a tool whose damage advances after each craft. Those inputs retain
 * AE2's template selection and input validation while extraction remains virtual.
 */
final class ECOCraftingInputPreview implements ICraftingInventory {
    private final ICraftingInventory source;
    private final KeyCounter removed = new KeyCounter();
    private final Set<AEKey> primaryInputs;
    private final Set<AEKey> possibleInputs;
    private final Set<AEKey> reusableTemplates;
    private final ProtectedAmountView protectedAmounts;

    @FunctionalInterface
    interface ProtectedAmountView {
        ProtectedAmountView EMPTY = key -> 0L;
        long get(AEKey key);
    }

    /** Native AE2 dispatch keeps substitution/fuzzy selection unrestricted while extraction stays virtual. */
    ECOCraftingInputPreview(ICraftingInventory source) {
        this.source = source;
        this.primaryInputs = Set.of();
        this.possibleInputs = Set.of();
        this.reusableTemplates = Set.of();
        this.protectedAmounts = ProtectedAmountView.EMPTY;
    }

    ECOCraftingInputPreview(ICraftingInventory source, ECOCompiledPatternInputs compiled,
            ProtectedAmountView protectedAmounts) {
        this.source = source;
        this.primaryInputs = compiled.primaryInputs();
        this.possibleInputs = compiled.possibleInputs();
        this.reusableTemplates = compiled.reusableTemplates();
        this.protectedAmounts = protectedAmounts == null ? ProtectedAmountView.EMPTY : protectedAmounts;
    }

    @Override
    public void insert(AEKey key, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) removed.remove(key, amount);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        long available = Math.max(0L,
            source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) - removed.get(key));
        available = Math.max(0L, available - Math.max(0L, protectedAmounts.get(key)));
        long extracted = Math.min(amount, available);
        if (mode == Actionable.MODULATE) removed.add(key, extracted);
        return extracted;
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
        // SpecialPatternResolver may reserve a non-primary reusable ingredient (for example an unlimited
        // infusion crystal), or a partially used tool. The returned tool changes its concrete damage key after
        // every craft. Requiring the encoded key here would strand that planned stock before the next dispatch.
        // CraftingCpuHelper still calls IInput.isValid on every template before extracting it, and only stock
        // physically owned by this CPU is visible through source.
        if (reusableTemplates.contains(key)) return source.findFuzzyTemplates(key);
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
