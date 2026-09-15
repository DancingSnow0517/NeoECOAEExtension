package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;
import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public interface IECOPatternStorageService extends IGridService {
    /**
     * 获取此网络的总 {@link IECOPatternStorage}
     */
    IECOPatternStorage getPatternStorage();

    default ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return getPatternStorage().insertPreparedPattern(prepared);
    }

    /**
     * Like {@link #insertPreparedPattern}, but also reports whether a container absorbed the recipe.
     *
     * <p>Callers that clear the source slot after a successful insertion need this. Routing into a slot and
     * routing into a container look identical in the plain result, yet the slot stored the pattern's item,
     * so the network already holds it and the source is cleared without anything owed; a container stored
     * only the recipe, so its item is gone and the caller has to hand a blank back. Handing a blank back on
     * the slot path would mint one for a pattern that was merely moved.</p>
     *
     * <p>The default reports "not absorbed". That is only safe for a storage that keeps the pattern item
     * itself: a storage that absorbs recipes and leaves the default in place gets its source cleared with
     * nothing returned, so it must override this and supply a non-empty replacement.</p>
     */
    default ECOPatternInsertion insertPreparedPatternReporting(ECOPreparedPattern prepared) {
        return ECOPatternInsertion.of(insertPreparedPattern(prepared));
    }

    /**
     * The recipes the network currently exposes to autocrafting, decoded.
     *
     * <p>A container publishes recipes rather than items, and a consumer that needs to know what the network
     * would offer - to decide whether a disk has to be decoded, or whether a recipe is already reachable -
     * has no other way to ask. The default is empty rather than an exception: a grid without pattern storage
     * exposes nothing, which is a valid answer.</p>
     */
    default java.util.List<appeng.api.crafting.IPatternDetails> getExposedPatterns() {
        return java.util.List.of();
    }

    /**
     * What a consumed pattern is replaced with.
     *
     * <p>Hard contract: an implementation that reports {@code absorbedByContainer} <em>must</em> answer with
     * a non-empty stack here, one blank per encoded pattern that was consumed. The caller clears the source
     * only after the replacement reaches the network in full, so answering empty leaves the source pattern
     * in place - nothing is lost, but nothing is migrated either. Storages that never absorb can leave the
     * default.</p>
     */
    default ItemStack blankPatternReplacementFor(ItemStack pattern) {
        return ItemStack.EMPTY;
    }

    /**
     * Returns network-wide migration index progress. Discovered candidates are claimable while {@code ready} is
     * false; readiness only means that the scanner has reached the end of the current generation.
     */
    ExternalPatternIndexState getExternalPatternIndex(IGrid grid);

    ExternalPatternClaim claimExternalPatternCandidates(IGrid grid, UUID owner, int maxCandidates);

    void releaseExternalPatternCandidates(UUID owner);

    /** Releases one candidate without removing it from the index, for a temporary NO_SPACE result. */
    default void releaseExternalPatternCandidate(ECOPatternSourceSlot slot) {
    }

    /** Removes a source slot after migration has emptied it, keeping the cached candidate list current. */
    void removeExternalPatternCandidate(ECOPatternSourceSlot slot);

    boolean containsPatternInNetwork(ItemStack pattern);

    default long getPatternCapacityGeneration() {
        return 0L;
    }

    /** Applies one atomic pattern-bus mutation to the network catalog. */
    default void onPatternSlotsChanged(ECOCraftingPatternBusBlockEntity bus,
                                       int previousRevision,
                                       int[] changedSlots) {
    }

    record ExternalPatternIndexState(
            boolean ready,
            int scannedSlots,
            int totalSlots,
            List<ECOPatternSourceSlot> candidates,
            long lastScanNanos,
            long scanBudgetNanos,
            int scanBudgetHits,
            long totalScanNanos) {
        public ExternalPatternIndexState(boolean ready, int scannedSlots, int totalSlots,
                                         List<ECOPatternSourceSlot> candidates) {
            this(ready, scannedSlots, totalSlots, candidates, 0L, 0L, 0, 0L);
        }

        public ExternalPatternIndexState(boolean ready, int scannedSlots, int totalSlots,
                                         List<ECOPatternSourceSlot> candidates,
                                         long lastScanNanos, long scanBudgetNanos, int scanBudgetHits) {
            this(ready, scannedSlots, totalSlots, candidates, lastScanNanos, scanBudgetNanos, scanBudgetHits, 0L);
        }
    }

    record ExternalPatternClaim(
            boolean ready,
            int scannedSlots,
            int totalSlots,
            List<ECOPatternSourceSlot> candidates,
            long lastScanNanos,
            long scanBudgetNanos,
            int scanBudgetHits,
            long totalScanNanos) {
        public ExternalPatternClaim(boolean ready, int scannedSlots, int totalSlots,
                                    List<ECOPatternSourceSlot> candidates) {
            this(ready, scannedSlots, totalSlots, candidates, 0L, 0L, 0, 0L);
        }

        public ExternalPatternClaim(boolean ready, int scannedSlots, int totalSlots,
                                    List<ECOPatternSourceSlot> candidates,
                                    long lastScanNanos, long scanBudgetNanos, int scanBudgetHits) {
            this(ready, scannedSlots, totalSlots, candidates, lastScanNanos, scanBudgetNanos, scanBudgetHits, 0L);
        }
    }
}
