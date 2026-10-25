package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;

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
            .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
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
        assertTrue(fixture.popup.getPositionX() + fixture.popup.getSizeWidth() <= 326);
        assertTrue(fixture.popup.getPositionY() + fixture.popup.getSizeHeight() <= 254);
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
            assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.getAction());
            assertEquals("/tp " + i + " 64 20", click.getValue());
            assertEquals(new BlockPos(i, 64, 20).asLong(), fixture.highlights.get(i).getLong("position"));
        }
        assertEquals(1, fixture.requests.size());
        assertEquals("inspect", fixture.requests.get(0).getString("action"));
    }

    @Test void partLocationsKeepTheirSideAndOnlyAppearAfterTheLocateAction() {
        var fixture = new Fixture(1);
        assertTrue(fixture.chat.isEmpty());
        var response = fixture.response();
        response.getList("sources", 10).getCompound(0).putString("side", "north");
        response.getList("sources", 10).getCompound(0).putLong("position", new BlockPos(264, 35, -201).asLong());
        fixture.popup.accept(response);
        fixture.click(25);
        assertEquals(1, fixture.chat.size());
        assertEquals("north", fixture.highlights.getFirst().getString("side"));
        assertTrue(fixture.chat.get(0).getString().endsWith(" · north"));
        var message = assertInstanceOf(TranslatableContents.class, fixture.chat.get(0).getContents());
        var coordinates = assertInstanceOf(Component.class, message.getArgs()[3]);
        assertEquals("/tp 264 35 -201", coordinates.getStyle().getClickEvent().getValue());
    }

    @Test void openingMultipleProvidersRequiresChoosingTheSourceAndCarriesTheJobToken() {
        var fixture = new Fixture(2);
        fixture.click(41);
        fixture.arrange();
        assertEquals(1, fixture.requests.size());
        fixture.click(28);
        assertEquals("open", fixture.requests.get(1).getString("action"));
        assertEquals("source-0", fixture.requests.get(1).getString("source"));
        assertEquals(fixture.job, fixture.requests.get(1).getUUID("job"));
    }

    @Test void aReplyForAnEarlierContextCannotEnableTheNewMenu() {
        var fixture = new Fixture(1);
        var old = fixture.response();
        fixture.popup.open(fixture.item, 20, 20);
        fixture.popup.accept(old);
        fixture.arrange();
        fixture.click(41);
        assertEquals(2, fixture.requests.size(), "Only the two inspect requests are sent");
    }

    @Test void aSingleProviderOpensDirectlyAndAnEmptyListDisablesBothActions() {
        var single = new Fixture(1);
        single.click(41);
        assertEquals("open", single.requests.getLast().getString("action"));
        assertEquals("source-0", single.requests.getLast().getString("source"));
        var empty = new Fixture(0);
        empty.click(25);
        empty.click(41);
        assertEquals(1, empty.requests.size());
        assertTrue(empty.chat.isEmpty());
    }

    @Test void providerChooserPagesAndEscapeClosesOnlyThePopup() {
        var fixture = new Fixture(6);
        fixture.click(41);
        fixture.arrange();
        var wheel = UIEvent.create(UIEvents.MOUSE_WHEEL);
        wheel.target = fixture.popup;
        wheel.deltaY = -1;
        com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchEvent(wheel);
        fixture.click(28);
        assertEquals("source-4", fixture.requests.getLast().getString("source"));
        var escape = UIEvent.create(UIEvents.KEY_DOWN);
        escape.target = fixture.popup;
        escape.keyCode = 256;
        com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchEvent(escape);
        fixture.arrange();
        assertFalse(fixture.popup.isDisplayed());
        assertTrue(escape.hasHandler);
    }

    private static final class Fixture {
        final List<CompoundTag> requests = new ArrayList<>();
        final List<Component> chat = new ArrayList<>();
        final List<CompoundTag> highlights = new ArrayList<>();
        final ComputationItemActionsPopup popup = new ComputationItemActionsPopup(registries,
            request -> requests.add(request.copy()), chat::add, source -> highlights.add(source.copy()));
        final UIElement root = new UIElement().layout(layout -> layout.width(326).height(254));
        final ComputationCpuItemEntry item = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE),
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN, false);
        final com.lowdragmc.lowdraglib2.gui.ui.ModularUI ui = com.lowdragmc.lowdraglib2.gui.ui.ModularUI.of(com.lowdragmc.lowdraglib2.gui.ui.UI.of(root));
        final UUID job = UUID.randomUUID();
        final int count;
        Fixture(int count) {
            this.count = count;
            root.addChild(popup);
            popup.open(item, 320, 250);
            popup.accept(response());
            arrange();
        }
        CompoundTag response() {
            var response = new CompoundTag();
            response.putLong("sequence", requests.get(0).getLong("sequence"));
            response.put("key", item.key().toTagGeneric(registries));
            response.putUUID("job", job);
            var sources = new ListTag();
            for (int i = 0; i < count; i++) {
                var source = new CompoundTag();
                source.putString("id", "source-" + i);
                source.putString("name", Component.Serializer.toJson(Component.literal("Provider " + i), registries));
                source.putString("dimension", "minecraft:overworld");
                source.putLong("position", new BlockPos(i, 64, 20).asLong());
                sources.add(source);
            }
            response.put("sources", sources);
            return response;
        }
        void arrange() {
            ui.init(326, 254);
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
