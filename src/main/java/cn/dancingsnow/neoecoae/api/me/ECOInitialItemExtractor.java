package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceAdapterRegistry;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Transactional, write-coalesced acquisition of a plan's initial network inventory. */
final class ECOInitialItemExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInitialItemExtractor.class);
    private static final ECOStorageSourceAdapterRegistry ADAPTERS = new ECOStorageSourceAdapterRegistry();
    private static final ECOSophisticatedMutationBatch.FlushFailureSink FLUSH_FAILURES =
        new ECOSophisticatedMutationBatch.FlushFailureSink() {
            @Override
            public void onFlushFailure(Set<AEKey> keys, Throwable failure) {
                LOGGER.error("Failed to persist a batched initial crafting extraction for keys {}", keys, failure);
            }

            @Override
            public void onFlushSuccess(Set<AEKey> keys) {
                LOGGER.info("Recovered a previously failed initial crafting extraction flush for keys {}", keys);
            }
        };

    private ECOInitialItemExtractor() {
    }

    /**
     * Uses one network snapshot as the read phase, then performs the minimum one real extraction per required key.
     * Sophisticated handlers coalesce all resulting inventory saves into one save per physical handler.
     */
    static @Nullable GenericStack tryExtract(
            ICraftingPlan plan, IGrid grid, ListCraftingInventory cpuInventory, IActionSource source) {
        KeyCounter required = plan.usedItems();
        if (required.isEmpty()) return null;

        MEStorage storage = grid.getStorageService().getInventory();
        if (cpuInventory.list.isEmpty()) {
            KeyCounter available = new KeyCounter();
            storage.getAvailableStacks(available);
            for (var entry : required) {
                long requested = entry.getLongValue();
                if (requested <= 0L) continue;
                long present = Math.max(0L, available.get(entry.getKey()));
                if (present < requested) {
                    return new GenericStack(entry.getKey(), requested - present);
                }
            }
        }

        var adapter = ADAPTERS.select(grid, storage);
        try (var batch = adapter.mutationBatch(FLUSH_FAILURES)) {
            try {
                for (var entry : required) {
                    AEKey key = entry.getKey();
                    long requested = entry.getLongValue();
                    if (requested <= 0L) continue;
                    ECOSophisticatedMutationBatch.setCurrentKey(key);
                    long extracted = storage.extract(key, requested, Actionable.MODULATE, source);
                    cpuInventory.insert(key, extracted, Actionable.MODULATE);
                    if (extracted < requested) {
                        rollback(storage, cpuInventory, source);
                        return new GenericStack(key, requested - extracted);
                    }
                }
                return null;
            } catch (RuntimeException | Error failure) {
                try {
                    rollback(storage, cpuInventory, source);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                ECOSophisticatedMutationBatch.setCurrentKey(null);
            }
        }
    }

    private static void rollback(MEStorage storage, ListCraftingInventory cpuInventory, IActionSource source) {
        for (var stored : cpuInventory.list) {
            ECOSophisticatedMutationBatch.setCurrentKey(stored.getKey());
            long returned = storage.insert(
                stored.getKey(), stored.getLongValue(), Actionable.MODULATE, source);
            if (returned != stored.getLongValue()) {
                LOGGER.error("Initial crafting extraction rollback returned only {} of {} for {}",
                    returned, stored.getLongValue(), stored.getKey());
            }
        }
        cpuInventory.clear();
    }
}
