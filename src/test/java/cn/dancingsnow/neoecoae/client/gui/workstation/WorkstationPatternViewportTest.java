package cn.dancingsnow.neoecoae.client.gui.workstation;

import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.client.gui.widgets.Scrollbar;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.gui.workstation.WorkstationPatternScroll;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
        Slot entering = fixture.slots.get(36);
        assertTrue(first.isActive());
        assertTrue(entering.isActive());
        assertFalse(fixture.slots.get(45).isActive());
        assertTrue(fixture.screen.isHovering(first, 110, 92));
        assertFalse(fixture.screen.isHovering(first, 110, 90), "Clipped pixels above the viewport cannot be clicked");
        assertTrue(fixture.screen.isHovering(entering, 110, 155));
        assertFalse(fixture.screen.isHovering(entering, 110, 163), "Clipped pixels below the viewport cannot be clicked");
        assertFalse(fixture.screen.isHovering(entering, 99, 155));
        assertFalse(fixture.screen.isHovering(entering, 270, 155));
    }

    @Test
    void subpixelHitTestsChangeRowsAtTheExactRenderedBoundary() throws Exception {
        var fixture = fixture(9.5);
        Slot first = fixture.slots.get(0);
        Slot second = fixture.slots.get(9);
        assertTrue(fixture.screen.isHovering(first, 110, 99.49));
        assertFalse(fixture.screen.isHovering(second, 110, 99.49));
        assertFalse(fixture.screen.isHovering(first, 110, 99.51));
        assertTrue(fixture.screen.isHovering(second, 110, 99.51));
    }

    @Test
    void slotIdentitiesStayFixedAsTheViewportMovesToTheFinalRow() throws Exception {
        var fixture = fixture(72);
        assertFalse(fixture.slots.get(35).isActive());
        assertTrue(fixture.slots.get(36).isActive());
        assertTrue(fixture.slots.get(71).isActive());
        assertEquals(8, fixture.slots.get(36).x);
        assertEquals(42, fixture.slots.get(36).y);
        assertEquals(152, fixture.slots.get(71).x);
        assertEquals(96, fixture.slots.get(71).y);
        assertEquals(71, fixture.slots.get(71).getSlotIndex());
    }

    @Test
    void onlyTheTwoPartiallyVisibleRowsNeedScissorsAndPoseTransformsAreRestored() throws Exception {
        var fixture = fixture(9.5);
        GuiGraphics graphics = mock(GuiGraphics.class);
        PoseStack pose = new PoseStack();
        when(graphics.pose()).thenReturn(pose);
        fixture.screen.renderSlot(graphics, fixture.slots.getFirst());
        verify(graphics).enableScissor(107, 91, 269, 163);
        verify(graphics).disableScissor();
        assertEquals(0, pose.last().pose().m31());
        clearInvocations(graphics);
        fixture.screen.renderSlot(graphics, fixture.slots.get(9));
        verify(graphics, never()).enableScissor(anyInt(), anyInt(), anyInt(), anyInt());
        verify(graphics, never()).disableScissor();
        assertEquals(0, pose.last().pose().m31());
        clearInvocations(graphics);
        fixture.screen.renderSlotHighlight(graphics, fixture.slots.get(36), 110, 155, 0);
        verify(graphics).enableScissor(107, 91, 269, 163);
        verify(graphics).disableScissor();
        assertEquals(0, pose.last().pose().m31());
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
