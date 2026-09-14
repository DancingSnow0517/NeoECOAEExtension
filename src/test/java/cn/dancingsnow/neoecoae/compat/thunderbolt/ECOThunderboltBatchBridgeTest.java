package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOThunderboltBatchBridgeTest {
    public record LegacyContext(IPatternDetails details, KeyCounter[] oneCopyTemplate,
            long maxCraft, Level level, UUID craftingJobId) {}

    @Test void legacyContextPreservesJobAndReturnsOnlyUnacceptedCopies() {
        verifyDispatch(true, false);
    }

    @Test void rejectedBatchReturnsAllCopies() {
        verifyDispatch(false, false);
    }

    @Test void submissionFailureIsNotReportedAsSafeRejection() {
        verifyDispatch(false, true);
    }

    private void verifyDispatch(boolean accepted, boolean failure) {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[]{new KeyCounter()};
        var level = mock(Level.class);
        var job = UUID.randomUUID();
        var batch = mock(ECOFastPathFacade.PreparedBatch.class);
        when(batch.craftCount()).thenReturn(3L);
        if (failure) when(batch.submit(any())).thenThrow(new IllegalStateException("commit failed"));
        else when(batch.submit(any())).thenReturn(accepted);
        try (var facade = mockStatic(ECOFastPathFacade.class)) {
            facade.when(() -> ECOFastPathFacade.prepareAllocated(bus, pattern, inputs, 8L, level, job))
                .thenReturn(batch);
            var context = new LegacyContext(pattern, inputs, 8L, level, job);
            if (failure) assertThrows(IllegalStateException.class,
                () -> ECOThunderboltBatchBridge.pushLegacy(bus, context));
            else assertEquals(accepted ? 5L : 8L, ECOThunderboltBatchBridge.pushLegacy(bus, context));
            verify(batch, times(1)).submit(any());
            facade.verify(() -> ECOFastPathFacade.prepareAllocated(bus, pattern, inputs, 8L, level, job));
        }
    }
}
