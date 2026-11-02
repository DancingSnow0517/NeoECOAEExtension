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
        registry.unfreeze(false);
        try {
            workstation = new ECOIntegratedWorkingStation(BlockBehaviour.Properties.of().setId(blockKey("neoecoae_occlusion_test:workstation")));
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
                assertTrue(Block.shouldRenderFace(level, BlockPos.ZERO.above(),
                    Blocks.GRASS_BLOCK.defaultBlockState(), state, Direction.DOWN), facing + " working=" + working);
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
                        assertTrue(Block.shouldRenderFace(level, BlockPos.ZERO.relative(direction),
                            Blocks.STONE.defaultBlockState(), state, direction.getOpposite()),
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
    private static net.minecraft.resources.ResourceKey<Block> blockKey(String id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.Identifier.parse(id));
    }
}
