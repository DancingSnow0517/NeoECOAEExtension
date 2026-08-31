package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.List;
import java.util.Objects;

/**
 * Policy seam for ordinary (one-craft-per-provider-call) dispatch.
 *
 * <p>The CPU still owns extraction, ownership transfer and accounting. A strategy only chooses the provider
 * order and how many ordinary calls may be attempted for the current task in this engine pass. This keeps
 * adaptive scheduling from having to duplicate the safety-critical dispatch transaction.</p>
 */
@FunctionalInterface
public interface ECOCraftingDispatchStrategy {
    DispatchDecision choose(DispatchContext context);

    /** Immutable snapshot supplied to a strategy for one task attempt. */
    record DispatchContext(
        IPatternDetails pattern,
        long taskRemaining,
        int dispatchBudget,
        List<ICraftingProvider> candidateProviders,
        int availableProviderSlots
    ) {
        public DispatchContext {
            Objects.requireNonNull(pattern, "pattern");
            taskRemaining = Math.max(0L, taskRemaining);
            dispatchBudget = Math.max(0, dispatchBudget);
            candidateProviders = List.copyOf(candidateProviders);
            availableProviderSlots = Math.max(0, availableProviderSlots);
        }
    }

    /** Provider order and ordinary-call budget selected for one task. */
    record DispatchDecision(List<ICraftingProvider> providers, int maxAttempts) {
        public DispatchDecision {
            providers = List.copyOf(providers);
            maxAttempts = Math.max(0, maxAttempts);
        }
    }
}
