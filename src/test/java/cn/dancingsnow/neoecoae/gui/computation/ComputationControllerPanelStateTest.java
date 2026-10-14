package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ComputationControllerPanelStateTest {
    @Test
    void packsPerCellTiersAndMirrors() {
        var state = ComputationControllerPanelState.of(3, List.of(1, 2), List.of(3, 3), List.of(0, 1), false);
        assertTrue(state.hasCell(0));
        assertTrue(state.hasCell(1));
        assertFalse(state.hasCell(2));
        assertEquals(1, state.upperTier(0));
        assertEquals(2, state.upperTier(1));
        assertEquals(3, state.coreTier(0));
        assertEquals(0, state.lowerTier(0));
        assertEquals(1, state.lowerTier(1));

        // Slot 0 and 1 move to the far end of the row: cells SLOT_COUNT-1 and SLOT_COUNT-2.
        var mirrored = ComputationControllerPanelState.of(3, List.of(1, 2), List.of(3, 3), List.of(0, 1), true);
        assertFalse(mirrored.hasCell(0));
        assertTrue(mirrored.hasCell(10));
        assertTrue(mirrored.hasCell(9));
        assertEquals(1, mirrored.upperTier(10));
        assertEquals(2, mirrored.upperTier(9));
    }

    @Test
    void clampsAndLimitsSlots() {
        var state = ComputationControllerPanelState.of(5, List.of(9), Collections.nCopies(20, 2), List.of(), false);
        assertEquals(3, state.hostTier());
        assertEquals(11, Integer.bitCount(state.cells()));
        assertEquals(3, state.upperTier(0));
        assertEquals(0, state.lowerTier(0));
    }

    @Test
    void tagRoundTripAndUnformed() {
        var state = ComputationControllerPanelState.of(3, List.of(1, 2), List.of(3, 3), List.of(0, 1), true);
        assertEquals(state, ComputationControllerPanelState.fromTag(state.toTag()));
        assertEquals(ComputationControllerPanelState.UNFORMED, ComputationControllerPanelState.fromTag(new CompoundTag()));
        assertEquals(ComputationControllerPanelState.UNFORMED, ComputationControllerPanelState.fromTag(null));
        assertFalse(ComputationControllerPanelState.UNFORMED.formed());
        assertTrue(state.formed());
    }
}
