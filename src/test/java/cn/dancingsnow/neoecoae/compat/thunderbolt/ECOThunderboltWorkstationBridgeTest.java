package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.ECOThunderboltMixinPlugin;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOThunderboltWorkstationBridgeTest {
    @Test void mixinFollowsThunderboltBatchApiAvailability() {
        var plugin = new ECOThunderboltMixinPlugin();
        boolean available = getClass().getClassLoader().getResource(
            "com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.class") != null
            && getClass().getClassLoader().getResource(
                "com/moakiee/thunderbolt/api/crafting/batch/BatchJobView.class") != null;
        assertEquals(available, plugin.shouldApplyMixin(LargeWorkstationPatternProvider.class.getName(),
            "cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.ECOThunderboltWorkstationProviderMixin"));
    }

    @Test void scalesCopyWithoutMutatingBorrowedTemplateAndPassesJobId() {
        var provider = mock(LargeWorkstationPatternProvider.class);
        var pattern = mock(IPatternDetails.class);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var oneCopy = new KeyCounter();
        oneCopy.add(key, 3);
        var id = UUID.randomUUID();
        when(provider.eco$getAvailableParallelSlots()).thenReturn(512);
        when(provider.eco$pushPatternBatch(eq(pattern), any(), eq(512L), eq(id)))
            .thenAnswer(c -> {
                KeyCounter[] totals = c.getArgument(1);
                assertEquals(1536, totals[0].get(key));
                totals[0].clear();
                return true;
            });

        assertEquals(188, ECOThunderboltWorkstationBridge.push(provider, pattern,
            new KeyCounter[]{oneCopy}, 700, id));
        assertEquals(3, oneCopy.get(key));
    }

    @Test void rejectionReportsEntireSliceAsLeftover() {
        var provider = mock(LargeWorkstationPatternProvider.class);
        when(provider.eco$getAvailableParallelSlots()).thenReturn(512);
        var oneCopy = new KeyCounter();
        oneCopy.add(mock(AEKey.class, RETURNS_DEEP_STUBS), 1);
        assertEquals(600, ECOThunderboltWorkstationBridge.push(provider,
            mock(IPatternDetails.class), new KeyCounter[]{oneCopy}, 600, null));
    }
}
