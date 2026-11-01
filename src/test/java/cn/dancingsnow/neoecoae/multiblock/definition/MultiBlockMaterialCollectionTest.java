package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiBlockMaterialCollectionTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void materialsMatchTheSceneWithoutAllocatingAnotherWorldOrRunningFormation() {
        var definition = MultiBlockDefinition.builder(Holder.direct(Blocks.STONE))
            .setBlock(new BlockPos(0, -1, 0), Blocks.GLASS.defaultBlockState())
            .setBlockRepeatable(BlockPos.ZERO, Direction.EAST, Blocks.STONE.defaultBlockState())
            .setBlockWithRepeatShifted(BlockPos.ZERO, Direction.EAST, 0, Blocks.GLASS.defaultBlockState())
            .onFormed((pos, world) -> fail("Material collection must not form a structure"))
            .expandMax(Integer.MAX_VALUE)
            .create(ignored -> {});
        var scene = MultiBlockContext.dummyDelegated(3, mock(TrackedDummyWorld.class));
        definition.createLevel(scene);
        try (var worlds = mockConstruction(TrackedDummyWorld.class)) {
            var materials = definition.getRequiredItems(3);
            assertEquals(scene.getRequiredItems().size(), materials.size());
            for (int i = 0; i < materials.size(); i++) {
                assertEquals(scene.getRequiredItems().get(i).count(), materials.get(i).count());
                assertSame(scene.getRequiredItems().get(i).stack().getItem(), materials.get(i).stack().getItem());
            }
            assertEquals(2, materials.get(0).count());
            assertEquals(3, materials.get(1).count());
            assertTrue(worlds.constructed().isEmpty());
        }
    }

    @Test
    void materialCollectionDoesNotKeepBlockPositions() {
        var context = new MultiBlockContext.MaterialsOnly(1);
        context.setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        context.setBlock(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
        context.setBlock(new BlockPos(2, 0, 0), Blocks.AIR.defaultBlockState());
        assertTrue(context.allBlocks().isEmpty());
        assertEquals(1, context.getRequiredItems().size());
        assertEquals(2, context.getRequiredItems().get(0).count());
    }
}
