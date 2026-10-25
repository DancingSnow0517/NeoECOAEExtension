package cn.dancingsnow.neoecoae.compat;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ThunderPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ThunderboltApi;
import cn.dancingsnow.neoecoae.compat.useless.UselessPatternSemanticAdapter;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;

/** Optional pattern capabilities are registered here rather than enumerated by the planner. */
public final class ECOPatternIntegrations {
    private static boolean initialized;

    private ECOPatternIntegrations() {}

    public static synchronized void initialize() {
        if (initialized) return;
        cn.dancingsnow.neoecoae.compat.ae2.ECORemoteProviderLocator.initialize();
        cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge.initialize();
        if (ThunderboltApi.OVERLOAD != null) {
            ECOPatternSemanticsRegistry.register(id("thunderbolt_semantics"), 200,
                new ThunderPatternSemanticAdapter());
        }
        ECOPatternSemanticsRegistry.register(id("useless_semantics"), 100,
            new UselessPatternSemanticAdapter());
        registerWrapper("thunderbolt_provider_lookup", 200, ThunderboltApi.PROVIDER_LOOKUP,
            "providerLookupPattern");
        registerWrapper("thunderbolt_wrapper", 100, ThunderboltApi.WRAPPER, "wrappedPatternDetails");
        initialized = true;
    }

    private static void registerWrapper(String name, int priority, Class<?> contract, String methodName) {
        if (contract == null) return;
        Method method = ThunderboltApi.method(contract, methodName);
        ECOPatternWrappers.register(id(name), priority, contract,
            wrapper -> (IPatternDetails) ThunderboltApi.invoke(method, wrapper));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoecoae", path);
    }
}
