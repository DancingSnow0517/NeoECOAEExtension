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
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
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
            AccessorRegistries.init();
        }
    }

    @Test
    void jsonPositionsTheInterfaceControlsAndTwentySevenPhantomFilters() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            UIElement root = StorageInterfaceUI.createRoot(host, player(), () -> Long.MAX_VALUE);
            assertEquals(TaffyPosition.RELATIVE, root.getLayout().getValueImmediate(LayoutProperties.POSITION));
            assertEquals(TaffyDimension.length(176), root.getLayout().getValueImmediate(LayoutProperties.WIDTH));
            assertEquals(TaffyDimension.length(218), root.getLayout().getValueImmediate(LayoutProperties.HEIGHT));
            SpriteTexture texture = (SpriteTexture) node(root, "background").getStyle().getValueImmediate(PropertyRegistry.BACKGROUND);
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
            assertEquals(LengthPercentageAuto.length(7), grid.getLayout().getValueImmediate(LayoutProperties.LEFT));
            assertEquals(LengthPercentageAuto.length(65), grid.getLayout().getValueImmediate(LayoutProperties.TOP));
            assertEquals(LengthPercentageAuto.length(144), filters.getLast().getLayout().getValueImmediate(LayoutProperties.LEFT));
            assertEquals(LengthPercentageAuto.length(36), filters.getLast().getLayout().getValueImmediate(LayoutProperties.TOP));
            InventorySlots inventory = (InventorySlots) node(root, "player_inventory");
            assertEquals(LengthPercentageAuto.length(133), inventory.getLayout().getValueImmediate(LayoutProperties.TOP));
            assertEquals(LengthPercentageAuto.length(4), inventory.hotbar.getLayout().getValueImmediate(LayoutProperties.MARGIN_TOP));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectedModeArtworkFollowsServerSnapshotWithoutSendingModeChanges() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            UIElement root = StorageInterfaceUI.createRoot(host, player(), () -> 0);
            BindableValue<Integer> mode = (BindableValue<Integer>) root.getChildren().getFirst();
            for (int selected = 0; selected < 3; selected++) {
                mode.setValue(selected);
                for (int button = 0; button < 3; button++) {
                    Button element = (Button) node(root, "mode_" + ECOStorageInterfaceMode.values()[button].getSerializedName());
                    element.getStyleBag().compute(selected + 1);
                    SpriteTexture base = (SpriteTexture) element.getButtonStyle().baseTexture();
                    SpriteTexture hover = (SpriteTexture) element.getButtonStyle().hoverTexture();
                    assertEquals(button * 16, base.spritePosition.x);
                    assertEquals(button == selected ? 159 : 140, base.spritePosition.y);
                    assertEquals((button + 3) * 16, hover.spritePosition.x);
                    assertEquals(17, base.spriteSize.height);
                }
            }
            verify(host, never()).setStorageInterfaceMode(any());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void receivedFiltersPopulateSamplesWithoutEditingOrMovingRealItems() {
        try (var ldlib = mockStatic(LDLib2.class); var keys = mockStatic(AEKeyTypesInternal.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var host = host();
            Player player = player();
            var grid = (StorageResourceFilterGrid) node(StorageInterfaceUI.createRoot(host, player, () -> 0), "resource_filters");
            var snapshot = (BindableValue<Tag>) grid.getChildren().getLast();
            var key = AEItemKey.of(Items.DIAMOND);
            snapshot.setValue(StorageInterfaceFilter.with(new CompoundTag(), 26, key, player.registryAccess()));
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
        try (var ldlib = mockStatic(LDLib2.class); var keys = mockStatic(AEKeyTypesInternal.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
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
            CompoundTag value = StorageInterfaceFilter.encode(key, player.registryAccess());
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
        try (var ldlib = mockStatic(LDLib2.class); var modifiers = mockStatic(UIElement.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            modifiers.when(UIElement::isShiftDown).thenReturn(false);
            Player player = player();
            var grid = (StorageResourceFilterGrid) node(StorageInterfaceUI.createRoot(host(), player, () -> 0), "resource_filters");
            RPCEmitter edits = mock(RPCEmitter.class);
            var editField = StorageResourceFilterGrid.class.getDeclaredField("edit");
            editField.setAccessible(true);
            editField.set(grid, edits);
            UIElement slot = grid.getChildren().getFirst();
            var ui = mock(ModularUI.class);
            var menu = mock(AbstractContainerMenu.class);
            var carried = new ItemStack(Items.STONE, 32);
            when(ui.getMenu()).thenReturn(menu);
            when(menu.getCarried()).thenReturn(carried);
            var uiField = UIElement.class.getDeclaredField("modularUI");
            uiField.setAccessible(true);
            uiField.set(slot, ui);
            for (int button : List.of(0, 1)) {
                UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
                event.target = slot;
                event.button = button;
                UIEventDispatcher.dispatchEvent(event, false, false, false);
                assertTrue(event.hasHandler);
                assertEquals(32, carried.getCount());
            }
            verify(edits).send(eq(0), argThat((Object value) -> value instanceof CompoundTag tag
                && tag.equals(StorageInterfaceFilter.encode(AEItemKey.of(Items.STONE), player.registryAccess()))));
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
        when(player.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return player;
    }

    private static UIElement node(UIElement root, String id) { return root.selectId(id).findFirst().orElseThrow(); }
}
