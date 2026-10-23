package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.implementations.CellWorkbenchMenu;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportJeiBookmarksPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void mixedBookmarksSkipRejectedKeysAndFillTheFluidCellsAvailableSlots() {
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        var stone = AEItemKey.of(Items.STONE);
        var fixture = new Fixture(2, Set.of(AEKeyType.fluids()));
        fixture.send(Arrays.asList(stone, null, water, stone, lava, water));
        assertEquals(water, fixture.config.getKey(0));
        assertEquals(lava, fixture.config.getKey(1));
        assertNull(fixture.config.getKey(2));
        verify(fixture.menu).broadcastChanges();
    }

    @Test void itemCellsSkipFluidsAndOmniConfigurationsKeepBothTypesInOrder() {
        var water = AEFluidKey.of(Fluids.WATER);
        var stone = AEItemKey.of(Items.STONE);
        var itemCell = new Fixture(1, Set.of(AEKeyType.items()));
        itemCell.send(List.of(water, stone));
        assertEquals(stone, itemCell.config.getKey(0));
        assertNull(itemCell.config.getKey(1));

        var omni = new Fixture(3, Set.of(AEKeyType.items(), AEKeyType.fluids()));
        omni.send(List.of(water, stone));
        assertEquals(water, omni.config.getKey(0));
        assertEquals(stone, omni.config.getKey(1));
        assertNull(omni.config.getKey(2));
    }

    @Test void staleMenuPacketsLeaveConfigurationUntouched() {
        var fixture = new Fixture(2, Set.of(AEKeyType.items()));
        var stone = AEItemKey.of(Items.STONE);
        fixture.send(List.of(stone));
        ECOImportJeiBookmarksC2SPacket.handle(
            new ECOImportJeiBookmarksC2SPacket(fixture.menu.containerId + 1, List.of()), fixture.context);
        assertEquals(stone, fixture.config.getKey(0));
        when(fixture.menu.stillValid(fixture.player)).thenReturn(false);
        fixture.send(List.of());
        assertEquals(stone, fixture.config.getKey(0));
    }

    private static final class Fixture {
        final CellWorkbenchMenu menu = mock(CellWorkbenchMenu.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final NEPacketContext context = mock(NEPacketContext.class);
        final GenericStackInv config = new GenericStackInv(null, GenericStackInv.Mode.CONFIG_TYPES, 5);

        Fixture(int cellSlots, Set<AEKeyType> types) {
            var host = mock(CellWorkbenchBlockEntity.class);
            var cell = mock(ICellWorkbenchItem.class);
            ConfigInventory cellConfig = ConfigInventory.configTypes(
                key -> types.contains(key.getType()), cellSlots, null);
            when(menu.getHost()).thenReturn(host);
            when(menu.getWorkbenchItem()).thenReturn(ItemStack.EMPTY);
            when(host.getConfig()).thenReturn(config);
            when(host.getCell()).thenReturn(cell);
            when(cell.getConfigInventory(ItemStack.EMPTY)).thenReturn(cellConfig);
            when(menu.stillValid(player)).thenReturn(true);
            player.containerMenu = menu;
            when(context.player()).thenReturn(player);
            doAnswer(call -> {
                ((Runnable) call.getArgument(0)).run();
                return CompletableFuture.completedFuture(null);
            }).when(context).enqueueWork(any(Runnable.class));
        }

        void send(List<AEKey> keys) {
            ECOImportJeiBookmarksC2SPacket.handle(
                new ECOImportJeiBookmarksC2SPacket(menu.containerId, keys), context);
        }
    }
}
