package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
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
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void locationsAreWrittenToChatAndCloseTheMenuWithoutOpeningADialogOrSendingAnotherRequest() {
        var fixture = new Fixture(6, false);
        assertTrue(fixture.popup.getPositionX() + fixture.popup.getSizeWidth() <= 326);
        assertTrue(fixture.popup.getPositionY() + fixture.popup.getSizeHeight() <= 254);
        assertEquals(138, fixture.popup.getSizeWidth());
        fixture.click(25);
        fixture.root.arrange();
        assertFalse(fixture.popup.isDisplayed());
        assertEquals(6, fixture.chat.size(), "Every provider is reported, including those beyond a chooser page");
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
        }
        assertEquals(1, fixture.requests.size());
        assertEquals("inspect", fixture.requests.get(0).getString("action"));
    }

    @Test void partLocationsKeepTheirSideAndOnlyAppearAfterTheLocateAction() {
        var fixture = new Fixture(1, false);
        assertTrue(fixture.chat.isEmpty());
        var response = fixture.response();
        response.getList("sources", 10).getCompound(0).putString("side", "north");
        response.getList("sources", 10).getCompound(0).putLong("position", new BlockPos(264, 35, -201).asLong());
        fixture.popup.accept(response);
        fixture.click(25);
        assertEquals(1, fixture.chat.size());
        assertTrue(fixture.chat.get(0).getString().endsWith(" · north"));
        var message = assertInstanceOf(TranslatableContents.class, fixture.chat.get(0).getContents());
        var coordinates = assertInstanceOf(Component.class, message.getArgs()[3]);
        assertEquals("/tp 264 35 -201", coordinates.getStyle().getClickEvent().getValue());
    }

    @Test void openingMultipleProvidersRequiresChoosingTheSourceAndCarriesTheJobToken() {
        var fixture = new Fixture(2, false);
        fixture.click(41);
        fixture.root.arrange();
        assertEquals(1, fixture.requests.size());
        fixture.click(28);
        assertEquals("open", fixture.requests.get(1).getString("action"));
        assertEquals("source-0", fixture.requests.get(1).getString("source"));
        assertEquals(fixture.job, fixture.requests.get(1).getUUID("job"));
    }

    @Test void stopAndResumeAreStepActionsAndNeverAnOrderCancel() {
        for (boolean stopped : List.of(false, true)) {
            var fixture = new Fixture(1, stopped);
            fixture.click(64);
            assertEquals(stopped ? "resume" : "stop", fixture.requests.get(1).getString("action"));
            assertFalse(fixture.requests.stream().anyMatch(request -> request.getString("action").equals("cancel")));
        }
    }

    @Test void aReplyForAnEarlierContextCannotEnableTheNewMenu() {
        var fixture = new Fixture(1, false);
        var old = fixture.response();
        fixture.popup.open(fixture.item, 20, 20);
        fixture.popup.accept(old);
        fixture.root.arrange();
        fixture.click(64);
        assertEquals(2, fixture.requests.size(), "Only the two inspect requests are sent");
    }

    private static final class Fixture {
        final List<CompoundTag> requests = new ArrayList<>();
        final List<Component> chat = new ArrayList<>();
        final ComputationItemActionsPopup popup = new ComputationItemActionsPopup(request -> requests.add(request.copy()), chat::add);
        final UIElement root = new UIElement().layout(layout -> layout.width(326).height(254));
        final ComputationCpuItemEntry item = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE),
            BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN, false);
        final UUID job = UUID.randomUUID();
        final int count;
        final boolean stopped;
        Fixture(int count, boolean stopped) {
            this.count = count; this.stopped = stopped;
            root.addChild(popup);
            popup.open(item, 320, 250);
            popup.accept(response());
            root.arrange();
        }
        CompoundTag response() {
            var response = new CompoundTag();
            response.putLong("sequence", requests.get(0).getLong("sequence"));
            response.put("key", item.key().toTagGeneric());
            response.putUUID("job", job);
            response.putBoolean("canStop", true);
            response.putBoolean("stopped", stopped);
            var sources = new ListTag();
            for (int i = 0; i < count; i++) {
                var source = new CompoundTag();
                source.putString("id", "source-" + i);
                source.putString("name", Component.Serializer.toJson(Component.literal("Provider " + i)));
                source.putString("dimension", "minecraft:overworld");
                source.putLong("position", new BlockPos(i, 64, 20).asLong());
                sources.add(source);
            }
            response.put("sources", sources);
            return response;
        }
        void click(float y) {
            float x = popup.getPositionX() + 10, top = popup.getPositionY() + y;
            popup.dispatchEvent(new UIEvent(UIEvents.MOUSE_DOWN, popup, x, top, 0));
            popup.dispatchEvent(new UIEvent(UIEvents.MOUSE_UP, popup, x, top, 0));
        }
    }
}
