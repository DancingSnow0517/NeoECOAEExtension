package cn.dancingsnow.neoecoae.compat.gtl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.reflect.Proxy;
import org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftIOPart;
import org.junit.jupiter.api.Test;

class GTLCraftingProviderCompatTest {
    @Test
    void nativeRulesRecognizeProvidersOutsideLegacyMachineInterfacesAndRefreshSettings() throws Exception {
        var nativeApi = GTLCraftingProviderCompat.NativeAutoExpand.resolve(NativeRules.class);
        ICraftingProvider enabledProvider = proxy(ICraftingProvider.class, 0L);
        NativeRules.enabledProvider = enabledProvider;
        assertTrue(nativeApi.canAutoExpand(enabledProvider));
        NativeRules.enabledProvider = null;
        assertFalse(nativeApi.canAutoExpand(enabledProvider));
    }

    @Test
    void nativeCapacityIsNegotiatedAndClampedForEachDispatch() throws Exception {
        var nativeApi = GTLCraftingProviderCompat.NativeAutoExpand.resolve(NativeRules.class);
        ICraftingProvider provider = proxy(IMECraftIOPart.class, 37L);
        IPatternDetails pattern = proxy(IPatternDetails.class, 0L);
        assertEquals(37L, nativeApi.getOperations(provider, pattern, 100L));
        assertEquals(10L, nativeApi.getOperations(provider, pattern, 10L));
    }

    @Test
    void nativeFailureDisablesExpansionInsteadOfAssumingUnlimitedCapacity() throws Exception {
        var nativeApi = GTLCraftingProviderCompat.NativeAutoExpand.resolve(FailingNativeRules.class);
        ICraftingProvider provider = proxy(IMECraftIOPart.class, 37L);
        assertFalse(nativeApi.canAutoExpand(provider));
        assertEquals(1L, nativeApi.getOperations(provider, proxy(IPatternDetails.class, 0L), 100L));
    }

    public static class NativeRules {
        static ICraftingProvider enabledProvider;

        public static boolean canAutoExpand(boolean processing, ICraftingProvider provider) {
            assertTrue(processing);
            return provider == enabledProvider;
        }

        public static long getOperations(
                boolean processing, ICraftingProvider provider, IPatternDetails pattern, long requested) {
            assertTrue(processing);
            return ((IMECraftIOPart) provider).gtlcore$getMaxPatternOperations(pattern, requested);
        }
    }

    public static class FailingNativeRules {
        public static boolean canAutoExpand(boolean processing, ICraftingProvider provider) {
            throw new IllegalStateException("Unavailable provider");
        }

        public static long getOperations(
                boolean processing, ICraftingProvider provider, IPatternDetails pattern, long requested) {
            throw new IllegalStateException("Unavailable downstream capacity");
        }
    }

    @Test
    void ignoresOrdinaryAe2Providers() {
        ICraftingProvider provider = proxy(ICraftingProvider.class, 0L);

        assertFalse(GTLCraftingProviderCompat.isAutoExpandProvider(provider));
    }

    @Test
    void recognizesGtlProviderAndNegotiatesCapacity() {
        ICraftingProvider provider = proxy(IMECraftIOPart.class, 37L);
        IPatternDetails pattern = proxy(IPatternDetails.class, 0L);

        assertTrue(GTLCraftingProviderCompat.isAutoExpandProvider(provider));
        assertEquals(37L, GTLCraftingProviderCompat.getMaxOperations(provider, pattern, 100L));
        assertEquals(10L, GTLCraftingProviderCompat.getMaxOperations(provider, pattern, 10L));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, long capacity) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().equals("gtlcore$getMaxPatternOperations")) {
                return capacity;
            }
            if (method.getName().equals("toString")) {
                return type.getSimpleName();
            }
            Class<?> returnType = method.getReturnType();
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == int.class) {
                return 0;
            }
            throw new UnsupportedOperationException(method.toString());
        });
    }
}
