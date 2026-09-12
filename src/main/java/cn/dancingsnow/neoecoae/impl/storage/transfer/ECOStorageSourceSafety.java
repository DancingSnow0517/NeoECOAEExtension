package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.NEMath;

public final class ECOStorageSourceSafety {
    private ECOStorageSourceSafety() {
    }

    public static boolean isEffectivelyInfiniteSource(
        MEStorage storage,
        AEKey key,
        long visibleAmount,
        IActionSource source
    ) {
        long amountPerUnit = Math.max(1L, key.getAmountPerUnit());
        long conventionalInfiniteAmount = NEMath.saturatingMultiply(Integer.MAX_VALUE, amountPerUnit);
        if (visibleAmount < conventionalInfiniteAmount) {
            return false;
        }
        // Only probe keys already at the conventional infinity threshold. Ordinary finite
        // aggregated storage must remain importable even if its unbounded simulation saturates.
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
    }
}
