package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegratedWorkstationOcclusionTest {
    private static ECOIntegratedWorkingStation workstation;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            workstation = new ECOIntegratedWorkingStation(BlockBehaviour.Properties.of());
            Registry.register(registry, "neoecoae_occlusion_test:workstation", workstation);
            workstation.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        } finally {
            registry.freeze();
        }
    }

    @Test
    void grassUndersideRemainsVisibleAboveTheRecessedScreen() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean working : new boolean[]{false, true}) {
                var state = workstation.defaultBlockState()
                    .setValue(ECOIntegratedWorkingStation.FACING, facing)
                    .setValue(ECOIntegratedWorkingStation.WORKING, working);
                var level = mock(BlockGetter.class);
                when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
                assertTrue(Block.shouldRenderFace(Blocks.GRASS_BLOCK.defaultBlockState(), level,
                    BlockPos.ZERO.above(), Direction.DOWN, BlockPos.ZERO), facing + " working=" + working);
                assertFalse(Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
            }
        }
    }

    @Test
    void opaqueNeighboursKeepTheirFacesAroundTheCutoutModel() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean formed : new boolean[]{false, true}) {
                for (boolean working : new boolean[]{false, true}) {
                    var state = workstation.defaultBlockState()
                        .setValue(ECOIntegratedWorkingStation.FACING, facing)
                        .setValue(ECOIntegratedWorkingStation.FORMED, formed)
                        .setValue(ECOIntegratedWorkingStation.WORKING, working);
                    var level = mock(BlockGetter.class);
                    when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
                    for (Direction direction : Direction.values()) {
                        assertTrue(Block.shouldRenderFace(Blocks.STONE.defaultBlockState(), level,
                            BlockPos.ZERO.relative(direction), direction.getOpposite(), BlockPos.ZERO),
                            facing + " formed=" + formed + " working=" + working + " neighbour=" + direction);
                    }
                }
            }
        }
    }

    @Test
    void formedControllerKeepsItsFullCollision() {
        var level = EmptyBlockGetter.INSTANCE;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = workstation.defaultBlockState()
                .setValue(ECOIntegratedWorkingStation.FACING, facing)
                .setValue(ECOIntegratedWorkingStation.FORMED, true);
            assertTrue(Block.isShapeFullBlock(state.getCollisionShape(level, BlockPos.ZERO)), facing.toString());
        }
    }
}
