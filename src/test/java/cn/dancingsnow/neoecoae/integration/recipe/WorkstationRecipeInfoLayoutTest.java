package cn.dancingsnow.neoecoae.integration.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationRecipeInfoLayoutTest {
    @Test void noCardsReclaimsTheReservedSpace() {
        assertEquals(154, new WorkstationRecipeInfoLayout(0).textWidth());
        assertEquals(137, new WorkstationRecipeInfoLayout(1).textWidth());
        assertEquals(83, new WorkstationRecipeInfoLayout(4).textWidth());
    }

    @Test void singleCardRetainsEqualTopBottomAndRightPadding() {
        var layout = new WorkstationRecipeInfoLayout(1);
        assertEquals(2, layout.cardY(0) - WorkstationRecipeInfoLayout.INFO_Y);
        assertEquals(2, WorkstationRecipeInfoLayout.INFO_Y + layout.infoHeight() - layout.cardY(0) - 16);
        assertEquals(2, 164 - layout.cardX(0) - 16);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 8, 9, 17, 40})
    void cardsNeverOverlapTheTextOrEscapeTheFooter(int count) {
        var layout = new WorkstationRecipeInfoLayout(count);
        assertTrue(layout.textWidth() >= 83);
        assertTrue(layout.energyY() > WorkstationRecipeInfoLayout.INFO_Y);
        assertTrue(layout.extraY() + 9 * 0.75f < WorkstationRecipeInfoLayout.INFO_Y + layout.infoHeight());
        assertTrue(layout.extraSlotsY() >= WorkstationRecipeInfoLayout.INFO_Y + layout.infoHeight() + 4);
        for (int i = 0; i < count; i++) {
            assertTrue(layout.cardX(i) >= WorkstationRecipeInfoLayout.TEXT_X + layout.textWidth() + 2);
            assertTrue(layout.cardX(i) + 16 <= 162);
            assertTrue(layout.cardY(i) >= 74);
            assertTrue(layout.cardY(i) + 16 <= 72 + layout.infoHeight() - 2);
            for (int j = i + 1; j < count; j++) {
                assertTrue(layout.cardX(i) + 16 <= layout.cardX(j) || layout.cardX(j) + 16 <= layout.cardX(i)
                    || layout.cardY(i) + 16 <= layout.cardY(j) || layout.cardY(j) + 16 <= layout.cardY(i));
            }
        }
    }

    @Test void extraCardRowsMoveTheFooterAndAdditionalIngredientsTogether() {
        var layout = new WorkstationRecipeInfoLayout(5);
        assertEquals(38, layout.infoHeight());
        assertEquals(114, layout.backgroundHeight());
        assertEquals(118, layout.extraSlotsY());
        assertEquals(114, layout.height(0, 0));
        assertEquals(154, layout.height(3, 2));
        assertEquals(146, layout.cardX(4));
        assertEquals(92, layout.cardY(4));
        assertFalse(layout.containsText(layout.cardX(0), layout.cardY(0)));
        assertTrue(layout.containsText(7, layout.energyY()));
    }

    @Test void compactAmountsUseSharedUnitsWithoutLosingTheExactRecipeValue() {
        assertEquals("0", LargeWorkstationRecipeDisplay.compactAmount(0));
        assertEquals("999", LargeWorkstationRecipeDisplay.compactAmount(999));
        assertEquals("1K", LargeWorkstationRecipeDisplay.compactAmount(1000));
        assertEquals("500K", LargeWorkstationRecipeDisplay.compactAmount(500_000));
        assertEquals("16.3M", LargeWorkstationRecipeDisplay.compactAmount(16_384_000));
        assertEquals("1G", LargeWorkstationRecipeDisplay.compactAmount(1_000_000_000));
        assertEquals("1T", LargeWorkstationRecipeDisplay.compactAmount(1_000_000_000_000L));
        assertEquals("9.22E", LargeWorkstationRecipeDisplay.compactAmount(Long.MAX_VALUE));
    }

    @Test void invalidCountsAndIndicesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorkstationRecipeInfoLayout(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> new WorkstationRecipeInfoLayout(0).cardX(0));
        assertThrows(IndexOutOfBoundsException.class, () -> new WorkstationRecipeInfoLayout(2).cardY(2));
    }
}
