package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<AEKey, Long> protectedAmounts;

    /** Native AE2 dispatch keeps substitution/fuzzy selection unrestricted while extraction stays virtual. */
    ECOCraftingInputPreview(ICraftingInventory source) {
        this.source = source;
        this.primaryInputs = Set.of();
        this.possibleInputs = Set.of();
        this.reusableTemplates = Set.of();
        this.protectedAmounts = Map.of();
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern) {
        this(source, pattern, Map.of());
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern,
            Map<AEKey, Long> protectedAmounts) {
        this(source, pattern, protectedAmounts, ECOCraftingRemainderCache.shared());
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern,
            Map<AEKey, Long> protectedAmounts, ECOCraftingRemainderCache remainderCache) {
        this.source = source;
        this.primaryInputs = new HashSet<>();
        this.possibleInputs = new HashSet<>();
        this.reusableTemplates = new HashSet<>();
        this.protectedAmounts = Map.copyOf(protectedAmounts);
        for (var input : pattern.getInputs()) {
            if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0) continue;
            var possible = input.getPossibleInputs();
            if (possible[0] == null || possible[0].what() == null) continue;
            primaryInputs.add(possible[0].what());
            for (var candidate : possible) {
                if (candidate != null && candidate.what() != null) {
                    possibleInputs.add(candidate.what());
                    if (isReusableTemplate(input, candidate.what(), remainderCache)) {
                        reusableTemplates.add(candidate.what());
                    }
                }
            }
        }
    }

    private static boolean isReusableTemplate(IPatternDetails.IInput input, AEKey key,
            ECOCraftingRemainderCache remainderCache) {
        try {
            AEKey remainder = remainderCache.get(input, key);
            if (key.equals(remainder)) return true;
            if (!(key instanceof AEItemKey item) || !(remainder instanceof AEItemKey returned)
                    || item.getItem() != returned.getItem()) return false;
            var before = item.toStack(1);
            var after = returned.toStack(1);
            return before.isDamageableItem() && after.isDamageableItem()
                && after.getDamageValue() > before.getDamageValue();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public void insert(AEKey key, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) removed.remove(key, amount);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        long available = Math.max(0L,
            source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) - removed.get(key));
        available = Math.max(0L, available - protectedAmounts.getOrDefault(key, 0L));
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
