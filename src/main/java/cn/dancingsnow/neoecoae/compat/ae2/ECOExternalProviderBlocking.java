package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.compat.advanced_ae.ECOAdvancedAEPatternScaling;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the live blocking switch before ECO batches work for an external provider. */
public final class ECOExternalProviderBlocking {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final Set<Class<?>> WARNED_PROVIDER_TYPES = ConcurrentHashMap.newKeySet();

    private ECOExternalProviderBlocking() {}

    public static boolean isEnabled(ICraftingProvider provider) {
        if (provider instanceof LargeWorkstationPatternProvider) return false;
        Object logic = provider;
        if (!(logic instanceof PatternProviderLogic) && !ECOAdvancedAEPatternScaling.isProvider(logic)) {
            if (provider instanceof PatternProviderLogicHost host) {
                logic = host.getLogic();
            } else {
                // Third-party providers may duck-type AE2's host contract without implementing it.
                try {
                    logic = provider.getClass().getMethod("getLogic").invoke(provider);
                } catch (NoSuchMethodException absent) {
                    // Some external providers expose their blocking setting directly.
                } catch (ReflectiveOperationException | RuntimeException unavailable) {
                    return failClosed(provider, "getLogic", unavailable);
                }
            }
        }
        if (logic == null) return false;
        if (logic instanceof LargeWorkstationPatternProvider) return false;
        try {
            if (logic instanceof PatternProviderLogic ae2) return ae2.isBlocking();
            if (ECOAdvancedAEPatternScaling.isProvider(logic)) {
                return ECOAdvancedAEPatternScaling.isBlocking(logic);
            }
            Method isBlocking = logic.getClass().getMethod("isBlocking");
            if (isBlocking.getReturnType() == boolean.class || isBlocking.getReturnType() == Boolean.class) {
                return Boolean.TRUE.equals(isBlocking.invoke(logic));
            }
        } catch (NoSuchMethodException absent) {
            // Fall back to the standard AE2 configuration setting.
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException unavailable) {
            return failClosed(provider, "isBlocking", unavailable);
        }
        try {
            Object config = logic.getClass().getMethod("getConfigManager").invoke(logic);
            if (config instanceof IConfigManager manager && manager.getSettings().contains(Settings.BLOCKING_MODE)) {
                return manager.getSetting(Settings.BLOCKING_MODE) == YesNo.YES;
            }
        } catch (NoSuchMethodException absent) {
            // Providers without a blocking setting can still use ECO batching.
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return failClosed(provider, "getConfigManager", unavailable);
        }
        return false;
    }

    /** An unreadable blocking switch is treated as enabled so ECO never batches past a provider's blocking mode. */
    private static boolean failClosed(ICraftingProvider provider, String accessor, Exception failure) {
        if (WARNED_PROVIDER_TYPES.add(provider.getClass())) {
            LOGGER.warn("Cannot read blocking mode of {} via {}; treating provider as blocking",
                provider.getClass().getName(), accessor, failure);
        }
        return true;
    }
}
