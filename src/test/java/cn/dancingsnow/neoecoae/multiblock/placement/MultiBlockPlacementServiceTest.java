package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiBlockPlacementServiceTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void conflictsPreventPlacementEvenInCreative() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        var plan = new MultiBlockPlacementPlan(List.of(), List.of(), List.of(BlockPos.ZERO), List.of(), 0);

        assertFalse(MultiBlockPlacementService.buildInstant(level, plan, player));
        verifyNoInteractions(level, player);
    }

    @Test
    void insufficientMaterialsPreventAnyWorldChanges() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        var block = new WorldPlannedBlock(BlockPos.ZERO, Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE));
        var plan = new MultiBlockPlacementPlan(List.of(block), List.of(block), List.of(),
            List.of(new RequiredItem(new ItemStack(Items.COBBLESTONE), 1)), 0);

        assertFalse(MultiBlockPlacementService.buildInstant(level, plan, player));
        verifyNoInteractions(level);
        assertTrue(inventory.isEmpty());
    }

    @Test
    void blockPlacedSincePreviewIsReusedWithoutConsumingMaterials() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player);
        inventory.setItem(0, new ItemStack(Items.COBBLESTONE));
        when(player.getInventory()).thenReturn(inventory);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        WorldBorder border = mock(WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(BlockPos.ZERO)).thenReturn(true);
        when(level.mayInteract(player, BlockPos.ZERO)).thenReturn(true);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.COBBLESTONE.defaultBlockState());
        var block = new WorldPlannedBlock(BlockPos.ZERO, Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE));
        var plan = new MultiBlockPlacementPlan(List.of(block), List.of(block), List.of(),
            List.of(new RequiredItem(new ItemStack(Items.COBBLESTONE), 1)), 0);

        assertTrue(MultiBlockPlacementService.buildInstant(level, plan, player));
        assertEquals(1, inventory.getItem(0).getCount());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
