package cn.dancingsnow.neoecoae.gui.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
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

/** 1.20.1 port of main's dialog regression on the ECO UI runtime; the full-screen modal case needs a real screen. */
class ECOBulkMigrationDialogTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        StorageHostUiTest.bootstrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void openingAndLoadingNeverMigrateAndOnlyConfirmMovesThePreviewedInstallationOnce() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
            assertFalse(f.dialog.isDisplay());
        }
    }

    @Test
    void cancelAndEscapeDiscardThePreviewWithoutMigrating() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.accept(f.request, f.preview(1));
            f.click(1);
            assertFalse(f.dialog.isDisplay());
            assertTrue(f.confirmed.isEmpty());
            f.open();
            f.dialog.accept(f.request, f.preview(1));
            assertTrue(f.dialog.keyPressed(256, 0, 0), "Escape is consumed by the dialog");
            assertFalse(f.dialog.isDisplay());
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void staleOrUnavailablePreviewsCannotEnableConfirmation() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
    void bodyClicksAreConsumedAndCannotConfirmOrReachTheUnderlyingInventory() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.accept(f.request, f.preview(1));
            UIEvent event = f.dispatch(UIEvents.MOUSE_DOWN, f.dialog.getPositionX() + 30,
                f.dialog.getPositionY() + 70);
            f.dispatch(UIEvents.MOUSE_UP, event.x, event.y);
            assertTrue(event.isPropagationStopped());
            assertTrue(f.dialog.isDisplay());
            assertTrue(f.confirmed.isEmpty());
        }
    }

    @Test
    void anImmediatePreviewIsAcceptedBeforeTheNextLayoutPass() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Fixture f = new Fixture();
            f.dialog.close();
            f.root.arrange();
            long request = f.dialog.open(f.position, 253, 231);
            f.dialog.accept(request, f.preview(1));
            f.root.arrange();
            f.click(0);
            assertEquals(List.of(f.position), f.confirmed);
        }
    }

    @Test
    void previewRendersAtMostSixItemTypesEvenIfTheResponseContainsMore() throws Exception {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
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
            GuiGraphics graphics = mock(GuiGraphics.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context, "graphics", graphics);
            f.dialog.drawBackgroundAdditional(context);
            verify(graphics, times(6)).renderItem(any(ItemStack.class), anyInt(), anyInt());
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
        long request;

        Fixture() {
            root.setOverflowVisible(true);
            root.addChild(dialog);
            open();
        }

        void open() {
            request = dialog.open(position, 253, 231);
            root.arrange();
        }

        CompoundTag preview(int count) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("cell_id", cellId);
            tag.putInt("total", count);
            ListTag items = new ListTag();
            var examples = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.COAL,
                Items.REDSTONE, Items.EMERALD, Items.LAPIS_LAZULI, Items.QUARTZ);
            for (int i = 0; i < count; i++) items.add(new ItemStack(examples.get(i)).save(new CompoundTag()));
            tag.put("items", items);
            return tag;
        }

        void click(int button) {
            float x = dialog.getPositionX() + dialog.getSizeWidth() / 2F + (button == 0 ? -56 : 56);
            float y = dialog.getPositionY() + (dialog.getSizeHeight() + dialog.panelHeight()) / 2F - 18;
            dispatch(UIEvents.MOUSE_DOWN, x, y);
            dispatch(UIEvents.MOUSE_UP, x, y);
            root.arrange();
        }

        UIEvent dispatch(UIEvents type, float x, float y) {
            UIEvent event = new UIEvent(type, dialog, x, y, 0);
            dialog.dispatchEvent(event);
            return event;
        }
    }
}
