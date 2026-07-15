package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HostStylesheetTest {
    @Test
    void ecoStylesheetParses() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/assets/neoecoae/lss/eco.lss")) {
            assertNotNull(stream);
            assertNotNull(Stylesheet.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
}
