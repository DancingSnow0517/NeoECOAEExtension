package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetainedPlayerInventoryTest {
    @BeforeAll
    static void bootstrap() {
        PlatformTestBootstrap.initialize();
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(new Inventory(player));
        return player;
    }

    @Test
    void menuAttachmentExposesAll36RealPlayerSlotsIncludingTheirCountsAndTags() {
        Player player = player();
        Inventory inventory = player.getInventory();
        for (int index = 0; index < 36; index++) {
            ItemStack stack = new ItemStack(Items.STONE, index + 1);
            stack.getOrCreateTag().putInt("player_slot", index);
            inventory.setItem(index, stack);
        }
        var slots = new InventorySlots();
        var root = new UIElement().addChild(new UIElement().addChild(slots));
        new ModularUI(UI.of(root), player);

        var nativeSlots = slots.getWidgetsByType(SlotWidget.class);
        assertEquals(36, nativeSlots.size());
        assertEquals(36, nativeSlots.stream().map(widget -> widget.getHandler().getContainerSlot()).distinct().count());
        for (int position = 0; position < nativeSlots.size(); position++) {
            var nativeSlot = nativeSlots.get(position).getHandler();
            int index = (position + 9) % 36;
            assertSame(inventory, nativeSlot.container);
            assertEquals(index, nativeSlot.getContainerSlot());
            assertTrue(ItemStack.matches(inventory.getItem(index), nativeSlot.getItem()));
        }
        assertTrue(ItemStack.matches(inventory.getItem(9), slots.rows[0].slots[0].getValue()));
        assertTrue(ItemStack.matches(inventory.getItem(8), slots.hotbar.slots[8].getValue()));
    }

    @Test
    void nativeSlotChangesAndPlayerInventoryUpdatesShareTheSameStorage() {
        Player player = player();
        var slots = new InventorySlots();
        new ModularUI(UI.of(slots), player);
        var nativeSlot = slots.rows[1].slots[4].getWidgetsByType(SlotWidget.class).get(0).getHandler();

        player.getInventory().setItem(22, new ItemStack(Items.DIAMOND, 17));
        assertEquals(17, slots.rows[1].slots[4].getValue().getCount());
        assertEquals(5, nativeSlot.remove(5).getCount());
        assertEquals(12, player.getInventory().getItem(22).getCount());
        assertTrue(nativeSlot.mayPlace(new ItemStack(Items.EMERALD)));
        assertTrue(nativeSlot.mayPickup(player));
        nativeSlot.setByPlayer(new ItemStack(Items.EMERALD, 7));
        assertTrue(player.getInventory().getItem(22).is(Items.EMERALD));
        assertEquals(7, slots.rows[1].slots[4].getValue().getCount());
    }

    @Test
    void lateChildrenAndReusedTreesBindToThePlayerOfTheCurrentMenu() {
        Player first = player();
        Player second = player();
        first.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        second.getInventory().setItem(0, new ItemStack(Items.EMERALD, 11));
        var root = new UIElement();
        new ModularUI(UI.of(root), first);
        var slots = new InventorySlots();
        root.addChild(slots);
        assertTrue(slots.hotbar.slots[0].getValue().is(Items.DIAMOND));

        root.attachUI(null);
        new ModularUI(UI.of(root), second);
        assertTrue(slots.hotbar.slots[0].getValue().is(Items.EMERALD));
        assertEquals(11, slots.hotbar.slots[0].getValue().getCount());
        slots.hotbar.slots[0].getSlot().remove(2);
        assertEquals(9, second.getInventory().getItem(0).getCount());
        assertEquals(3, first.getInventory().getItem(0).getCount());
    }

    @Test
    void automaticBindingPreservesExplicitInventoryAndLockedSlots() {
        Player player = player();
        Player other = player();
        var slots = new InventorySlots();
        Slot locked = new Slot(player.getInventory(), 4, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player owner) { return false; }
        };
        slots.hotbar.slots[4].bind(locked);
        new ModularUI(UI.of(slots), player);
        assertSame(locked, slots.hotbar.slots[4].getSlot());
        var nativeSlot = slots.hotbar.slots[4].getWidgetsByType(SlotWidget.class).get(0).getHandler();
        assertFalse(nativeSlot.mayPlace(new ItemStack(Items.DIAMOND)));
        assertFalse(nativeSlot.mayPickup(player));
        assertSame(player.getInventory(), slots.hotbar.slots[3].getSlot().container);

        slots.bind(other.getInventory());
        new ModularUI(UI.of(slots), player);
        slots.apply(element -> assertSame(other.getInventory(), element.getSlot().container));
    }
}
