package cn.dancingsnow.neoecoae.api.me;

/**
 * Compatibility alias for integrations that previously installed the adaptive ordinary-dispatch policy.
 * Ordinary dispatch no longer applies a per-provider credit window.
 */
@Deprecated(forRemoval = true)
public final class ECOAdaptiveDispatchStrategy implements ECOCraftingDispatchStrategy {
    @Override
    public DispatchDecision choose(DispatchContext context) {
        return ECOParallelDispatchStrategy.INSTANCE.choose(context);
    }
}
