package cn.dancingsnow.neoecoae.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import cn.dancingsnow.neoecoae.client.gui.ClientModularUITooltipComponent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CraftingControllerTooltipsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"11,5632,14080,8448", "22,45056,112640,67584", "22,112640,281600,168960", "22,180224,450560,270336"})
    void limitAndCardUseTheActualQueueCapacity(int fx, long capacity, long ft, long overflow) {
        withFont(font -> {
            var view = new CraftingControllerViewState();
            view.workerCores = fx;
            view.batchCapacity = capacity;
            view.ftParallelCapacity = ft;
            view.overflowCapacity = overflow;
            var tooltips = new CraftingControllerTooltips(view);
            assertEquals(capacity, tooltips.parallelLimit());
            assertEquals("60%", tooltips.overflowPercent());
            var drawn = draw(tooltips.parallelTooltip(), font);
            assertTrue(drawn.contains(CraftingTooltipCard.group(ft)));
            assertTrue(drawn.contains(" / " + CraftingTooltipCard.group(capacity)));
            assertTrue(drawn.contains("60.0%"));
        });
    }

    @Test void parallelCardShowsCurrentLimitOverflowRatioRuntimeAndOverclock() {
        withFont(font -> {
            var view = new CraftingControllerViewState();
            view.ftParallelCapacity = 20_000;
            view.batchCapacity = 5_632;
            view.overflowCapacity = 14_368;
            view.executionTicks = 3;
            view.effectiveOverclockTimes = 2;
            view.theoreticalOverclockTimes = 9;
            var drawn = draw(new CraftingControllerTooltips(view).parallelTooltip(), font);
            assertTrue(drawn.contains("20,000"));
            assertTrue(drawn.contains(" / 5,632"));
            assertTrue(drawn.contains("71.8%"));
            assertTrue(drawn.stream().anyMatch(text -> text.endsWith("+14,368")));
            assertTrue(drawn.stream().anyMatch(text -> text.endsWith("3 t")));
            assertTrue(drawn.stream().anyMatch(text -> text.endsWith("2 / 9")));
        });
    }

    @Test void queueCardsDistinguishIdleActiveFullAndUnboundedCapacity() {
        withFont(font -> {
            for (String state : List.of("idle", "active", "full")) {
                var view = new CraftingControllerViewState();
                view.batchCapacity = 100;
                view.queuedCraftCount = state.equals("idle") ? 0 : state.equals("active") ? 50 : 100;
                var drawn = draw(new CraftingControllerTooltips(view).queueTooltip(), font);
                assertTrue(drawn.contains(("gui.neoecoae.crafting.ui.queue_card." + state).toUpperCase(java.util.Locale.ROOT)));
                assertTrue(drawn.contains(Long.toString(view.queuedCraftCount)));
                assertTrue(drawn.contains(" / 100"));
            }
            var view = new CraftingControllerViewState();
            view.batchCapacity = Long.MAX_VALUE;
            view.queuedCraftCount = Long.MAX_VALUE;
            var drawn = draw(new CraftingControllerTooltips(view).queueTooltip(), font);
            assertTrue(drawn.contains(" / ∞"));
            assertTrue(drawn.contains("GUI.NEOECOAE.CRAFTING.UI.QUEUE_CARD.ACTIVE"));
        });
    }

    @Test void infiniteModeHasItsOwnValueAndStatusWithoutFiniteOverflowRows() {
        withFont(font -> {
            var view = new CraftingControllerViewState();
            view.virtualMode = true;
            view.executionTicks = 1;
            var drawn = draw(new CraftingControllerTooltips(view).parallelTooltip(), font);
            assertTrue(drawn.contains("∞"));
            assertTrue(drawn.contains("GUI.NEOECOAE.CRAFTING.UI.VIRTUAL_CRAFTING"));
            assertTrue(drawn.stream().anyMatch(text -> text.endsWith("1 t")));
            assertFalse(drawn.stream().anyMatch(text -> text.endsWith("+0")));
        });
    }

    @Test void fxCardShowsActiveCoresParallelAndBatchCapacity() {
        withFont(font -> {
            var view = new CraftingControllerViewState();
            view.activeWorkerCores = 3;
            view.workerCores = 11;
            view.ftParallelCapacity = 5_632;
            view.batchCapacity = 352;
            var drawn = draw(new CraftingControllerTooltips(view).fxTooltip(), font);
            assertTrue(drawn.contains("gui.neoecoae.crafting.ui.fx_card.title"));
            assertTrue(drawn.contains("gui.neoecoae.crafting.ui.fx_card.fx_active"));
            assertTrue(drawn.contains("3"));
            assertTrue(drawn.contains(" / 11"));
            assertTrue(drawn.contains("5,632"));
            assertTrue(drawn.contains("352"));
            view.batchCapacity = Long.MAX_VALUE;
            assertTrue(draw(new CraftingControllerTooltips(view).fxTooltip(), font).contains("∞"));
        });
    }

    @Test void infiniteModeChecklistIsRebuiltFromTheSyncedReasons() {
        withFont(font -> {
            String reason = "gui.neoecoae.crafting.virtual_reason.";
            var status = Component.translatable(VirtualModeStatusCard.TITLE_KEY)
                .append("\n").append(Component.translatable(reason + "overclock"))
                .append("\n").append(Component.translatable(reason + "coolant_type", Component.literal("Rime Solution")))
                .append("\n").append(Component.translatable(reason + "host_count", "1", "8"))
                .append("\n").append(Component.translatable(reason + "high_energy_switch"));
            assertTrue(VirtualModeStatusCard.applies(status));
            assertFalse(VirtualModeStatusCard.applies(Component.literal("other")));
            var drawn = draw(VirtualModeStatusCard.create(status), font);
            String card = "gui.neoecoae.crafting.ui.virtual_card.";
            assertTrue(drawn.contains(card + "unmet"));
            assertTrue(drawn.contains("1 / 8"));
            assertTrue(drawn.contains(card + "disabled"));
            assertTrue(drawn.contains(card + "enabled"));
            assertTrue(drawn.contains(card + "requires"));
            assertTrue(drawn.contains(card + "high_energy_switch.missing"));
            assertFalse(drawn.contains(card + "max_length.missing"));
            assertFalse(drawn.contains(card + "f9.missing"));

            var satisfied = draw(VirtualModeStatusCard.create(Component.translatable(VirtualModeStatusCard.TITLE_KEY)), font);
            assertTrue(satisfied.contains(card + "met"));
            assertTrue(satisfied.contains("8 / 8"));
        });
    }

    @Test void forgeRegistersAnImageOnlyTooltipWithMeasuredContentAndRestoresPose() {
        withFont(font -> {
            Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> factories = new HashMap<>();
            NeoECOAEClient.onRegisterTooltipComponents(new RegisterClientTooltipComponentFactoriesEvent(factories));
            var content = CraftingTooltipCard.builder(CraftingTooltipCard.Icon.CHIP, Component.literal("Parallel"))
                .value("9,223,372,036,854,775,807", "9,223,372,036,854,775,807")
                .status(Component.literal("Overflow"), CraftingTooltipCard.WARN)
                .segment(0.5, CraftingTooltipCard.ACCENT, false).build();
            var renderer = factories.get(ModularUITooltipComponent.class).apply(content);
            assertTrue(renderer.getWidth(font) >= (int) Math.ceil(140 * CraftingTooltipCard.CONTENT_SCALE));
            assertTrue(renderer.getHeight() >= (int) Math.ceil(33 * CraftingTooltipCard.CONTENT_SCALE));
            var graphics = mock(GuiGraphics.class);
            var pose = new PoseStack();
            when(graphics.pose()).thenReturn(pose);
            var original = new Matrix4f(pose.last().pose());
            doThrow(new IllegalStateException("render failed")).when(graphics)
                .drawString(eq(font), any(Component.class), anyInt(), anyInt(), anyInt(), anyBoolean());
            assertThrows(IllegalStateException.class, () -> renderer.renderImage(font, 100, 80, graphics));
            assertEquals(original, pose.last().pose());
        });
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
