package cn.dancingsnow.neoecoae.gui.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypesInternal;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Label;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageInterfaceUiTest {
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
    void jsonPositionsTheInterfaceControlsAndTwentySevenPhantomFilters() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            var host = host();
            UIElement root = StorageInterfaceUI.createRoot(host, player(), () -> Long.MAX_VALUE);
            assertEquals(TaffyPosition.RELATIVE, root.getLayout().getValueImmediate(LayoutProperties.POSITION));
            assertEquals((float)(176), root.getLayoutStyle().width);
            assertEquals((float)(218), root.getLayoutStyle().height);
            SpriteTexture texture = (SpriteTexture) node(root, "background").getStyle().backgroundTexture();
            assertEquals("neoecoae:textures/guis/storage/storage_interface.png", texture.getImageLocation().toString());
            assertEquals(176, texture.spriteSize.width);
            assertEquals(218, texture.spriteSize.height);
            var grid = node(root, "resource_filters");
            List<ItemSlot> filters = grid.getChildren().stream().filter(ItemSlot.class::isInstance).map(ItemSlot.class::cast).toList();
            assertEquals(27, filters.size());
            for (var slot : filters) {
                assertFalse(slot.getSlot().mayPickup(null));
                assertEquals(0, slot.getSlot().getMaxStackSize());
            }
            assertEquals((float)(7), grid.getLayoutStyle().left);
            assertEquals((float)(65), grid.getLayoutStyle().top);
            assertEquals((float)(144), cn.dancingsnow.neoecoae.platform.JdkCompat.last(filters).getLayoutStyle().left);
            assertEquals((float)(36), cn.dancingsnow.neoecoae.platform.JdkCompat.last(filters).getLayoutStyle().top);
            InventorySlots inventory = (InventorySlots) node(root, "player_inventory");
            assertEquals((float)(133), inventory.getLayoutStyle().top);
            assertEquals((float)(4), inventory.hotbar.getLayoutStyle().marginTop);
            for (String id : List.of("title", "mode_caption", "structure_status", "network_status",
                "transfer_status", "filter_caption", "filter_hint")) {
                Label text = (Label) node(root, id);
                assertEquals(6F, text.getTextStyle().getValueImmediate(PropertyRegistry.FONT_SIZE), 0.001F);
            }
        }
    }

    @Test
    void longStatusTextFitsItsBoxWithoutDiscardingTheColoredState() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            UIElement root = StorageInterfaceUI.createRoot(host(), player(), () -> 0);
            Label text = spy((Label) node(root, "structure_status"));
            Component value = Component.literal("Infinite: ")
                .append(Component.literal("Unavailable in this structure").withStyle(style -> style.withColor(0xB53643)));
            text.setValue(value);
            cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(text);
            Font font = mock(Font.class);
            when(font.width(value)).thenReturn(180);
            doReturn(font).when(text).getFont();
            doReturn(89F).when(text).getContentWidth();
            doReturn(8F).when(text).getContentHeight();
            doReturn(200F).when(text).getContentX();
            doReturn(300F).when(text).getContentY();
            GUIContext context = mock(GUIContext.class);
            cn.dancingsnow.neoecoae.util.UiTestSupport.set(context,"graphics",mock(GuiGraphics.class));
            PoseStack pose = new PoseStack();
            when(context.graphics.pose()).thenReturn(pose);
            when(context.graphics.drawString(eq(font), same(value), eq(0), eq(0), anyInt(), eq(false)))
                .thenAnswer(call -> {
                    var matrix = pose.last().pose();
                    assertEquals(89F, 180 * matrix.m00(), 0.001F);
                    assertEquals(200F, matrix.m30(), 0.001F);
                    assertEquals(300 + (8 - font.lineHeight * matrix.m00()) / 2, matrix.m31(), 0.001F);
                    assertEquals(0xB53643, cn.dancingsnow.neoecoae.platform.JdkCompat.first(value.getSiblings()).getStyle().getColor().getValue());
                    return 0;
                });
            text.drawBackgroundAdditional(context);
            verify(context.graphics).drawString(eq(font), same(value), eq(0), eq(0), anyInt(), eq(false));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void modeCaptionAndButtonStatesFollowServerSnapshotWithoutSendingModeChanges() {
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            var host = host();
            when(host.getStorageInterfaceMode()).thenReturn(ECOStorageInterfaceMode.OUTPUT);
            UIElement root = StorageInterfaceUI.createRoot(host, player(), () -> 0);
            BindableValue<Integer> mode = (BindableValue<Integer>) cn.dancingsnow.neoecoae.platform.JdkCompat.first(root.getChildren());
            Label caption = (Label) node(root, "mode_caption");
            for (int selected : List.of(2, 1, 0, 2, 1)) {
                mode.setValue(selected);
                assertEquals(Component.translatable("gui.neoecoae.storage_interface.mode."
                    + ECOStorageInterfaceMode.values()[selected].getSerializedName()), caption.getValue());
                for (int button = 0; button < 3; button++) {
                    Button element = (Button) node(root, "mode_" + ECOStorageInterfaceMode.values()[button].getSerializedName());
                    cn.dancingsnow.neoecoae.util.UiTestSupport.restyle(element);
                    SpriteTexture base = (SpriteTexture) element.getButtonStyle().baseTexture();
                    SpriteTexture hover = (SpriteTexture) element.getButtonStyle().hoverTexture();
                    SpriteTexture pressed = (SpriteTexture) element.getButtonStyle().pressedTexture();
                    assertEquals(button * 16, base.spritePosition.x);
                    assertEquals(button == selected ? 159 : 140, base.spritePosition.y);
                    assertEquals((button + 3) * 16, hover.spritePosition.x);
                    assertEquals(base.spritePosition.y, hover.spritePosition.y);
                    assertEquals(hover.spritePosition.x, pressed.spritePosition.x);
                    assertEquals(159, pressed.spritePosition.y);
                    assertEquals(17, pressed.spriteSize.height);
                    for (SpriteTexture texture : List.of(base, hover)) {
                        assertEquals(button == selected ? 17 : 18, texture.spriteSize.height);
                        assertEquals(0, texture.borderLT.y);
                        assertEquals(0, texture.borderRB.y);
                    }
                    assertEquals((float)(9 + button * 21),
                        element.getLayoutStyle().left);
                    assertEquals((float)(32), element.getLayoutStyle().top);
                    assertEquals((float)(18), element.getLayoutStyle().height);
                    Button drawing = spy(element);
                    doReturn(209F).when(drawing).getPositionX();
                    doReturn(240F).when(drawing).getPositionY();
                    doReturn(16F).when(drawing).getSizeWidth();
                    doReturn(18F).when(drawing).getSizeHeight();
                    for (Button.State state : Button.State.values()) {
                        doReturn(state).when(drawing).getState();
                        SpriteTexture texture = switch (state) {
                            case DEFAULT -> base;
                            case HOVERED -> hover;
                            case PRESSED -> pressed;
                        };
                        GUIContext context = mock(GUIContext.class);
                        drawing.drawBackgroundAdditional(context);
                        verify(context).drawTexture(same(texture), eq(209F),
                            eq(258F - texture.spriteSize.height), eq(16F), eq((float) texture.spriteSize.height));
                        verifyNoMoreInteractions(context);
                    }
                }
            }
            verify(host, never()).setStorageInterfaceMode(any());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void receivedFiltersPopulateSamplesWithoutEditingOrMovingRealItems() {
        try (var ldlib = mockStatic(LDLib.class); var keys = mockStatic(AEKeyTypesInternal.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var host = host();
            Player player = player();
            var grid = (StorageResourceFilterGrid) node(StorageInterfaceUI.createRoot(host, player, () -> 0), "resource_filters");
            var snapshot = (BindableValue<Tag>) cn.dancingsnow.neoecoae.platform.JdkCompat.last(grid.getChildren());
            var key = AEItemKey.of(Items.DIAMOND);
            snapshot.setValue(StorageInterfaceFilter.with(new CompoundTag(), 26, key));
            ItemSlot slot = (ItemSlot) grid.getChildren().get(26);
            assertTrue(slot.getValue().is(Items.DIAMOND));
            assertEquals(1, slot.getValue().getCount());
            assertTrue(slot.getSlot().remove(64).isEmpty());
            assertTrue(slot.getValue().is(Items.DIAMOND));
            snapshot.setValue(new CompoundTag());
            assertTrue(slot.getValue().isEmpty());
            verify(host, never()).setStorageResourceFilter(anyInt(), any());
        }
    }

    @Test
    void serverRejectsEditsFromAClosedOrDistantMenuAndRejectsInvalidKeys() throws Exception {
        try (var ldlib = mockStatic(LDLib.class); var keys = mockStatic(AEKeyTypesInternal.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var host = host();
            Player player = player();
            Level level = mock(Level.class);
            when(host.getLevel()).thenReturn(level);
            when(host.supportsStorageInterfaceUi()).thenReturn(true);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(player.level()).thenReturn(level);
            when(player.blockPosition()).thenReturn(BlockPos.ZERO);
            AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
            player.containerMenu = menu;
            when(menu.stillValid(player)).thenReturn(true);
            var grid = (StorageResourceFilterGrid) node(StorageInterfaceUI.createRoot(host, player, () -> 0), "resource_filters");
            ModularUI ui = mock(ModularUI.class);
            when(ui.getMenu()).thenReturn(menu);
            var field = UIElement.class.getDeclaredField("modularUI");
            field.setAccessible(true);
            field.set(grid, ui);
            var key = AEItemKey.of(Items.STONE);
            CompoundTag value = StorageInterfaceFilter.encode(key);
            grid.applyEdit(26, value);
            verify(host).setStorageResourceFilter(26, key);
            grid.applyEdit(26, new CompoundTag());
            verify(host).setStorageResourceFilter(26, null);
            grid.applyEdit(-1, value);
            grid.applyEdit(27, value);
            CompoundTag invalid = new CompoundTag();
            invalid.putString("invalid", "resource");
            grid.applyEdit(0, invalid);
            when(player.blockPosition()).thenReturn(new BlockPos(100, 0, 0));
            grid.applyEdit(0, value);
            when(player.blockPosition()).thenReturn(BlockPos.ZERO);
            player.containerMenu = mock(AbstractContainerMenu.class);
            grid.applyEdit(0, value);
            player.containerMenu = menu;
            when(menu.stillValid(player)).thenReturn(false);
            grid.applyEdit(0, value);
            verify(host, times(2)).setStorageResourceFilter(anyInt(), nullable(appeng.api.stacks.AEKey.class));
        }
    }

    @Test
    void fluidContainersSelectTheirFluidUnlessTheItemIsRequested() {
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        try (var fluid = mockStatic(FluidUtil.class)) {
            fluid.when(() -> FluidUtil.getFluidContained(bucket)).thenReturn(java.util.Optional.of(new FluidStack(Fluids.WATER, 1000)));
            assertEquals(AEFluidKey.of(Fluids.WATER), StorageResourceFilterGrid.sample(bucket, false));
            assertEquals(AEItemKey.of(Items.WATER_BUCKET), StorageResourceFilterGrid.sample(bucket, true));
            assertEquals(1, bucket.getCount());
            assertNull(StorageResourceFilterGrid.sample(ItemStack.EMPTY, false));
        }
    }

    @Test
    void clicksSendOnlyFilterKeysAndLeaveTheCursorStackUntouched() throws Exception {
        try (var ldlib = mockStatic(LDLib.class); var modifiers = mockStatic(UIElement.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            modifiers.when(UIElement::isShiftDown).thenReturn(false);
            Player player = player();
            var grid = (StorageResourceFilterGrid) node(StorageInterfaceUI.createRoot(host(), player, () -> 0), "resource_filters");
            RPCEmitter edits = mock(RPCEmitter.class);
            var editField = StorageResourceFilterGrid.class.getDeclaredField("edit");
            editField.setAccessible(true);
            editField.set(grid, edits);
            UIElement slot = cn.dancingsnow.neoecoae.platform.JdkCompat.first(grid.getChildren());
            var ui = mock(ModularUI.class);
            var menu = mock(AbstractContainerMenu.class);
            var carried = new ItemStack(Items.STONE, 32);
            when(ui.getMenu()).thenReturn(menu);
            when(menu.getCarried()).thenReturn(carried);
            var uiField = UIElement.class.getDeclaredField("modularUI");
            uiField.setAccessible(true);
            uiField.set(slot, ui);
            for (int button : List.of(0, 1)) {
                UIEvent event = new UIEvent(UIEvents.MOUSE_DOWN,null,0,0,0);
                event.target = slot;
                event.button = button;
                event.target.dispatchEvent(event);
                assertTrue(event.hasHandler);
                assertEquals(32, carried.getCount());
            }
            verify(edits).send(eq(0), argThat((Object value) -> value instanceof CompoundTag tag
                && tag.equals(StorageInterfaceFilter.encode(AEItemKey.of(Items.STONE)))));
            verify(edits).send(eq(0), argThat((Object value) -> value instanceof CompoundTag tag && tag.isEmpty()));
            verify(menu, never()).setCarried(any());
        }
    }

    @SuppressWarnings("unchecked")
    private static ECOMachineInterfaceBlockEntity<NEStorageCluster> host() {
        var host = (ECOMachineInterfaceBlockEntity<NEStorageCluster>) mock(ECOMachineInterfaceBlockEntity.class);
        when(host.getStorageInterfaceMode()).thenReturn(ECOStorageInterfaceMode.STORAGE);
        when(host.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        when(host.getStorageResourceFilters()).thenReturn(new CompoundTag());
        return host;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.level().registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return player;
    }

    private static UIElement node(UIElement root, String id) { return root.selectId(id).findFirst().orElseThrow(); }
}
