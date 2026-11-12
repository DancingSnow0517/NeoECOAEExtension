package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationStatCardsTest {
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(UIElement.class.getName());
        }
    }

    @Test void byteUnitsHaveNoBinaryInfixAndMarkRoundedValues() {
        assertEquals("0 B", ComputationStatCards.bytes(0));
        assertEquals("1 KB", ComputationStatCards.bytes(1024));
        assertEquals("1.5 KB", ComputationStatCards.bytes(1536));
        assertEquals("≈8 EB", ComputationStatCards.bytes(Long.MAX_VALUE));
        assertEquals("2.14G", ComputationStatCards.count(Integer.MAX_VALUE));
    }

    @Test void cardsBuildToATitleLineAndACardComponent() throws Exception {
        try (var minecraft = mockStatic(Minecraft.class);
             var ldlib = mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlib.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
            var client = mock(Minecraft.class);
            Font font = mock(Font.class);
            when(font.width(any(FormattedText.class))).thenAnswer(call -> ((FormattedText) call.getArgument(0)).getString().length() * 6);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, font);
            minecraft.when(Minecraft::getInstance).thenReturn(client);

            var entry = new ComputationCpuEntry(1, "CPU #1", null, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                0, 0, Long.MAX_VALUE, Integer.MAX_VALUE, appeng.api.config.CpuSelectionMode.ANY,
                net.minecraft.resources.Identifier.parse("neoecoae:cpu.png"), "idle");
            assertCard(ComputationCards.cpu(entry, false), "CPU #1");
            assertCard(ComputationStatCards.storage(0, Long.MAX_VALUE), "GUI.NEOECOAE.COMPUTATION.UI.CARD.STORAGE_TITLE");
            assertCard(ComputationStatCards.threads(0, Integer.MAX_VALUE), "GUI.NEOECOAE.COMPUTATION.UI.CARD.THREADS_TITLE");
            assertCard(ComputationStatCards.parallel(1, 1, 8, 1), "GUI.NEOECOAE.COMPUTATION.UI.CARD.PARALLEL_TITLE");
        }
    }

    private static void assertCard(List<Object> card, String titlePrefix) {
        assertEquals(2, card.size());
        assertTrue(((Component) card.get(0)).getString().startsWith(titlePrefix), () -> card.get(0).toString());
        assertInstanceOf(ModularUITooltipComponent.class, card.get(1));
    }
}
