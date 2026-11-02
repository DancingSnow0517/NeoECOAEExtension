package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.Nullable;

public final class ECOBulkMarkerActions {
    private ECOBulkMarkerActions() {}

    /** One compression denomination at a time, without wrapping at either endpoint. */
    public static @Nullable AEItemKey shift(AEItemKey marker, int delta) {
        if (delta != -1 && delta != 1) return null;
        var items = ECOCompressionService.units(marker).items();
        int target = items.indexOf(marker) + delta;
        return target >= 0 && target < items.size() ? items.get(target) : null;
    }
}
