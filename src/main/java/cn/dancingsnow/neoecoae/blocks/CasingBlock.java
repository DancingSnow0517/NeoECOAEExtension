package cn.dancingsnow.neoecoae.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.stream.Stream;

public class CasingBlock extends Block {
    public static final VoxelShape SHAPE = Stream.of(
        Block.box(0, 0, 0, 12, 4, 4),
        Block.box(0, 0, 4, 4, 4, 16),
        Block.box(4, 0, 12, 16, 4, 16),
        Block.box(12, 0, 0, 16, 4, 12),
        Block.box(12, 12, 0, 16, 16, 12),
        Block.box(0, 12, 0, 12, 16, 4),
        Block.box(0, 12, 4, 4, 16, 16),
        Block.box(4, 12, 12, 16, 16, 16),
        Block.box(0, 4, 0, 4, 12, 4),
        Block.box(0, 4, 12, 4, 12, 16),
        Block.box(12, 4, 12, 16, 12, 16),
        Block.box(12, 4, 0, 16, 12, 4),
        Block.box(4, 4, 4, 12, 12, 12)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    public CasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
