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
import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.ItemHandlerSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.ColorBorderTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.FluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Toggle;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
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

        }
    }

    @Test
    void upgradeColumnMatchesNativeAe2GeometryAfterScreenResize() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
                    cn.dancingsnow.neoecoae.util.UiTestSupport.init(ui,screenWidth, 360);
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
                UIElement column = cn.dancingsnow.neoecoae.platform.JdkCompat.first(rail.getChildren());
                ColorBorderTexture frame = assertInstanceOf(ColorBorderTexture.class,
                    column.getStyle().backgroundTexture());
                assertEquals(-1, cn.dancingsnow.neoecoae.util.UiTestSupport.get(frame,"border"));
                assertEquals(0xFFF2F2F2, cn.dancingsnow.neoecoae.util.UiTestSupport.color(frame));
                assertEquals(count * 18, column.getSizeHeight());
                SpriteTexture background = assertInstanceOf(SpriteTexture.class, rail.getStyle().backgroundTexture());
                assertEquals(5, background.borderLT.y);
                assertEquals(7, background.borderRB.y);
            }
        }
    }

    @Test
    void integratedArtworkKeepsNativeSlotsAndTankInteriorsAfterResize() {
        try (var ldlib = mockStatic(LDLib.class); var factory = mockStatic(ECOModularUI.class);
                var tools = mockStatic(NetworkToolItem.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            AtomicReference<UIElement> captured = new AtomicReference<>();
            Player player = mock(Player.class);
            when(player.getInventory()).thenReturn(new Inventory(player));
            factory.when(() -> ECOModularUI.create(any(), eq(player))).thenAnswer(call -> {
                captured.set(call.getArgument(0));
                return null;
            });
            IntegratedWorkstationUI.create(config(new FluidTank(1_000), new FluidTank(1_000),
                EnumSet.noneOf(RelativeSide.class), new ArrayList<>()), player);
            UIElement root = captured.get();
            ModularUI ui = new ModularUI(UI.of(root));
            for (int width : List.of(640, 800)) {
                cn.dancingsnow.neoecoae.util.UiTestSupport.init(ui,width, 480);
                assertEquals(176, root.getSizeWidth());
                assertEquals(190, root.getSizeHeight());
                List<ItemSlot> slots = collect(root, ItemSlot.class);
                assertEquals(50, slots.size());
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        ItemSlot slot = slots.get(row * 3 + column);
                        assertEquals(root.getPositionX() + 36 + column * 18, slot.getContentX());
                        assertEquals(root.getPositionY() + 24 + row * 18, slot.getContentY());
                        assertEquals(16, slot.getContentWidth());
                        assertEquals(16, slot.getContentHeight());
                        assertSame(IGuiTexture.EMPTY, slot.getStyle().backgroundTexture());
                    }
                }
                var inventory = cn.dancingsnow.neoecoae.platform.JdkCompat.first(collect(root, InventorySlots.class));
                assertEquals(root.getPositionX() + 8, inventory.rows[0].slots[0].getContentX());
                assertEquals(root.getPositionY() + 106, inventory.rows[0].slots[0].getContentY());
                assertEquals(root.getPositionY() + 164, inventory.hotbar.slots[0].getContentY());
                assertEquals(16, inventory.hotbar.slots[0].getContentWidth());
                for (FluidSlot tank : collect(root, FluidSlot.class)) {
                    assertEquals(root.getPositionY() + 23, tank.getContentY());
                    assertEquals(16, tank.getContentWidth());
                    assertEquals(54, tank.getContentHeight());
                }
                List<Button> clearButtons = collect(root, Button.class).stream()
                    .filter(button -> button.getSizeWidth() == 12 && button.getSizeHeight() == 11).toList();
                assertEquals(2, clearButtons.size());
                for (Button button : clearButtons) {
                    assertEquals(root.getPositionY() + 81, button.getPositionY());
                    SpriteTexture base = assertInstanceOf(SpriteTexture.class, button.getButtonStyle().baseTexture());
                    SpriteTexture hover = assertInstanceOf(SpriteTexture.class, button.getButtonStyle().hoverTexture());
                    SpriteTexture pressed = assertInstanceOf(SpriteTexture.class, button.getButtonStyle().pressedTexture());
                    assertEquals(0, base.spritePosition.x);
                    assertEquals(12, hover.spritePosition.x);
                    assertEquals(24, pressed.spritePosition.x);
                    assertEquals(6, base.spritePosition.y);
                    assertEquals(WorkstationTextures.WIDGETS, base.getImageLocation());
                }
            }
        }
    }

    @Test
    void largeArtworkKeepsInventoryContentsAndFluidInsideBakedFrames() {
        try (var ldlib = mockStatic(LDLib.class); var factory = mockStatic(ECOModularUI.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            AtomicReference<UIElement> captured = new AtomicReference<>();
            Player player = mock(Player.class);
            when(player.getInventory()).thenReturn(new Inventory(player));
            factory.when(() -> ECOModularUI.create(any(), eq(player))).thenAnswer(call -> {
                captured.set(call.getArgument(0));
                return null;
            });
            IFluidHandler fluids = mock(IFluidHandler.class);
            when(fluids.getTanks()).thenReturn(2);
            when(fluids.getFluidInTank(anyInt())).thenReturn(FluidStack.EMPTY);
            when(fluids.getTankCapacity(anyInt())).thenReturn(1_000);
            LargeWorkstationUI.create(new LargeWorkstationUI.Config(fluids,
                offset -> new CompoundTag(), () -> null, ignored -> {}, () -> {}, () -> {}, () -> {},
                new ItemStackHandler(1)), player);
            UIElement root = captured.get();
            ModularUI ui = new ModularUI(UI.of(root));
            for (int width : List.of(640, 800)) {
                cn.dancingsnow.neoecoae.util.UiTestSupport.init(ui,width, 480);
                assertEquals(253, root.getSizeWidth());
                assertEquals(214, root.getSizeHeight());
                List<ItemSlot> allSlots = collect(root, ItemSlot.class);
                assertEquals(37, allSlots.size());
                List<ItemSlot> slots = allSlots.stream().filter(slot -> !(slot.getSlot() instanceof ItemHandlerSlot)).toList();
                assertEquals(36, slots.size());
                for (ItemSlot slot : slots) {
                    assertEquals(16, slot.getContentWidth());
                    assertEquals(16, slot.getContentHeight());
                }
                assertEquals(root.getPositionX() + 8, cn.dancingsnow.neoecoae.platform.JdkCompat.first(slots).getContentX());
                assertEquals(root.getPositionY() + 130, cn.dancingsnow.neoecoae.platform.JdkCompat.first(slots).getContentY());
                assertEquals(root.getPositionY() + 188, slots.get(27).getContentY());
                List<FluidSlot> tanks = collect(root, FluidSlot.class);
                assertEquals(2, tanks.size());
                assertEquals(root.getPositionX() + 204, cn.dancingsnow.neoecoae.platform.JdkCompat.first(tanks).getContentX());
                assertEquals(root.getPositionX() + 227, cn.dancingsnow.neoecoae.platform.JdkCompat.last(tanks).getContentX());
                for (FluidSlot tank : tanks) {
                    assertEquals(root.getPositionY() + 78, tank.getContentY());
                    assertEquals(18, tank.getContentWidth());
                    assertEquals(46, tank.getContentHeight());
                }
                List<Button> controls = collect(root, Button.class).stream()
                    .filter(button -> button.getId().startsWith("large-workstation-")
                        && button.getSizeWidth() == 15).toList();
                assertEquals(2, controls.size());
                assertEquals(List.of("large-workstation-overclock", "large-workstation-cooling"),
                    controls.stream().map(UIElement::getId).toList());
                for (int index = 0; index < controls.size(); index++) {
                    assertEquals(root.getPositionX() + List.of(102, 146).get(index), controls.get(index).getPositionX());
                    assertEquals(root.getPositionY() + 89, controls.get(index).getPositionY());
                }
                for (String id : List.of("temperature-setting", "temperature-info", "cooling-info", "overclock-info")) {
                    UIElement row = collect(root, UIElement.class).stream()
                        .filter(element -> ("large-workstation-" + id).equals(element.getId())).findFirst().orElseThrow();
                    assertFalse(row instanceof Button, "Status rows only display telemetry");
                    assertNull(cn.dancingsnow.neoecoae.util.UiTestSupport.serverAction(row), "No machine action may be registered on " + id);
                }
                UIElement scrollbar = collect(root, UIElement.class).stream()
                    .filter(element -> "large-workstation-scrollbar".equals(element.getId())).findFirst().orElseThrow();
                assertEquals(root.getPositionX() + 76, scrollbar.getPositionX());
                assertEquals(root.getPositionY() + 37, scrollbar.getPositionY());
                assertEquals(13, scrollbar.getSizeWidth());
                assertEquals(77, scrollbar.getSizeHeight());
                for (String id : List.of("overclock", "cooling", "temperature-setting",
                    "parallel", "temperature", "energy", "module-logo", "module-info", "task-0", "scrollbar", "status",
                    "temperature-info", "cooling-info", "overclock-info")) {
                    UIElement control = collect(root, UIElement.class).stream()
                        .filter(element -> ("large-workstation-" + id).equals(element.getId())).findFirst().orElseThrow();
                    var hit = root.hitTest(control.getPositionX() + 2, control.getPositionY() + 2);
                    assertNotNull(hit, id);
                    assertSame(control, hit, "The inventory container must not intercept " + id);
                    UIEvent hover = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                    hover.target = hit;
                    hover.target.dispatchEvent(hover);
                    assertNotNull(hover.hoverTooltips, id);
                    assertFalse(hover.hoverTooltips.tooltips().isEmpty(), id);
                }
                for (FluidSlot tank : tanks) {
                    var hit = root.hitTest(tank.getPositionX() + 2, tank.getPositionY() + 2);
                    assertNotNull(hit);
                    UIEvent hover = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                    hover.target = hit;
                    hover.target.dispatchEvent(hover);
                    assertNotNull(hover.hoverTooltips, "Empty fluid tanks still explain their role and capacity");
                    assertTrue(hover.hoverTooltips.tooltips().size() >= 3);
                    var title = cn.dancingsnow.neoecoae.platform.JdkCompat.first(hover.hoverTooltips.tooltips());
                    // The isolated JVM has no NeoForge fluid-type registry; only its descriptive fields are mocked.
                    try (var fluidInfo = mockStatic(com.lowdragmc.lowdraglib.side.fluid.FluidHelper.class)) {
                        fluidInfo.when(() -> com.lowdragmc.lowdraglib.side.fluid.FluidHelper.getDisplayName(any()))
                            .thenReturn(net.minecraft.network.chat.Component.literal("Water"));
                        fluidInfo.when(com.lowdragmc.lowdraglib.side.fluid.FluidHelper::getUnit).thenReturn("mB");
                        for (FluidStack contents : List.of(new FluidStack(Fluids.WATER, 500), FluidStack.EMPTY)) {
                            ((com.lowdragmc.lowdraglib.gui.widget.TankWidget)cn.dancingsnow.neoecoae.util.UiTestSupport.get(tank,"tank")).setFluid(com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(contents.getFluid(),contents.getAmount(),contents.getTag()),false);
                            UIEvent filledHover = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                            filledHover.target = tank;
                            filledHover.target.dispatchEvent(filledHover);
                            assertEquals(title, cn.dancingsnow.neoecoae.platform.JdkCompat.first(filledHover.hoverTooltips.tooltips()),
                                "The tank role must remain visible when fluid replaces the empty placeholder");
                        }
                    }
                }
                ItemSlot module = allSlots.stream().filter(slot -> slot.getSlot() instanceof ItemHandlerSlot).findFirst().orElseThrow();
                assertEquals(root.getPositionX() + 227, module.getContentX());
                assertEquals(root.getPositionY() + 42, module.getContentY());
                assertEquals(16, module.getContentWidth());
                assertEquals(16, module.getContentHeight());
                assertSame(module, root.hitTest(module.getPositionX() + 3, module.getPositionY() + 3));
            }
        }
    }

    @Test
    void fluidSlotsKeepSeparateHandlersForContainerTransfers() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib.class); var factory = mockStatic(ECOModularUI.class);
                var tools = mockStatic(NetworkToolItem.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
            IFluidHandler inputHandler = (IFluidHandler) handlerField.get(cn.dancingsnow.neoecoae.platform.JdkCompat.first(slots));
            IFluidHandler outputHandler = (IFluidHandler) handlerField.get(cn.dancingsnow.neoecoae.platform.JdkCompat.last(slots));
            assertSame(input, inputHandler);
            assertSame(output, outputHandler);
            assertTrue((boolean)cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.platform.JdkCompat.first(slots),"tank"),"allowClickDrained"));
            assertTrue((boolean)cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.platform.JdkCompat.last(slots),"tank"),"allowClickFilled"));
            assertFalse((boolean)cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.util.UiTestSupport.get(cn.dancingsnow.neoecoae.platform.JdkCompat.last(slots),"tank"),"allowClickDrained"));
            inputHandler.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
            FluidStack extracted = outputHandler.drain(250, IFluidHandler.FluidAction.EXECUTE);
            assertTrue(extracted.getFluid()==Fluids.LAVA);
            assertEquals(500, input.getFluidAmount());
            assertEquals(750, output.getFluidAmount());
        }
    }

    @Test
    void outputSideButtonsKeepMirroredDirectionsAndApplyServerActions() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
                UIEvent click = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
                click.button = 0;
                cn.dancingsnow.neoecoae.util.UiTestSupport.serverClick(toggle.toggleButton,click);
                assertTrue(enabled.contains(side));
                toggle.setValue(true);
                UIEvent hover = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                hover.target = toggle;
                hover.target.dispatchEvent(hover);
                assertEquals("gui.neoecoae.integrated_working_station.allow_outputs.enabled",
                    cn.dancingsnow.neoecoae.platform.JdkCompat.last(hover.hoverTooltips.tooltips()).getString());
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
