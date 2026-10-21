package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternSearchDirectionsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void possibleIngredientsAndPrimaryOutputAreIndexedWithoutMixingByproducts() {
        var details = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        var copper = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1);
        // Unit bootstrap has no NeoForge fluid-type registry; use the same AE fluid-key API with a named key.
        var fluid = mock(AEFluidKey.class);
        when(fluid.getDisplayName()).thenReturn(Component.literal("Water"));
        var water = new GenericStack(fluid, 1000);
        var primary = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var byproduct = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{iron, copper, water});
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(details.getPrimaryOutput()).thenReturn(primary);
        when(details.getOutputs()).thenReturn(java.util.List.of(primary, byproduct).toArray(GenericStack[]::new));
        var terms = PatternSearchKeywords.directions(details);
        for (var ingredient : input.getPossibleInputs()) {
            assertTrue(terms.inputs().contains(ingredient.what().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT)));
        }
        assertEquals(primary.what().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT)
                + "\n@minecraft", terms.output());
        assertTrue(terms.inputs().contains("@minecraft"));
        assertFalse(terms.inputs().contains(terms.output()));
        verify(details, never()).getOutputs();
        assertEquals(PatternSearchKeywords.Directions.EMPTY, PatternSearchKeywords.directions(null));
    }
}
