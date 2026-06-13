package cn.dancingsnow.neoecoae.blocks;

import appeng.block.AEBaseEntityBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

public abstract class NEBlock<T extends NEBlockEntity<?, T>> extends AEBaseEntityBlock<T> {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected NEBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(FORMED) ? 1 : 0));
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        final T cp = this.getBlockEntity(level, pos);
        if (cp != null) {
            cp.breakCluster();
        }

        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        final NEBlockEntity<?, T> be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.updateMultiBlock(pos);
        }
    }
}
