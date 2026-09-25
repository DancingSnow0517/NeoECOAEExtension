package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.ECOThunderboltMixinPlugin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

/** Uses interfaces from the actual selected dependency jars, not local API stubs. */
class ThunderboltRuntimeContractTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void selectedRuntimeMatchesRequestedLayout() throws Exception {
        String expected = System.getProperty("neoeco.test.thunderboltLayout");
        if (expected != null) {
            assertEquals(expected.equals("legacy"), batchContract().getName().contains(".ae2.api."));
        }
    }

    @Test void absentThunderboltLeavesOrdinaryPatternsAndProvidersUsable() throws Exception {
        String api = ThunderboltApi.class.getName();
        String adapter = ThunderPatternSemanticAdapter.class.getName();
        String registry = PatternSemanticAdapters.class.getName();
        String dispatcher = "cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingPatternDispatcher$Contract";
        var isolated = Set.of(api, adapter, registry, dispatcher);
        var parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.moakiee.thunderbolt.")) throw new ClassNotFoundException(name);
                if (!isolated.contains(name)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        var defaults = (List<?>) loader.loadClass(registry).getMethod("defaults").invoke(null);
        assertTrue(defaults.stream().noneMatch(value -> value.getClass().getName().equals(adapter)));
        var instance = loader.loadClass(adapter).getConstructor().newInstance();
        assertEquals(false, instance.getClass().getMethod("supports", IPatternDetails.class)
                .invoke(instance, mock(IPatternDetails.class)));
        var provider = loader.loadClass(dispatcher).getDeclaredMethod("forProvider", ICraftingProvider.class);
        provider.setAccessible(true);
        assertNull(provider.invoke(null, mock(ICraftingProvider.class)));
    }

    @Test void providerApiIsNotInjectedIntoNeoEcoHosts() throws Exception {
        assertTrue(batchContract().isInterface());
        var plugin = new ECOThunderboltMixinPlugin();
        String prefix = "cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.";
        assertFalse(plugin.shouldApplyMixin("unused", prefix + "ECOThunderboltProviderMixin"));
        assertFalse(plugin.shouldApplyMixin("unused", prefix + "ECOLegacyThunderboltBridgeMixin"));
    }

    @Test void actualOverloadInterfaceMapsFuzzyInputAndRegistersAdapter() throws Exception {
        Class<?> contract = overloadContract();
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

    @Test void actualWrapperPreservesOutputsAndFuzzyMatching() throws Exception {
        var output = new GenericStack(AEItemKey.of(Items.STONE), 3);
        var source = mock(IPatternDetails.class);
        when(source.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(source.getOutputs()).thenReturn(List.of(output));
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IPatternDetails.class, overloadContract(), wrapperContract()},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDefinition" -> null;
                    case "wrappedPatternDetails" -> source;
                    case "hasFuzzyInputs" -> false;
                    case "isFuzzyOutput" -> true;
                    case "getOutputs" -> List.of(output);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var semantics = new ThunderPatternSemanticAdapter().analyze(pattern);
        assertTrue(semantics.supported(), semantics.unsupportedReason());
        assertEquals(List.of(output), semantics.producedOutputs());
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, semantics.matchingMode());
        assertFalse(semantics.cycleSafe());
    }

    @Test void cyclicWrapperIsRejected() throws Exception {
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IPatternDetails.class, overloadContract(), wrapperContract()},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDefinition" -> null;
                    case "wrappedPatternDetails" -> proxy;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var semantics = new ThunderPatternSemanticAdapter().analyze(pattern);
        assertFalse(semantics.supported());
        assertEquals("MALFORMED_THUNDER_PATTERN:IllegalArgumentException", semantics.unsupportedReason());
    }

    @Test void actualBatchProviderPreservesCapacityModeAndLeftovers() throws Exception {
        var details = mock(IPatternDetails.class);
        var inputs = new KeyCounter[0];
        Class<?> api = batchContract();
        var provider = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{api},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBatchCapacity" -> 64L;
                    case "getBatchDispatchMode" -> java.util.Arrays.stream(method.getReturnType().getEnumConstants())
                            .filter(value -> ((Enum<?>) value).name().equals("UNBOUNDED")).findFirst().orElseThrow();
                    case "pushBatch" -> {
                        assertSame(details, args[0]);
                        assertSame(inputs, args[1]);
                        yield (long) args[2] - 3L;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(64L, ThunderboltApi.invoke(
                ThunderboltApi.method(api, "getBatchCapacity", IPatternDetails.class), provider, details));
        assertEquals("UNBOUNDED", ((Enum<?>) ThunderboltApi.invoke(
                ThunderboltApi.method(api, "getBatchDispatchMode", IPatternDetails.class), provider, details)).name());
        assertEquals(7L, ThunderboltApi.invoke(ThunderboltApi.method(api, "pushBatch",
                IPatternDetails.class, KeyCounter[].class, long.class), provider, details, inputs, 10L));
    }

    @Test void reflectiveBatchPushPreservesProviderFailure() throws Exception {
        var failure = new IllegalStateException("provider failed after accepting inputs");
        var provider = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{batchContract()},
                (proxy, method, args) -> { throw failure; });
        var push = ThunderboltApi.method(batchContract(), "pushBatch",
                IPatternDetails.class, KeyCounter[].class, long.class);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ThunderboltApi.invoke(push, provider, null, new KeyCounter[0], 1L)));
    }

    @Test void nativeBatchContractCannotOverrideEcoOwnedMultiplierPolicy() throws Exception {
        var optionalSession = mock(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtBatchCapability.Session.class);
        try (var optional = mockStatic(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtBatchCapability.class)) {
                var provider = Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{batchContract(), ICraftingProvider.class},
                        (proxy, method, args) -> { throw new AssertionError("Unexpected native dispatch: " + method); });
                optional.when(() -> cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtBatchCapability.open(provider))
                        .thenReturn(optionalSession);
                assertNull(dispatcherMethod("forProvider", ICraftingProvider.class).invoke(null, provider));
            optional.verifyNoInteractions();
            verifyNoInteractions(optionalSession);
        }
    }

    private static Method dispatcherMethod(String name, Class<?>... parameters) throws Exception {
        var type = Class.forName("cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingPatternDispatcher$Contract");
        var method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Class<?> overloadContract() throws ClassNotFoundException {
        return contract("com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails",
                "com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails");
    }

    private static Class<?> wrapperContract() throws ClassNotFoundException {
        return contract("com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails",
                "com.moakiee.thunderbolt.ae2.overload.pattern.WrappedPatternDetails");
    }

    private static Class<?> batchContract() throws ClassNotFoundException {
        return contract("com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider",
                "com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider");
    }

    private static Class<?> contract(String modern, String legacy) throws ClassNotFoundException {
        try {
            return Class.forName(modern);
        } catch (ClassNotFoundException absent) {
            return Class.forName(legacy);
        }
    }
}
