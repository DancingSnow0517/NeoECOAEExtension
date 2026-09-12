package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.execution.InputTemplate;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import com.google.common.collect.MapMaker;
import net.minecraft.world.level.Level;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

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
    private static final Map<IPatternDetails, PatternMetadata> METADATA_BY_PATTERN =
        new MapMaker().weakKeys().makeMap();

    private final ICraftingInventory source;
    // Virtual removals only use exact keys; a fuzzy KeyCounter index needlessly queries item components.
    private final Object2LongOpenHashMap<AEKey> removed = new Object2LongOpenHashMap<>();
    private final Set<AEKey> primaryInputs;
    private final Set<AEKey> possibleInputs;
    private final Set<AEKey> reusableTemplates;
    private final Map<AEKey, Long> protectedAmounts;
    private ECOCraftingInputTemplateCache templateCache;

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

    ECOCraftingInputPreview(ICraftingInventory source, ECOCraftingInputTemplateCache templateCache) {
        this(source);
        this.templateCache = templateCache;
    }

    Iterable<InputTemplate> inputTemplates(IPatternDetails.IInput input, Level level) {
        if (templateCache == null) {
            return appeng.crafting.execution.CraftingCpuHelper.getValidItemTemplates(this, input, level);
        }
        return templateCache.get(source, input);
    }

    boolean needsTemplateValidation() {
        return templateCache != null;
    }

    long extractTemplates(InputTemplate template, long multiplier) {
        long available = Math.max(0L, availableExact(template.key()) - removed.getLong(template.key()));
        available = Math.max(0L, available - protectedAmounts.getOrDefault(template.key(), 0L));
        long crafts = Math.min(multiplier, available / template.amount());
        if (crafts <= 0L) return 0L;
        removed.addTo(template.key(), Math.multiplyExact(crafts, template.amount()));
        return crafts;
    }

    ECOCraftingInputPreview(ICraftingInventory source, IPatternDetails pattern,
            Map<AEKey, Long> protectedAmounts, ECOCraftingRemainderCache remainderCache) {
        this.source = source;
        PatternMetadata metadata = metadata(pattern, remainderCache);
        this.primaryInputs = metadata.primaryInputs();
        this.possibleInputs = metadata.possibleInputs();
        this.reusableTemplates = metadata.reusableTemplates();
        this.protectedAmounts = Map.copyOf(protectedAmounts);
    }

    private static PatternMetadata metadata(IPatternDetails pattern, ECOCraftingRemainderCache remainderCache) {
        long reloadGeneration = AE2PatternIntrospection.reloadGeneration();
        return METADATA_BY_PATTERN.compute(pattern, (ignored, cached) ->
            cached != null && cached.reloadGeneration() == reloadGeneration
                ? cached : buildMetadata(pattern, remainderCache, reloadGeneration));
    }

    private static PatternMetadata buildMetadata(IPatternDetails pattern,
            ECOCraftingRemainderCache remainderCache, long reloadGeneration) {
        Set<AEKey> primaryInputs = new HashSet<>();
        Set<AEKey> possibleInputs = new HashSet<>();
        Set<AEKey> reusableTemplates = new HashSet<>();
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
        return new PatternMetadata(reloadGeneration, Set.copyOf(primaryInputs),
            Set.copyOf(possibleInputs), Set.copyOf(reusableTemplates));
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
        if (mode == Actionable.MODULATE) removed.addTo(key, -amount);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        long available = Math.max(0L, availableExact(key) - removed.getLong(key));
        available = Math.max(0L, available - protectedAmounts.getOrDefault(key, 0L));
        long extracted = Math.min(amount, available);
        if (mode == Actionable.MODULATE && extracted > 0L) removed.addTo(key, extracted);
        return extracted;
    }

    /**
     * Resolves a planned ordinary input without asking AE2 to enumerate fuzzy templates. The planner has already
     * selected the first concrete possible input for substitution slots. Reusable inputs deliberately return a
     * negative sentinel and continue through AE2's normal template path because their concrete key may change after
     * each craft.
     */
    long extractPrimaryInput(IPatternDetails.IInput input, long multiplier, Level level,
            KeyCounter extractedInputs, KeyCounter expectedContainerItems,
            ECOCraftingRemainderCache remainderCache) {
        GenericStack primary = primaryInput(input);
        if (primary == null || multiplier < 0L || primary.amount() <= 0L
                || reusableTemplates.contains(primary.what())) return -1L;
        try {
            if (!input.isValid(primary.what(), level)) return -1L;
        } catch (RuntimeException unavailable) {
            return -1L;
        }

        long available = availableExact(primary.what());
        long removedAmount = removed.getLong(primary.what());
        if (removedAmount >= available) return 0L;
        available -= removedAmount;
        long protectedAmount = protectedAmounts.getOrDefault(primary.what(), 0L);
        if (protectedAmount >= available) return 0L;
        available -= protectedAmount;

        long crafts = Math.min(multiplier, available / primary.amount());
        if (crafts <= 0L) return 0L;
        long amount = Math.multiplyExact(crafts, primary.amount());
        removed.addTo(primary.what(), amount);
        extractedInputs.add(primary.what(), amount);
        AEKey remainder = remainderCache.get(input, primary.what());
        if (remainder != null) expectedContainerItems.add(remainder, crafts);
        return crafts;
    }

    static boolean hasReusableTemplates(IPatternDetails pattern, ECOCraftingRemainderCache remainderCache) {
        return !metadata(pattern, remainderCache).reusableTemplates().isEmpty();
    }

    private GenericStack primaryInput(IPatternDetails.IInput input) {
        try {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null
                    || possible[0].what() == null || !primaryInputs.contains(possible[0].what())) return null;
            return possible[0];
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private long availableExact(AEKey key) {
        if (source instanceof ListCraftingInventory list) return Math.max(0L, list.list.get(key));
        return Math.max(0L, source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
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

    private record PatternMetadata(long reloadGeneration, Set<AEKey> primaryInputs,
            Set<AEKey> possibleInputs, Set<AEKey> reusableTemplates) {
    }
}
