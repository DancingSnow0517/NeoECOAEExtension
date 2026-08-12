package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;
import appeng.api.networking.IGrid;

import java.util.List;

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

    /** Removes a source slot after migration has emptied it, keeping the cached candidate list current. */
    void removeExternalPatternCandidate(ECOPatternSourceSlot slot);

    record ExternalPatternIndexState(
            boolean ready,
            int scannedSlots,
            int totalSlots,
            List<ECOPatternSourceSlot> candidates) {
    }
}
