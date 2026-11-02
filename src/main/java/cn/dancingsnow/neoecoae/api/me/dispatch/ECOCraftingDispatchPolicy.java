package cn.dancingsnow.neoecoae.api.me.dispatch;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * Optional veto/selection policy for ECO's CPU scheduler.
 *
 * <p>The policy is consulted in addition to ECO's own ownership and rollback checks. A policy must not perform
 * input extraction, provider pushes, or task mutation from either callback.</p>
 */
public interface ECOCraftingDispatchPolicy {

    default boolean mayTick(ECOCraftingCpuContext cpu) {
        return true;
    }

    default boolean isProviderAvailable(ECOCraftingCpuContext cpu, ICraftingProvider provider) {
        return !provider.isBusy();
    }
}
