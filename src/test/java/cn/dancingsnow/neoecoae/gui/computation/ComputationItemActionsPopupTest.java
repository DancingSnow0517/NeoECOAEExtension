package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import appeng.api.stacks.AEItemKey;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationItemActionsPopupTest {
    private Object previousMissingContentItem;
    private org.mockito.MockedStatic<appeng.api.stacks.AEKeyTypesInternal> keys;
    private org.mockito.MockedStatic<com.lowdragmc.lowdraglib2.LDLib2> ldlib;
    @org.junit.jupiter.api.BeforeEach void setupKeys() throws Exception {
        ldlib = org.mockito.Mockito.mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class);
        ldlib.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
        keys = org.mockito.Mockito.mockStatic(appeng.api.stacks.AEKeyTypesInternal.class);
        keys.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
            .thenReturn(cn.dancingsnow.neoecoae.gui.common.GuiTestKeys.REGISTRY);
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        previousMissingContentItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missingItem = org.mockito.Mockito.mock(net.neoforged.neoforge.registries.DeferredItem.class);
        org.mockito.Mockito.when(missingItem.get()).thenReturn(Items.BARRIER);
        org.mockito.Mockito.when(missingItem.asItem()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);

    }
    @org.junit.jupiter.api.AfterEach void cleanupKeys() throws Exception {
        keys.close(); ldlib.close();
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
    }
    private static net.minecraft.core.HolderLookup.Provider registries;
    @BeforeAll static void bootstrap() throws ClassNotFoundException {
        ComputationControllerUITest.bootstrap();
        com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
        registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }

    @Test void locationsAreWrittenToChatAndCloseTheMenuWithoutOpeningADialogOrSendingAnotherRequest() {
        var fixture = new Fixture(6);
        assertTrue(fixture.popup.getPositionX() + fixture.popup.getSizeWidth() <= ComputationCpuStatusPanel.WIDTH);
        assertTrue(fixture.popup.getPositionY() + fixture.popup.getSizeHeight() <= ComputationCpuStatusPanel.HEIGHT);
        assertEquals(138, fixture.popup.getSizeWidth());
        fixture.click(25);
        fixture.arrange();
        assertFalse(fixture.popup.isDisplayed());
        assertEquals(6, fixture.chat.size(), "Every provider is reported, including those beyond a chooser page");
        assertEquals(6, fixture.highlights.size(), "All located providers are also highlighted in the world");
        for (int i = 0; i < fixture.chat.size(); i++) {
            var message = assertInstanceOf(TranslatableContents.class, fixture.chat.get(i).getContents());
            assertEquals("gui.neoecoae.processing.pattern_location", message.getKey());
            assertEquals(fixture.item.key().getDisplayName(), message.getArgs()[0]);
            assertEquals(Component.literal("Provider " + i), message.getArgs()[1]);
            assertEquals("minecraft:overworld", message.getArgs()[2]);
            var coordinates = assertInstanceOf(Component.class, message.getArgs()[3]);
            assertEquals(new BlockPos(i, 64, 20).toShortString(), coordinates.getString());
            assertEquals(ChatFormatting.AQUA.getColor(), coordinates.getStyle().getColor().getValue());
            assertTrue(coordinates.getStyle().isUnderlined());
            var click = coordinates.getStyle().getClickEvent();
            assertNotNull(click);
            assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
            assertEquals("/tp " + i + " 64 20", ((ClickEvent.SuggestCommand) click).command());
            assertEquals(new BlockPos(i, 64, 20).asLong(), fixture.highlights.get(i).getLongOr("position", 0L));
        }
        assertEquals(1, fixture.requests.size());
        assertEquals("inspect", fixture.requests.get(0).getStringOr("action", ""));
    }

    @Test void partLocationsKeepTheirSideAndOnlyAppearAfterTheLocateAction() {
        var fixture = new Fixture(1);
        assertTrue(fixture.chat.isEmpty());
        var response = fixture.response();
        response.getListOrEmpty("sources").getCompoundOrEmpty(0).putString("side", "north");
        response.getListOrEmpty("sources").getCompoundOrEmpty(0).putLong("position", new BlockPos(264, 35, -201).asLong());
        fixture.popup.accept(response);
        fixture.click(25);
        assertEquals(1, fixture.chat.size());
        assertEquals("north", fixture.highlights.getFirst().getStringOr("side", ""));
        assertTrue(fixture.chat.get(0).getString().endsWith(" · north"));
        var message = assertInstanceOf(TranslatableContents.class, fixture.chat.get(0).getContents());
        var coordinates = assertInstanceOf(Component.class, message.getArgs()[3]);
        assertEquals("/tp 264 35 -201", ((ClickEvent.SuggestCommand) coordinates.getStyle().getClickEvent()).command());
    }

    @Test void openingMultipleProvidersRequiresChoosingTheSourceAndCarriesTheJobToken() {
        var fixture = new Fixture(2);
        fixture.click(PROVIDER_ROW);
        fixture.arrange();
        assertEquals(1, fixture.requests.size());
        fixture.click(28);
        assertEquals("open", fixture.requests.get(1).getStringOr("action", ""));
        assertEquals("source-0", fixture.requests.get(1).getStringOr("source", ""));
        assertEquals(fixture.job, fixture.requests.get(1).read("job", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
    }

    @Test void aReplyForAnEarlierContextCannotEnableTheNewMenu() {
        var fixture = new Fixture(1);
        var old = fixture.response();
        fixture.popup.open(fixture.item, 20, 20);
        fixture.popup.accept(old);
        fixture.arrange();
        fixture.click(PROVIDER_ROW);
        assertEquals(2, fixture.requests.size(), "Only the two inspect requests are sent");
    }

    @Test void aSingleProviderOpensDirectlyAndAnEmptyListDisablesBothActions() {
        var single = new Fixture(1);
        single.click(PROVIDER_ROW);
        assertEquals("open", single.requests.getLast().getStringOr("action", ""));
        assertEquals("source-0", single.requests.getLast().getStringOr("source", ""));
        var empty = new Fixture(0);
        empty.click(25);
        empty.click(MACHINE_ROW);
        empty.click(PROVIDER_ROW);
        assertEquals(1, empty.requests.size());
        assertTrue(empty.chat.isEmpty());
    }

    @Test void providerChooserPagesAndEscapeClosesOnlyThePopup() {
        var fixture = new Fixture(6);
        fixture.click(PROVIDER_ROW);
        fixture.arrange();
        var wheel = UIEvent.create(UIEvents.MOUSE_WHEEL);
        wheel.target = fixture.popup;
        wheel.deltaY = -1;
        com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchEvent(wheel);
        fixture.click(28);
        assertEquals("source-4", fixture.requests.getLast().getStringOr("source", ""));
        var escape = UIEvent.create(UIEvents.KEY_DOWN);
        escape.target = fixture.popup;
        escape.keyCode = 256;
        com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchEvent(escape);
        fixture.arrange();
        assertFalse(fixture.popup.isDisplayed());
        assertTrue(escape.hasHandler);
    }

    @Test void disabledActionsCollectTheirTooltipWithoutMouseCoordinatesInTheEvent() throws Exception {
        var fixture = new Fixture(0);
        set(fixture.ui, "lastMouseX", fixture.popup.getPositionX() + 10);
        for (int top : new int[] {25, PROVIDER_ROW}) {
            set(fixture.ui, "lastMouseY", fixture.popup.getPositionY() + top);
            var tooltip = fixture.popup.collectHoverTooltips();
            assertNotNull(tooltip);
            assertEquals(List.of(Component.translatable("gui.neoecoae.processing.no_pattern")), tooltip.tooltips().stream().filter(net.minecraft.network.chat.Component.class::isInstance).map(net.minecraft.network.chat.Component.class::cast).toList());
        }
        set(fixture.ui, "lastMouseY", fixture.popup.getPositionY() + MACHINE_ROW);
        var tooltip = fixture.popup.collectHoverTooltips();
        assertNotNull(tooltip);
        assertEquals(List.of(Component.translatable("gui.neoecoae.processing.no_machine")), cn.dancingsnow.neoecoae.gui.common.UiTestRendering.tooltipTexts(tooltip));
    }

    @Test void machinesOpenDirectlyOrThroughTheChooserWithTheirOwnAction() {
        var single = new Fixture(1, 1);
        single.click(MACHINE_ROW);
        assertEquals("open_machine", single.requests.getLast().getStringOr("action", ""));
        assertEquals("machine-0", single.requests.getLast().getStringOr("source", ""));
        assertEquals(single.job, single.requests.getLast().read("job", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
        var several = new Fixture(1, 3);
        several.click(MACHINE_ROW);
        several.arrange();
        assertEquals(1, several.requests.size(), "Several machines open the chooser first");
        several.click(58);
        assertEquals("open_machine", several.requests.getLast().getStringOr("action", ""));
        assertEquals("machine-1", several.requests.getLast().getStringOr("source", ""));
    }

    // 26.1 port: GUI rendering is deferred to strata in GuiRenderState, so the old depth (z > 200) and flush()
    // assertions no longer apply; only the background-before-text order and pose restoration remain.
    @Test void popupDrawsItsBackgroundBeforeText() throws Exception {
        var fixture = new Fixture(1);
        var client = cn.dancingsnow.neoecoae.gui.common.ClientTestSupport.withOptions(mock(net.minecraft.client.Minecraft.class));
        set(client, "font", mock(net.minecraft.client.gui.Font.class));
        when(client.font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        var graphics = mock(net.minecraft.client.gui.GuiGraphicsExtractor.class);
        var pose = new org.joml.Matrix3x2fStack(16);
        when(graphics.pose()).thenReturn(pose);
        var context = spy(new com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext());
        context.graphics = graphics;
        context.pose = new com.lowdragmc.lowdraglib2.gui.ui.rendering.EnhancedPoseStack(pose);
        doNothing().when(context).drawTexture(any(), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        try (var minecraft = mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
            fixture.popup.drawBackgroundAdditional(context);
        }
        var order = inOrder(context, graphics);
        order.verify(context).drawTexture(any(), anyFloat(), anyFloat(), anyFloat(), anyFloat());
        order.verify(graphics).text(eq(client.font), anyString(), anyInt(), anyInt(), anyInt(), eq(false));
        assertEquals(new org.joml.Matrix3x2f(), new org.joml.Matrix3x2f(pose), "the popup must restore the enclosing GUI pose");
    }

    // Menu rows: locate (22..38), open machine (38..54), open provider (54..70).
    private static final int MACHINE_ROW = 41, PROVIDER_ROW = 57;

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture {
        final List<CompoundTag> requests = new ArrayList<>();
        final List<Component> chat = new ArrayList<>();
        final List<CompoundTag> highlights = new ArrayList<>();
        final ComputationItemActionsPopup popup = new ComputationItemActionsPopup(registries,
            request -> requests.add(request.copy()), chat::add, source -> highlights.add(source.copy()));
        final UIElement root = new UIElement().layout(layout ->
            layout.width(ComputationCpuStatusPanel.WIDTH).height(ComputationCpuStatusPanel.HEIGHT));
        final ComputationCpuItemEntry item = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE),
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN, false);
        final com.lowdragmc.lowdraglib2.gui.ui.ModularUI ui = com.lowdragmc.lowdraglib2.gui.ui.ModularUI.of(com.lowdragmc.lowdraglib2.gui.ui.UI.of(root));
        final UUID job = UUID.randomUUID();
        final int count, machines;
        Fixture(int count) {
            this(count, 0);
        }
        Fixture(int count, int machines) {
            this.count = count;
            this.machines = machines;
            root.addChild(popup);
            popup.open(item, ComputationCpuStatusPanel.WIDTH - 6, ComputationCpuStatusPanel.HEIGHT - 4);
            popup.accept(response());
            arrange();
        }
        CompoundTag response() {
            var response = new CompoundTag();
            response.putLong("sequence", requests.get(0).getLongOr("sequence", 0L));
            response.put("key", cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.writeKey(registries, item.key()));
            response.store("job", net.minecraft.core.UUIDUtil.CODEC, job);
            var sources = new ListTag();
            for (int i = 0; i < count; i++) {
                var source = new CompoundTag();
                source.putString("id", "source-" + i);
                source.putString("name", net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(
                    registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    Component.literal("Provider " + i)).getOrThrow().toString());
                source.putString("dimension", "minecraft:overworld");
                source.putLong("position", new BlockPos(i, 64, 20).asLong());
                sources.add(source);
            }
            response.put("sources", sources);
            var machineList = new ListTag();
            for (int i = 0; i < machines; i++) {
                var machine = new CompoundTag();
                machine.putString("id", "machine-" + i);
                machine.putString("name", net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Component.literal("Machine " + i)).getOrThrow().toString());
                machine.putString("dimension", "minecraft:overworld");
                machine.putLong("position", new BlockPos(i, 70, 20).asLong());
                machineList.add(machine);
            }
            response.put("machines", machineList);
            return response;
        }
        void arrange() {
            ui.init(ComputationCpuStatusPanel.WIDTH, ComputationCpuStatusPanel.HEIGHT);
            // LDLib2 refreshes this during rendering before it routes mouse events.
            try {
                var hovered = com.lowdragmc.lowdraglib2.gui.ui.ModularUI.class.getDeclaredField("lastHoveredElement");
                hovered.setAccessible(true);
                hovered.set(ui, popup);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }
        void dispatch(String type, float x, float y) {
            var event = UIEvent.create(type);
            event.target = popup; event.x = x; event.y = y; event.button = 0;
            com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchEvent(event);
        }
        void click(float y) {
            float x = popup.getPositionX() + 10, top = popup.getPositionY() + y;
            dispatch(UIEvents.MOUSE_DOWN, x, top);
            dispatch(UIEvents.MOUSE_UP, x, top);
        }
    }
}
