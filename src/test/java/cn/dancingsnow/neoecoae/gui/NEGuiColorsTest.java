package cn.dancingsnow.neoecoae.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NEGuiColorsTest {

    @Test
    void textColorMakesRgbOpaque() {
        assertEquals(0xff403e53, NEGuiColors.textColor(0x403e53));
    }

    @Test
    void textColorMakesBlackOpaque() {
        assertEquals(0xff000000, NEGuiColors.textColor(0));
    }

    @Test
    void textColorKeepsExistingAlpha() {
        assertEquals(0x80403e53, NEGuiColors.textColor(0x80403e53));
    }
}
