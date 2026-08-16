package cn.dancingsnow.neoecoae.impl.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Allocation allocation = allocateInitialItems(plan, storage, source, fuzzyItemIds);
        if (!allocation.missing().isEmpty()) {
            var first = allocation.missing().entrySet().iterator().next();
            return new GenericStack(first.getKey(), first.getValue());
        }

        List<Reservation> reservations = allocation.reservations();
        List<GenericStack> extracted = new ArrayList<>(reservations.size());
        for (Reservation reservation : reservations) {
            long taken = storage.extract(reservation.concreteKey(), reservation.amount(), Actionable.MODULATE, source);
            if (taken > 0L) {
                inventory.insert(reservation.concreteKey(), taken, Actionable.MODULATE);
                extracted.add(new GenericStack(reservation.concreteKey(), taken));
            }
            if (taken != reservation.amount()) {
                // A provider changed after the complete simulated allocation. Restore concrete
                // variants and leave this CPU with no owned inputs.
                restoreInitialReservation(storage, source, inventory, extracted);
                return new GenericStack(reservation.requestedKey(), reservation.amount() - taken);
            }
        }
        return null;
    }

    /** Returns every input deficit against the current network contents without mutating storage. */
    public static Map<AEKey, Long> auditMissingInitialItems(
        ICraftingPlan plan,
        IGrid grid,
        IActionSource source,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        return Map.copyOf(allocateInitialItems(
            plan,
            grid.getStorageService().getInventory(),
            source,
            fuzzyItemIds
        ).missing());
    }

    private static Allocation allocateInitialItems(
        ICraftingPlan plan,
        MEStorage storage,
        IActionSource source,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        List<Reservation> reservations = new ArrayList<>();
        Map<AEKey, Long> missing = new LinkedHashMap<>();
        Map<AEKey, Long> available = new LinkedHashMap<>();
        for (var entry : plan.usedItems()) {
            AEKey requested = entry.getKey();
            long required = entry.getLongValue();
            List<AEKey> candidateList = new ArrayList<>();
            if (isConfiguredFuzzy(requested, fuzzyItemIds)) {
                for (var entryCandidate : storage.getAvailableStacks().findFuzzy(requested, FuzzyMode.IGNORE_ALL)) {
                    candidateList.add(entryCandidate.getKey());
                }
            } else {
                candidateList.add(requested);
            }
            long remaining = required;
            for (AEKey candidate : candidateList) {
                if (remaining <= 0L) {
                    break;
                }
                long candidateAvailable = available.computeIfAbsent(
                    candidate,
                    key -> storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source)
                );
                long allocated = Math.min(remaining, candidateAvailable);
                if (allocated > 0L) {
                    reservations.add(new Reservation(requested, candidate, allocated));
                    available.put(candidate, candidateAvailable - allocated);
                    remaining -= allocated;
                }
            }
            if (remaining > 0L) {
                missing.merge(requested, remaining, ECOFuzzyCraftingInventory::saturatingAdd);
            }
        }
        return new Allocation(List.copyOf(reservations), Map.copyOf(missing));
    }

    private static void restoreInitialReservation(
        MEStorage storage,
        IActionSource source,
        ListCraftingInventory inventory,
        List<GenericStack> extracted
    ) {
        for (GenericStack stack : extracted) {
            long restored = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (restored != stack.amount()) {
                throw new IllegalStateException("Failed to restore initial crafting reservation");
            }
        }
        inventory.clear();
    }

    private record Reservation(AEKey requestedKey, AEKey concreteKey, long amount) {
    }

    private record Allocation(List<Reservation> reservations, Map<AEKey, Long> missing) {
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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
