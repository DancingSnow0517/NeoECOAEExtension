package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extraction overlay for an ECO execution plan. The planner commits each substitution slot to its primary
 * concrete member for ordinary materials. Reusable and durability inputs are resolved separately by the planner:
 * they may use an unchanged alternative or a tool whose damage advances after each craft. Those inputs retain
 * AE2's template selection and input validation while extraction remains virtual.
 */
final class ECOCraftingInputPreview implements ICraftingInventory {
    private final ICraftingInventory source;
    private final KeyCounter removed = new KeyCounter();
    private final Map<AEKey, TemplateMetadata> templates;
    private final Map<AEKey, Long> protectedAmounts;

    /** Native AE2 dispatch keeps substitution/fuzzy selection unrestricted while extraction stays virtual. */
    ECOCraftingInputPreview(ICraftingInventory source) {
        this.source = source;
        this.templates = Map.of();
        this.protectedAmounts = Map.of();
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern) {
        this(source, pattern, Map.of());
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern,
            Map<AEKey, Long> protectedAmounts) {
        this(source, metadataFor(pattern), protectedAmounts);
    }

    ECOCraftingInputPreview(ICraftingInventory source, PatternMetadata metadata,
            Map<AEKey, Long> protectedAmounts) {
        this.source = source;
        this.templates = metadata.templates;
        this.protectedAmounts = protectedAmounts.isEmpty() ? Map.of() : Map.copyOf(protectedAmounts);
    }

    static PatternMetadata metadataFor(IPatternDetails pattern) {
        var templates = new HashMap<AEKey, TemplateMetadata>();
        for (var input : pattern.getInputs()) {
            if (input == null) continue;
            var possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0) continue;
            if (possible[0] == null || possible[0].what() == null) continue;
            for (var candidate : possible) {
                if (candidate != null && candidate.what() != null) {
                    var template = templates.get(candidate.what());
                    if (template == null) {
                        template = new TemplateMetadata(input);
                        templates.put(candidate.what(), template);
                    } else {
                        template.addInput(input);
                    }
                }
            }
            templates.get(possible[0].what()).primary = true;
        }
        // Indexing candidates must not invoke recipes. Large substitution lists used to build a complete
        // crafting grid for every candidate here, even when none of those alternatives was in the inventory.
        return new PatternMetadata(Collections.unmodifiableMap(templates));
    }

    static final class PatternMetadata {
        private final Map<AEKey, TemplateMetadata> templates;

        private PatternMetadata(Map<AEKey, TemplateMetadata> templates) {
            this.templates = templates;
        }
    }

    /** Task-local, server-thread-only classification; actual recipe remainders are never cached here. */
    private static final class TemplateMetadata {
        private final IPatternDetails.IInput firstInput;
        private List<IPatternDetails.IInput> otherInputs;
        private boolean primary;
        // null means unexamined, or that a previous recipe query failed transiently.
        private Boolean reusable;

        private TemplateMetadata(IPatternDetails.IInput input) {
            this.firstInput = input;
        }

        private void addInput(IPatternDetails.IInput input) {
            if (input == firstInput) return;
            if (otherInputs == null) otherInputs = new ArrayList<>();
            for (var other : otherInputs) if (other == input) return;
            otherInputs.add(input);
        }

        private boolean isReusable(AEKey key) {
            if (reusable != null) return reusable;
            Boolean result = isReusableTemplate(firstInput, key);
            if (Boolean.TRUE.equals(result)) {
                reusable = true;
                return true;
            }
            boolean unavailable = result == null;
            // A concrete key may occur in more than one slot. Preserve the original union semantics:
            // being reusable in any one of those slots permits AE2's fuzzy selection for that key.
            if (otherInputs != null) {
                for (var input : otherInputs) {
                    result = isReusableTemplate(input, key);
                    if (Boolean.TRUE.equals(result)) {
                        reusable = true;
                        return true;
                    }
                    unavailable |= result == null;
                }
            }
            if (!unavailable) reusable = false;
            return false;
        }
    }

    private static Boolean isReusableTemplate(IPatternDetails.IInput input, AEKey key) {
        try {
            AEKey remainder = input.getRemainingKey(key);
            if (key.equals(remainder)) return true;
            if (!(key instanceof AEItemKey item) || !(remainder instanceof AEItemKey returned)
                    || item.getItem() != returned.getItem()) return false;
            var before = item.toStack(1);
            var after = returned.toStack(1);
            return before.isDamageableItem() && after.isDamageableItem()
                && after.getDamageValue() > before.getDamageValue();
        } catch (RuntimeException unavailable) {
            // Do not turn a transient recipe failure into a cached negative for the rest of the job.
            return null;
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
        var template = templates.get(key);
        if (template == null) return source.findFuzzyTemplates(key);
        if (!template.primary && Boolean.FALSE.equals(template.reusable)) return List.of();

        var candidates = source.findFuzzyTemplates(key);
        // SpecialPatternResolver may reserve a non-primary reusable ingredient (for example an unlimited
        // infusion crystal), or a partially used tool. The returned tool changes its concrete damage key after
        // every craft. Requiring the encoded key here would strand that planned stock before the next dispatch.
        // CraftingCpuHelper still calls IInput.isValid on every template before extracting it, and only stock
        // physically owned by this CPU is visible through source.
        if (Boolean.TRUE.equals(template.reusable)) return candidates;

        boolean exactMatch = false;
        boolean checkedReusable = false;
        for (var candidate : candidates) {
            if (template.primary && key.equals(candidate)) {
                if (checkedReusable || Boolean.FALSE.equals(template.reusable)) return List.of(key);
                exactMatch = true;
            } else if (!checkedReusable) {
                // Only a match that would otherwise be rejected needs recipe classification. Empty lookups
                // and primary-key-only lookups have the same result whether the ingredient is reusable or not.
                if (template.isReusable(key)) return source.findFuzzyTemplates(key);
                if (!template.primary) return List.of();
                if (exactMatch) return List.of(key);
                checkedReusable = true;
            }
        }
        // Keep the planner's exact concrete reservation for ordinary materials. Reusable tools take the
        // unrestricted branch above, including when their damage key changes after an earlier craft.
        return exactMatch ? List.of(key) : List.of();
    }
}
