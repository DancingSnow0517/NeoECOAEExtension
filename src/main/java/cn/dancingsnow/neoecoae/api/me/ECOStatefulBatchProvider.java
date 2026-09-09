package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOStatefulBatchCalculator;
import org.jetbrains.annotations.Nullable;

/** Optional extension for providers that can calculate reusable/durability batch materials. */
public interface ECOStatefulBatchProvider extends ECOBatchCapacityProvider {
    /** Returns a calculator bound to the verified recipe for this exact synchronous dispatch. */
    @Nullable
    ECOStatefulBatchCalculator eco$getStatefulBatchCalculator(ECOBatchDispatchContext context);
}
