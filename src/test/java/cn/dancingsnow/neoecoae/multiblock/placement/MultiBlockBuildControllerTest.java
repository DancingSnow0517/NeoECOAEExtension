package cn.dancingsnow.neoecoae.multiblock.placement;

import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiBlockBuildControllerTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void typedLengthAndLargeStepsClampWithoutIntegerOverflow() {
        var host = mock(MultiBlockBuildController.Host.class);
        Player player = mock(Player.class);
        when(host.canPlayerInteract(player)).thenReturn(true);
        when(host.getMinBuildLength()).thenReturn(2);
        when(host.getMaxBuildLength()).thenReturn(16);
        when(host.getSelectedBuildLength()).thenReturn(7);
        var controller = new MultiBlockBuildController(host);

        controller.setBuildLength(player, -5);
        verify(host).setSelectedBuildLength(2);
        controller.setBuildLength(player, 99);
        verify(host).setSelectedBuildLength(16);
        controller.changeBuildLength(player, Integer.MAX_VALUE);
        verify(host, times(2)).setSelectedBuildLength(16);
        controller.changeBuildLength(player, Integer.MIN_VALUE);
        verify(host, times(2)).setSelectedBuildLength(2);
    }

    @Test
    void deniedOrRunningBuildsRejectLengthAndMirrorChanges() {
        var host = mock(MultiBlockBuildController.Host.class);
        Player player = mock(Player.class);
        var controller = new MultiBlockBuildController(host);
        controller.setBuildLength(player, 4);
        controller.setMirrorBuild(player, true);
        when(host.canPlayerInteract(player)).thenReturn(true);
        when(host.isBuildInProgress()).thenReturn(true);
        controller.setBuildLength(player, 5);
        controller.setMirrorBuild(player, true);
        verify(host, never()).setSelectedBuildLength(anyInt());
        verify(host, never()).setMirrorBuild(anyBoolean());
        verify(host, never()).buildStateChanged();
    }

    @Test
    void startBuildUsesTheSelectedLengthAndMirrorForPlacement() {
        var host = mock(MultiBlockBuildController.Host.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        MultiBlockDefinition definition = mock(MultiBlockDefinition.class);
        var state = Blocks.COBBLESTONE.defaultBlockState();
        when(host.canPlayerInteract(player)).thenReturn(true);
        when(host.getBuildLevel()).thenReturn(level);
        when(host.getBuildPosition()).thenReturn(BlockPos.ZERO);
        when(host.getBuildState()).thenReturn(state);
        when(host.getBuildDefinition()).thenReturn(definition);
        when(host.getSelectedBuildLength()).thenReturn(7);
        when(host.isMirrorBuild()).thenReturn(true);
        when(definition.getExpandMin()).thenReturn(2);
        when(definition.getExpandMax()).thenReturn(16);
        when(player.isCreative()).thenReturn(true);
        var plan = new MultiBlockPlacementPlan(List.of(), List.of(), List.of(), List.of(), 0);
        try (var placement = mockStatic(MultiBlockPlacementService.class)) {
            placement.when(() -> MultiBlockPlacementService.preview(level, BlockPos.ZERO, state, definition, 7, true))
                .thenReturn(plan);
            new MultiBlockBuildController(host).autoBuild(player);
            placement.verify(() -> MultiBlockPlacementService.preview(level, BlockPos.ZERO, state, definition, 7, true));
            verify(host).rebuildAfterBuild();
            verify(player).closeContainer();
        }
    }
}
