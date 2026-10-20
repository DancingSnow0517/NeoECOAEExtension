package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.common.HostProgressTestSupport;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.math.BigInteger;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageResourceHoverTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        StorageHostUiTest.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(strings = {"resource_bar", "capacity_bar"})
    void hoverHighlightsOnlyTheSelectedSegmentAndShowsItsMergedResourceAmounts(String id) {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement bar = bar(state(false, 0.75, List.of(type(1, "fluids", 30),
                type(2, "items", 60), type(2, "items", 30))), id);
            boolean capacity = id.equals("capacity_bar");
            float itemWidth = capacity ? 90F : 120F;
            float fluidWidth = capacity ? 30F : 40F;
            int tooltipLines = capacity ? 3 : 1;
            GUIContext context = mock(GUIContext.class);
            context.mouseX = 220;
            context.mouseY = 337;
            bar.drawBackgroundAdditional(context);
            verify(context, times(1)).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                eq(208F), eq(334F), eq(itemWidth), eq(8F));
            Object[] item = tooltipArguments(bar, tooltipLines);
            assertEquals("items", ((Component) item[0]).getString());
            assertEquals("90", item[1]);
            assertEquals("75.0%", item[2]);

            clearInvocations(context);
            context.mouseX = Math.round(208 + itemWidth); // Exact boundary belongs to the next resource, not both.
            bar.drawBackgroundAdditional(context);
            verify(context, times(1)).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                eq(208F + itemWidth), eq(334F), eq(fluidWidth), eq(8F));
            Object[] fluid = tooltipArguments(bar, tooltipLines);
            assertEquals("fluids", ((Component) fluid[0]).getString());
            assertEquals("30", fluid[1]);
            assertEquals("25.0%", fluid[2]);

            for (float mouseX : new float[] {208 + itemWidth + fluidWidth, 209 + itemWidth + fluidWidth}) {
                clearInvocations(context);
                context.mouseX = Math.round(mouseX);
                bar.drawBackgroundAdditional(context);
                verify(context, never()).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                    anyFloat(), anyFloat(), anyFloat(), anyFloat());
                assertEquals(capacity ? 2 : 0, bar.collectHoverTooltips().tooltipTexts().size());
            }
        }
    }

    @Test
    void outsideEdgesAndLeavingTheBarClearTheHighlightAndTooltip() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement bar = bar(state(false, 0.75, List.of(type(2, "items", 90), type(1, "fluids", 30))));
            GUIContext context = mock(GUIContext.class);
            for (int[] point : List.of(new int[] {368, 337},
                new int[] {207, 337}, new int[] {220, 333}, new int[] {220, 342})) {
                context.mouseX = 220;
                context.mouseY = 337;
                bar.drawBackgroundAdditional(context);
                assertFalse(bar.collectHoverTooltips().tooltipTexts().isEmpty());
                clearInvocations(context);
                context.mouseX = point[0];
                context.mouseY = point[1];
                bar.drawBackgroundAdditional(context);
                verify(context, never()).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                    anyFloat(), anyFloat(), anyFloat(), anyFloat());
                assertTrue(bar.collectHoverTooltips().tooltipTexts().isEmpty());
            }
            context.mouseX = 220;
            context.mouseY = 337;
            bar.drawBackgroundAdditional(context);
            clearInvocations(context);
            doReturn(false).when(bar).isHover();
            bar.drawBackgroundAdditional(context);
            verify(context, never()).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                anyFloat(), anyFloat(), anyFloat(), anyFloat());
            assertTrue(bar.collectHoverTooltips().tooltipTexts().isEmpty());
        }
    }

    @Test
    void finiteResourceHoverStillMatchesItsShareWhenCapacityUsageIsTiny() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement bar = bar(state(false, 0.000001,
                List.of(type(2, "items", 32_769), type(1, "fluids", 81_796))));
            GUIContext context = mock(GUIContext.class);
            context.mouseX = 321;
            context.mouseY = 337;
            bar.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 208F, 334F, 114F, 8F);
            Object[] fluid = tooltipArguments(bar);
            assertEquals("fluids", ((Component) fluid[0]).getString());
            assertEquals("81,796", fluid[1]);
            assertEquals("71.4%", fluid[2]);

            clearInvocations(context);
            context.mouseX = 322;
            bar.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 322F, 334F, 46F, 8F);
            Object[] item = tooltipArguments(bar);
            assertEquals("items", ((Component) item[0]).getString());
            assertEquals("32,769", item[1]);
            assertEquals("28.6%", item[2]);
        }
    }

    @Test
    void infiniteResourceHoverUsesExactAmountsBeyondLongAndOnlyItsOwnSegment() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            BigInteger huge = BigInteger.TEN.pow(100);
            BigInteger items = huge.multiply(BigInteger.valueOf(3));
            UIElement bar = bar(state(true, 1, List.of(type(1, "fluids", huge), type(2, "items", items))));
            GUIContext context = mock(GUIContext.class);
            context.mouseX = 327;
            context.mouseY = 337;
            bar.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 208F, 334F, 120F, 8F);
            Object[] tooltip = tooltipArguments(bar);
            assertEquals("items", ((Component) tooltip[0]).getString());
            assertEquals(HostText.fullBytes(items), tooltip[1]);
            assertNotEquals(HostText.ae2Amount(Long.MAX_VALUE), tooltip[1]);
            assertEquals("75.0%", tooltip[2]);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void removingResourcesWhileHoveredClearsTheCachedTooltip() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) root.getChildren().getFirst();
            display.setValue(state(false, 0.75, List.of(type(2, "items", 90))).toTag());
            UIElement bar = positioned(root);
            GUIContext context = mock(GUIContext.class);
            context.mouseX = 220;
            context.mouseY = 337;
            bar.drawBackgroundAdditional(context);
            assertFalse(bar.collectHoverTooltips().tooltipTexts().isEmpty());
            display.setValue(StoragePanelState.EMPTY.toTag());
            clearInvocations(context);
            bar.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
            assertTrue(bar.collectHoverTooltips().tooltipTexts().isEmpty());
        }
    }

    @Test
    void suppliedHoverSpriteRetainsItsOriginalWhiteColorAndTransparency() throws Exception {
        var texture = StoragePanelTextures.RESOURCE_HOVER;
        assertEquals(24, texture.spritePosition.x);
        assertEquals(78, texture.spritePosition.y);
        assertEquals(8, texture.spriteSize.width);
        assertEquals(8, texture.spriteSize.height);
        assertEquals(-1, texture.color);
        try (var input = getClass().getResourceAsStream("/assets/neoecoae/textures/guis/storage/storage_widget.png")) {
            assertNotNull(input);
            var atlas = ImageIO.read(input);
            for (int y = 78; y < 86; y++) {
                for (int x = 24; x < 32; x++) assertEquals(0x64F7F8FF, atlas.getRGB(x, y));
            }
        }
    }

    private static Object[] tooltipArguments(UIElement bar) {
        return tooltipArguments(bar, 1);
    }

    private static Object[] tooltipArguments(UIElement bar, int expectedLines) {
        // LDLib2 collects hover tooltips after drawing, without filling in event.x/event.y.
        var lines = bar.collectHoverTooltips().tooltipTexts();
        assertEquals(expectedLines, lines.size());
        return assertInstanceOf(TranslatableContents.class, lines.getLast().getContents()).getArgs();
    }

    private static UIElement bar(StoragePanelState state) {
        return bar(state, "resource_bar");
    }

    @SuppressWarnings("unchecked")
    private static UIElement bar(StoragePanelState state, String id) {
        UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
        ((BindableValue<Tag>) root.getChildren().getFirst()).setValue(state.toTag());
        return positioned(root, id);
    }

    private static UIElement positioned(UIElement root) {
        return positioned(root, "resource_bar");
    }

    private static UIElement positioned(UIElement root, String id) {
        UIElement bar = spy(root.selectId(id).findFirst().orElseThrow());
        doReturn(208F).when(bar).getPositionX();
        doReturn(334F).when(bar).getPositionY();
        doReturn(160F).when(bar).getSizeWidth();
        doReturn(8F).when(bar).getSizeHeight();
        doReturn(true).when(bar).isHover();
        HostProgressTestSupport.drawAfterOpening(bar, mock(GUIContext.class));
        return bar;
    }

    private static StoragePanelState.TypeLine type(int swatch, String name, long used) {
        return type(swatch, name, BigInteger.valueOf(used));
    }

    private static StoragePanelState.TypeLine type(int swatch, String name, BigInteger used) {
        return new StoragePanelState.TypeLine(swatch, Component.literal(name), StoragePanelTextures.COLORS[swatch],
            1, 10, used.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(), 100, used.toString(), swatch, used);
    }

    private static StoragePanelState state(boolean infinite, double usage, List<StoragePanelState.TypeLine> types) {
        return new StoragePanelState(Component.empty(), 2, true, infinite, false,
            0, 100, 0, "120", usage, 0, 0, List.of(), types, "", 0, false);
    }
}
