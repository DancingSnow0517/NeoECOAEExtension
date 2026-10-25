package cn.dancingsnow.neoecoae.compat.gregtech;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchInputScaling;
import cn.dancingsnow.neoecoae.mixins.compat.ECOOptionalAddonMixinPlugin;
import java.net.URL;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.junit.jupiter.api.Test;

class ECOGregTechProcessingDispatchTest {
    public interface CapacityProvider extends ICraftingProvider, IPatternProviderAutoExpand {}
    public interface Holder extends ICraftingProvider { Object getLogic(); }

    @Test void optionalCapacityContractsAreResolvedThroughProviderHostsAndBoundedToTheOffer() {
        var logic = mock(CapacityProvider.class);
        var host = mock(Holder.class);
        when(host.getLogic()).thenReturn(logic);
        var pattern = mock(IPatternDetails.class);
        when(logic.gtlcore$getMaxPatternOperations(pattern, 10)).thenReturn(50L);
        assertTrue(ECOGregTechProcessingDispatch.hasCapacityContract(host));
        assertEquals(10, ECOGregTechProcessingDispatch.maxOperations(host, pattern, new KeyCounter[0], 10));
        when(logic.gtlcore$getMaxPatternOperations(pattern, 10)).thenReturn(-50L);
        assertEquals(0, ECOGregTechProcessingDispatch.maxOperations(host, pattern, new KeyCounter[0], 10));
    }

    @Test void circuitConfigurationRuleRequiresBothGtlSupportAndTheExactItemIdentifier() {
        var circuit = mock(AEItemKey.class);
        when(circuit.getId()).thenReturn(new ResourceLocation("gtceu", "programmed_circuit"));
        var ordinary = mock(AEItemKey.class);
        when(ordinary.getId()).thenReturn(new ResourceLocation("gtceu", "silicon_ingot"));
        var scaling = ECOGregTechProcessingDispatch.inputScaling(mock(CapacityProvider.class));
        assertEquals(1, scaling.multiplier(circuit, 64));
        assertEquals(64, scaling.multiplier(ordinary, 64));
        assertEquals(64, ECOGregTechProcessingDispatch.inputScaling(mock(ICraftingProvider.class))
                .multiplier(circuit, 64));
    }

    @Test void nestedNativeAdmissionsRestoreTheirOwnInputContextEvenWhenTheInnerPushThrows() {
        var firstPattern = mock(IPatternDetails.class);
        var secondPattern = mock(IPatternDetails.class);
        var firstExecution = mock(IPatternDetails.class);
        var secondExecution = mock(IPatternDetails.class);
        var firstInputs = new KeyCounter[]{new KeyCounter()};
        var secondInputs = new KeyCounter[]{new KeyCounter()};
        var first = mock(ICraftingProvider.class);
        var second = mock(ICraftingProvider.class);
        when(second.pushPattern(secondPattern, secondInputs)).thenAnswer(call -> {
            assertSame(secondExecution, ECOGregTechProcessingDispatch.bufferExecutionPattern(secondPattern, secondInputs));
            assertSame(firstPattern, ECOGregTechProcessingDispatch.bufferExecutionPattern(firstPattern, firstInputs));
            throw new IllegalStateException("Native admission failed");
        });
        when(first.pushPattern(firstPattern, firstInputs)).thenAnswer(call -> {
            assertSame(firstExecution, ECOGregTechProcessingDispatch.bufferExecutionPattern(firstPattern, firstInputs));
            assertSame(firstPattern, ECOGregTechProcessingDispatch.bufferExecutionPattern(firstPattern, secondInputs));
            assertThrows(IllegalStateException.class, () -> ECOGregTechProcessingDispatch.pushBuffer(
                    second, secondPattern, secondExecution, secondInputs));
            assertSame(firstExecution, ECOGregTechProcessingDispatch.bufferExecutionPattern(firstPattern, firstInputs));
            return true;
        });
        assertTrue(ECOGregTechProcessingDispatch.pushBuffer(first, firstPattern, firstExecution, firstInputs));
        assertSame(firstPattern, ECOGregTechProcessingDispatch.bufferExecutionPattern(firstPattern, firstInputs));
        assertSame(secondPattern, ECOGregTechProcessingDispatch.bufferExecutionPattern(secondPattern, secondInputs));
    }

    @Test void aRecursiveHostCannotTrapTheDispatchThread() {
        var host = mock(Holder.class);
        when(host.getLogic()).thenReturn(host);
        assertFalse(ECOGregTechProcessingDispatch.hasCapacityContract(host));
        assertSame(ECOBatchInputScaling.LINEAR, ECOGregTechProcessingDispatch.inputScaling(host));
    }

    @Test void bridgeLoadsAndRunsWithoutAnyGregTechOrGtlClasses() throws Exception {
        var bridgeName = ECOGregTechProcessingDispatch.class.getName();
        var parent = getClass().getClassLoader();
        var isolated = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.gregtechceu.") || name.startsWith("org.gtlcore.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(bridgeName)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    var known = findLoadedClass(name);
                    if (known != null) return known;
                    try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        var loaded = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
            }
        };
        var bridge = isolated.loadClass(bridgeName);
        var provider = mock(ICraftingProvider.class);
        assertEquals(false, bridge.getMethod("isBuffer", Object.class).invoke(null, provider));
        assertSame(ECOBatchInputScaling.LINEAR,
                bridge.getMethod("inputScaling", Object.class).invoke(null, provider));
    }

    @Test void optionalMixinIsSkippedWhenTheNativeGtBufferResourceIsMissing() throws Exception {
        var parent = getClass().getClassLoader();
        var pluginName = ECOOptionalAddonMixinPlugin.class.getName();
        var isolated = new ClassLoader(parent) {
            @Override public URL getResource(String name) {
                return name.startsWith("com/gregtechceu/") ? null : super.getResource(name);
            }
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(pluginName)) return super.loadClass(name, resolve);
                try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                    assertNotNull(stream);
                    var bytes = stream.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        };
        var pluginClass = isolated.loadClass(pluginName);
        var plugin = pluginClass.getConstructor().newInstance();
        pluginClass.getMethod("onLoad", String.class).invoke(plugin, "cn.dancingsnow.neoecoae.mixins.compat.gregtech");
        assertEquals(false, pluginClass.getMethod("shouldApplyMixin", String.class, String.class)
                .invoke(plugin, "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine",
                        "cn.dancingsnow.neoecoae.mixins.compat.gregtech.GTMEPatternBufferMixin"));
    }
}
