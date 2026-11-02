package cn.dancingsnow.neoecoae.compat.omnisequence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import com.atir.molecularmanipulator.api.crafting.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOOmniSequenceBatchAdapterTest {
    @Test void acceptedDeliveryAcknowledgesOwnershipExactlyOnce() { verifyDelivery(true, false); }
    @Test void rejectedDeliveryIsReturnedToCpuWithoutReceipt() { verifyDelivery(false, false); }
    @Test void receiptFailureNeverBecomesRejection() { verifyDelivery(true, true); }

    private void verifyDelivery(boolean accepted, boolean receiptThrows) {
        var provider = mock(ICraftingProvider.class);
        var level = mock(Level.class);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{mock(IPatternDetails.IInput.class)});
        var key = mock(AEKey.class);
        var outputs = List.of(new GenericStack(key, 5));
        var prepared = mock(ECOFastPathFacade.PreparedBatch.class);
        when(prepared.craftCount()).thenReturn(5L);
        when(prepared.outputs()).thenReturn(outputs);
        when(prepared.submit(any())).thenReturn(accepted);
        var delivery = mock(OmniBatchDelivery.class);
        when(delivery.request()).thenReturn(new OmniBatchRequest(UUID.randomUUID(), UUID.randomUUID(), pattern,
            5, List.of(new OmniBatchRequest.Input(0, key, 10)), outputs));
        if (receiptThrows) doThrow(new IllegalStateException("receipt failure")).when(delivery).accept(any());
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(eq(provider), eq(pattern), any(), anyLong(), eq(level), any()))
                .thenReturn(prepared);
            var admission = ECOOmniSequenceBatchAdapter.prepare(provider, level,
                new OmniBatchProbe(pattern, List.of(new OmniBatchProbe.Input(0, key, 2)), 5));
            assertNotNull(admission);
            if (receiptThrows) assertThrows(IllegalStateException.class, () -> admission.commit(delivery));
            else admission.commit(delivery);
            assertThrows(IllegalStateException.class, () -> admission.commit(delivery));
            verify(prepared).submit(any());
            verify(delivery, times(accepted ? 1 : 0)).accept(any());
            verify(delivery, times(accepted ? 0 : 1)).reject(any());
        }
    }
}
