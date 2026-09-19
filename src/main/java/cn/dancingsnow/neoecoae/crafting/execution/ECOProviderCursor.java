package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingProviderRevision;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Transient provider traversal only; contains no job progress or material state. */
final class ECOProviderCursor {
    private final Map<IPatternDetails, Cursor> cursors = new HashMap<>();
    private CraftingService service;
    private long revision;
    private long tick;
    private final Set<ICraftingProvider> indeterminate = new HashSet<>();

    void beginPass(CraftingService currentService, long tick) {
        boolean newTick = this.tick != tick;
        this.tick = tick;
        if (newTick) indeterminate.clear();
        long currentRevision = currentService instanceof ECOCraftingProviderRevision source
                ? source.neoecoae$getProviderRevision()
                : 0;
        if (service != currentService || revision != currentRevision) {
            cursors.clear();
            indeterminate.clear();
            service = currentService;
            revision = currentRevision;
        }
    }

    /** Suppress a provider after a commit whose result could not be determined. */
    void suppressIndeterminate(ICraftingProvider provider) {
        indeterminate.add(provider);
    }

    @Nullable ICraftingProvider nextAvailable(
            IPatternDetails pattern,
            Supplier<Iterable<ICraftingProvider>> providers,
            Predicate<ICraftingProvider> eligible) {
        var available = availableProviders(pattern, providers, eligible);
        if (available.isEmpty()) return null;
        var selected = available.get(0);
        advanceAfter(pattern, selected);
        return selected;
    }

    /**
     * Returns each eligible provider once, starting at the transient round-robin cursor.
     *
     * <p>The caller can try the providers in this order without resolving the pattern inputs again. Scanning
     * a full snapshot preserves the cursor; the caller must call {@link #advanceAfter} only when actually
     * attempting a provider, so skipped providers cannot undo rejection fairness.</p>
     */
    List<ICraftingProvider> availableProviders(
            IPatternDetails pattern,
            Supplier<Iterable<ICraftingProvider>> providers,
            Predicate<ICraftingProvider> eligible) {
        return availableProviders(pattern, providers, eligible, (provider, busy) -> {});
    }

    List<ICraftingProvider> availableProviders(
            IPatternDetails pattern,
            Supplier<Iterable<ICraftingProvider>> providers,
            Predicate<ICraftingProvider> eligible,
            BiConsumer<ICraftingProvider, Boolean> observer) {
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
                    if (snapshot.get(i) == nextProvider) {
                        cursor.next = i;
                        break;
                    }
                }
            }
            cursors.put(pattern, cursor);
        }
        if (cursor.providers.isEmpty()) return List.of();

        var available = new ArrayList<ICraftingProvider>(cursor.providers.size());
        int size = cursor.providers.size();
        int scanIndex = cursor.next;
        for (int checked = 0; checked < size; checked++) {
            var provider = cursor.providers.get(scanIndex);
            scanIndex = (scanIndex + 1) % size;
            if (indeterminate.contains(provider)) continue;
            // Enumeration never moves the shared cursor, even if a provider's busy check throws.
            // Only advanceAfter records a dispatch attempt.
            if (eligible.test(provider)) {
                boolean busy = provider.isBusy();
                observer.accept(provider, busy);
                if (!busy) available.add(provider);
            }
        }
        return List.copyOf(available);
    }

    /** Records the provider currently being attempted so the next pass starts after it. */
    void advanceAfter(IPatternDetails pattern, ICraftingProvider provider) {
        Cursor cursor = cursors.get(pattern);
        if (cursor == null || cursor.providers.isEmpty()) {
            return;
        }
        for (int index = 0; index < cursor.providers.size(); index++) {
            if (cursor.providers.get(index) == provider) {
                cursor.next = (index + 1) % cursor.providers.size();
                return;
            }
        }
    }

    void forget(IPatternDetails pattern) {
        cursors.remove(pattern);
    }

    void clear() {
        cursors.clear();
        indeterminate.clear();
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
