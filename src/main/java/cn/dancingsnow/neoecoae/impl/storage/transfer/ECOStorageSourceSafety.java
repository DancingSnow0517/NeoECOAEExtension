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
        // MEStorage is aggregated. If simulating an unbounded extraction is still unbounded,
        // conservatively treat the whole key as infinite instead of importing from it.
        if (storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE) {
            return true;
        }

        long amountPerUnit = Math.max(1L, key.getAmountPerUnit());
        long conventionalInfiniteAmount = NEMath.saturatingMultiply(Integer.MAX_VALUE, amountPerUnit);
        return visibleAmount >= conventionalInfiniteAmount;
    }
}
