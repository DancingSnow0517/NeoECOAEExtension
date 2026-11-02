package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.Scrollbar;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.gui.workstation.WorkstationPatternScroll;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual screen's slot geometry and rendering hooks without needing an OpenGL window. */
class WorkstationPatternViewportTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void partiallyVisibleSlotsOnlyAcceptPointerHitsInsideTheClippedViewport() throws Exception {
        var fixture = fixture(9.5);
        Slot first = fixture.slots.getFirst();
        Slot entering = fixture.slots.get(63);
        assertTrue(first.isActive());
        assertTrue(entering.isActive());
        assertTrue(fixture.slots.get(62).isActive());
        assertTrue(fixture.screen.isHovering(first, 110, 73));
        assertFalse(fixture.screen.isHovering(first, 110, 71), "Clipped pixels above the viewport cannot be clicked");
        assertTrue(fixture.screen.isHovering(entering, 110, 190));
        assertFalse(fixture.screen.isHovering(entering, 110, 198), "Clipped pixels below the viewport cannot be clicked");
        assertFalse(fixture.screen.isHovering(entering, 99, 190));
        assertFalse(fixture.screen.isHovering(entering, 270, 190));
    }

    @Test
    void subpixelHitTestsChangeRowsAtTheExactRenderedBoundary() throws Exception {
        var fixture = fixture(9.5);
        Slot first = fixture.slots.get(0);
        Slot second = fixture.slots.get(9);
        assertTrue(fixture.screen.isHovering(first, 110, 80.49));
        assertFalse(fixture.screen.isHovering(second, 110, 80.49));
        assertFalse(fixture.screen.isHovering(first, 110, 80.51));
        assertTrue(fixture.screen.isHovering(second, 110, 80.51));
    }

    @Test
    void slotIdentitiesStayFixedAsTheViewportMovesToTheFinalRow() throws Exception {
        var fixture = fixture(18);
        assertFalse(fixture.slots.get(8).isActive());
        assertTrue(fixture.slots.get(9).isActive());
        assertTrue(fixture.slots.get(71).isActive());
        assertEquals(8, fixture.slots.get(9).x);
        assertEquals(23, fixture.slots.get(9).y);
        assertEquals(152, fixture.slots.get(71).x);
        assertEquals(131, fixture.slots.get(71).y);
        assertEquals(71, fixture.slots.get(71).getSlotIndex());
    }

    @Test
    void onlyTheTwoPartiallyVisibleRowsNeedScissorsAndPoseTransformsAreRestored() throws Exception {
        var fixture = fixture(9.5);
        GuiGraphics graphics = mock(GuiGraphics.class);
        PoseStack pose = new PoseStack();
        when(graphics.pose()).thenReturn(pose);
        fixture.screen.renderSlot(graphics, fixture.slots.getFirst());
        verify(graphics).enableScissor(108, 73, 268, 197);
        verify(graphics).disableScissor();
        assertEquals(0, pose.last().pose().m31());
        clearInvocations(graphics);
        fixture.screen.renderSlot(graphics, fixture.slots.get(9));
        verify(graphics, never()).enableScissor(anyInt(), anyInt(), anyInt(), anyInt());
        verify(graphics, never()).disableScissor();
        assertEquals(0, pose.last().pose().m31());
        clearInvocations(graphics);
        fixture.screen.renderSlotHighlight(graphics, fixture.slots.get(63), 110, 190, 0);
        verify(graphics).enableScissor(108, 73, 268, 197);
        verify(graphics).disableScissor();
        assertEquals(0, pose.last().pose().m31());
    }

    @Test
    void scrollbarDrawsAndAcceptsDraggingInTheExtraPixelOnItsLeftEdge() throws Exception {
        var fixture = fixture(0);
        var constructor = Class.forName(LargeIntegratedWorkingStationPatternProviderScreen.class.getName()
            + "$PatternScrollbar").getDeclaredConstructor(LargeIntegratedWorkingStationPatternProviderScreen.class);
        constructor.setAccessible(true);
        var scrollbar = (Scrollbar) constructor.newInstance(fixture.screen);
        scrollbar.setPosition(new Point(174, 23));
        scrollbar.setSize(8, 124);
        set(LargeIntegratedWorkingStationPatternProviderScreen.class, fixture.screen, "patternScrollbar", scrollbar);
        set(LargeIntegratedWorkingStationPatternProviderScreen.class, fixture.screen, "shiftDraggedSlots", new HashSet<>());
        var minecraft = mock(Minecraft.class, RETURNS_DEEP_STUBS);
        when(minecraft.getGuiSprites().getSprite(Scrollbar.SMALL.enabledSprite()).contents().width()).thenReturn(7);
        when(minecraft.getGuiSprites().getSprite(Scrollbar.SMALL.enabledSprite()).contents().height()).thenReturn(15);
        try (var client = mockStatic(Minecraft.class); var blitters = mockStatic(Blitter.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            assertEquals(174, scrollbar.getBounds().getX());
            assertEquals(8, scrollbar.getBounds().getWidth());
            assertEquals(182, scrollbar.getBounds().getX() + scrollbar.getBounds().getWidth(),
                "Expanding to the left must retain the original right edge");
            var blitter = mock(Blitter.class, RETURNS_SELF);
            blitters.when(() -> Blitter.guiSprite(Scrollbar.SMALL.enabledSprite())).thenReturn(blitter);
            var graphics = mock(GuiGraphics.class);
            when(graphics.pose()).thenReturn(new PoseStack());
            scrollbar.drawForegroundLayer(graphics, scrollbar.getBounds(), Point.ZERO);
            verify(blitter).dest(174, 23, 8, 15);
            assertTrue(fixture.screen.mouseClicked(274, 73, 0), "The newly visible left pixel must start dragging");
            assertTrue(fixture.screen.mouseDragged(274, 182, 0, 0, 109));
            assertFalse(fixture.slots.getFirst().isActive());
            assertEquals(23, fixture.slots.get(9).y, "Dragging from the extra pixel must reach the final row");
        }
    }

    private static Fixture fixture(double offset) throws Exception {
        var inventory = new AppEngInternalInventory(72);
        List<Slot> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) slots.add(new AppEngSlot(inventory, slot));
        var menu = mock(LargeIntegratedWorkingStationPatternProviderMenu.class);
        when(menu.getSlots(SlotSemantics.ENCODED_PATTERN)).thenReturn(slots);
        when(menu.getPatternSlotCount()).thenReturn(72);
        when(menu.getSlotSemantic(any(Slot.class))).thenReturn(SlotSemantics.ENCODED_PATTERN);
        var screen = mock(LargeIntegratedWorkingStationPatternProviderScreen.class, CALLS_REAL_METHODS);
        var scroll = new WorkstationPatternScroll();
        scroll.setSlotCount(72);
        scroll.jumpTo(offset);
        set(AbstractContainerScreen.class, screen, "menu", menu);
        set(AbstractContainerScreen.class, screen, "leftPos", 100);
        set(AbstractContainerScreen.class, screen, "topPos", 50);
        set(LargeIntegratedWorkingStationPatternProviderScreen.class, screen, "patternScroll", scroll);
        set(LargeIntegratedWorkingStationPatternProviderScreen.class, screen, "patternScrollbar", new Scrollbar());
        var update = LargeIntegratedWorkingStationPatternProviderScreen.class.getDeclaredMethod("updatePatternSlots");
        update.setAccessible(true);
        update.invoke(screen);
        return new Fixture(screen, slots);
    }

    private static void set(Class<?> owner, Object instance, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private record Fixture(LargeIntegratedWorkingStationPatternProviderScreen screen, List<Slot> slots) {}
}
