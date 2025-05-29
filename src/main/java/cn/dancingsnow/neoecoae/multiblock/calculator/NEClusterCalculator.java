package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

public abstract class NEClusterCalculator<C extends NECluster<C>> extends MBCalculator<NEBlockEntity<C, ?>, C> {
    public NEClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateBlockEntities(C c, ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) level.getBlockEntity(blockPos);
            if (blockEntity == null) {
                throw new IllegalStateException("Expected NEBlockEntity at %s, but got null.".formatted(blockPos));
            }
            blockEntity.updateCluster(c);
            c.addBlockEntity(blockEntity);
        }

        c.updateFormed(true);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return te instanceof NEBlockEntity<?, ?>;
    }

    @FunctionalInterface
    public interface Factory<C extends NECluster<C>> {
        NEClusterCalculator<C> create(NEBlockEntity<C, ?> blockEntity);
    }

    public static <T> boolean validateBlock(Level level, BlockPos pos, BiPredicate<BlockState, T> fn, T value) {
        return fn.test(level.getBlockState(pos), value);
    }

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, Holder<Block> type) {
        return expandTowards(level, direction, start, type.value());
    }

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, Block type) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        while (
            level.getBlockState(
                new BlockPos(
                    mutable.getX() + direction.getStepX(),
                    mutable.getY() + direction.getStepY(),
                    mutable.getZ() + direction.getStepZ()
                )
            ).is(type)
        ) {
            mutable.set(
                mutable.getX() + direction.getStepX(),
                mutable.getY() + direction.getStepY(),
                mutable.getZ() + direction.getStepZ()
            );
        }
        return mutable;
    }

    public static <T> boolean validateBlocks(Level level, BlockPos from, BlockPos to, BiPredicate<BlockState, T> fn, T value) {
        for (BlockPos blockPos : BlockPos.betweenClosed(from, to)) {
            if (!fn.test(level.getBlockState(blockPos), value)) {
                return false;
            }
        }
        return true;
    }
}
