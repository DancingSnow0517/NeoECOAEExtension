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
    void tierZeroMembersKeepPresenceTexturesAndMirroredPositionsThroughSync() {
        var texture = new net.minecraft.resources.ResourceLocation("addon:textures/gui/core.png");
        var zero = new ComputationControllerPanelState.Member(true, 0, texture);
        var fallback = new ComputationControllerPanelState.Member(true, 0, null);
        var state = ComputationControllerPanelState.ofMembers(0, List.of(zero), List.of(fallback),
            List.of(ComputationControllerPanelState.Member.ABSENT), true);
        var received = ComputationControllerPanelState.fromTag(state.toTag());
        assertEquals(state, received);
        assertFalse(received.hasCell(0));
        assertTrue(received.hasCell(10));
        assertEquals(zero, received.upperMember(10));
        assertEquals(fallback, received.coreMember(10));
        assertFalse(received.lowerMember(10).present());
        assertFalse(received.coreMember(-1).present());
        assertFalse(received.coreMember(11).present());
        assertEquals(0, received.coreTier(10));
    }

    @Test
    void oldSnapshotsRetainMissingUpperAndLowerRows() {
        var state = ComputationControllerPanelState.of(1, List.of(0), List.of(1), List.of(2), false);
        CompoundTag legacy = state.toTag();
        legacy.remove("upperMembers"); legacy.remove("coreMembers"); legacy.remove("lowerMembers");
        assertEquals(state, ComputationControllerPanelState.fromTag(legacy));
        assertFalse(state.upperMember(0).present());
        assertTrue(state.coreMember(0).present());
        assertTrue(state.lowerMember(0).present());
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
