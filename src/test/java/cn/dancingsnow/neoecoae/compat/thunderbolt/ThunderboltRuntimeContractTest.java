package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.ECOThunderboltMixinPlugin;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Uses interfaces from the actual selected dependency jars, not local API stubs. */
class ThunderboltRuntimeContractTest {
    @Test void actualRuntimeSelectsExactlyOneBridge() throws Exception {
        String modern = "com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider";
        assertTrue(Class.forName(modern).isInterface());
        var plugin = new ECOThunderboltMixinPlugin();
        String prefix = "cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.";
        assertTrue(plugin.shouldApplyMixin("unused", prefix + "ECOThunderboltProviderMixin"));
        assertFalse(plugin.shouldApplyMixin("unused", prefix + "ECOLegacyThunderboltBridgeMixin"));
    }

    @Test void actualOverloadInterfaceMapsFuzzyInputAndRegistersAdapter() throws Exception {
        Class<?> contract = Class.forName(
            "com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails");
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{IPatternDetails.class, contract}, (proxy, method, args) -> {
                if (method.getName().equals("isFuzzyInput")) return (int) args[0] == 1;
                throw new UnsupportedOperationException(method.getName());
            });
        var adapter = new ThunderPatternSemanticAdapter();
        assertTrue(adapter.supports(pattern));
        assertFalse(adapter.ignoresComponents(pattern, 0));
        assertTrue(adapter.ignoresComponents(pattern, 1));
        assertInstanceOf(ThunderPatternSemanticAdapter.class,
            PatternSemanticAdapters.find(PatternSemanticAdapters.defaults(), pattern));
    }
}
