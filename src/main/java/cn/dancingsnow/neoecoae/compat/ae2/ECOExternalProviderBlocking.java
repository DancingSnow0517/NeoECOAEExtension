package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.compat.advanced_ae.ECOAdvancedAEPatternScaling;
import cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reads the live blocking switch before ECO batches work for an external provider. */
public final class ECOExternalProviderBlocking {
    private ECOExternalProviderBlocking() {}

    public static boolean isEnabled(ICraftingProvider provider) {
        if (provider instanceof LargeWorkstationPatternProvider) return false;
        Object logic = provider;
        if (!(logic instanceof PatternProviderLogic) && !ECOAdvancedAEPatternScaling.isProvider(logic)) {
            try {
                Method accessor = ProcessingReflection.publicNoArgMethod(provider.getClass(), "getLogic");
                if (accessor != null) logic = accessor.invoke(provider);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return true;
            }
        }
        if (logic == null) return false;
        if (logic instanceof LargeWorkstationPatternProvider) return false;
        try {
            if (logic instanceof PatternProviderLogic ae2) return ae2.isBlocking();
            if (ECOAdvancedAEPatternScaling.isProvider(logic)) {
                return ECOAdvancedAEPatternScaling.isBlocking(logic);
            }
            Method isBlocking = ProcessingReflection.publicNoArgMethod(logic.getClass(), "isBlocking");
            if (isBlocking != null && (isBlocking.getReturnType() == boolean.class || isBlocking.getReturnType() == Boolean.class)) {
                return Boolean.TRUE.equals(isBlocking.invoke(logic));
            }
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException unavailable) {
            return true;
        }
        try {
            Method accessor = ProcessingReflection.publicNoArgMethod(logic.getClass(), "getConfigManager");
            if (accessor == null) return false;
            Object config = accessor.invoke(logic);
            if (config instanceof IConfigManager manager && manager.getSettings().contains(Settings.BLOCKING_MODE)) {
                return manager.getSetting(Settings.BLOCKING_MODE) == YesNo.YES;
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return true;
        }
        return false;
    }
}
