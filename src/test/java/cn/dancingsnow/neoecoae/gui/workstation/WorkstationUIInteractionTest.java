package cn.dancingsnow.neoecoae.gui.workstation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.IManagedGridNode;
import appeng.items.tools.NetworkToolItem;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchInterfaceConfig;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkstationUIInteractionTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initializeUi();
    }

    private static final List<String> SMART_COOLING_TEXTS = List.of(
        "title", "subtitle", "target-label", "target-range", "target-value", "target-min", "target-max",
        "status-label", "status", "status-hint", "buffer-label", "buffer-amount", "buffer-target",
        "energy-label", "energy-value", "coolant-use-label", "coolant-use-value", "help", "help-warning");

    @Test
    @SuppressWarnings("unchecked")
    void smartCoolingTextAndControlsStayInSeparateRegionsAfterStatusUpdatesAndResize() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            UIElement root = SmartCoolingModuleUI.createRoot(smartCoolingHost());
            ModularUI ui = new ModularUI(UI.of(root));
            BindableValue<Component> status = (BindableValue<Component>) root.selectId("status-sync").findFirst().orElseThrow();
            BindableValue<Component> hint = (BindableValue<Component>) root.selectId("status-hint-sync").findFirst().orElseThrow();
            for (int[] size : List.of(new int[] {320, 240}, new int[] {640, 360}, new int[] {800, 480})) {
                for (Component state : List.of(
                    Component.translatable("gui.neoecoae.smart_cooling.unformed"),
                    Component.translatable("gui.neoecoae.smart_cooling.disabled"),
                    Component.translatable("gui.neoecoae.smart_cooling.holding"),
                    Component.translatable("gui.neoecoae.smart_cooling.heating"))) {
                    status.setValue(state);
                    hint.setValue(Component.translatable("gui.neoecoae.smart_cooling.current", "99.99"));
                    ui.init(size[0], size[1]);
                    assertTrue(root.getPositionX() >= 0 && root.getPositionY() >= 0);
                    assertTrue(root.getPositionX() + root.getSizeWidth() <= size[0]);
                    assertTrue(root.getPositionY() + root.getSizeHeight() <= size[1]);
                    assertEquals(state, ((TextElement) root.selectId("status").findFirst().orElseThrow()).getText());
                    for (String id : SMART_COOLING_TEXTS) {
                        TextElement text = (TextElement) root.selectId(id).findFirst().orElseThrow();
                        assertEquals(TextWrap.WRAP, text.getTextStyle().textWrap());
                        assertFalse(text.getTextStyle().textShadow());
                        assertFalse(text.getTextStyle().adaptiveWidth());
                        assertFalse(text.getTextStyle().adaptiveHeight());
                        assertTrue(text.getSizeHeight() > 0);
                        assertInside(text, text.getParent());
                    }
                    // Rows stack vertically; the two cards of each row sit side by side.
                    List<List<String>> rows = List.of(List.of("badge"), List.of("settings"),
                        List.of("status-card", "buffer"), List.of("energy", "coolant-use"), List.of("notes"));
                    for (int row = 0; row < rows.size(); row++) {
                        List<UIElement> cards = rows.get(row).stream()
                            .map(id -> root.selectId(id).findFirst().orElseThrow()).toList();
                        cards.forEach(card -> assertInside(card, root));
                        if (cards.size() == 2) assertTrue(cards.get(0).getPositionX() + cards.get(0).getSizeWidth()
                            <= cards.get(1).getPositionX());
                        if (row == 0) continue;
                        for (String above : rows.get(row - 1)) {
                            UIElement previous = root.selectId(above).findFirst().orElseThrow();
                            for (UIElement card : cards) {
                                assertTrue(previous.getPositionY() + previous.getSizeHeight() <= card.getPositionY(), above);
                            }
                        }
                    }
                    UIElement label = root.selectId("target-label").findFirst().orElseThrow();
                    UIElement value = root.selectId("target-value").findFirst().orElseThrow();
                    UIElement decrease = root.selectId("target-decrease").findFirst().orElseThrow();
                    UIElement field = root.selectId("target").findFirst().orElseThrow();
                    UIElement increase = root.selectId("target-increase").findFirst().orElseThrow();
                    assertTrue(label.getPositionX() + label.getSizeWidth() <= decrease.getPositionX());
                    assertTrue(value.getPositionX() + value.getSizeWidth() <= decrease.getPositionX());
                    assertTrue(decrease.getPositionX() + decrease.getSizeWidth() <= field.getPositionX());
                    assertTrue(field.getPositionX() + field.getSizeWidth() <= increase.getPositionX());
                    for (UIElement control : List.of(decrease, field, increase)) assertInside(control, control.getParent());
                }
            }
        }
    }

    @Test
    void smartCoolingKeepsTargetEditingAndShowsTheSynchronizedCoolantAmount() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = smartCoolingHost();
            host.setLevel(mock(Level.class));
            UIElement root = SmartCoolingModuleUI.createRoot(host);
            TextField field = (TextField) root.selectId("target").findFirst().orElseThrow();
            for (int target : List.of(0, 99, 75)) {
                field.setText(String.valueOf(target));
                assertEquals(target, host.getTargetTemperature());
            }
            field.setText("");
            assertEquals(75, host.getTargetTemperature());
            assertEquals("75%", ((TextElement) root.selectId("target-value").findFirst().orElseThrow()).getText().getString());
            FluidSlot coolant = (FluidSlot) root.selectId("coolant").findFirst().orElseThrow();
            assertFalse(coolant.isAllowClickFilled());
            assertTrue(coolant.isAllowClickDrained());
            FluidSlot filter = (FluidSlot) root.selectId("coolant-filter").findFirst().orElseThrow();
            assertFalse(filter.isAllowClickFilled());
            assertFalse(filter.isAllowClickDrained());
            assertNotSame(coolant, filter);
            TextElement amount = (TextElement) root.selectId("buffer-amount").findFirst().orElseThrow();
            for (int quantity : List.of(2_000, 16_384_000, 0)) {
                coolant.setValue(quantity == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, quantity), false);
                var contents = assertInstanceOf(TranslatableContents.class, amount.getText().getContents());
                assertEquals("gui.neoecoae.smart_cooling.buffer_amount", contents.getKey());
                String expected = switch (quantity) {
                    case 2_000 -> "2K";
                    case 16_384_000 -> "16.3M";
                    default -> "0";
                };
                assertArrayEquals(new Object[] {expected}, contents.getArgs());
            }
            assertEquals(SmartCoolingModuleUI.Phase.OFFLINE, SmartCoolingModuleUI.phase(host));
            assertEquals("gui.neoecoae.smart_cooling.unformed",
                ((TranslatableContents) SmartCoolingModuleUI.status(host).getContents()).getKey());
            assertEquals("gui.neoecoae.smart_cooling.unformed_hint",
                ((TranslatableContents) SmartCoolingModuleUI.statusHint(host).getContents()).getKey());
        }
    }

    @Test
    void smartCoolingChineseAndEnglishTranslationsFitTheirWrappedTextRegions() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = SmartCoolingModuleUI.createRoot(smartCoolingHost());
            new ModularUI(UI.of(root)).init(320, 240);
            // Conservative glyph widths exercise Minecraft's wrapping without creating a GL client.
            StringSplitter splitter = new StringSplitter((codePoint, style) -> codePoint == ' ' ? 4 : codePoint < 128 ? 6 : 9);
            for (String locale : List.of("zh_cn", "en_us")) {
                try (var stream = getClass().getClassLoader().getResourceAsStream("assets/neoecoae/lang/" + locale + ".json")) {
                    assertNotNull(stream);
                    var translations = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String id : SMART_COOLING_TEXTS) {
                        TextElement text = (TextElement) root.selectId(id).findFirst().orElseThrow();
                        if (!(text.getText().getContents() instanceof TranslatableContents contents)) continue;
                        Object[] args = id.equals("buffer-amount") || id.equals("buffer-target")
                            ? new Object[] {"16,384,000"} : contents.getArgs();
                        String translated = String.format(java.util.Locale.ROOT,
                            translations.get(contents.getKey()).getAsString(), args);
                        assertTextFits(splitter, text, translated, locale);
                    }
                    TextElement status = (TextElement) root.selectId("status").findFirst().orElseThrow();
                    for (String key : List.of("unformed", "unmarked", "disabled", "heating", "cooling", "holding")) {
                        assertTextFits(splitter, status, translations.get("gui.neoecoae.smart_cooling." + key).getAsString(), locale);
                    }
                    TextElement hint = (TextElement) root.selectId("status-hint").findFirst().orElseThrow();
                    for (String key : List.of("unformed_hint", "coolant_mode", "current")) {
                        String translated = String.format(java.util.Locale.ROOT,
                            translations.get("gui.neoecoae.smart_cooling." + key).getAsString(), "99.99");
                        assertTextFits(splitter, hint, translated, locale);
                    }
                }
            }
        }
    }

    @Test
    void smartCoolingMarkerClicksRequestServerEditsWithoutMovingFluidOrItems() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = smartCoolingHost();
            var player = mock(Player.class);
            when(player.level()).thenReturn(mock(Level.class));
            var root = SmartCoolingModuleUI.createRoot(host, player);
            FluidSlot filter = (FluidSlot) root.selectId("coolant-filter").findFirst().orElseThrow();
            var request = mock(RPCEmitter.class);
            var field = filter.getClass().getDeclaredField("click");
            field.setAccessible(true);
            field.set(filter, request);
            for (int button : List.of(0, 1)) {
                UIEvent click = UIEvent.create(UIEvents.MOUSE_DOWN);
                click.target = filter;
                click.button = button;
                UIEventDispatcher.dispatchEvent(click, false, false, false);
                assertTrue(click.hasHandler);
                verify(request).send(button);
            }
            assertTrue(host.tank.isEmpty());
            assertTrue(host.getMarkedCoolant().isEmpty());
            new ModularUI(UI.of(root)).init(320, 240);
            assertInside(filter, filter.getParent());
            UIElement title = root.selectId("buffer-label").findFirst().orElseThrow();
            assertTrue(title.getPositionX() + title.getSizeWidth() <= filter.getPositionX());
            UIElement stock = root.selectId("coolant").findFirst().orElseThrow();
            assertTrue(filter.getPositionY() + filter.getSizeHeight() <= stock.getPositionY());
        }
    }

    @Test
    void smartCoolingCanMarkFromTheMainHandWithoutAnInventoryAndStillClearWithAnEmptyCursor() {
        var host = mock(ECOLargeIntegratedWorkingStationInputHatchBlockEntity.class);
        when(host.getMarkedCoolant()).thenReturn(FluidStack.EMPTY);
        var player = mock(Player.class);
        var menu = mock(AbstractContainerMenu.class);
        player.containerMenu = menu;
        var bucket = new ItemStack(Items.WATER_BUCKET);
        when(player.getMainHandItem()).thenReturn(bucket);
        when(menu.getCarried()).thenReturn(ItemStack.EMPTY);
        try (var config = mockStatic(FluidHatchInterfaceConfig.class)) {
            config.when(() -> FluidHatchInterfaceConfig.click(null, bucket, 1))
                .thenReturn(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
            SmartCoolingModuleUI.markCoolant(host, player, 1);
            verify(host).setMarkedCoolant(argThat(fluid -> fluid.getAmount() == 1 && fluid.getFluid() == Fluids.WATER));
            SmartCoolingModuleUI.markCoolant(host, player, 0);
            config.verify(() -> FluidHatchInterfaceConfig.click(null, ItemStack.EMPTY, 0));
            verify(host).setMarkedCoolant(FluidStack.EMPTY);
            // A container already on the cursor takes precedence over the held item.
            var carried = new ItemStack(Items.LAVA_BUCKET);
            when(menu.getCarried()).thenReturn(carried);
            SmartCoolingModuleUI.markCoolant(host, player, 1);
            config.verify(() -> FluidHatchInterfaceConfig.click(null, carried, 1));
            verify(menu, never()).setCarried(any());
            assertEquals(1, bucket.getCount());
            assertEquals(1, carried.getCount());
        }
    }

    private static void assertTextFits(StringSplitter splitter, TextElement text, String translated, String locale) {
        var style = text.getTextStyle();
        // LDLib wraps at the unscaled width: content width divided by fontSize / 9.
        int wrapWidth = Math.round(text.getContentWidth() * 9 / style.fontSize());
        int lines = splitter.splitLines(translated, wrapWidth, Style.EMPTY).size();
        float renderedHeight = lines * style.fontSize() + Math.max(0, lines - 1) * style.lineSpacing();
        assertTrue(renderedHeight <= text.getContentHeight(), locale + " " + text.getId() + ": " + translated);
    }

    private static ECOLargeIntegratedWorkingStationInputHatchBlockEntity smartCoolingHost() {
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        return new ECOLargeIntegratedWorkingStationInputHatchBlockEntity(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState()) {
            @Override public boolean isSmartCoolingModule() { return true; }
            @Override protected IManagedGridNode createMainNode() { return mock(IManagedGridNode.class, RETURNS_SELF); }
            @Override public void setChanged() {}
            @Override public void markForUpdate() {}
        };
    }

    private static void assertInside(UIElement child, UIElement parent) {
        assertTrue(child.getPositionX() >= parent.getPositionX(), child.getId());
        assertTrue(child.getPositionY() >= parent.getPositionY(), child.getId());
        assertTrue(child.getPositionX() + child.getSizeWidth() <= parent.getPositionX() + parent.getSizeWidth(), child.getId());
        assertTrue(child.getPositionY() + child.getSizeHeight() <= parent.getPositionY() + parent.getSizeHeight(), child.getId());
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
    void largeArtworkKeepsInventoryContentsAndFluidInsideBakedFrames() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var factory = mockStatic(ECOModularUI.class);
             var minecraft = mockStatic(net.minecraft.client.Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            installFont(minecraft);
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
    void temperatureHoverShowsActualSignedRatesAndHidesThemInCoolantMode() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(net.minecraft.client.Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            installFont(minecraft);
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
            // The card keeps the vanilla tooltip frame: a title line followed by the card component.
            assertEquals(1, hover.hoverTooltips.tooltipTexts().size());
            assertNotNull(hover.hoverTooltips.tooltipComponent());
            var texts = panel.temperatureCardBuilder().texts();
            assertTrue(texts.stream().anyMatch(text -> "15.50%".equals(text.getString())));
            // Rows follow the card order: net change, processing heat, active cooling, natural cooling.
            var net = translatable(texts, "gui.neoecoae.workstation.card.rate");
            assertEquals("+0.12", net.getArgs()[0]);
            var rates = texts.stream().map(Component::getContents)
                .filter(TranslatableContents.class::isInstance).map(TranslatableContents.class::cast)
                .filter(text -> "gui.neoecoae.workstation.card.rate_short".equals(text.getKey())).toList();
            assertEquals("+0.00", rates.getFirst().getArgs()[0], "Idle telemetry must not substitute theoretical full-load heating");
            assertEquals("-0.10", rates.getLast().getArgs()[0]);
            assertFalse(texts.stream().anyMatch(text -> text.getContents() instanceof TranslatableContents contents
                && "gui.neoecoae.workstation.card.preheat".equals(contents.getKey())));
            snapshot.putBoolean("temperatureEnabled", false);
            panel.setValue(snapshot.copy());
            hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
            hover.target = region;
            UIEventDispatcher.dispatchEvent(hover, false, false, false);
            assertNotNull(hover.hoverTooltips.tooltipComponent());
            assertTrue(panel.temperatureCardBuilder().texts().stream().noneMatch(text -> text.getContents()
                instanceof TranslatableContents contents && contents.getKey().startsWith("gui.neoecoae.workstation.card.rate")));
        }
    }

    @Test
    void overclockEnergyAndModuleHoversAreCards() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(net.minecraft.client.Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            installFont(minecraft);
            var snapshot = new CompoundTag();
            snapshot.putBoolean("overclocked", true);
            snapshot.putLong("parallelCapacity", 256);
            snapshot.putDouble("energyUsage", 12.5);
            snapshot.putDouble("energyStored", 4_000_000);
            snapshot.putDouble("energyCapacity", 16_000_000);
            var panel = new LargeWorkstationPanel(new LargeWorkstationUI.Config(new FluidTank(1000),
                offset -> snapshot.copy(), () -> null, ignored -> {}, () -> {}, () -> {}, () -> {},
                new ItemStackHandler(1)), mock(Player.class));
            for (String id : List.of("overclock", "overclock-info", "energy", "module-info", "module-logo")) {
                UIElement region = collect(panel, UIElement.class).stream()
                    .filter(element -> ("large-workstation-" + id).equals(element.getId())).findFirst().orElseThrow();
                UIEvent hover = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
                hover.target = region;
                UIEventDispatcher.dispatchEvent(hover, false, false, false);
                assertEquals(1, hover.hoverTooltips.tooltipTexts().size(), id);
                assertNotNull(hover.hoverTooltips.tooltipComponent(), id);
            }

            var overclock = panel.overclockCardBuilder(true).texts();
            assertTrue(overclock.stream().anyMatch(text -> "256".equals(text.getString())));
            translatable(overclock, "gui.neoecoae.workstation.card.auto_preheat");
            translatable(overclock, "gui.neoecoae.workstation.card.preheat_off");
            assertFalse(panel.overclockCardBuilder(false).texts().stream().anyMatch(text -> text.getString().contains(
                "gui.neoecoae.workstation.card.mouse_left")), "The info region carries no click hints");
            snapshot.putBoolean("smartCooling", true);
            panel.setValue(snapshot.copy());
            assertFalse(panel.overclockCardBuilder(true).texts().stream().anyMatch(text -> text.getContents()
                instanceof TranslatableContents contents && "gui.neoecoae.workstation.card.auto_preheat".equals(contents.getKey())),
                "Smart cooling owns the temperature, so preheat is hidden");

            var energy = panel.energyCardBuilder().texts();
            assertTrue(energy.stream().anyMatch(text -> "12.5".equals(text.getString())));
            String quarter = cn.dancingsnow.neoecoae.gui.common.HostText.percent(0.25);
            assertTrue(energy.stream().anyMatch(text -> quarter.equals(text.getString())), "Bar caption: " + energy);

            snapshot.putDouble("energyUsage", 1_234_567.5);
            snapshot.putDouble("feUsage", 138_936_320_000D);
            snapshot.putLong("feBuffer", Long.MAX_VALUE);
            panel.setValue(snapshot.copy());
            var largeEnergy = panel.energyCardBuilder().texts();
            assertTrue(largeEnergy.stream().anyMatch(text -> "1.23M".equals(text.getString())));
            assertEquals("138G", translatable(largeEnergy, "gui.neoecoae.workstation.card.fe_rate").getArgs()[0]);
            assertEquals("9.22E", translatable(largeEnergy, "gui.neoecoae.workstation.card.fe_amount").getArgs()[0]);
            assertEquals("16,000,000", translatable(largeEnergy, "gui.neoecoae.workstation.card.ae_amount").getArgs()[0]);

            var collapsed = translatable(panel.moduleCardBuilder(false).texts(), "gui.neoecoae.workstation.card.card_count_shift");
            int cards = (Integer) collapsed.getArgs()[0];
            assertTrue(cards > 0);
            assertEquals(panel.moduleCardBuilder(false).texts().size() + cards,
                panel.moduleCardBuilder(true).texts().size(), "Shift lists every supported card");
        }
    }

    private static TranslatableContents translatable(List<Component> texts, String key) {
        return texts.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance)
            .map(TranslatableContents.class::cast).filter(text -> key.equals(text.getKey())).findFirst().orElseThrow(() ->
                new AssertionError("Missing " + key + " in " + texts));
    }

    private static void installFont(org.mockito.MockedStatic<net.minecraft.client.Minecraft> minecraft)
        throws ReflectiveOperationException {
        var client = mock(net.minecraft.client.Minecraft.class);
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(org.mockito.ArgumentMatchers.any(net.minecraft.network.chat.FormattedText.class)))
            .thenAnswer(call -> ((net.minecraft.network.chat.FormattedText) call.getArgument(0)).getString().length() * 6);
        var field = net.minecraft.client.Minecraft.class.getDeclaredField("font");
        field.setAccessible(true);
        field.set(client, font);
        minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
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
