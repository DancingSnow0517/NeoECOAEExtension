package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        if (sizeX > sizeZ) {
            return sizeX <= maxLength() && sizeY == 3 && sizeZ == 2;
        } else {
            return sizeZ <= maxLength() && sizeY == 3 && sizeX == 2;
        }
    }

    protected abstract int maxLength();

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

    protected static boolean validateCasing(
        ServerLevel level,
        BlockPos centerPos,
        Direction top,
        Direction down,
        Holder<Block> casing
    ) {
        if (!validateBlock(level, centerPos, BlockState::is, casing)) {
            return false;
        }
        if (!validateBlock(level, centerPos.relative(top), BlockState::is, casing)) {
            return false;
        }
        return validateBlock(level, centerPos.relative(down), BlockState::is, casing);
    }

    protected boolean validateInterface(
        ServerLevel level,
        BlockPos interfacePos,
        Direction top,
        Direction down,
        Holder<Block> interfaceType,
        Holder<Block> casingType
    ) {
        if (!validateBlock(level, interfacePos, BlockState::is, interfaceType)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, casingType)) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), BlockState::is, casingType);
    }

    protected static boolean ensureSameSurface(List<BlockPos> list) {
        int x = list.getFirst().getX();
        int y = list.getFirst().getY();
        int z = list.getFirst().getZ();
        boolean sameX = true;
        boolean sameY = true;
        boolean sameZ = true;
        for (BlockPos blockPos : list) {
            if (blockPos.getX() != x) {
                sameX = false;
            }
            if (blockPos.getY() != y) {
                sameY = false;
            }
            if (blockPos.getZ() != z) {
                sameZ = false;
            }
            x = blockPos.getX();
            y = blockPos.getY();
            z = blockPos.getZ();
        }
        return sameX || sameY || sameZ;
    }

    protected static DataResult<BlockPos> validateBlockLine(
        Level level,
        Direction expandDirection,
        BlockPos start,
        BiPredicate<BlockState, BlockPos> blockPredicate
    ) {
        if (!validateBlock(
            level,
            start,
            it -> blockPredicate.test(it, start)
        )) {
            return DataResult.error(NEClusterCalculator::fail);
        }
        BlockPos end = expandTowards(
            level,
            expandDirection,
            start,
            blockPredicate
        );
        if (end.equals(start)) {
            if (validateBlock(
                level,
                end,
                it -> blockPredicate.test(it, start)
            )) {
                return DataResult.success(end);
            }
        }
        return DataResult.success(end);
    }

    private static String fail(){
        return "";
    }
}
