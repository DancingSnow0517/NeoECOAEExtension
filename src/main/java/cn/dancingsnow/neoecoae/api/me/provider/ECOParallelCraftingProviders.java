package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.networking.crafting.ICraftingProvider;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a provider's parallel intake contract, including providers owned by other mods.
 *
 * <p>A provider that can take a batch of ordinary crafts says so by implementing
 * {@link ECOParallelCraftingProvider}. A machine from another mod cannot implement an interface of this mod
 * without depending on it, and its class has to stay loadable in a pack that ships no NEO ECO, so its
 * integration hands an adapter over here instead - keyed by the provider class. The dispatch paths then ask
 * <em>this</em> class rather than testing the interface, which is what makes the two kinds of provider
 * indistinguishable to them.</p>
 *
 * <p>An adapter's answer is cached per provider instance, weakly: a lookup sits on autocrafting paths, where
 * allocating an adapter per call would be waste, and a retired machine must not be kept alive by the cache.</p>
 *
 * <p>Registration happens while an integration loads - this mod's own setup, or another mod's - which is
 * before any dispatch. Nothing unregisters, in the same spirit as this mod's other registration facades: a
 * registered integration is present for the whole session.</p>
 */
public final class ECOParallelCraftingProviders {

    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.parallel");

    /**
     * Supplies the parallel contract of one provider class.
     *
     * <p>Called with a provider that is already an instance of the class it was registered for, and must
     * answer {@code null} when it has no contract after all - a machine can be present without being able to
     * take a batch right now.</p>
     */
    @FunctionalInterface
    public interface Adapter {

        /** @return the contract for {@code provider}, or {@code null} when it has none */
        @Nullable
        ECOParallelCraftingProvider adapt(ICraftingProvider provider);
    }

    private static final Map<Class<?>, Adapter> ADAPTERS = new ConcurrentHashMap<>();

    private static final Map<ICraftingProvider, WeakReference<ECOParallelCraftingProvider>> RESOLVED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ECOParallelCraftingProviders() {
    }

    /**
     * Teaches this mod that {@code providerType} providers can take a batch, through {@code adapter}.
     *
     * <p>Meant for an integration loading alongside this mod. Registering the same class twice replaces the
     * adapter, which keeps a reload in a development environment from stacking two of them.</p>
     */
    public static void register(Class<? extends ICraftingProvider> providerType, Adapter adapter) {
        ADAPTERS.put(providerType, adapter);
    }

    /**
     * @param provider a machine that may or may not be able to take a batch; may be {@code null}
     * @return its parallel contract, or {@code null} when it has none
     */
    @Nullable
    public static ECOParallelCraftingProvider find(@Nullable Object provider) {
        if (provider instanceof ECOParallelCraftingProvider own) {
            return own;
        }
        if (!(provider instanceof ICraftingProvider crafting)) {
            return null;
        }
        var known = RESOLVED.get(crafting);
        if (known != null) {
            var adapted = known.get();
            if (adapted != null) {
                return adapted;
            }
            RESOLVED.remove(crafting);
        }
        for (Map.Entry<Class<?>, Adapter> entry : ADAPTERS.entrySet()) {
            if (!entry.getKey().isInstance(crafting)) {
                continue;
            }
            ECOParallelCraftingProvider adapted;
            try {
                adapted = entry.getValue().adapt(crafting);
            } catch (RuntimeException broken) {
                // A broken adapter must not take the dispatch path down with it: this is called while a CPU is
                // choosing where to send a craft, where a throw would strand the job rather than skip a provider.
                LOGGER.warn("Parallel provider adapter for {} failed; treating the provider as slot-only",
                        entry.getKey().getName(), broken);
                return null;
            }
            if (adapted != null) {
                RESOLVED.put(crafting, new WeakReference<>(adapted));
                return adapted;
            }
        }
        return null;
    }
}
