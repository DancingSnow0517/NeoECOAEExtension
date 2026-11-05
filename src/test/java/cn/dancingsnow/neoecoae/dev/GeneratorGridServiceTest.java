package cn.dancingsnow.neoecoae.dev;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import cn.dancingsnow.neoecoae.config.DevToolsConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeneratorGridServiceTest {
    private MockedStatic<DevToolsConfig> configuration;
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @BeforeEach void enableTools() {
        configuration = mockStatic(DevToolsConfig.class);
        configuration.when(DevToolsConfig::enabled).thenReturn(true);
    }
    @AfterEach void closeConfiguration() { configuration.close(); }

    @Test void installedCellRequiresActivePoweredHostAndStopsWhenUnmounted() {
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        MEStorage storage = grid.getStorageService().getInventory();
        var node = mock(IGridNode.class);
        var host = mock(AEBaseBlockEntity.class, withSettings().extraInterfaces(IChestOrDrive.class));
        var drive = (IChestOrDrive) host;
        when(node.getOwner()).thenReturn(host);
        when(node.getGrid()).thenReturn(grid);
        when(drive.getCellCount()).thenReturn(1);
        var cell = new GeneratorGridService.GeneratorCell(ItemStack.EMPTY);
        cell.state = new GeneratorState();
        cell.state.settings = new GeneratorSettings(1, 10, 10, 1);
        cell.state.selected.add(AEItemKey.of(Items.STONE));
        when(drive.getOriginalCellInventory(0)).thenReturn(cell);
        var service = new GeneratorGridService();
        service.addNode(node, null);

        service.onServerEndTick(); // Node is inactive.
        when(node.isActive()).thenReturn(true);
        service.onServerEndTick(); // Drive is unpowered.
        verify(storage, never()).insert(any(), anyLong(), any(), any());

        when(drive.isPowered()).thenReturn(true);
        configuration.when(DevToolsConfig::enabled).thenReturn(false);
        service.onServerEndTick();
        verify(storage, never()).insert(any(), anyLong(), any(), any());
        configuration.when(DevToolsConfig::enabled).thenReturn(true);
        service.onServerEndTick();
        verify(storage, times(1)).insert(any(), eq(10L), any(), any());
        when(drive.getOriginalCellInventory(0)).thenReturn(null);
        service.onServerEndTick();
        service.removeNode(node);
        service.onServerEndTick();
        verify(storage, times(1)).insert(any(), anyLong(), any(), any());
    }
}
