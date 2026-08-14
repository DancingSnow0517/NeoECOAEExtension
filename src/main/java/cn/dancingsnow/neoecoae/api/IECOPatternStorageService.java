package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;
import appeng.api.networking.IGrid;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public interface IECOPatternStorageService extends IGridService {
    /**
     * 获取此网络的总 {@link IECOPatternStorage}
     */
    IECOPatternStorage getPatternStorage();

    /**
     * Returns the network-wide migration candidates. While the index is rebuilding, {@code ready} is false and the
     * caller must wait for a later tick instead of falling back to a full synchronous inventory scan.
     */
    ExternalPatternIndexState getExternalPatternIndex(IGrid grid);

    ExternalPatternClaim claimExternalPatternCandidates(IGrid grid, UUID owner, int maxCandidates);

    void releaseExternalPatternCandidates(UUID owner);

    /** Removes a source slot after migration has emptied it, keeping the cached candidate list current. */
    void removeExternalPatternCandidate(ECOPatternSourceSlot slot);

    boolean containsPatternInNetwork(ItemStack pattern);

    default long getPatternCapacityGeneration() {
        return 0L;
    }

    record ExternalPatternIndexState(
            boolean ready,
            int scannedSlots,
            int totalSlots,
            List<ECOPatternSourceSlot> candidates,
            long lastScanNanos,
            long scanBudgetNanos,
            int scanBudgetHits) {
        public ExternalPatternIndexState(boolean ready, int scannedSlots, int totalSlots,
                                         List<ECOPatternSourceSlot> candidates) {
            this(ready, scannedSlots, totalSlots, candidates, 0L, 0L, 0);
        }
    }

    record ExternalPatternClaim(
            boolean ready,
            int scannedSlots,
            int totalSlots,
            List<ECOPatternSourceSlot> candidates,
            long lastScanNanos,
            long scanBudgetNanos,
            int scanBudgetHits) {
        public ExternalPatternClaim(boolean ready, int scannedSlots, int totalSlots,
                                    List<ECOPatternSourceSlot> candidates) {
            this(ready, scannedSlots, totalSlots, candidates, 0L, 0L, 0);
        }
    }
}
