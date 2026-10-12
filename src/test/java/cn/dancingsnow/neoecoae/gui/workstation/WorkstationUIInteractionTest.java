package cn.dancingsnow.neoecoae.gui.workstation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.orientation.RelativeSide;
import appeng.items.tools.NetworkToolItem;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkstationUIInteractionTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class); var registrate = mockStatic(NERegistrate.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(UIElement.class.getName());
            Class.forName(NETextures.class.getName());
            AccessorRegistries.init();
        }
    }

    @Test
    void upgradeColumnMatchesNativeAe2GeometryAfterScreenResize() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            for (int count : List.of(1, 3, 4)) {
                ItemStackHandler upgrades = new ItemStackHandler(count);
                List<ItemSlot> slots = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    slots.add(new ItemSlot(new ItemHandlerSlot(upgrades, index)));
                }
                UIElement rail = HostSideButtonBar.rightSlots(slots);
                UIElement root = new UIElement().layout(layout -> layout.width(170).height(173))
                    .addChild(rail);
                ModularUI ui = new ModularUI(UI.of(root));
                for (int screenWidth : List.of(640, 800)) {
                    ui.init(screenWidth, 360);
                    assertEquals(root.getPositionX() + 168, rail.getPositionX());
                    assertEquals(root.getPositionY(), rail.getPositionY());
                    assertEquals(12 + count * 18, rail.getSizeHeight());
                    for (int index = 0; index < count; index++) {
                        ItemSlot slot = slots.get(index);
                        assertEquals(rail.getPositionX(), slot.getPositionX());
                        assertEquals(rail.getPositionY() + 5 + index * 18, slot.getPositionY());
                        assertEquals(18, slot.getSizeWidth());
                        assertEquals(18, slot.getSizeHeight());
                        assertSame(AETextures.icon(ECOIcon.SLOT_BACKGROUND), slot.getStyle().backgroundTexture());
                    }
                }
                UIElement column = rail.getChildren().getFirst();
                ColorBorderTexture frame = assertInstanceOf(ColorBorderTexture.class,
                    column.getStyle().backgroundTexture());
                assertEquals(-1, frame.border);
                assertEquals(0xFFF2F2F2, frame.color);
                assertEquals(count * 18, column.getSizeHeight());
                SpriteTexture background = assertInstanceOf(SpriteTexture.class, rail.getStyle().backgroundTexture());
                assertEquals(5, background.borderLT.getY());
                assertEquals(7, background.borderRB.getY());
            }
        }
    }

    @Test
    void fluidSlotsKeepSeparateHandlersForContainerTransfers() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var factory = mockStatic(ECOModularUI.class);
                var tools = mockStatic(NetworkToolItem.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            FluidTank input = new FluidTank(1_000);
            FluidTank output = new FluidTank(2_000);
            output.fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE);
            AtomicReference<UIElement> root = new AtomicReference<>();
            Player player = mock(Player.class);
            when(player.getInventory()).thenReturn(new Inventory(player));
            factory.when(() -> ECOModularUI.create(any(), eq(player))).thenAnswer(call -> {
                root.set(call.getArgument(0));
                return null;
            });

            IntegratedWorkstationUI.create(config(input, output, EnumSet.noneOf(RelativeSide.class),
                new ArrayList<>()), player);

            List<FluidSlot> slots = collect(root.get(), FluidSlot.class);
            assertEquals(2, slots.size());
            var handlerField = FluidSlot.class.getDeclaredField("boundHandler");
            handlerField.setAccessible(true);
            IFluidHandler inputHandler = (IFluidHandler) handlerField.get(slots.getFirst());
            IFluidHandler outputHandler = (IFluidHandler) handlerField.get(slots.getLast());
            assertSame(input, inputHandler);
            assertSame(output, outputHandler);
            assertTrue(slots.getFirst().isAllowClickDrained());
            assertTrue(slots.getLast().isAllowClickFilled());
            assertFalse(slots.getLast().isAllowClickDrained());
            inputHandler.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
            FluidStack extracted = outputHandler.drain(250, IFluidHandler.FluidAction.EXECUTE);
            assertTrue(extracted.is(Fluids.LAVA));
            assertEquals(500, input.getFluidAmount());
            assertEquals(750, output.getFluidAmount());
        }
    }

    @Test
    void outputSideButtonsKeepMirroredDirectionsAndApplyServerActions() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            EnumSet<RelativeSide> enabled = EnumSet.noneOf(RelativeSide.class);
            List<RelativeSide> clicked = new ArrayList<>();
            UIElement panel = WorkstationOutputPanel.create(config(new FluidTank(1_000),
                new FluidTank(1_000), enabled, clicked));
            List<Toggle> toggles = collect(panel, Toggle.class);
            List<RelativeSide> expected = List.of(RelativeSide.TOP, RelativeSide.RIGHT,
                RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BOTTOM, RelativeSide.BACK);
            assertEquals(expected.size(), toggles.size());
            for (int index = 0; index < toggles.size(); index++) {
                Toggle toggle = toggles.get(index);
                RelativeSide side = expected.get(index);
                UIEvent click = UIEvent.create(UIEvents.MOUSE_DOWN);
                click.button = 0;
                toggle.toggleButton.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor()
                    .apply(new Object[] {click});
                assertTrue(enabled.contains(side));
                toggle.setValue(true);
                UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                hover.target = toggle;
                UIEventDispatcher.dispatchEvent(hover, false, false, false);
                assertEquals("gui.neoecoae.integrated_working_station.allow_outputs.enabled",
                    hover.hoverTooltips.tooltipTexts().getLast().getString());
            }
            assertEquals(expected, clicked);
        }
    }

    @Test
    void networkToolSlotIsLockedInMainInventoryAndHotbar() throws ReflectiveOperationException {
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player);
        var method = WorkstationInventoryUI.class.getDeclaredMethod("playerSlot",
            Inventory.class, int.class, Integer.class);
        method.setAccessible(true);
        for (int locked : List.of(2, 17)) {
            for (int index = 0; index < 36; index++) {
                Slot slot = (Slot) method.invoke(null, inventory, index, locked);
                assertEquals(index != locked, slot.mayPickup(player));
                assertEquals(index != locked, slot.mayPlace(new ItemStack(Items.DIAMOND)));
            }
        }
    }

    private static IntegratedWorkstationUI.Config config(FluidTank input, FluidTank output,
            EnumSet<RelativeSide> enabled, List<RelativeSide> clicked) {
        return new IntegratedWorkstationUI.Config(input, output, new ItemStackHandler(10),
            new ItemStackHandler(4), () -> 0, 100, () -> {}, () -> {}, () -> false,
            ignored -> {}, ignored -> {}, enabled::contains, side -> {
                clicked.add(side);
                if (!enabled.remove(side)) enabled.add(side);
            }, (side, on) -> {
                if (on) enabled.add(side);
                else enabled.remove(side);
            }, ignored -> ItemStack.EMPTY, List::of);
    }

    private static <T> List<T> collect(UIElement root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        for (UIElement child : root.getChildren()) result.addAll(collect(child, type));
        return result;
    }
}
