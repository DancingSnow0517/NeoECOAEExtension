package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiBlockPlacementServiceTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        NeoForge.EVENT_BUS.start();
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
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
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
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
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

    @ParameterizedTest
    @MethodSource("buildModesAndFacings")
    void placementEventRotationIsRepairedWithoutResettingDerivedState(Direction facing, boolean creative) {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, new ItemStack(Items.FURNACE, 2));
        when(player.getInventory()).thenReturn(inventory);
        when(player.isCreative()).thenReturn(creative);
        Map<BlockPos, BlockState> world = new HashMap<>();
        ServerLevel level = placementLevel(player, world);
        BlockState target = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        var block = new WorldPlannedBlock(BlockPos.ZERO, target, new ItemStack(Items.FURNACE));
        AtomicInteger eventCount = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() != level) return;
            eventCount.incrementAndGet();
            level.setBlock(event.getPos(), target
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise())
                .setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            if (creative) {
                var plan = new MultiBlockPlacementPlan(List.of(block), List.of(block), List.of(), List.of(), 0);
                assertTrue(MultiBlockPlacementService.buildInstant(level, plan, player));
            } else {
                var session = new MultiBlockBuildSession(List.of(block), 0);
                assertEquals(MultiBlockPlacementService.PlacementTickResult.COMPLETED,
                    MultiBlockPlacementService.tickBuild(level, session, player));
            }
            BlockState actual = level.getBlockState(BlockPos.ZERO);
            assertEquals(facing, actual.getValue(BlockStateProperties.HORIZONTAL_FACING));
            assertTrue(actual.getValue(BlockStateProperties.LIT));
            assertEquals(creative ? 2 : 1, inventory.getItem(0).getCount());
            assertEquals(1, eventCount.get());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    private static Stream<Arguments> buildModesAndFacings() {
        return Stream.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
            .flatMap(facing -> Stream.of(false, true).map(creative -> Arguments.of(facing, creative)));
    }

    @Test
    void cancelledPlacementIsRolledBackWithoutConsumingMaterials() {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, new ItemStack(Items.FURNACE));
        when(player.getInventory()).thenReturn(inventory);
        ServerLevel level = placementLevel(player, new HashMap<>());
        var block = new WorldPlannedBlock(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState(), new ItemStack(Items.FURNACE));
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() != level) return;
            level.setBlock(event.getPos(), block.targetState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            assertEquals(MultiBlockPlacementService.PlacementTickResult.BLOCKED,
                MultiBlockPlacementService.tickBuild(level, new MultiBlockBuildSession(List.of(block), 0), player));
            assertTrue(level.getBlockState(BlockPos.ZERO).isAir());
            assertEquals(1, inventory.getItem(0).getCount());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    @Test
    void incompatibleReplacementIsRolledBackWithoutConsumingMaterials() {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, new ItemStack(Items.FURNACE));
        when(player.getInventory()).thenReturn(inventory);
        ServerLevel level = placementLevel(player, new HashMap<>());
        var block = new WorldPlannedBlock(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState(), new ItemStack(Items.FURNACE));
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == level) level.setBlock(event.getPos(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            assertEquals(MultiBlockPlacementService.PlacementTickResult.BLOCKED,
                MultiBlockPlacementService.tickBuild(level, new MultiBlockBuildSession(List.of(block), 0), player));
            assertTrue(level.getBlockState(BlockPos.ZERO).isAir());
            assertEquals(1, inventory.getItem(0).getCount());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    @Test
    void acceptedReplacementRetainsItsOwnState() {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, new ItemStack(Items.FURNACE));
        when(player.getInventory()).thenReturn(inventory);
        ServerLevel level = placementLevel(player, new HashMap<>());
        BlockState target = Blocks.FURNACE.defaultBlockState();
        BlockState replacement = Blocks.BLAST_FURNACE.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST).setValue(BlockStateProperties.LIT, true);
        var block = new WorldPlannedBlock(BlockPos.ZERO, target, new ItemStack(Items.FURNACE),
            ECOMultiblockRequirement.of(target, replacement));
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == level) level.setBlock(event.getPos(), replacement, Block.UPDATE_ALL);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            assertEquals(MultiBlockPlacementService.PlacementTickResult.COMPLETED,
                MultiBlockPlacementService.tickBuild(level, new MultiBlockBuildSession(List.of(block), 0), player));
            assertSame(replacement, level.getBlockState(BlockPos.ZERO));
            assertTrue(inventory.getItem(0).isEmpty());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    @Test
    void persistentDirectionOverrideStopsTheBuildWithoutConsumingMaterials() {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        inventory.setItem(0, new ItemStack(Items.FURNACE));
        when(player.getInventory()).thenReturn(inventory);
        Map<BlockPos, BlockState> world = new HashMap<>();
        ServerLevel level = placementLevel(player, world);
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(call -> {
            BlockState state = call.getArgument(1);
            if (state.is(Blocks.FURNACE)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            return world.put(((BlockPos) call.getArgument(0)).immutable(), state) != state;
        });
        var block = new WorldPlannedBlock(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState(), new ItemStack(Items.FURNACE));

        assertEquals(MultiBlockPlacementService.PlacementTickResult.BLOCKED,
            MultiBlockPlacementService.tickBuild(level, new MultiBlockBuildSession(List.of(block), 0), player));
        assertTrue(level.getBlockState(BlockPos.ZERO).isAir());
        assertEquals(1, inventory.getItem(0).getCount());
    }

    private static ServerLevel placementLevel(ServerPlayer player, Map<BlockPos, BlockState> world) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.mayInteract(player, BlockPos.ZERO)).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call ->
            world.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(call -> {
            BlockState state = call.getArgument(1);
            return world.put(((BlockPos) call.getArgument(0)).immutable(), state) != state;
        });
        return level;
    }
}
