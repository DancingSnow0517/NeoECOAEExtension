package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NEUiJsonBridgeTest {
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }

    @Test
    void nativeInventoryUsesAll36DistinctPlayerSlots() {
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        var element = NEPlayerInventory.create(player);
        var slots = element.getWidgetsByType(SlotWidget.class);
        assertEquals(36, slots.size());
        assertEquals(36, slots.stream().map(slot -> slot.getHandler().getContainerSlot()).distinct().count());
    }

    @Test
    void retainedJsonBuildsARealFluidTankAndNativeInventory() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(new Inventory(player));
        FluidTank tank = new FluidTank(16_000);
        var ui = FluidHatchUI.createFluidHatchUI(IUIHolder.EMPTY, player, tank,
            "block.neoecoae.input_hatch", true, true);
        assertEquals(170, ui.getWidth());
        assertEquals(137, ui.getHeight());
        var tanks = ui.mainGroup.getWidgetsByType(TankWidget.class);
        assertEquals(1, tanks.size());
        assertSame(tank, ((com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper)
            tanks.get(0).getFluidTank()).getHandler());
        assertEquals(36, ui.mainGroup.getWidgetsByType(SlotWidget.class).size());
    }

    @Test
    void unknownElementAndLayoutAreRejected() {
        var context = new NEUiJsonBridge.Context() {
            public NEUiElement createCustom(JsonObject node) { return null; }
            public NEUiElement playerInventory() { return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> NEUiJsonBridge.load(
            JsonParser.parseString("{\"type\":\"scene\"}").getAsJsonObject(), context));
        assertThrows(IllegalArgumentException.class, () -> NEUiJsonBridge.load(
            JsonParser.parseString("{\"layout\":{\"flexGrow\":1}}").getAsJsonObject(), context));
    }
}
