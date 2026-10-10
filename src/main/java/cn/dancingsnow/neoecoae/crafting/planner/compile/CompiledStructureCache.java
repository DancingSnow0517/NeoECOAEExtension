package cn.dancingsnow.neoecoae.crafting.planner.compile;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Bounded cross-order structural reuse. Inventory, numeric choices and search budgets are never cached. */
public final class CompiledStructureCache {
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_WEIGHT = 50_000;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    public record Structure(CompiledNetwork network, CondensationGraph condensation) {}
    private record Entry(WeakReference<ICraftingService> owner, long revision, AEKey goal,
            boolean cycles, Set<ResourceLocation> fuzzyItems, Structure structure, int weight) {}

    private CompiledStructureCache() {}

    public static long revision(ICraftingService service) {
        return service instanceof ECOCraftingProviderRevision source
                && source.neoecoae$isProviderSnapshotStable() ? source.neoecoae$getProviderRevision() : -1;
    }

    public static synchronized Structure get(ICraftingService service, long revision, AEKey goal,
            boolean cycles, Set<ResourceLocation> fuzzyItems) {
        ENTRIES.removeIf(entry -> entry.owner().get() == null
                || entry.owner().get() == service && entry.revision() != revision);
        if (revision < 0 || revision(service) != revision) return null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (matches(entry, service, revision, goal, cycles, fuzzyItems)) {
                ENTRIES.remove(i);
                ENTRIES.add(entry);
                return entry.structure();
            }
        }
        return null;
    }

    public static synchronized void put(ICraftingService service, long revision, AEKey goal,
            boolean cycles, Set<ResourceLocation> fuzzyItems, Structure structure) {
        if (revision < 0 || revision(service) != revision) return;
        long weight = (long) structure.network().reachablePatternCount() + structure.network().edgeCount()
                + structure.network().keys().size();
        if (weight > MAX_WEIGHT) return;
        ENTRIES.removeIf(entry -> entry.owner().get() == null
                || entry.owner().get() == service && (entry.revision() != revision
                    || matches(entry, service, revision, goal, cycles, fuzzyItems)));
        int totalWeight = ENTRIES.stream().mapToInt(Entry::weight).sum();
        while (!ENTRIES.isEmpty() && (ENTRIES.size() >= MAX_ENTRIES || totalWeight + weight > MAX_WEIGHT)) {
            totalWeight -= ENTRIES.removeFirst().weight();
        }
        ENTRIES.add(new Entry(new WeakReference<>(service), revision, goal, cycles,
                Set.copyOf(fuzzyItems), structure, (int) weight));
    }

    private static boolean matches(Entry entry, ICraftingService service, long revision, AEKey goal,
            boolean cycles, Set<ResourceLocation> fuzzyItems) {
        return entry.owner().get() == service && entry.revision() == revision && entry.goal().equals(goal)
                && entry.cycles() == cycles && entry.fuzzyItems().equals(fuzzyItems);
    }
}
