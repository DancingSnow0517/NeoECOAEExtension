package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

/** Transient provider traversal only; contains no job progress or material state. */
final class ECOProviderCursor {
    private static final int CHECKS_PER_TICK = 64;

    private final Map<IPatternDetails, Cursor> cursors = new HashMap<>();
    private CraftingService service;
    private long revision;
    private long budgetTick = Long.MIN_VALUE;
    private int remainingChecks;

    boolean beginPass(CraftingService currentService, long tick) {
        if (budgetTick != tick) {
            budgetTick = tick;
            remainingChecks = CHECKS_PER_TICK;
        }
        // Without the revision extension, only retain a snapshot for the current tick.
        long currentRevision = currentService instanceof ECOCraftingProviderRevision source
            ? source.neoecoae$getProviderRevision() : tick;
        if (service != currentService || revision != currentRevision) {
            cursors.clear();
            service = currentService;
            revision = currentRevision;
        }
        return remainingChecks > 0;
    }

    @Nullable
    ICraftingProvider nextAvailable(IPatternDetails pattern,
            Supplier<Iterable<ICraftingProvider>> providers) {
        if (remainingChecks <= 0) return null;
        Cursor cursor = cursors.computeIfAbsent(pattern, ignored -> {
            var snapshot = new ArrayList<ICraftingProvider>();
            providers.get().forEach(snapshot::add);
            return new Cursor(List.copyOf(snapshot));
        });
        int checks = Math.min(remainingChecks, cursor.providers.size());
        while (checks-- > 0) {
            var provider = cursor.providers.get(cursor.next);
            cursor.next = (cursor.next + 1) % cursor.providers.size();
            remainingChecks--;
            // Advance before returning, so rejection resumes at the next provider next tick.
            if (!provider.isBusy()) return provider;
        }
        return null;
    }

    void forget(IPatternDetails pattern) {
        cursors.remove(pattern);
    }

    void clear() {
        cursors.clear();
        service = null;
        // Do not replenish the shared tick budget when a job finishes or is replaced.
    }

    private static final class Cursor {
        final List<ICraftingProvider> providers;
        int next;

        Cursor(List<ICraftingProvider> providers) {
            this.providers = providers;
        }
    }
}
