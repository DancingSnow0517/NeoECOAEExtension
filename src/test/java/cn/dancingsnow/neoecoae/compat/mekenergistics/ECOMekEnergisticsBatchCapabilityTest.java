package cn.dancingsnow.neoecoae.compat.mekenergistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.api.MeAeSupportOwner;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOMekEnergisticsBatchCapabilityTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void absentOptionalApiDoesNotClaimOrdinaryProvider() {
        assertNull(ECOMekEnergisticsBatchCapability.open(mock(ICraftingProvider.class)));
    }

    @Test
    void multipleLanesAndEncodedMultiplierAreScaledExactlyOnce() {
        var f = new Fixture();
        when(f.support.enqueueSmartPattern(eq(f.pattern), any())).thenAnswer(call -> {
            KeyCounter[] total = call.getArgument(1);
            assertEquals(2, total.length);
            assertEquals(60, total[0].get(AEItemKey.of(Items.IRON_INGOT)));
            assertEquals(40, total[1].get(AEItemKey.of(Items.GOLD_INGOT)));
            return true;
        });
        assertEquals(10, f.session.inspect(f.pattern, f.inputs, 10));
        assertEquals(0, f.session.submit(f.pattern, f.inputs, 10));
        assertEquals(6, f.inputs[0].get(AEItemKey.of(Items.IRON_INGOT)));
        verify(f.provider, never()).pushPattern(any(), any());
    }

    @Test
    void overflowIsRejectedBeforeQueueMutation() {
        var f = new Fixture();
        assertEquals(Long.MAX_VALUE, f.session.submit(f.pattern, f.inputs, Long.MAX_VALUE));
        verify(f.support, never()).enqueueSmartPattern(any(), any());
    }

    @Test
    void malformedOrAlreadyScaledPrototypeIsRejectedWithoutDisablingSmartMode() {
        var f = new Fixture();
        f.inputs[0].add(AEItemKey.of(Items.IRON_INGOT), 6);
        assertEquals(0, f.session.inspect(f.pattern, f.inputs, 10));
        assertEquals(10, f.session.submit(f.pattern, f.inputs, 10));
        verify(f.support, never()).enqueueSmartPattern(any(), any());
        verify(f.provider, never()).pushPattern(any(), any());
    }

    @Test
    void disabledBetweenInspectionAndSubmissionRejectsBatch() {
        var f = new Fixture();
        assertEquals(10, f.session.inspect(f.pattern, f.inputs, 10));
        when(f.provider.isSmartPatternMultiplicationEnabled()).thenReturn(false);
        assertEquals(10, f.session.submit(f.pattern, f.inputs, 10));
        verify(f.support, never()).enqueueSmartPattern(any(), any());
        verify(f.provider, never()).pushPattern(any(), any());
    }

    private static final class Fixture {
        final MeAeSupportOwner provider = mock(MeAeSupportOwner.class);
        final AbstractMeAeSupport<?> support = mock(AbstractMeAeSupport.class);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        final KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
        final ECOMekEnergisticsBatchCapability.Session session;

        Fixture() {
            var node = mock(IManagedGridNode.class);
            doReturn(support).when(provider).getPatternAeSupport();
            when(provider.isSmartPatternMultiplicationEnabled()).thenReturn(true);
            when(support.getMainNode()).thenReturn(node);
            when(node.isActive()).thenReturn(true);
            when(support.hasRegisteredPattern(pattern)).thenReturn(true);
            var iron = AEItemKey.of(Items.IRON_INGOT);
            var gold = AEItemKey.of(Items.GOLD_INGOT);
            var first = mock(IPatternDetails.IInput.class);
            var second = mock(IPatternDetails.IInput.class);
            when(first.getMultiplier()).thenReturn(3L);
            when(first.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(iron, 2)});
            when(second.getMultiplier()).thenReturn(1L);
            when(second.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(gold, 4)});
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{first, second});
            inputs[0].add(iron, 6);
            inputs[1].add(gold, 4);
            session = ECOMekEnergisticsBatchCapability.open(provider);
            assertNotNull(session);
        }
    }
}
