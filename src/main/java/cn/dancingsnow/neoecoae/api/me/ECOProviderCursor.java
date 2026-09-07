package cn.dancingsnow.neoecoae.api.me;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

/** Transient provider traversal only; contains no job progress or material state. */
final class ECOProviderCursor {
    private final Map<IPatternDetails, Cursor> cursors = new HashMap<>();
    private CraftingService service;
    private long revision;
    private long tick;

    void beginPass(CraftingService currentService, long tick) {
        this.tick = tick;
        long currentRevision = currentService instanceof ECOCraftingProviderRevision source
            ? source.neoecoae$getProviderRevision() : 0;
        if (service != currentService || revision != currentRevision) {
            cursors.clear();
            service = currentService;
            revision = currentRevision;
        }
    }

    @Nullable
    ICraftingProvider nextAvailable(IPatternDetails pattern,
            Supplier<Iterable<ICraftingProvider>> providers, Predicate<ICraftingProvider> eligible) {
        Cursor previous = cursors.get(pattern);
        Cursor cursor = previous;
        if (cursor == null || (!(service instanceof ECOCraftingProviderRevision) && cursor.tick != tick)) {
            var snapshot = new ArrayList<ICraftingProvider>();
            providers.get().forEach(snapshot::add);
            cursor = new Cursor(List.copyOf(snapshot), tick);
            // Refresh fallback snapshots each tick while preserving the next live provider by identity.
            if (previous != null && !previous.providers.isEmpty()) {
                var nextProvider = previous.providers.get(previous.next);
                for (int i = 0; i < snapshot.size(); i++) {
                    if (snapshot.get(i) == nextProvider) { cursor.next = i; break; }
                }
            }
            cursors.put(pattern, cursor);
        }
        int checks = cursor.providers.size();
        while (checks-- > 0) {
            var provider = cursor.providers.get(cursor.next);
            cursor.next = (cursor.next + 1) % cursor.providers.size();
            // Advance before returning, so rejection resumes at the next provider next tick.
            if (eligible.test(provider) && !provider.isBusy()) return provider;
        }
        return null;
    }

    void forget(IPatternDetails pattern) {
        cursors.remove(pattern);
    }

    void clear() {
        cursors.clear();
        service = null;
    }

    private static final class Cursor {
        final List<ICraftingProvider> providers;
        final long tick;
        int next;

        Cursor(List<ICraftingProvider> providers, long tick) {
            this.providers = providers;
            this.tick = tick;
        }
    }
}
