package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.client.gui.ClientModularUITooltipComponent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationStatCardsTest {
    private static final String CARD = "gui.neoecoae.computation.ui.card.";

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void byteUnitsHaveNoBinaryInfixAndMarkRoundedValues() {
        assertEquals("0 B", ComputationStatCards.bytes(0));
        assertEquals("1 KB", ComputationStatCards.bytes(1024));
        assertEquals("1.5 KB", ComputationStatCards.bytes(1536));
        assertEquals("≈8 EB", ComputationStatCards.bytes(Long.MAX_VALUE));
        assertFalse(ComputationStatCards.bytes(Long.MAX_VALUE).contains("i"));
        assertEquals("2.14G", ComputationStatCards.count(Integer.MAX_VALUE));
    }

    @Test void storageThreadAndParallelCardsShowTheirValues() {
        withFont(font -> {
            var storage = draw(ComputationStatCards.storage(0, Long.MAX_VALUE), font);
            assertTrue(storage.contains("0 B"));
            assertTrue(storage.contains(" / ≈8 EB"));
            assertTrue(storage.contains("≈8 EB"));
            assertTrue(storage.contains((CARD + "used_percent").toUpperCase(java.util.Locale.ROOT)));

            var threads = draw(ComputationStatCards.threads(0, Integer.MAX_VALUE), font);
            assertTrue(threads.contains("0"));
            assertTrue(threads.contains(" / 2.14G"));
            assertTrue(threads.contains((CARD + "idle").toUpperCase(java.util.Locale.ROOT)));
            assertTrue(threads.stream().anyMatch(text -> text.equals("2.14G")));

            var parallel = draw(ComputationStatCards.parallel(Integer.MAX_VALUE, Integer.MAX_VALUE, 8, Integer.MAX_VALUE), font);
            assertTrue(parallel.contains("2.14G"));
            assertTrue(parallel.contains("×8"));
            assertTrue(parallel.contains(CARD + "full_per_thread"));
            assertTrue(parallel.contains((CARD + "built").toUpperCase(java.util.Locale.ROOT)));
        });
    }

    @Test void cpuCardShowsCompactValuesAndExactValuesWithShift() {
        withFont(font -> {
            var entry = new ComputationCpuEntry(1, "CPU #1", null, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
                0, 0, Long.MAX_VALUE, Integer.MAX_VALUE, appeng.api.config.CpuSelectionMode.ANY,
                new net.minecraft.resources.ResourceLocation("neoecoae:cpu.png"), "idle");
            var compact = draw(ComputationCards.cpu(entry, false), font);
            assertTrue(compact.contains("CPU #1"));
            assertTrue(compact.contains("GUI.NEOECOAE.CPU.STATUS.IDLE"));
            assertTrue(compact.contains("≈8 EB"));
            assertTrue(compact.contains("2.14G"));
            assertTrue(compact.contains(CARD + "hold_shift"));
            var exact = draw(ComputationCards.cpu(entry, true), font);
            assertTrue(exact.contains("9,223,372,036,854,775,807 B"));
            assertTrue(exact.contains("2,147,483,647"));
            assertFalse(exact.contains(CARD + "hold_shift"));
        });
    }

    @Test void idleItemsHaveNoMachineRows() {
        var item = new ComputationCpuItemEntry(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE),
            java.math.BigInteger.ONE, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO, false);
        assertTrue(ComputationProcessingTooltips.rows(item).isEmpty());
    }

    private static List<String> draw(ModularUITooltipComponent tooltip, Font font) {
        var renderer = new ClientModularUITooltipComponent(tooltip);
        var graphics = mock(GuiGraphics.class);
        var pose = new PoseStack();
        when(graphics.pose()).thenReturn(pose);
        List<String> texts = new ArrayList<>();
        when(graphics.drawString(eq(font), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenAnswer(call -> { texts.add(((Component) call.getArgument(1)).getString()); return 0; });
        renderer.renderImage(font, 100, 80, graphics);
        assertEquals(new Matrix4f(), pose.last().pose());
        return texts;
    }

    private static void withFont(java.util.function.Consumer<Font> action) {
        try (var mocked = mockStatic(Minecraft.class)) {
            var client = mock(Minecraft.class);
            var font = mock(Font.class);
            UiTestSupport.set(client, "font", font);
            mocked.when(Minecraft::getInstance).thenReturn(client);
            when(font.width(any(Component.class))).thenAnswer(call -> ((Component) call.getArgument(0)).getString().length() * 6);
            action.accept(font);
        }
    }
}
