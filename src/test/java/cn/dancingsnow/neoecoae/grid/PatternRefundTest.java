package cn.dancingsnow.neoecoae.grid;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The migration clears source slots, so these rules are what stops a recipe the network already reaches
 * from destroying the pattern the player still owns.
 *
 * <p>Stacks are mocks on purpose: a unit test here runs without the item registry, and the rule worth
 * pinning down is how many blanks are owed and when clearing is allowed - neither depends on an item.</p>
 */
class PatternRefundTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    private static ItemStack counted(int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getCount()).thenReturn(amount);
        when(stack.isEmpty()).thenReturn(amount <= 0);
        return stack;
    }

    @Test
    void aBlankIsOwedForEveryConsumedPattern() {
        // A container records the recipe once, so a stack of four has to come back as four blanks.
        assertEquals(4, PatternRefund.blankCountFor(counted(4)));
    }

    @Test
    void aSinglePatternOwesASingleBlank() {
        assertEquals(1, PatternRefund.blankCountFor(counted(1)));
    }

    @Test
    void anEmptySourceStillOwesOneSoNothingIsHandedBackForFree() {
        assertEquals(1, PatternRefund.blankCountFor(counted(0)));
    }

    @Test
    void anEmptyReplacementNeverCovers() {
        // The storage could not say what a consumed pattern becomes; the source has to stay put.
        assertFalse(PatternRefund.covers(counted(0), 0));
    }

    @Test
    void nothingPlacedNeverCovers() {
        assertFalse(PatternRefund.covers(counted(2), 0));
    }

    @Test
    void aPartialHandBackStillRefusesToClear() {
        // Four owed, three placed: clearing would drop the fourth on the floor.
        assertFalse(PatternRefund.covers(counted(4), 3));
    }

    @Test
    void handingBackAtLeastWhatWasOwedCovers() {
        // Overshooting has already covered the loss; refusing here would strand the source for no gain.
        assertTrue(PatternRefund.covers(counted(2), 4));
    }

    @Test
    void fullCoverageAllowsClearing() {
        assertTrue(PatternRefund.covers(counted(4), 4));
    }

    // ---- the decision a caller makes from the storage's answer --------------------------------------

    @Test
    void aSlotStoredPatternIsAlwaysCleared() {
        // The slot kept the pattern as a stack, so the network already holds it: no refund is owed and
        // refusing here would leave the same pattern in two places.
        assertTrue(PatternRefund.mayClearSource(false, false));
        assertTrue(PatternRefund.mayClearSource(false, true));
    }

    @Test
    void anAbsorbedPatternIsClearedOnlyOnceTheBlankLanded() {
        assertTrue(PatternRefund.mayClearSource(true, true));
    }

    @Test
    void anAbsorbedPatternIsKeptWhenTheBlankDidNotLand() {
        // Nothing was left in its place, so clearing now would destroy a pattern the player still owns.
        assertFalse(PatternRefund.mayClearSource(true, false));
    }
}
