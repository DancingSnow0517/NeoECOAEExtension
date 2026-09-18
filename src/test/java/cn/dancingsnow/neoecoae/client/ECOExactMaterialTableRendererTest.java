package cn.dancingsnow.neoecoae.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOExactMaterialTableRendererTest {
    @Test
    void acyclicMissingPlanRendersGraphInventoryAndShortage() {
        AEKey diamond = mock(AEKey.class);
        when(diamond.formatAmount(anyLong(), any())).thenAnswer(call -> Long.toString(call.getArgument(0)));
        var node = new CraftingGraphSnapshot.MaterialNode(0, diamond, 137346430105L,
            21864722041L, 0, 115481708064L, CraftingGraphSnapshot.MaterialStatus.MISSING);
        var snapshot = new CraftingGraphSnapshot(0, List.of(node), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 1, 0, 0, 0, 0));
        assertTrue(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.MISSING_ITEMS, snapshot));
        assertFalse(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.MISSING_ITEMS,
            CraftingGraphSnapshot.EMPTY));
        assertFalse(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.PARTIAL_UNSUPPORTED, snapshot));
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        assertEquals(2, renderer.getEntryDescription(node).size());
        verify(diamond).formatAmount(21864722041L, AmountFormat.SLOT);
        verify(diamond).formatAmount(115481708064L, AmountFormat.SLOT);
        verify(diamond, never()).formatAmount(137346430105L, AmountFormat.SLOT);
        assertEquals(0x1AFF0000, renderer.getEntryOverlayColor(node));
        try (var rendering = mockStatic(AEKeyRendering.class)) {
            rendering.when(() -> AEKeyRendering.getTooltip(diamond)).thenReturn(new ArrayList<>());
            assertEquals(3, renderer.getEntryTooltip(node).size());
            verify(diamond).formatAmount(21864722041L, AmountFormat.FULL);
            verify(diamond).formatAmount(115481708064L, AmountFormat.FULL);
        }
    }
}
