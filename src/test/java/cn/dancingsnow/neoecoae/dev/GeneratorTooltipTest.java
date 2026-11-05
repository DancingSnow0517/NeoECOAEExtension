package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTooltipTest {
    @Test void resourceTooltipShowsConfiguredKindsRangeAndIntervalWithExistingNumberFormatting() {
        var data = new CompoundTag();
        data.putInt("types", 1024);
        data.putLong("minimum", 1000);
        data.putLong("maximum", Long.MAX_VALUE);
        data.putInt("interval", 2000);
        var lines = new ArrayList<Component>();
        GeneratorTooltip.append(data, true, lines);
        assertEquals("tooltip.neoecoae.dev.resource_mode", key(lines.get(0)));
        assertTrue(lines.get(0).getStyle().isBold());
        assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(), lines.get(0).getStyle().getColor().getValue());
        assertEquals("1,024", argument(lines.get(2), 0).getString());
        assertEquals(ChatFormatting.AQUA.getColor(), argument(lines.get(2), 0).getStyle().getColor().getValue());
        assertEquals(DisplayNumbers.compact(1000), argument(lines.get(3), 0).getString());
        assertEquals(DisplayNumbers.compact(Long.MAX_VALUE), argument(lines.get(3), 1).getString());
        assertEquals("2,000", argument(lines.get(4), 0).getString());
        assertEquals("tooltip.neoecoae.dev.help", key(lines.get(5)));
    }

    @Test void energyTooltipShowsOneResourceAndSeparateProducerConsumerRanges() {
        var state = new GeneratorState();
        state.fe = new FeWorkload(new FeWorkloadSettings(1024, 1000, 1000000, 2048, 16, 64000, 42, true));
        state.feRunning = true;
        var lines = new ArrayList<Component>();
        GeneratorTooltip.append(state.write(null), false, lines);
        assertEquals("tooltip.neoecoae.dev.fe_mode", key(lines.get(0)));
        assertEquals("tooltip.neoecoae.dev.disabled", key(lines.get(2)));
        assertEquals("1", argument(lines.get(4), 0).getString());
        assertEquals("1,024", argument(lines.get(5), 0).getString());
        assertEquals(DisplayNumbers.compact(1000000), argument(lines.get(6), 1).getString());
        assertEquals("2,048", argument(lines.get(7), 0).getString());
        assertEquals(DisplayNumbers.compact(64000), argument(lines.get(8), 1).getString());
        assertEquals("tooltip.neoecoae.dev.fe_paused", key(lines.get(9)));
    }

    private static String key(Component component) { return ((TranslatableContents) component.getContents()).getKey(); }
    private static Component argument(Component component, int index) {
        return (Component) ((TranslatableContents) component.getContents()).getArgs()[index];
    }
}
