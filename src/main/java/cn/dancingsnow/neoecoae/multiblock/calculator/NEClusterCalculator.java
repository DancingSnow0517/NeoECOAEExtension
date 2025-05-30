package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
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
import java.util.function.Predicate;

public abstract class NEClusterCalculator<C extends NECluster<C>> extends MBCalculator<NEBlockEntity<C, ?>, C> {
    public NEClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateBlockEntities(C c, ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) level.getBlockEntity(blockPos);
            if (blockEntity == null) {
                throw new IllegalStateException("Expected NEBlockEntity at %s, but got null.".formatted(blockPos));
            }
            c.addBlockEntity(blockEntity);
        }
        c.getBlockEntities().forEachRemaining(it -> it.updateCluster(c));
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

    public static <T> boolean validateBlock(Level level, BlockPos pos, Predicate<BlockState> fn) {
        return fn.test(level.getBlockState(pos));
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

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, Predicate<BlockState> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        while (
            fn.test(level.getBlockState(
                new BlockPos(
                    mutable.getX() + direction.getStepX(),
                    mutable.getY() + direction.getStepY(),
                    mutable.getZ() + direction.getStepZ()
                )
            ))
        ) {
            mutable.set(
                mutable.getX() + direction.getStepX(),
                mutable.getY() + direction.getStepY(),
                mutable.getZ() + direction.getStepZ()
            );
        }
        return mutable;
    }

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, BiPredicate<BlockState, BlockPos> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        BlockPos pos = new BlockPos(
            mutable.getX() + direction.getStepX(),
            mutable.getY() + direction.getStepY(),
            mutable.getZ() + direction.getStepZ()
        );
        while (
            fn.test(level.getBlockState(pos), pos)
        ) {
            mutable.set(
                mutable.getX() + direction.getStepX(),
                mutable.getY() + direction.getStepY(),
                mutable.getZ() + direction.getStepZ()
            );
            pos = new BlockPos(
                mutable.getX() + direction.getStepX(),
                mutable.getY() + direction.getStepY(),
                mutable.getZ() + direction.getStepZ()
            );
        }
        return mutable;
    }

    public static boolean validateBlocks(Level level, BlockPos from, BlockPos to, Predicate<BlockState> fn) {
        for (BlockPos blockPos : BlockPos.betweenClosed(from, to)) {
            if (!fn.test(level.getBlockState(blockPos))) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean validateBlocks(Level level, Iterable<BlockPos> iterable, BiPredicate<BlockState, T> fn, T value) {
        for (BlockPos blockPos : iterable) {
            if (!fn.test(level.getBlockState(blockPos), value)) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean validateBlocks(Level level, BlockPos from, BlockPos to, BiPredicate<BlockState, T> fn, T value) {

        return validateBlocks(level, BlockPos.betweenClosed(from, to), fn, value);
    }
}
