package cn.dancingsnow.neoecoae.integration.omnisequence;

import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.integration.common.processing.ProcessingReflection;
import com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OmniSequence registers its machines through NeoECOAE's provider API, but only flushes post-accounting
 * outputs from its own AE2/AdvancedAE CPU hooks. ECO CPUs therefore call Omni's registry after accounting.
 */
public final class ECOOmniSequenceOutputFlush {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("molecularmanipulator");

    private ECOOmniSequenceOutputFlush() {}

    public static void afterCpuAccounting(@Nullable ICraftingProvider provider) {
        if (!LOADED || provider == null) return;
        try {
            if (Registry.flush(provider)) return;
            // Providers exposed through a host block entity own the output on their logic.
            if (ProcessingReflection.call(provider, "getLogic") instanceof ICraftingProvider logic && logic != provider) {
                Registry.flush(logic);
            }
        } catch (RuntimeException failure) {
            // Omni retains unflushed output for its normal retry path; accounting is already settled.
            LOGGER.warn("OmniSequence post-accounting flush failed for {}", provider.getClass().getName(), failure);
        }
    }

    /** Keeps Omni types out of this class's linkage when the mod is absent. */
    private static final class Registry {
        static boolean flush(ICraftingProvider provider) {
            return OmniPostAccountingOutputAdapterRegistry.flushAfterCpuAccounting(provider);
        }
    }
}
