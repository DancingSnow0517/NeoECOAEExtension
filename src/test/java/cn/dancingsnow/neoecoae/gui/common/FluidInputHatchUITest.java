package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluidInputHatchUITest {
    @Test
    void chineseResourcesContainReadableTextAndTheCardKeepsTheSuppliedPng() throws Exception {
        for (String locale : List.of("zh_cn", "zh_tw", "zh_hk", "lzh")) {
            String path = "assets/neoecoae/lang/" + locale + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : json.entrySet()) {
                    if (entry.getKey().equals("item.neoecoae.network_pull_card")
                        || entry.getKey().startsWith("gui.neoecoae.fluid_input_hatch.")) {
                        assertFalse(entry.getValue().getAsString().contains("?"), entry.getKey());
                    }
                }
                if (locale.equals("zh_cn")) assertEquals("网络拉取卡", json.get("item.neoecoae.network_pull_card").getAsString());
            }
        }
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/neoecoae/textures/item/network_pull_card.png")) {
            assertNotNull(stream);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            assertEquals("38a472cc38f8e35f4186ad363c318e04d819e18ad12720c0854f8ff814b70722", hash);
        }
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/neoecoae/textures/guis/fluid_input_hatch.png")) {
            assertNotNull(stream);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            assertEquals("c05ee646cd7de822df9a2f4bf622d92831b5c26467c8429ed2447ae646e720f2", hash);
        }
    }

    @BeforeAll
    static void bootstrap() throws Exception {
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
    void createsOneUpgradeSlotAlongsideDistinctBufferMarkAndQuantityControls() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            UIElement root = FluidInputHatchUI.createRoot(host(), player());
            assertInstanceOf(FluidSlot.class, node(root, "stock"));
            assertInstanceOf(FluidSlot.class, node(root, "filter"));
            assertInstanceOf(Button.class, node(root, "edit-amount"));
            assertTrue(root.selectId("amount-window").findAny().isEmpty());
            UIElement rail = node(root, "upgrades");
            assertEquals(1, rail.getChildren().getFirst().getChildren().stream().filter(ItemSlot.class::isInstance).count());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertingAndRemovingTheCardSwitchesContentsWithoutRebuildingInventoryOrSlots() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var root = FluidInputHatchUI.createRoot(host(), player());
            var legacy = node(root, "legacy-fluid-content");
            var network = node(root, "network-fluid-content");
            var rail = node(root, "upgrades");
            var inventory = root.getChildren().stream().filter(InventorySlots.class::isInstance).findFirst().orElseThrow();
            var mode = (BindableValue<Boolean>) node(root, "network-pull-mode");
            assertEquals(TaffyDisplay.NONE, network.getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
            assertNotEquals(TaffyDisplay.NONE, legacy.getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
            for (boolean installed : List.of(true, false, true, false)) {
                mode.setValue(installed);
                assertEquals(installed ? TaffyDisplay.NONE : TaffyDisplay.FLEX,
                    legacy.getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
                assertEquals(installed ? TaffyDisplay.FLEX : TaffyDisplay.NONE,
                    network.getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
                assertSame(rail, node(root, "upgrades"));
                assertTrue(root.getChildren().contains(inventory));
                assertEquals(1, root.getChildren().stream().filter(InventorySlots.class::isInstance).count());
            }
        }
    }

    @Test
    void savedCardStateSelectsTheNetworkContentsWhenTheMenuOpens() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            when(host.hasNetworkPullCard()).thenReturn(true);
            var root = FluidInputHatchUI.createRoot(host, player());
            assertEquals(TaffyDisplay.NONE, node(root, "legacy-fluid-content")
                .getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
            assertNotEquals(TaffyDisplay.NONE, node(root, "network-fluid-content")
                .getStyleBag().computeCandidate(LayoutProperties.DISPLAY));
        }
    }

    @Test
    void originalTankLayoutDisplaysPreservedLongStockWithoutClippingItsAmount() {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            host.tank.add(new FluidStack(Fluids.WATER, 1), 5_000_000_001L);
            var root = FluidInputHatchUI.createRoot(host, player());
            var slot = (FluidSlot) node(root, "legacy-fluid-content").getChildren().getFirst();
            slot.setValue(host.tank.getFluid(), false); // The isolated UI has no S2C transport.
            assertEquals(com.lowdragmc.lowdraglib2.gui.util.TextFormattingUtil.formatLongToCompactStringBuckets(5_000_000_001L, 3) + "B",
                slot.getFluidAmountText().getString());
            assertEquals(5_000_000_001L, host.tank.getStoredAmount());
        }
    }

    @Test
    void markingClicksUseServerActionsWithoutConsumingTheCarriedFluidContainer() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            var root = FluidInputHatchUI.createRoot(host, player());
            FluidSlot mark = (FluidSlot) node(root, "filter");
            var ui = mock(ModularUI.class);
            var menu = mock(AbstractContainerMenu.class);
            var bucket = new ItemStack(Items.WATER_BUCKET);
            when(menu.getCarried()).thenReturn(bucket);
            when(ui.getMenu()).thenReturn(menu);
            var uiField = UIElement.class.getDeclaredField("modularUI");
            uiField.setAccessible(true);
            uiField.set(mark, ui);
            RPCEmitter edits = mock(RPCEmitter.class);
            var clickField = mark.getClass().getDeclaredField("click");
            clickField.setAccessible(true);
            clickField.set(mark, edits);
            click(mark, 0);
            assertEquals(1, bucket.getCount());
            assertTrue(host.tank.isEmpty());
            click(mark, 1);
            assertTrue(mark.getValue().isEmpty());
            assertEquals(1, bucket.getCount());
            verify(edits).send(0);
            verify(edits).send(1);
            verify(menu, never()).setCarried(any());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void gearUsesMouseDownToRequestTheNativeStockSubmenuAndKeepsTheSharedSlots() throws Exception {
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            var host = host();
            when(host.hasNetworkPullCard()).thenReturn(true);
            when(host.getMarkedFluid()).thenReturn(new FluidStack(Fluids.WATER, 1));
            var root = FluidInputHatchUI.createRoot(host, player());
            FluidSlot mark = (FluidSlot) node(root, "filter");
            mark.setValue(new FluidStack(Fluids.WATER, 1), false);
            assertEquals(AEFluidKey.of(Fluids.WATER).formatAmount(16_000L, AmountFormat.SLOT), mark.getFluidAmountText().getString());
            var button = node(root, "edit-amount");
            var field = button.getClass().getDeclaredField("openAmount");
            field.setAccessible(true);
            var request = mock(RPCEmitter.class);
            field.set(button, request);
            UIEvent open = UIEvent.create(UIEvents.MOUSE_DOWN);
            open.target = button;
            open.button = 0;
            UIEventDispatcher.dispatchEvent(open, false, false, false);
            verify(request).send();
            assertTrue(root.selectId("amount-window").findAny().isEmpty());
            assertTrue(root.selectId("amount").findAny().isEmpty());
            var upgrade = node(root, "upgrades");
            ((BindableValue<Boolean>) node(root, "network-pull-mode")).setValue(false);
            assertSame(upgrade, node(root, "upgrades"));
            assertEquals(Fluids.WATER, mark.getValue().getFluid());
        }
    }

    private static void click(UIElement element, int button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        assertTrue(event.hasHandler);
    }

    private static ECOFluidInputHatchBlockEntity host() {
        var host = mock(ECOFluidInputHatchBlockEntity.class);
        host.tank = new LongFluidTank();
        when(host.getFluidTankHandler()).thenCallRealMethod();
        when(host.getStoredFluidAmount()).thenCallRealMethod();
        when(host.getMarkedFluid()).thenReturn(FluidStack.EMPTY);
        when(host.getTargetAmount()).thenReturn(16_000L);
        when(host.getMainNode()).thenReturn(mock(IManagedGridNode.class));
        var upgrades = mock(IUpgradeInventory.class);
        when(upgrades.toItemHandler()).thenReturn(new ItemStackHandler(1));
        when(host.getUpgrades()).thenReturn(upgrades);
        return host;
    }

    private static Player player() {
        var player = mock(Player.class);
        when(player.level()).thenReturn(mock(Level.class));
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        return player;
    }

    private static UIElement node(UIElement root, String id) {
        return root.selectId(id).findFirst().orElseThrow();
    }
}
