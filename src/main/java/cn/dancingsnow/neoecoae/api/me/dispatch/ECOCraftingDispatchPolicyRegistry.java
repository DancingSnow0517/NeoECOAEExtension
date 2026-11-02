package cn.dancingsnow.neoecoae.api.me.dispatch;

import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.crafting.ICraftingProvider;

/** Process-wide registration point for scheduler policies. Every registered policy is a fail-closed veto. */
public final class ECOCraftingDispatchPolicyRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.api");
    private static final CopyOnWriteArrayList<ECOCraftingDispatchPolicy> POLICIES = new CopyOnWriteArrayList<>();

    private ECOCraftingDispatchPolicyRegistry() {
    }

    public static void register(ECOCraftingDispatchPolicy policy) {
        if (policy == null) throw new NullPointerException("policy");
        POLICIES.addIfAbsent(policy);
    }

    public static void unregister(ECOCraftingDispatchPolicy policy) {
        if (policy != null) POLICIES.remove(policy);
    }

    public static boolean mayTick(ECOCraftingCpuContext context) {
        for (var policy : POLICIES) {
            try {
                if (!policy.mayTick(context)) return false;
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting dispatch policy failed during mayTick; denying this tick", failure);
                return false;
            }
        }
        return true;
    }

    /** Public convenience overload; the raw provider busy state remains authoritative when no policies are present. */
    public static boolean isProviderAvailable(ECOCraftingCpuContext context, ICraftingProvider provider) {
        return isProviderAvailable(context, provider, provider != null && provider.isBusy());
    }

    @ApiStatus.Internal
    public static boolean isProviderAvailable(
            ECOCraftingCpuContext context, ICraftingProvider provider, boolean providerBusy) {
        if (provider == null) return false;
        if (POLICIES.isEmpty()) return !providerBusy;
        for (var policy : POLICIES) {
            try {
                if (!policy.isProviderAvailable(context, provider)) return false;
            } catch (RuntimeException failure) {
                LOGGER.warn("ECO crafting dispatch policy failed during provider selection; denying provider", failure);
                return false;
            }
        }
        return true;
    }
}
