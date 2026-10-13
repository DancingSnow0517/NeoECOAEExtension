package cn.dancingsnow.neoecoae.gui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FittedTextTest {
    private static final float DELTA = 1.0E-6F;

    @Test
    void textThatFitsKeepsTheMaxScale() {
        assertEquals(0.75F, FittedText.fitScale(10, 100, 0.75F), DELTA);
    }

    @Test
    void wideTextShrinksToTheWidthRatio() {
        assertEquals(0.5F, FittedText.fitScale(40, 20, 1.0F), DELTA);
    }

    @Test
    void scaleIsCappedByMaxScaleEvenWhenWidthAllows() {
        assertEquals(0.25F, FittedText.fitScale(40, 20, 0.25F), DELTA);
    }

    @Test
    void emptyTextUsesWidthOneSoItNeverDividesByZero() {
        assertEquals(0.75F, FittedText.fitScale(0, 20, 0.75F), DELTA);
    }

    @Test
    void nonPositiveWidthGivesNoPositiveScale() {
        assertTrue(FittedText.fitScale(10, 0, 1.0F) <= 0.0F);
        assertTrue(FittedText.fitScale(10, -5, 1.0F) <= 0.0F);
    }

    @Test
    void scaledTextNeverExceedsTheWidth() {
        for (int textWidth = 1; textWidth <= 300; textWidth++) {
            for (int maxWidth = 1; maxWidth <= 200; maxWidth += 7) {
                float scale = FittedText.fitScale(textWidth, maxWidth, 1.0F);
                assertTrue(textWidth * scale <= maxWidth + 1.0E-3F,
                    "text " + textWidth + " in " + maxWidth + " at " + scale);
            }
        }
    }
}
