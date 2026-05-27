package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.mojang.serialization.DataResult;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public abstract class NEClusterCalculator<C extends NECluster<C>> extends MBCalculator<NEBlockEntity<C, ?>, C> {

    public NEClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
    }

    @Override
    public void calculateMultiblock(ServerLevel level, BlockPos pos) {
        super.calculateMultiblock(level, pos);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateBlockEntities(C c, ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            BlockEntity rawBlockEntity = level.getBlockEntity(blockPos);
            if (!isValidBlockEntity(rawBlockEntity)) {
                this.disconnect();
                return;
            }
            @SuppressWarnings("unchecked")
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) rawBlockEntity;
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

        boolean valid;
        if (sizeX > sizeZ) {
            valid = sizeX <= maxLength() && sizeY == 3 && sizeZ == 2;
        } else {
            valid = sizeZ <= maxLength() && sizeY == 3 && sizeX == 2;
        }
        return valid;
    }

    protected abstract int maxLength();

    /** No-op: debug logging removed. Subclasses may still call this safely. */
    protected void logVerifyFailure(ServerLevel level, String step, BlockPos pos,
                                     String expected, @Nullable Direction expectedFacing) {
    }

    /** No-op: debug logging removed. Subclasses may still call this safely. */
    protected void logVerifyFailure(ServerLevel level, @Nullable BlockPos min, @Nullable BlockPos max,
                                     String step, BlockPos pos, String expected,
                                     @Nullable Direction expectedFacing) {
    }

    /** No-op: debug logging removed. Subclasses may still call this safely. */
    protected void logVerifyContext(ServerLevel level, BlockPos min, BlockPos max,
                                     BlockPos controllerPos, BlockState controllerState,
                                     Direction front, Direction back, Direction left, Direction right,
                                     Direction top, Direction down) {
    }

    /** No-op: debug logging removed. Subclasses may still call this safely. */
    protected void logLineProbe(ServerLevel level, String step, BlockPos start,
                                 Direction direction, int maxSamples) {
    }

    /** No-op: debug logging removed. Subclasses may still call this safely. */
    protected void logLineProbe(ServerLevel level, @Nullable BlockPos min, @Nullable BlockPos max,
                                 String step, BlockPos start, Direction direction, int maxSamples) {
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
        if (!validateBlock(level, centerPos, (state, block) -> state.is(block.value()), casing)) {
            return false;
        }
        if (!validateBlock(level, centerPos.relative(top), (state, block) -> state.is(block.value()), casing)) {
            return false;
        }
        return validateBlock(level, centerPos.relative(down), (state, block) -> state.is(block.value()), casing);
    }

    protected static boolean validateCasing(
        ServerLevel level,
        BlockPos centerPos,
        Direction top,
        Direction down,
        BlockEntry<? extends Block> casing
    ) {
        return validateCasing(level, centerPos, top, down, casing.get().builtInRegistryHolder());
    }

    protected boolean validateInterface(
        ServerLevel level,
        BlockPos interfacePos,
        Direction top,
        Direction down,
        Holder<Block> interfaceType,
        Holder<Block> casingType
    ) {
        if (!validateBlock(level, interfacePos, (state, block) -> state.is(block.value()), interfaceType)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), (state, block) -> state.is(block.value()), casingType)) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), (state, block) -> state.is(block.value()), casingType);
    }

    protected boolean validateInterface(
        ServerLevel level,
        BlockPos interfacePos,
        Direction top,
        Direction down,
        BlockEntry<? extends Block> interfaceType,
        BlockEntry<? extends Block> casingType
    ) {
        return validateInterface(
            level,
            interfacePos,
            top,
            down,
            interfaceType.get().builtInRegistryHolder(),
            casingType.get().builtInRegistryHolder()
        );
    }

    protected static boolean ensureSameSurface(List<BlockPos> list) {
        int x = list.get(0).getX();
        int y = list.get(0).getY();
        int z = list.get(0).getZ();
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

    private static String fail() {
        return "";
    }
}
