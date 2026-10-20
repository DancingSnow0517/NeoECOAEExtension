package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOExtendedAEPlusMatrixBridgeTest {
    abstract static class UltimateProvider extends SuperAssemblerMatrixBlockEntity {}

    @Test void superMatrixDispatchesScaledPatternAndCopiedTotalSlots() { dispatch(SuperAssemblerMatrixBlockEntity.class); }
    @Test void ultimateMatrixUsesSameProviderSuperclass() { dispatch(UltimateProvider.class); }

    @Test void attachedPatternCoreUsesSuperMatrixQueueButOrdinaryCoreDoesNot() {
        var core = mock(com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity.class);
        assertFalse(ECOExtendedAEPlusMatrixBridge.supportsProvider(core));
        when(core.eap$getSuperMatrixCluster()).thenReturn(new Object());
        assertTrue(ECOExtendedAEPlusMatrixBridge.supportsProvider(core));
        dispatch(com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity.class);
    }

    private void dispatch(Class<? extends appeng.api.networking.crafting.ICraftingProvider> type) {
        var provider = mock(type);
        if (provider instanceof com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity core) {
            when(core.eap$getSuperMatrixCluster()).thenReturn(new Object());
        }
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 2)});
        var pattern = mock(IMolecularAssemblerSupportedPattern.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        var context = new ECOBatchDispatchContext(pattern, List.of(List.of(new GenericStack(key, 2))),
            List.of(new GenericStack(key, 1)), List.of(), null, null);
        // Queue/ownership contract test: recipe materialization is verified separately in production.
        // Stubbing a CALLS_REAL_METHODS mock would execute verify() before the stub is installed,
        // initializing Minecraft registries outside bootstrap and poisoning the shared test JVM.
        try (var bridge = mockStatic(ECOExtendedAEPlusMatrixBridge.class, invocation ->
                invocation.getMethod().getName().equals("verify") ? true : invocation.callRealMethod())) {
            var preparation = ECOExtendedAEPlusMatrixBridge.adapt(provider).eco$prepareFastPath(context);
            assertNotNull(preparation);
            when(provider.pushPattern(any(), any())).thenAnswer(call -> {
                var scaled = (ScaledMolecularAssemblerPattern) call.getArgument(0);
                KeyCounter[] slots = call.getArgument(1);
                assertEquals(5, scaled.multiplier);
                assertEquals(10, slots[0].get(key));
                slots[0].clear();
                return true;
            });
            var batch = new ECOFastPathDispatchProvider.Batch(5, List.of(new GenericStack(key, 10)),
                List.of(new GenericStack(key, 5)), List.of());
            assertTrue(preparation.push(batch));
            assertEquals(2, context.inputCounters()[0].get(key));
            when(provider.isBusy()).thenReturn(true);
            assertFalse(preparation.push(batch));
            when(provider.isBusy()).thenReturn(false);
            if (provider instanceof com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity core) {
                when(core.eap$getSuperMatrixCluster()).thenReturn(null);
                assertFalse(preparation.push(batch));
                when(core.eap$getSuperMatrixCluster()).thenReturn(new Object());
            }
            doReturn(false).when(provider).pushPattern(any(), any());
            assertFalse(preparation.push(batch));
            doThrow(new IllegalStateException("after enqueue")).when(provider).pushPattern(any(), any());
            assertThrows(ECOIndeterminateBatchException.class, () -> preparation.push(batch));
            when(input.getRemainingKey(key)).thenReturn(key);
            assertNull(ECOExtendedAEPlusMatrixBridge.adapt(provider).eco$prepareFastPath(context));
        }
    }

    @Test void inputMultiplicationOverflowDeclinesWithoutMutation() {
        var key = mock(AEKey.class);
        var counter = new KeyCounter();
        counter.add(key, Long.MAX_VALUE);
        assertNull(ECOExtendedAEPlusMatrixBridge.multiplyInputHolder(new KeyCounter[]{counter}, 2));
        assertEquals(Long.MAX_VALUE, counter.get(key));
    }
}
