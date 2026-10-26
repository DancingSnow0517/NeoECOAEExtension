package cn.dancingsnow.neoecoae.gui.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOBulkMigrationDialogTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        StorageHostUiTest.bootstrap();
        com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void openingAndLoadingNeverMigrateAndOnlyConfirmMovesThePreviewedInstallationOnce() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            assertTrue(f.confirmed.isEmpty());
            f.click(0);
            assertTrue(f.confirmed.isEmpty(), "Confirmation is disabled while the preview is loading");
            f.dialog.accept(f.request, f.preview(2));
            assertTrue(f.confirmed.isEmpty(), "Receiving the preview must be read-only");
            f.click(0);
            f.click(0);
            assertEquals(List.of(f.position), f.confirmed);
            assertEquals(List.of(f.cellId), f.installations);
            assertFalse(f.dialog.isDisplayed());
        }
    }

    @Test
    void cancelAndEscapeDiscardThePreviewWithoutMigrating() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.accept(f.request, f.preview(1));
            f.click(1);
            assertFalse(f.dialog.isDisplayed());
            assertTrue(f.confirmed.isEmpty());
            f.open();
            f.dialog.accept(f.request, f.preview(1));
            var escape = UIEvent.create(UIEvents.KEY_DOWN);
            escape.target = f.dialog;
            escape.keyCode = 256;
            UIEventDispatcher.dispatchEvent(escape);
            f.ui.init(253, 231);
            assertFalse(f.dialog.isDisplayed());
            assertTrue(escape.hasHandler);
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void staleOrUnavailablePreviewsCannotEnableConfirmation() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            long stale = f.request;
            f.open();
            f.dialog.accept(stale, f.preview(1));
            f.click(0);
            assertTrue(f.confirmed.isEmpty());
            f.dialog.accept(f.request, new CompoundTag());
            f.click(0);
            assertTrue(f.confirmed.isEmpty());
            f.open();
            f.dialog.accept(f.request, f.preview(0));
            f.click(0);
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void bodyClicksAreConsumedAndCannotConfirmOrReachTheUnderlyingInventory() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.accept(f.request, f.preview(1));
            UIEvent event = f.dispatch(UIEvents.MOUSE_DOWN, f.dialog.getPositionX() + 30,
                f.dialog.getPositionY() + 70);
            f.dispatch(UIEvents.MOUSE_UP, event.x, event.y);
            assertTrue(event.hasHandler);
            assertTrue(f.dialog.isDisplayed());
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void anImmediatePreviewIsAcceptedBeforeTheNextLayoutPass() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.close();
            f.ui.init(253, 231);
            long request = f.dialog.open(f.position, 253, 231);
            f.dialog.accept(request, f.preview(1));
            f.ui.init(253, 231);
            f.click(0);
            assertEquals(List.of(f.position), f.confirmed);
        }
    }

    @Test
    void theModalAlsoBlocksSideButtonsAndTheAreaOutsideTheHost() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.ui.init(600, 400);
            f.dialog.open(f.position, 253, 231);
            f.ui.init(600, 400);
            assertEquals(600, f.dialog.getSizeWidth());
            assertEquals(400, f.dialog.getSizeHeight());
            var side = f.root.hitTest(f.root.getPositionX() - 30, f.root.getPositionY() + 30);
            assertNotNull(side, "root=" + f.root.getPositionX() + "," + f.root.getPositionY()
                + " dialog=" + f.dialog.getPositionX() + "," + f.dialog.getPositionY()
                + " overflow=" + f.root.getStyle().overflowVisible());
            assertSame(f.dialog, side.getA(), "Host side buttons must be covered");
            assertSame(f.dialog, f.root.hitTest(f.root.getPositionX() - 100,
                f.root.getPositionY() - 50).getA(), "Outside clicks must not drop carried items");
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void previewRendersAtMostSixItemTypesEvenIfTheResponseContainsMore() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.accept(f.request, f.preview(8));
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            Font font = mock(Font.class);
            fontField.set(client, font);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
            GUIContext context = mock(GUIContext.class);
            context.graphics = mock(GuiGraphics.class);
            f.dialog.drawBackgroundAdditional(context);
            verify(context.graphics, times(6)).renderItem(any(ItemStack.class), anyInt(), anyInt());
            assertTrue(f.confirmed.isEmpty());
        }
    }

    private static final class Fixture {
        final BlockPos position = new BlockPos(1, 2, 3);
        final UUID cellId = UUID.randomUUID();
        final List<BlockPos> confirmed = new ArrayList<>();
        final List<UUID> installations = new ArrayList<>();
        final ECOBulkMigrationDialog dialog = new ECOBulkMigrationDialog(registries, (pos, id) -> {
            confirmed.add(pos);
            installations.add(id);
        });
        final UIElement root = new UIElement().layout(layout -> layout.width(253).height(231));
        final ModularUI ui = ModularUI.of(UI.of(root));
        long request;

        Fixture() throws Exception {
            root.setOverflowVisible(true);
            root.addChild(dialog);
            open();
        }

        void open() throws Exception {
            request = dialog.open(position, 253, 231);
            ui.init(253, 231);
            var hovered = ModularUI.class.getDeclaredField("lastHoveredElement");
            hovered.setAccessible(true);
            hovered.set(ui, dialog);
        }

        CompoundTag preview(int count) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("cell_id", cellId);
            tag.putInt("total", count);
            ListTag items = new ListTag();
            var examples = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.COAL,
                Items.REDSTONE, Items.EMERALD, Items.LAPIS_LAZULI, Items.QUARTZ);
            for (int i = 0; i < count; i++) items.add(new ItemStack(examples.get(i)).save(registries));
            tag.put("items", items);
            return tag;
        }

        void click(int button) {
            float x = dialog.getPositionX() + (dialog.getSizeWidth() - 240) / 2F + (button == 0 ? 60 : 180);
            float y = dialog.getPositionY() + (dialog.getSizeHeight() - 158) / 2F + 142;
            dispatch(UIEvents.MOUSE_DOWN, x, y);
            dispatch(UIEvents.MOUSE_UP, x, y);
            ui.init(253, 231);
        }

        UIEvent dispatch(String type, float x, float y) {
            UIEvent event = UIEvent.create(type);
            event.target = dialog;
            event.x = x;
            event.y = y;
            event.button = 0;
            UIEventDispatcher.dispatchEvent(event);
            return event;
        }
    }
}
