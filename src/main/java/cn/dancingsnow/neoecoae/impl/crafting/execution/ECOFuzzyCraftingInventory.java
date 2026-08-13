package cn.dancingsnow.neoecoae.impl.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Applies computation-interface fuzzy item rules only to one CPU execution. */
public final class ECOFuzzyCraftingInventory implements ICraftingInventory {
    private final ListCraftingInventory delegate;
    private final Set<ResourceLocation> fuzzyItemIds;

    public ECOFuzzyCraftingInventory(
        ListCraftingInventory delegate,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.fuzzyItemIds = Set.copyOf(Objects.requireNonNull(fuzzyItemIds, "fuzzyItemIds"));
    }

    public static GenericStack tryExtractInitialItems(
        ICraftingPlan plan,
        IGrid grid,
        ListCraftingInventory inventory,
        IActionSource source,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        var storage = grid.getStorageService().getInventory();
        List<GenericStack> extracted = new ArrayList<>();
        for (var entry : plan.usedItems()) {
            AEKey requested = entry.getKey();
            long required = entry.getLongValue();
            long remaining = required;
            List<AEKey> candidateList = new ArrayList<>();
            if (isConfiguredFuzzy(requested, fuzzyItemIds)) {
                for (var entryCandidate : storage.getAvailableStacks().findFuzzy(requested, FuzzyMode.IGNORE_ALL)) {
                    candidateList.add(entryCandidate.getKey());
                }
            } else {
                candidateList.add(requested);
            }
            Iterable<AEKey> candidates = candidateList;
            for (AEKey candidate : candidates) {
                if (remaining <= 0L) {
                    break;
                }
                long taken = storage.extract(candidate, remaining, Actionable.MODULATE, source);
                if (taken > 0L) {
                    inventory.insert(candidate, taken, Actionable.MODULATE);
                    extracted.add(new GenericStack(candidate, taken));
                    remaining -= taken;
                }
            }
            if (remaining > 0L) {
                for (GenericStack stack : extracted) {
                    storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
                }
                inventory.clear();
                return new GenericStack(requested, remaining);
            }
        }
        return null;
    }

    public static boolean isConfiguredFuzzy(AEKey key, Set<ResourceLocation> fuzzyItemIds) {
        return key instanceof AEItemKey && fuzzyItemIds.contains(key.getId());
    }

    @Override
    public void insert(AEKey key, long amount, Actionable mode) {
        delegate.insert(key, amount, mode);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        return delegate.extract(key, amount, mode);
    }

    /** Extracts from any configured component variant matching a template key. */
    public long extractTemplate(AEKey key, long amount, Actionable mode) {
        if (!isConfiguredFuzzy(key, fuzzyItemIds)) {
            return delegate.extract(key, amount, mode);
        }
        long remaining = amount;
        long extracted = 0L;
        for (AEKey candidate : delegate.findFuzzyTemplates(key)) {
            if (remaining <= 0L) {
                break;
            }
            long taken = delegate.extract(candidate, remaining, mode);
            extracted += taken;
            remaining -= taken;
        }
        return extracted;
    }

    /** Extracts one already-selected concrete variant without expanding it again. */
    public long extractConcrete(AEKey key, long amount, Actionable mode) {
        return delegate.extract(key, amount, mode);
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
        if (isConfiguredFuzzy(key, fuzzyItemIds)) {
            return delegate.findFuzzyTemplates(key);
        }
        return delegate.list.get(key) > 0L ? List.of(key) : List.of();
    }
}
