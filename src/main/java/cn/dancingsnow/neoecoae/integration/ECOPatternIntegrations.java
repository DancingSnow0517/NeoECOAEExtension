package cn.dancingsnow.neoecoae.integration;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternWrappers;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.integration.thunderbolt.ThunderPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.integration.thunderbolt.ThunderboltApi;
import cn.dancingsnow.neoecoae.integration.useless.UselessPatternSemanticAdapter;
import java.lang.reflect.Method;
import net.minecraft.resources.Identifier;

/** Optional pattern capabilities are registered here rather than enumerated by the planner. */
public final class ECOPatternIntegrations {
    private static boolean initialized;

    private ECOPatternIntegrations() {}

    public static synchronized void initialize() {
        if (initialized) return;
        cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingMonitor.initialize();
        cn.dancingsnow.neoecoae.integration.ae2.ECORemoteProviderLocator.initialize();
        cn.dancingsnow.neoecoae.integration.thunderbolt.ECOOverloadCpuAccountingBridge.initialize();
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoecoae", path);
    }
}
