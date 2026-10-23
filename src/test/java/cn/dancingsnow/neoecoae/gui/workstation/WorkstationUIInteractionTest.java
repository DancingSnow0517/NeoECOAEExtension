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
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
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
import net.minecraft.nbt.CompoundTag;
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
    void integratedArtworkKeepsNativeSlotsAndTankInteriorsAfterResize() {
        try (var ldlib = mockStatic(LDLib2.class); var factory = mockStatic(ECOModularUI.class);
                var tools = mockStatic(NetworkToolItem.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
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
                ui.init(width, 480);
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
                var inventory = collect(root, InventorySlots.class).getFirst();
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
                    assertEquals(0, base.spritePosition.getX());
                    assertEquals(12, hover.spritePosition.getX());
                    assertEquals(24, pressed.spritePosition.getX());
                    assertEquals(6, base.spritePosition.getY());
                    assertEquals(WorkstationTextures.WIDGETS, base.getImageLocation());
                }
            }
        }
    }

    @Test
    void largeArtworkKeepsInventoryContentsAndFluidInsideBakedFrames() {
        try (var ldlib = mockStatic(LDLib2.class); var factory = mockStatic(ECOModularUI.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
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
                ui.init(width, 480);
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
                assertEquals(root.getPositionX() + 8, slots.getFirst().getContentX());
                assertEquals(root.getPositionY() + 130, slots.getFirst().getContentY());
                assertEquals(root.getPositionY() + 188, slots.get(27).getContentY());
                List<FluidSlot> tanks = collect(root, FluidSlot.class);
                assertEquals(2, tanks.size());
                assertEquals(root.getPositionX() + 204, tanks.getFirst().getContentX());
                assertEquals(root.getPositionX() + 227, tanks.getLast().getContentX());
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
                    assertNull(row.getBaubleServerEvent(UIEvents.MOUSE_DOWN), "No machine action may be registered on " + id);
                }
                UIElement scrollbar = collect(root, UIElement.class).stream()
                    .filter(element -> "large-workstation-scrollbar".equals(element.getId())).findFirst().orElseThrow();
                assertEquals(root.getPositionX() + 79, scrollbar.getPositionX());
                assertEquals(root.getPositionY() + 37, scrollbar.getPositionY());
                assertEquals(8, scrollbar.getSizeWidth());
                assertEquals(77, scrollbar.getSizeHeight());
                for (String id : List.of("overclock", "cooling", "temperature-setting",
                    "parallel", "temperature", "energy", "module-logo", "module-info", "task-0", "scrollbar", "status",
                    "temperature-info", "cooling-info", "overclock-info")) {
                    UIElement control = collect(root, UIElement.class).stream()
                        .filter(element -> ("large-workstation-" + id).equals(element.getId())).findFirst().orElseThrow();
                    var hit = root.hitTest(control.getPositionX() + 2, control.getPositionY() + 2);
                    assertNotNull(hit, id);
                    assertSame(control, hit.getA(), "The inventory container must not intercept " + id);
                    UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                    hover.target = hit.getA();
                    UIEventDispatcher.dispatchEvent(hover, false, false, false);
                    assertNotNull(hover.hoverTooltips, id);
                    assertFalse(hover.hoverTooltips.tooltipTexts().isEmpty(), id);
                }
                for (FluidSlot tank : tanks) {
                    var hit = root.hitTest(tank.getPositionX() + 2, tank.getPositionY() + 2);
                    assertNotNull(hit);
                    UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                    hover.target = hit.getA();
                    UIEventDispatcher.dispatchEvent(hover, false, false, false);
                    assertNotNull(hover.hoverTooltips, "Empty fluid tanks still explain their role and capacity");
                    assertTrue(hover.hoverTooltips.tooltipTexts().size() >= 3);
                    var title = hover.hoverTooltips.tooltipTexts().getFirst();
                    // The isolated JVM has no NeoForge fluid-type registry; only its descriptive fields are mocked.
                    try (var fluidInfo = mockStatic(com.lowdragmc.lowdraglib2.utils.FluidHelper.class)) {
                        fluidInfo.when(() -> com.lowdragmc.lowdraglib2.utils.FluidHelper.getDisplayName(any()))
                            .thenReturn(net.minecraft.network.chat.Component.literal("Water"));
                        fluidInfo.when(com.lowdragmc.lowdraglib2.utils.FluidHelper::getUnit).thenReturn("mB");
                        for (FluidStack contents : List.of(new FluidStack(Fluids.WATER, 500), FluidStack.EMPTY)) {
                            tank.setFluid(contents);
                            UIEvent filledHover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                            filledHover.target = tank;
                            UIEventDispatcher.dispatchEvent(filledHover, false, false, false);
                            assertEquals(title, filledHover.hoverTooltips.tooltipTexts().getFirst(),
                                "The tank role must remain visible when fluid replaces the empty placeholder");
                        }
                    }
                }
                ItemSlot module = allSlots.stream().filter(slot -> slot.getSlot() instanceof ItemHandlerSlot).findFirst().orElseThrow();
                assertEquals(root.getPositionX() + 227, module.getContentX());
                assertEquals(root.getPositionY() + 42, module.getContentY());
                assertEquals(16, module.getContentWidth());
                assertEquals(16, module.getContentHeight());
                assertSame(module, root.hitTest(module.getPositionX() + 3, module.getPositionY() + 3).getA());
            }
        }
    }

    @Test
    void largeTaskQueueColorsRunningAndQueuedStatuses() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            CompoundTag snapshot = taskSnapshot(12);
            var panel = taskPanel(snapshot);
            for (int row = 0; row < 2; row++) {
                UIElement region = taskRegion(panel, "task-" + row);
                UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                hover.target = region;
                UIEventDispatcher.dispatchEvent(hover, false, false, false);
                assertEquals(row == 0 ? 0x55FF55 : 0xFFFF55,
                    hover.hoverTooltips.tooltipTexts().get(3).getStyle().getColor().getValue());
            }
            snapshot.getList("tasks", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putString("status", "gui.neoecoae.workstation.pause.no_power");
            panel.setValue(snapshot.copy());
            UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            hover.target = taskRegion(panel, "task-0");
            UIEventDispatcher.dispatchEvent(hover, false, false, false);
            assertEquals(0x9B4438, hover.hoverTooltips.tooltipTexts().get(3).getStyle().getColor().getValue());
        }
    }

    @Test
    void largeTaskQueueMovesHitboxesWithTheCardsAndClipsPartialRows() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            var panel = taskPanel(taskSnapshot(12));
            var ui = new ModularUI(UI.of(panel));
            ui.init(640, 480);
            UIEvent wheel = UIEvent.create(UIEvents.MOUSE_WHEEL);
            wheel.x = panel.getPositionX() + 20;
            wheel.y = panel.getPositionY() + 50;
            wheel.deltaY = -0.5f;
            wheel.target = panel.hitTest(wheel.x, wheel.y).getA();
            UIEventDispatcher.dispatchEvent(wheel, true, true, false);
            panel.advanceScroll(1.0 / 60);
            computeStyles(panel);
            UIElement first = taskRegion(panel, "task-0");
            float shift = first.getStyle().transform2D().translate().resolveY(first.getSizeHeight());
            assertTrue(shift < 0 && shift > -13, "Wheel input should animate through fractional pixels");
            UIElement button = taskRegion(panel, "return-0");
            assertSame(button, panel.hitTest(button.getPositionX() + 2, button.getPositionY() + shift + 2).getA());
            assertNotSame(first, panel.hitTest(panel.getPositionX() + 15, panel.getPositionY() + 37).getA());
            UIElement bottom = taskRegion(panel, "task-3");
            assertSame(bottom, panel.hitTest(panel.getPositionX() + 15, panel.getPositionY() + 113).getA(),
                "The incoming fourth card is already interactive inside the viewport");
            UIElement outside = taskRegion(panel, "return-3");
            assertNotSame(outside, panel.hitTest(outside.getPositionX() + 2, outside.getPositionY() + shift + 2).getA(),
                "A return button outside the clipped viewport must not intercept clicks");
            panel.advanceScroll(1);
            computeStyles(panel);
            assertEquals(-13, first.getStyle().transform2D().translate().resolveY(first.getSizeHeight()));
            UIEvent more = UIEvent.create(UIEvents.MOUSE_WHEEL);
            more.x = wheel.x;
            more.y = wheel.y;
            more.deltaY = -0.25f;
            more.target = panel.hitTest(more.x, more.y).getA();
            UIEventDispatcher.dispatchEvent(more, true, true, false);
            panel.advanceScroll(1);
            computeStyles(panel);
            assertSame(outside, panel.hitTest(outside.getPositionX() + 2, outside.getPositionY() - 19.5f + 2).getA(),
                "The partially visible incoming return button must follow its card");
        }
    }

    @Test
    void largeTaskScrollbarRetainsItsGrabPointAndCanStopBetweenRows() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            var panel = taskPanel(taskSnapshot(12));
            var ui = new ModularUI(UI.of(panel));
            ui.init(640, 480);
            UIElement scrollbar = taskRegion(panel, "scrollbar");
            assertSame(scrollbar, panel.hitTest(scrollbar.getPositionX() + 1, scrollbar.getPositionY() + 19).getA(),
                "The thumb track must stay above the task cards along their shared edge");
            UIEvent press = UIEvent.create(UIEvents.MOUSE_DOWN);
            press.button = 0;
            press.x = scrollbar.getPositionX() + 3;
            press.y = scrollbar.getPositionY() + 5;
            press.target = scrollbar;
            UIEventDispatcher.dispatchEvent(press, false, false, false);
            UIEvent drag = UIEvent.create(UIEvents.DRAG_SOURCE_UPDATE);
            drag.x = press.x;
            drag.y = press.y + 2.5f;
            drag.target = scrollbar;
            UIEventDispatcher.dispatchEvent(drag, false, false, false);
            computeStyles(panel);
            UIElement row = taskRegion(panel, "task-0");
            float shift = row.getStyle().transform2D().translate().resolveY(row.getSizeHeight());
            assertEquals(-2.5 / 62 * 9 * 26, shift, 1e-5);
            panel.advanceScroll(1);
            computeStyles(panel);
            assertEquals(shift, row.getStyle().transform2D().translate().resolveY(row.getSizeHeight()));
        }
    }

    @Test
    void largeTaskScrollbarDrawsTheNativeHandleInsideTheBakedTrack() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            for (int total : List.of(0, 12)) {
                var panel = taskPanel(taskSnapshot(total));
                new ModularUI(UI.of(panel)).init(640, 480);
                UIElement scrollbar = taskRegion(panel, "scrollbar");
                var context = mock(GUIContext.class);
                scrollbar.drawBackgroundAdditional(context);
                verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                        && sprite.spritePosition.getX() == (total > 3 ? 69 : 75)
                        && sprite.spritePosition.getY() == 62),
                    eq(panel.getPositionX() + 80), eq(panel.getPositionY() + 37), eq(6F), eq(15F));
                assertTrue(scrollbar.getPositionX() >= panel.getPositionX() + 78,
                    "The scrollbar must not cover task cards or their return buttons");
            }
        }
    }

    private static LargeWorkstationPanel taskPanel(CompoundTag snapshot) {
        return new LargeWorkstationPanel(new LargeWorkstationUI.Config(new FluidTank(1_000),
            offset -> snapshot.copy(), () -> null, ignored -> {}, () -> {}, () -> {}, () -> {},
            new ItemStackHandler(1)), mock(Player.class));
    }

    private static UIElement taskRegion(UIElement panel, String id) {
        return collect(panel, UIElement.class).stream()
            .filter(element -> ("large-workstation-" + id).equals(element.getId())).findFirst().orElseThrow();
    }

    private static void computeStyles(UIElement element) {
        element.getStyleBag().compute(0);
        for (var child : element.getChildren()) computeStyles(child);
    }

    private static CompoundTag taskSnapshot(int total) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("totalTasks", total);
        var tasks = new net.minecraft.nbt.ListTag();
        for (int row = 0; row < Math.min(total, LargeWorkstationUI.SNAPSHOT_SIZE); row++) {
            var task = new CompoundTag();
            task.putUUID("id", new java.util.UUID(0, row));
            task.putLong("amount", 100 + row);
            task.putBoolean("returnable", true);
            task.putBoolean("current", row == 0);
            task.putString("status", "gui.neoecoae.workstation." + (row == 0 ? "pause.none" : "task_queued"));
            tasks.add(task);
        }
        snapshot.put("tasks", tasks);
        return snapshot;
    }

    @Test
    void overclockButtonSeparatesLeftClickFromRightClickPreheat() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            var overclock = new java.util.concurrent.atomic.AtomicInteger();
            var preheat = new java.util.concurrent.atomic.AtomicInteger();
            var cooling = new java.util.concurrent.atomic.AtomicInteger();
            var panel = new LargeWorkstationPanel(new LargeWorkstationUI.Config(new FluidTank(1000),
                offset -> new CompoundTag(), () -> null, ignored -> {}, overclock::incrementAndGet,
                cooling::incrementAndGet, preheat::incrementAndGet, new ItemStackHandler(1)), mock(Player.class));
            Button control = collect(panel, Button.class).stream()
                .filter(button -> "large-workstation-overclock".equals(button.getId())).findFirst().orElseThrow();
            for (int button : List.of(0, 1, 2)) {
                UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
                event.button = button;
                control.getBaubleServerEvent(UIEvents.MOUSE_DOWN).executor().apply(new Object[] {event});
            }
            assertEquals(1, overclock.get());
            assertEquals(1, preheat.get());
            assertEquals(0, cooling.get());
        }
    }

    @Test
    void temperatureHoverShowsActualSignedRatesAndHidesThemInCoolantMode() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            var snapshot = new CompoundTag();
            snapshot.putDouble("temperature", 15.5);
            snapshot.putDouble("temperatureChangePerSecond", 0.123);
            snapshot.putDouble("naturalCoolingPerSecond", 0.1);
            var panel = new LargeWorkstationPanel(new LargeWorkstationUI.Config(new FluidTank(1000),
                offset -> snapshot.copy(), () -> null, ignored -> {}, () -> {}, () -> {}, () -> {},
                new ItemStackHandler(1)), mock(Player.class));
            UIElement region = collect(panel, UIElement.class).stream()
                .filter(element -> "large-workstation-temperature-info".equals(element.getId())).findFirst().orElseThrow();
            UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            hover.target = region;
            UIEventDispatcher.dispatchEvent(hover, false, false, false);
            var lines = hover.hoverTooltips.tooltipTexts();
            var temperature = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, lines.get(1).getContents());
            assertEquals("15.50", temperature.getArgs()[0]);
            var net = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, lines.get(3).getContents());
            assertEquals("gui.neoecoae.workstation.temperature_net_rate", net.getKey());
            assertEquals("+0.12", net.getArgs()[0]);
            var processing = assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, lines.get(4).getContents());
            assertEquals("gui.neoecoae.workstation.temperature_processing_rate", processing.getKey());
            assertEquals("0.00", processing.getArgs()[0], "Idle telemetry must not substitute theoretical full-load heating");
            assertFalse(lines.stream().anyMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && "gui.neoecoae.workstation.temperature_preheat_rate".equals(text.getKey())));
            snapshot.putBoolean("temperatureEnabled", false);
            panel.setValue(snapshot.copy());
            hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            hover.target = region;
            UIEventDispatcher.dispatchEvent(hover, false, false, false);
            assertEquals(3, hover.hoverTooltips.tooltipTexts().size());
            assertTrue(hover.hoverTooltips.tooltipTexts().stream().noneMatch(line -> line.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents text && text.getKey().endsWith("_rate")));
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
