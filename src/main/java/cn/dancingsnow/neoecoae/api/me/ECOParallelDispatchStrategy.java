package cn.dancingsnow.neoecoae.api.me;

/**
 * Default ordinary dispatch policy. It fills the currently advertised provider capacity for the task, allowing
 * one task to occupy several independent ECO workers in the same tick while remaining bounded by the CPU budget.
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
