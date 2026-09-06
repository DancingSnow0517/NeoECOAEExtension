package cn.dancingsnow.neoecoae.api.me;

/**
 * Default ordinary dispatch policy. The CPU supplies one visit slot per candidate provider, so this policy preserves
 * the provider order while bounding the pass by the remaining CPU operation budget.
 */
public final class ECOParallelDispatchStrategy implements ECOCraftingDispatchStrategy {
    public static final ECOParallelDispatchStrategy INSTANCE = new ECOParallelDispatchStrategy();

    private ECOParallelDispatchStrategy() {
    }

    @Override
    public DispatchDecision choose(DispatchContext context) {
        int attempts = Math.min(context.dispatchBudget(), context.availableProviderSlots());
        return new DispatchDecision(context.candidateProviders(), attempts);
    }
}
