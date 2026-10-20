package cn.dancingsnow.neoecoae.gui.crafting;

import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingControllerPanelStateTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void mirrorsVisibleFxCellsAndParallelCoreTiers() {
        var state = CraftingControllerPanelState.of(
            3, List.of(1, 2), List.of(3, 1), 2, true, true, false, 250, 500);

        assertTrue(state.formed());
        assertTrue(state.hasFx(10));
        assertTrue(state.hasFx(9));
        assertEquals(1, state.upperTier(10));
        assertEquals(2, state.upperTier(9));
        assertEquals(3, state.lowerTier(10));
        assertEquals(1, state.lowerTier(9));
        assertEquals(2, state.fxCount());
    }

    @Test
    void coolantOverlayRequiresActiveCoolingAndAFluidAmount() {
        assertFalse(CraftingControllerPanelState.coolantVisible(false, fluid(), 100));
        assertFalse(CraftingControllerPanelState.coolantVisible(true, FluidStack.EMPTY, 100));
        assertFalse(CraftingControllerPanelState.coolantVisible(true, fluid(), 0));
        assertTrue(CraftingControllerPanelState.coolantVisible(true, fluid(), 1));
    }

    @Test
    void permilleClampsInvalidAndFullValues() {
        assertEquals(0, CraftingControllerPanelState.permille(0, 100));
        assertEquals(500, CraftingControllerPanelState.permille(1, 2));
        assertEquals(CraftingControllerPanelState.FULL, CraftingControllerPanelState.permille(101, 100));
        assertEquals(0, CraftingControllerPanelState.permille(1, 0));
    }

    @Test
    void tagRoundTripPreservesDisplayState() {
        var expected = CraftingControllerPanelState.of(
            2, List.of(1, 3), List.of(2, 0), 2, false, true, true, 345, 876);
        var actual = CraftingControllerPanelState.fromTag(expected.toTag());

        assertEquals(expected, actual);
    }

    private static FluidStack fluid() {
        return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
    }
}
