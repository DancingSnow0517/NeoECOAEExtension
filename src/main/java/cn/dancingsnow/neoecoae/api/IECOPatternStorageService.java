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
