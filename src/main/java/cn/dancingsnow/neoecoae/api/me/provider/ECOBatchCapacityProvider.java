package cn.dancingsnow.neoecoae.api.me.provider;

/**
 * @deprecated Use {@link ECOFastPathDispatchProvider}. This name is retained as a source and
 * binary integration bridge for providers compiled against the earlier API.
 */
@Deprecated
public interface ECOBatchCapacityProvider extends ECOFastPathDispatchProvider {
    @Override
    @org.jetbrains.annotations.Nullable
    default Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return eco$prepareBatch(context);
    }

    /** @deprecated Use {@link #eco$prepareFastPath(ECOBatchDispatchContext)}. */
    @Deprecated
    @org.jetbrains.annotations.Nullable
    default Preparation eco$prepareBatch(ECOBatchDispatchContext context) {
        return null;
    }
}
