package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.common.HostProgressTestSupport;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.LocalSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import io.netty.buffer.Unpooled;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.DrawerHelper;
import appeng.api.storage.cells.CellState;
import java.util.List;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageHostUiTest {
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
    void guiAtlasLoadsTheOriginalInfinitySheetWithItsAnimationMetadata() throws Exception {
        var loader = getClass().getClassLoader();
        try (var atlas = loader.getResourceAsStream("assets/minecraft/atlases/gui.json")) {
            assertNotNull(atlas);
            var source = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(atlas,
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("sources")
                .get(0).getAsJsonObject();
            assertEquals("minecraft:single", source.get("type").getAsString());
            assertEquals("neoecoae:storage/infinity", source.get("sprite").getAsString());
            String path = "assets/neoecoae/textures/" + source.get("resource").getAsString().split(":", 2)[1] + ".png";
            try (var png = loader.getResourceAsStream(path);
                 var metadata = loader.getResourceAsStream(path + ".mcmeta")) {
                assertNotNull(png);
                assertNotNull(metadata);
                var image = javax.imageio.ImageIO.read(png);
                var animation = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(metadata,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animation");
                var parsed = net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER
                    .fromJson(animation);
                var frame = parsed.calculateFrameSize(image.getWidth(), image.getHeight());
                assertEquals(12, frame.width());
                assertEquals(12, frame.height());
                assertEquals(10, image.getHeight() / frame.height());
                assertEquals(3, parsed.getDefaultFrameTime());
                assertTrue(parsed.isInterpolatedFrames());
            }
        }
    }

    @Test
    void formedStructureDrawsOnlyItsActualColumnsEvenWhenTheDrivesAreEmpty() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            for (int columns : List.of(3, 11)) {
                List<StorageHostUI.CellEntry> cells = new java.util.ArrayList<>();
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < columns; column++) {
                        cells.add(StorageHostUI.CellEntry.EMPTY.atDrive(new BlockPos(column + 2, 1 - row, 0), ItemStack.EMPTY, null));
                    }
                }
                cells.set(columns + 1, new StorageHostUI.CellEntry(7, 1, 1, 1, 1, 100, 100,
                    false, CellState.FULL, true).atDrive(new BlockPos(3, 0, 0), new ItemStack(Items.DIAMOND), null));
                var grid = spy(node(StorageHostUI.create(StoragePanelStateTest.config(cells, false)), "storage_cells"));
                doReturn(222F).when(grid).getPositionX();
                doReturn(111F).when(grid).getPositionY();
                GUIContext context = mock(GUIContext.class);
                grid.drawBackgroundAdditional(context);
                verify(context, times(columns * 2)).drawTexture(any(SpriteTexture.class), anyFloat(), anyFloat(), eq(16F), eq(18F));
                verify(context, times(columns)).drawTexture(any(SpriteTexture.class), anyFloat(), anyFloat(), eq(16F), eq(16F));
                verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                    && sprite.spritePosition.y == 52 && sprite.spriteSize.height == 18), eq(222F), eq(111F), eq(16F), eq(18F));
                verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                    && sprite.spritePosition.y == 70 && sprite.spriteSize.height == 18), eq(240F), eq(129F), eq(16F), eq(18F));
                verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                    && sprite.spritePosition.y == 88 && sprite.spriteSize.height == 16),
                    eq(222F + (columns - 1) * 18), eq(147F), eq(16F), eq(16F));
                if (columns < 11) verify(context, never()).drawTexture(any(SpriteTexture.class), eq(222F + columns * 18), anyFloat(), anyFloat(), anyFloat());
                int visualDisk = 11 + 1;
                assertTrue(((ItemSlot) grid.getChildren().get(visualDisk)).getValue().is(Items.DIAMOND));
            }
        }
    }

    @Test
    void driveBackgroundsKeepTheOpaqueHorizontalSeparatorsFromTheArtwork() throws java.io.IOException {
        try (var resource = getClass().getResourceAsStream("/assets/neoecoae/textures/guis/storage/storage_widget.png")) {
            assertNotNull(resource);
            var atlas = javax.imageio.ImageIO.read(resource);
            for (int row : List.of(0, 1)) {
                SpriteTexture texture = StoragePanelTextures.cellBackground(row);
                assertEquals(18, texture.spriteSize.height);
                for (int y = 16; y < 18; y++) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(0xFFCBCCD4, atlas.getRGB((int)texture.spritePosition.x + x, (int)texture.spritePosition.y + y));
                    }
                }
            }
            SpriteTexture bottom = StoragePanelTextures.cellBackground(2);
            assertEquals(16, bottom.spriteSize.height);
            assertEquals(52, java.util.stream.IntStream.range(0, 3)
                .map(row -> StoragePanelTextures.cellBackground(row).spriteSize.height).sum());
            // Every row must contain the same complete 16px glyph, with no leading separator.
            for (int row = 1; row < 3; row++) {
                var texture = StoragePanelTextures.cellBackground(row);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(atlas.getRGB(x, 52 + y), atlas.getRGB((int)texture.spritePosition.x + x,
                            (int)texture.spritePosition.y + y));
                    }
                }
            }
        }
    }

    @Test
    void driveBackgroundsEndBeforeThePanelsClosingBorder() throws java.io.IOException {
        try (var resource = getClass().getResourceAsStream("/assets/neoecoae/textures/guis/storage/storage_controller.png")) {
            assertNotNull(resource);
            var background = javax.imageio.ImageIO.read(resource);
            int bottom = 31 + 2 * 18 + 16;
            for (int x = 49; x < 245; x++) {
                assertEquals(0xFFF2F2F2, background.getRGB(x, bottom));
                assertEquals(0xFFCBCCD4, background.getRGB(x, bottom + 1));
                assertEquals(0xFF9A9FB4, background.getRGB(x, bottom + 2));
            }
        }
    }

    @Test
    void unformedStructureRetainsTheOriginalCrossBackground() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            var base = StoragePanelStateTest.config(List.of(StorageHostUI.CellEntry.EMPTY.atDrive(BlockPos.ZERO, ItemStack.EMPTY, null)), false);
            var config = new StorageHostUI.Config(base.title(), base.tier(), () -> false, base.storedEnergy(),
                base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(), base.cellEntries(),
                base.storageTypes(), base.infiniteStorage(), base.migratingToInfinite(), base.canExtractInfiniteComponents(),
                base.domainId(), base.missingMembers(), base.infiniteComponentInventory());
            GUIContext context = mock(GUIContext.class);
            node(StorageHostUI.create(config), "storage_cells").drawBackgroundAdditional(context);
            verifyNoInteractions(context);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorDiskClickTargetsTheEmptyPhysicalDriveButNeverAPaddingCell() throws Exception {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            BlockPos position = new BlockPos(10, 80, 14);
            var base = StoragePanelStateTest.config(List.of(StorageHostUI.CellEntry.EMPTY.atDrive(position, ItemStack.EMPTY, null)), false);
            var grid = (StorageCellGrid) node(StorageHostUI.create(base), "storage_cells");
            var menu = mock(AbstractContainerMenu.class);
            ItemStack carried = new ItemStack(Items.DIAMOND);
            when(menu.getCarried()).thenReturn(carried);
            var mui = mock(ModularUI.class);
            when(mui.getMenu()).thenReturn(menu);
            var muiField = UIElement.class.getDeclaredField("modularUI");
            muiField.setAccessible(true);
            muiField.set(grid, mui);
            RPCEmitter insert = mock(RPCEmitter.class);
            // No UI host means no request was queued, allowing both mouse buttons to be checked.
            when(insert.send(any(java.util.function.Consumer.class), any(BlockPos.class), any(ItemStack.class))).thenReturn(false);
            var insertField = StorageCellGrid.class.getDeclaredField("insertion");
            insertField.setAccessible(true);
            insertField.set(grid, insert);
            var slot = (StorageCellGrid.CellSlot) cn.dancingsnow.neoecoae.platform.JdkCompat.first(grid.getChildren());
            muiField.set(slot, mui);
            for (int button : List.of(0, 1)) {
                UIEvent click = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
                click.button = button;
                slot.onMouseDown(click);
                assertTrue(click.hasHandler);
            }
            verify(insert, times(2)).send(any(java.util.function.Consumer.class), eq(position),
                argThat((ItemStack stack) -> ItemStack.matches(stack, carried)));
            ((StorageCellGrid.CellSlot) grid.getChildren().get(1)).onMouseDown(new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0));
            verifyNoMoreInteractions(insert);
            assertEquals(1, carried.getCount());
        }
    }

    @Test
    void clickRpcPreservesInstallationIdentityAndRequiresTheOriginalOpenMenu() throws Exception {
        // The unit bootstrap lacks NeoForge's registry-sync flags, installed by the real launcher.
        try (var ldlib = mockStatic(LDLib.class); var registrySync = mockStatic(RegistryManager.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            ItemStack disk = new ItemStack(Items.DIAMOND);
            disk.setHoverName(Component.literal("Disk with resources"));
            BlockPos position = new BlockPos(12, 75, 28);
            Player player = mock(Player.class);
            RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            when(player.level().registryAccess()).thenReturn(registries);
            AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
            player.containerMenu = menu;
            when(menu.stillValid(player)).thenReturn(true);
            StorageHostUI.CellExtractor extractor = mock(StorageHostUI.CellExtractor.class);
            var interactionId = java.util.UUID.randomUUID();
            when(extractor.extract(position, interactionId, true)).thenReturn(true);
            StorageHostUI.CellInserter inserter = mock(StorageHostUI.CellInserter.class);
            when(inserter.insert(eq(position), any(ItemStack.class))).thenReturn(true);
            var base = StoragePanelStateTest.config(List.of(StoragePanelStateTest.cell(50, 100).atDrive(position, disk, null, interactionId)), false);
            var config = new StorageHostUI.Config(base.title(), base.tier(), base.formed(), base.storedEnergy(),
                base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(), base.cellEntries(),
                base.storageTypes(), base.infiniteStorage(), base.migratingToInfinite(), base.canExtractInfiniteComponents(),
                base.domainId(), base.missingMembers(), base.infiniteComponentInventory(), player, extractor, inserter);
            var grid = (StorageCellGrid) node(StorageHostUI.create(config), "storage_cells");
            ModularUI mui = mock(ModularUI.class);
            when(mui.getMenu()).thenReturn(menu);
            var muiField = UIElement.class.getDeclaredField("modularUI");
            muiField.setAccessible(true);
            muiField.set(grid, mui);
            var field = StorageCellGrid.class.getDeclaredField("extraction");
            field.setAccessible(true);
            var rpc = cn.dancingsnow.neoecoae.util.UiTestSupport.rpc((RPCEmitter) field.get(grid));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                rpc.writeParametersToBuffer(buffer, new Object[] {position, interactionId, true});
                Object[] request = rpc.readParametersFromBuffer(buffer);
                assertEquals(position, request[0]);
                assertEquals(interactionId, request[1]);
                assertEquals(true, rpc.executor().apply(request));
                verify(extractor).extract(position, interactionId, true);
                player.containerMenu = mock(AbstractContainerMenu.class);
                assertEquals(false, rpc.executor().apply(request));
                player.containerMenu = menu;
                when(menu.stillValid(player)).thenReturn(false);
                assertEquals(false, rpc.executor().apply(request));
                verifyNoMoreInteractions(extractor);

                var insertField = StorageCellGrid.class.getDeclaredField("insertion");
                insertField.setAccessible(true);
                var insertRpc = cn.dancingsnow.neoecoae.util.UiTestSupport.rpc((RPCEmitter) insertField.get(grid));
                insertRpc.writeParametersToBuffer(buffer, new Object[] {position, disk});
                Object[] insertRequest = insertRpc.readParametersFromBuffer(buffer);
                assertEquals(position, insertRequest[0]);
                assertTrue(ItemStack.matches(disk, (ItemStack) insertRequest[1]));
                assertEquals(false, insertRpc.executor().apply(insertRequest));
                when(menu.stillValid(player)).thenReturn(true);
                player.containerMenu = mock(AbstractContainerMenu.class);
                assertEquals(false, insertRpc.executor().apply(insertRequest));
                player.containerMenu = menu;
                assertEquals(true, insertRpc.executor().apply(insertRequest));
                verify(inserter).insert(eq(position), argThat(stack -> ItemStack.matches(stack, disk)));
                verifyNoMoreInteractions(inserter);
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void jsonBuildsTheFullHostAndKeepsTheSameTreeAcrossStorageModes() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement normal = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            UIElement infinite = StorageHostUI.create(StoragePanelStateTest.config(List.of(), true));
            assertEquals(normal.getChildren().stream().map(UIElement::getId).toList(),
                infinite.getChildren().stream().map(UIElement::getId).toList());
            assertEquals("storage_controller_root", normal.getId());
            UIElement background = node(normal, "background");
            assertEquals((float)(StorageHostUI.ROOT_HEIGHT), normal.getLayoutStyle().height);
            SpriteTexture texture = (SpriteTexture) background.getStyle().backgroundTexture();
            assertEquals("neoecoae:textures/guis/storage/storage_controller.png", texture.getImageLocation().toString());
            assertEquals(253, texture.spriteSize.width);
            assertEquals(231, texture.spriteSize.height);
            assertEquals(0, texture.spritePosition.x);
            assertEquals(0, texture.spritePosition.y);
            assertTrue(background.getChildren().isEmpty());
            assertEquals(33, node(normal, "storage_cells").getChildren().size());
            assertInstanceOf(InventorySlots.class, node(normal, "player_inventory"));
            assertInstanceOf(ItemSlot.class, node(normal, "infinite_component"));
            assertEquals((float)(92), node(normal, "energy_stored_text").getLayoutStyle().top);
            assertEquals((float)(92), node(normal, "energy_text").getLayoutStyle().top);
            assertEquals((float)(104), node(normal, "capacity_text").getLayoutStyle().top);
            assertEquals((float)(104), node(normal, "capacity_percentage").getLayoutStyle().top);
            assertEquals((float)(8), node(normal, "capacity_text").getLayoutStyle().left);
            assertEquals((float)(132), node(normal, "capacity_percentage").getLayoutStyle().left);
            assertEquals((float)(36), node(normal, "capacity_percentage").getLayoutStyle().width);
            assertEquals((float)(124), node(normal, "resource_text").getLayoutStyle().top);
            assertEquals((float)(8), node(normal, "capacity_bar").getLayoutStyle().left);
            assertEquals((float)(113), node(normal, "capacity_bar").getLayoutStyle().top);
            assertEquals((float)(134), node(normal, "resource_bar").getLayoutStyle().top);
            assertEquals((float)(173), node(normal, "energy_bar").getLayoutStyle().left);
            assertEquals((float)(92), node(normal, "energy_bar").getLayoutStyle().top);
            assertEquals((float)(74), node(normal, "energy_bar").getLayoutStyle().width);
            assertEquals((float)(7), node(normal, "energy_bar").getLayoutStyle().height);
            assertEquals(true,
                node(normal, "storage_cells").isOverflowVisible());
            assertEquals((float)(52), node(normal, "storage_cells").getLayoutStyle().height);
            assertEquals((float)(146), node(normal, "player_inventory").getLayoutStyle().top);
            assertEquals((float)(201), node(normal, "infinite_component").getLayoutStyle().top);
            assertTrue(normal.selectId("storage_gauge").findFirst().isEmpty());
            assertTrue(normal.selectId("cell_page").findFirst().isEmpty());
            assertFalse(node(normal, "host_icon") instanceof ItemSlot);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void energyCaptionsUseSyncedAmountsAndCapacityCaptionsAlignToBothBarEdges() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            Font font = mock(Font.class);
            fontField.set(client, font);
            when(font.width(any(net.minecraft.network.chat.FormattedText.class))).thenReturn(40);
            when(font.width(any(net.minecraft.util.FormattedCharSequence.class))).thenReturn(32);
            when(font.substrByWidth(any(net.minecraft.network.chat.FormattedText.class), anyInt()))
                .thenAnswer(call -> call.getArgument(0));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                12_000_000_000L, 22_000_000_000L, 72_000, "150", 0.75, 0, 0,
                List.of(), List.of(), "", 0, false, BigInteger.valueOf(200)).toTag());
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            PoseStack pose = spy(new PoseStack());
            when(context.graphics.pose()).thenReturn(pose);
            UIElement stored = positionedBar(root, "energy_stored_text", 208, 294, 160, 9);
            UIElement drain = positionedBar(root, "energy_text", 208, 303, 160, 9);
            UIElement bytes = positionedBar(root, "capacity_text", 208, 312, 122, 9);
            UIElement usage = positionedBar(root, "capacity_percentage", 332, 312, 36, 9);
            for (UIElement caption : List.of(stored, drain, bytes, usage)) caption.drawBackgroundAdditional(context);
            var rendered = org.mockito.ArgumentCaptor.forClass(net.minecraft.network.chat.FormattedText.class);
            verify(font, times(3)).substrByWidth(rendered.capture(), anyInt());
            assertEquals(Component.translatable("gui.neoecoae.storage.ui.energy", "12G", "22G"), rendered.getAllValues().get(0));
            assertEquals(Component.literal("75.0%"), rendered.getAllValues().get(2));
            verify(pose).translate(208F, 312F, 0F);
            // The 32px text at 0.75 scale ends at x=368, the capacity bar's right edge.
            verify(pose).translate(344F, 312F, 0F);

            display.setValue(new StoragePanelState(Component.empty(), 2, true, true, false,
                0, 22_000_000_000L, 0, "0", 1, 0, 0,
                List.of(), List.of(), "", 0, false).toTag());
            clearInvocations(font, pose, context.graphics);
            stored.drawBackgroundAdditional(context);
            usage.drawBackgroundAdditional(context);
            verify(font).substrByWidth(eq(Component.translatable("gui.neoecoae.storage.ui.energy", "0", "22G")), anyInt());
            verify(context.graphics, times(1)).drawString(eq(font), any(net.minecraft.util.FormattedCharSequence.class), eq(0), eq(0), anyInt(), eq(false));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void informationIconUsesSyncedStatusAndLimitsHoverToTheVisibleButton() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            UIElement frame = node(root, "storage_information");
            UIElement information = cn.dancingsnow.neoecoae.platform.JdkCompat.first(frame.getChildren());
            assertEquals((float)(-21), frame.getLayoutStyle().right);
            assertEquals((float)(0), frame.getLayoutStyle().top);
            assertEquals((float)(23), frame.getLayoutStyle().width);
            assertEquals((float)(30), frame.getLayoutStyle().height);
            assertEquals((float)(3), information.getLayoutStyle().top);
            assertEquals((float)(21), information.getLayoutStyle().width);
            assertEquals((float)(21), information.getLayoutStyle().height);
            assertEquals("neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png",
                ((SpriteTexture) frame.getStyle().backgroundTexture()).getImageLocation().toString());
            for (UIEvents eventName : List.of(UIEvents.MOUSE_ENTER, UIEvents.MOUSE_LEAVE)) {
                UIEvent hover = new UIEvent(eventName,null,0,0,0);
                hover.target = information;
                hover.target.dispatchEvent(hover);
                String filename = eventName.equals(UIEvents.MOUSE_ENTER) ? "information_highlight.png" : "information.png";
                assertEquals("neoecoae:textures/gui/widget/" + filename,
                    ((SpriteTexture) information.getStyle().backgroundTexture()).getImageLocation().toString());
            }
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 0, "0", 0, 0, 0, List.of(), List.of(), "test-domain", 4, false).toTag());
            UIEvent tooltip = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
            tooltip.target = information;
            tooltip.target.dispatchEvent(tooltip);
            assertEquals(List.of(Component.translatable("gui.neoecoae.storage.ui.normal"),
                Component.literal("UUID: test-domain").withStyle(style -> style.withColor(0x55FFFF)),
                Component.translatable("gui.neoecoae.storage.members_missing", 4).withStyle(style -> style.withColor(0xFF6A75)),
                Component.translatable("gui.neoecoae.storage.members_return").withStyle(style -> style.withColor(0xFF6A75))),
                tooltip.hoverTooltips.tooltips());
            display.setValue(StoragePanelState.EMPTY.toTag());
            UIEvent restored = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
            restored.target = information;
            restored.target.dispatchEvent(restored);
            assertEquals(List.of(Component.translatable("gui.neoecoae.storage.ui.normal")), restored.hoverTooltips.tooltips());
            UIEvent frameHover = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
            frameHover.target = frame;
            frameHover.target.dispatchEvent(frameHover);
            assertNull(frameHover.hoverTooltips);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingMembersLeaveAllThreeResourceRowsAvailable() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            List<StoragePanelState.TypeLine> types = List.of(
                new StoragePanelState.TypeLine(1, Component.literal("items"), 0xEC8600, 1, 10, 50, 100, "50"),
                new StoragePanelState.TypeLine(2, Component.literal("fluids"), 0x3894FF, 1, 10, 50, 100, "50"),
                new StoragePanelState.TypeLine(3, Component.literal("other"), 0xFFB2D8, 1, 10, 50, 100, "50"));
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 0, "150", 0.5, 0, 0, List.of(), types, "", 4, false).toTag());
            UIElement panel = node(root, "storage_types");
            for (int row = 0; row < 3; row++) {
                UIEvent tooltip = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                tooltip.target = panel.getChildren().get(row);
                tooltip.target.dispatchEvent(tooltip);
                assertEquals(types.get(row).name(), cn.dancingsnow.neoecoae.platform.JdkCompat.first(tooltip.hoverTooltips.tooltips()));
            }
            GUIContext context = mock(GUIContext.class);
            node(root, "type_scrollbar").drawBackgroundAdditional(context);
            verifyNoInteractions(context);
        }
    }

    @Test
    void hostIconRendersTheSuppliedItemInEveryTierWithoutAStorageUsageFill() {
        try (var ldlib = mockStatic(LDLib.class); var drawer = mockStatic(DrawerHelper.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            for (int tier : List.of(1, 2, 3)) {
                for (boolean formed : List.of(false, true)) {
                    for (boolean infinite : List.of(false, true)) {
                        ItemStack item = new ItemStack(Items.DIAMOND, 7);
                        item.setHoverName(Component.literal("L" + tier + " host"));
                        ItemStack expected = item.copyWithCount(1);
                        var base = StoragePanelStateTest.config(List.of(StoragePanelStateTest.cell(50, 100)), infinite);
                        var config = new StorageHostUI.Config(base.title(), () -> tier, () -> formed,
                            base.storedEnergy(), base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(),
                            base.cellEntries(), base.storageTypes(), base.infiniteStorage(), base.migratingToInfinite(),
                            base.canExtractInfiniteComponents(), base.domainId(), base.missingMembers(),
                            base.infiniteComponentInventory(), base.player(), base.cellExtractor(), base.cellInserter(), () -> item);
                        UIElement icon = positionedBar(StorageHostUI.create(config), "host_icon", 209, 240, 16, 34);
                        item.setHoverName(Component.literal("Changed after UI creation"));
                        GUIContext context = mock(GUIContext.class);
                        cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
                        icon.drawBackgroundAdditional(context);
                        verify(context).drawTexture(StoragePanelTextures.HOST_ICON_BACKGROUND, 209F, 240F, 16F, 34F);
                        if (formed) {
                            int sourceX = 18 + (tier - 1) * 16;
                            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                                && sprite.spritePosition.x == sourceX && sprite.spritePosition.y == 19
                                && sprite.spriteSize.width == 13 && sprite.spriteSize.height == 8),
                                eq(211F), eq(241F), eq(13F), eq(8F));
                            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                                && sprite.spritePosition.x == sourceX && sprite.spritePosition.y == 43
                                && sprite.spriteSize.width == 13 && sprite.spriteSize.height == 8),
                                eq(211F), eq(265F), eq(13F), eq(8F));
                        }
                        verify(context.graphics).renderItem(argThat(stack -> ItemStack.matches(expected, stack)), eq(209), eq(249));
                        verifyNoMoreInteractions(context, context.graphics);
                    }
                }
            }
            drawer.verifyNoInteractions();
        }
    }

    @Test
    void realDriveItemsAndHoverUseTheSameRowSpacing() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            var cells = List.of(StorageHostUI.CellEntry.EMPTY.atDrive(new BlockPos(2, 1, 0), ItemStack.EMPTY, null),
                StorageHostUI.CellEntry.EMPTY.atDrive(new BlockPos(2, 0, 0), ItemStack.EMPTY, null),
                StorageHostUI.CellEntry.EMPTY.atDrive(new BlockPos(2, -1, 0), ItemStack.EMPTY, null));
            UIElement grid = node(StorageHostUI.create(StoragePanelStateTest.config(cells, false)), "storage_cells");
            GUIContext context = mock(GUIContext.class);
            var middle = (StorageCellGrid.CellSlot) grid.getChildren().get(11);
            var bottom = (StorageCellGrid.CellSlot) grid.getChildren().get(22);
            assertEquals((float)(18), middle.getLayoutStyle().top);
            assertEquals((float)(36), bottom.getLayoutStyle().top);
            middle.drawHover(context);
            bottom.drawHover(context);
            verify(context, times(2)).drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0F, 0F, 16F, 16F);
            ((StorageCellGrid.CellSlot) grid.getChildren().get(1)).drawHover(context);
            verifyNoMoreInteractions(context);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void halfUsedByteSegmentsStopAtHalfWidthWhileResourceSharesAndEnergyDrawIndependently() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            var item = new StoragePanelState.TypeLine(2, Component.literal("items"), 0xEC8600,
                1, 10, 90, 100, "90", 2, BigInteger.valueOf(90));
            var fluid = new StoragePanelState.TypeLine(1, Component.literal("fluids"), 0x3894FF,
                1, 10, 30, 60, "30", 1, BigInteger.valueOf(30));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                25, 100, 999, "120", 0.5, 0, 0, List.of(), List.of(fluid, item), "", 0, false).toTag());
            UIElement capacity = positionedBar(root, "capacity_bar", 208, 313, 160, 8);
            UIElement resources = positionedBar(root, "resource_bar", 208, 334, 160, 8);
            UIElement energy = positionedBar(root, "energy_bar", 373, 292, 74, 7);
            GUIContext context = mock(GUIContext.class);
            HostProgressTestSupport.drawAfterOpening(capacity, context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62), eq(208F), eq(313F), eq(60F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 24 && sprite.spritePosition.y == 62), eq(268F), eq(313F), eq(20F), eq(8F));
            HostProgressTestSupport.drawAfterOpening(resources, context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62), eq(208F), eq(334F), eq(120F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 24 && sprite.spritePosition.y == 62), eq(328F), eq(334F), eq(40F), eq(8F));
            HostProgressTestSupport.drawAfterOpening(energy, context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 133 && sprite.spriteSize.width == 19), eq(373F), eq(292F), eq(19F), eq(7F));
            verifyNoMoreInteractions(context);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unboundedCapacityUsesTheNewPatternBarWithoutAnOverlaySymbolOrEnergyFill() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            UIElement capacity = positionedBar(root, "capacity_bar", 208, 313, 160, 8);
            UIElement resources = positionedBar(root, "resource_bar", 208, 334, 160, 8);
            UIElement energy = positionedBar(root, "energy_bar", 373, 292, 74, 7);
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            PoseStack pose = spy(new PoseStack());
            when(context.graphics.pose()).thenReturn(pose);
            display.setValue(StoragePanelState.EMPTY.toTag());
            capacity.drawBackgroundAdditional(context);
            resources.drawBackgroundAdditional(context);
            energy.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
            for (boolean migrating : List.of(false, true)) {
                display.setValue(new StoragePanelState(Component.empty(), 2, true, !migrating, migrating,
                    0, 100, 999, "0", 0, 0, 0, List.of(), List.of(), "", 0, false).toTag());
                HostProgressTestSupport.drawAfterOpening(capacity, context);
                resources.drawBackgroundAdditional(context);
                energy.drawBackgroundAdditional(context);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_BAR, 208F, 313F, 160F, 8F);
                verifyNoMoreInteractions(context);
                clearInvocations(context);
            }
            // Switching back to finite capacity restores byte segments within the used width.
            var item = new StoragePanelState.TypeLine(2, Component.literal("items"), 0xEC8600,
                1, 10, 100, 100, "100", 2, BigInteger.valueOf(100));
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 999, "100", 1, 0, 0, List.of(), List.of(item), "", 0, false).toTag());
            capacity.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62), eq(208F), eq(313F), eq(160F), eq(8F));
            verifyNoInteractions(context.graphics, pose);
        }
    }

    private static UIElement positionedBar(UIElement root, String id, float x, float y, float width, float height) {
        UIElement bar = spy(node(root, id));
        doReturn(x).when(bar).getPositionX();
        doReturn(y).when(bar).getPositionY();
        doReturn(width).when(bar).getSizeWidth();
        doReturn(height).when(bar).getSizeHeight();
        return bar;
    }

    @Test
    @SuppressWarnings("unchecked")
    void unusedResourceCardRowsStayHiddenAndRemovingTheLastTypeClearsItsCard() throws ReflectiveOperationException {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            Minecraft client = mock(Minecraft.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, mock(Font.class));
            when(client.font.substrByWidth(any(net.minecraft.network.chat.FormattedText.class), anyInt()))
                .thenAnswer(call -> call.getArgument(0));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            UIElement panel = positionedBar(root, "storage_types", 373, 304, 68, 87);
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            when(context.graphics.pose()).thenReturn(new PoseStack());
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 0, "0", 0, 0, 0, List.of(), List.of(), "", 4, false).toTag());
            panel.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
            var emptyItemDisk = new StoragePanelState.TypeLine(2, Component.literal("items"), 0xEC8600,
                0, 10, 0, 100, "0", 2, BigInteger.ZERO);
            display.setValue(new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 0, "0", 0, 0, 0, List.of(), List.of(emptyItemDisk), "", 4, false).toTag());
            panel.drawBackgroundAdditional(context);
            verify(context, times(1)).drawTexture(eq(StoragePanelTextures.TYPE_PANEL), eq(373F), eq(304F), eq(68F), eq(29F));
            verify(context, never()).drawTexture(eq(StoragePanelTextures.TYPE_PANEL), anyFloat(), eq(333F), anyFloat(), anyFloat());
            verify(context, never()).drawTexture(eq(StoragePanelTextures.TYPE_PANEL), anyFloat(), eq(362F), anyFloat(), anyFloat());
            display.setValue(StoragePanelState.EMPTY.toTag());
            clearInvocations(context);
            panel.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
        }
    }

    @Test
    void installedDiskUsesTheActualItemStackAndItsNativeItemRenderer() {
        try (var ldlib = mockStatic(LDLib.class); var drawer = mockStatic(DrawerHelper.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            ItemStack disk = new ItemStack(Items.DIAMOND);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(StoragePanelStateTest.cell(50, 100)
                .atDrive(new BlockPos(5, 60, 12), disk, null)), false));
            var slot = (StorageCellGrid.CellSlot) cn.dancingsnow.neoecoae.platform.JdkCompat.first(node(root, "storage_cells").getChildren());
            assertTrue(ItemStack.matches(disk, slot.getValue()));
            // Display slots do not rebind changing native menu slot IDs when paging.
            assertInstanceOf(LocalSlot.class, slot.getSlot());
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            slot.drawItemStack(context, slot.getValue());
            drawer.verify(() -> DrawerHelper.drawItemStack(context.graphics, slot.getValue(), 0, 0, context.elementColor, null));
        }
    }

    @Test
    void installedCellsUseNativeItemRenderingWithoutExtraStatusLights() {
        try (var ldlib = mockStatic(LDLib.class); var drawer = mockStatic(DrawerHelper.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            ItemStack disk = new ItemStack(Items.DIAMOND);
            List<StorageHostUI.CellEntry> cells = new java.util.ArrayList<>(java.util.Collections.nCopies(33, StorageHostUI.CellEntry.EMPTY));
            cells.set(1, new StorageHostUI.CellEntry(7, 1, 1, 1, 1, 10, 100, false, CellState.TYPES_FULL, true));
            cells.set(2, new StorageHostUI.CellEntry(7, 3, 1, 1, 1, 100, 100, false, CellState.FULL, true));
            cells.set(3, new StorageHostUI.CellEntry(7, 2, 1, 1, 1, 10, 100, false, CellState.NOT_EMPTY, false));
            for (int i = 1; i <= 3; i++) cells.set(i, cells.get(i).atDrive(new BlockPos(i, 60, 12), disk, null));
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(cells, false));
            UIElement grid = node(root, "storage_cells");
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            assertTrue(((ItemSlot) cn.dancingsnow.neoecoae.platform.JdkCompat.first(grid.getChildren())).getValue().isEmpty());
            for (int i = 1; i <= 3; i++) {
                var slot = (StorageCellGrid.CellSlot) grid.getChildren().get(i);
                slot.drawItemStack(context, slot.getValue());
                drawer.verify(() -> DrawerHelper.drawItemStack(context.graphics, slot.getValue(), 0, 0, context.elementColor, null));
            }
            drawer.verifyNoMoreInteractions();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void wheelRequestsTheNextPageAndFullCellsStayInTheServerSnapshot() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(
                java.util.Collections.nCopies(100, StoragePanelStateTest.cell(50, 100)), false));
            BindableValue<Integer> request = (BindableValue<Integer>) root.getChildren().get(1);
            UIEvent wheel = new UIEvent(UIEvents.MOUSE_WHEEL,null,0,0,0);
            wheel.target = node(root, "storage_cells");
            wheel.deltaY = -1;
            wheel.target.dispatchEvent(wheel);
            assertEquals(33, request.getValue());
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            StoragePanelState state = StoragePanelState.fromTag(display.getValue());
            assertEquals(100, state.cellCount());
            assertEquals(33, state.cells().size());
        }
    }

    @Test
    void componentExtractionStillUsesTheServerLock() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            AtomicBoolean unlocked = new AtomicBoolean(false);
            StorageHostUI.Config base = StoragePanelStateTest.config(List.of(), true);
            base.infiniteComponentInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND));
            StorageHostUI.Config config = new StorageHostUI.Config(base.title(), base.tier(), base.formed(),
                base.storedEnergy(), base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(),
                base.cellEntries(), base.storageTypes(), base.infiniteStorage(), base.migratingToInfinite(),
                unlocked::get, base.domainId(), base.missingMembers(), base.infiniteComponentInventory());
            ItemSlot slot = (ItemSlot) node(StorageHostUI.create(config), "infinite_component");
            assertFalse(slot.getSlot().mayPickup(null));
            unlocked.set(true);
            assertTrue(slot.getSlot().mayPickup(null));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void infiniteAnimationUsesTheRegisteredGuiSpriteOnlyInInfiniteMode() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), true));
            UIElement animation = spy(node(root, "infinite_animation"));
            doReturn(322F).when(animation).getPositionX();
            doReturn(411F).when(animation).getPositionY();
            doReturn(12F).when(animation).getSizeWidth();
            doReturn(12F).when(animation).getSizeHeight();
            GUIContext context = mock(GUIContext.class);
            animation.drawBackgroundAdditional(context);
            assertEquals("neoecoae:storage/infinity", StoragePanelTextures.INFINITE_ANIMATION.getSprite().toString());
            verify(context).drawTexture(StoragePanelTextures.INFINITE_ANIMATION, 322F, 411F, 12F, 12F);
            BindableValue<Tag> display = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            Tag active = display.getValue();
            for (boolean migrating : List.of(false, true)) {
                display.setValue(new StoragePanelState(Component.empty(), 2, true, false, migrating,
                    0, 100, 0, "0", 0, 0, 0, List.of(), List.of(), "", 0, false).toTag());
                clearInvocations(context);
                animation.drawBackgroundAdditional(context);
                verifyNoInteractions(context);
                UIEvent tooltip = new UIEvent(UIEvents.HOVER_TOOLTIPS,null,0,0,0);
                tooltip.target = animation;
                tooltip.target.dispatchEvent(tooltip);
                assertTrue(tooltip.hoverTooltips.tooltips().isEmpty());
            }
            display.setValue(active);
            animation.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.INFINITE_ANIMATION, 322F, 411F, 12F, 12F);
        }
    }

    private static UIElement node(UIElement root, String id) {
        return root.selectId(id).findFirst().orElseThrow();
    }
}
