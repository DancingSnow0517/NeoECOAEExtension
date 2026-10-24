package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CraftingControllerTooltipsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({
        "11,5632,14080,8448",
        "22,45056,112640,67584",
        "22,112640,281600,168960",
        "22,180224,450560,270336"
    })
    void limitAndTooltipUseTheSameActualCapacityAsTheQueue(
            int fx, long capacity, long ft, long overflow) {
        var view = new CraftingControllerViewState();
        view.workerCores = fx;
        view.batchCapacity = capacity;
        view.ftParallelCapacity = ft;
        view.overflowCapacity = overflow;
        view.executionTicks = 1;
        view.effectiveOverclockTimes = 9;
        view.theoreticalOverclockTimes = 9;
        var tooltips = new CraftingControllerTooltips(view);

        assertEquals(capacity, tooltips.parallelLimit());
        assertEquals("60%", tooltips.overflowPercent());
        var lines = tooltips.parallelTooltip();
        assertTranslation(lines.get(0), "total_parallel", ft);
        assertTranslation(lines.get(1), "parallel_limit", capacity);
        assertTranslation(lines.get(2), "parallel_overflow", overflow, "60%");
        assertTranslation(lines.get(3), "runtime_ticks", 1);
        assertTranslation(lines.get(4), "runtime", 9, 9);
    }

    @Test
    void virtualTooltipKeepsInfiniteCapacityAndIndependentOneTickRuntime() {
        var view = new CraftingControllerViewState();
        view.virtualMode = true;
        view.batchCapacity = Long.MAX_VALUE;
        view.executionTicks = 1;

        var lines = new CraftingControllerTooltips(view).parallelTooltip();

        assertEquals(4, lines.size());
        assertTranslation(lines.get(1), "total_parallel", "∞");
        assertTranslation(lines.get(2), "parallel_limit", "∞");
        assertTranslation(lines.get(3), "runtime_ticks", 1);
    }

    private static void assertTranslation(Component component, String key, Object... arguments) {
        var contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals("gui.neoecoae.crafting.ui." + key, contents.getKey());
        assertArrayEquals(arguments, contents.getArgs());
    }
}
