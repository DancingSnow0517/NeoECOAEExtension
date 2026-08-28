package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
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
            BlockEntity candidate = level.getBlockEntity(blockPos);
            if (!(candidate instanceof NEBlockEntity<?, ?>) || !isValidBlockEntity(candidate)) {
                this.disconnect();
                return;
            }
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) candidate;
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

    protected abstract Holder<Block> casing();

    @FunctionalInterface
    public interface Factory<C extends NECluster<C>> {
        NEClusterCalculator<C> create(NEBlockEntity<C, ?> blockEntity);
    }

    public static boolean validateBlock(Level level, BlockPos pos, Predicate<BlockState> fn) {
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
        // Reuse the mutable cursor while probing long structures to avoid one allocation per block.
        while (level.getBlockState(mutable.relative(direction)).is(type)) {
            mutable.move(direction);
        }
        return mutable.immutable();
    }

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, Predicate<BlockState> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        while (fn.test(level.getBlockState(mutable.relative(direction)))) {
            mutable.move(direction);
        }
        return mutable.immutable();
    }

    public static BlockPos expandTowards(Level level, Direction direction, BlockPos start, BiPredicate<BlockState, BlockPos> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        BlockPos pos = mutable.relative(direction);
        while (fn.test(level.getBlockState(pos), pos)) {
            mutable.move(direction);
            pos = mutable.relative(direction);
        }
        return mutable.immutable();
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

    protected final boolean validateCasing(
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

    protected final boolean validateCasing(
        ServerLevel level,
        BlockPos centerPos,
        Direction top,
        Direction down
    ) {
        return validateCasing(level, centerPos, top, down, casing());
    }

    protected final boolean validateCasing(
        ServerLevel level,
        BlockPos origin,
        Direction top,
        Direction down,
        Direction direction
    ) {
        return validateCasing(level, origin.relative(direction), top, down);
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

    protected static Optional<BlockPos> validateBlockLine(
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
            return Optional.empty();
        }
        BlockPos end = expandTowards(
            level,
            expandDirection,
            start,
            blockPredicate
        );
        return Optional.of(end);
    }

    protected final <T extends NEBlockEntity<?, ?>> Optional<ControllerContext<T>> findUniqueController(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        Class<T> controllerType
    ) {
        Set<BlockPos> validControllerPositions = MultiBlockUtil.allPossibleController(min, max);
        Optional<T> controller = findUnique(
            BlockPos.betweenClosed(min, max),
            pos -> controllerType.isInstance(level.getBlockEntity(pos))
                ? controllerType.cast(level.getBlockEntity(pos))
                : null
        );
        if (controller.isEmpty()) {
            return Optional.empty();
        }

        T blockEntity = controller.orElseThrow();
        if (!validControllerPositions.contains(blockEntity.getBlockPos())) {
            return Optional.empty();
        }
        BlockState state = blockEntity.getBlockState();
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(state, RelativeSide.BACK);
        Direction top = strategy.getSide(state, RelativeSide.TOP);
        Direction left = strategy.getSide(state, RelativeSide.LEFT);
        return Optional.of(new ControllerContext<>(
            blockEntity,
            blockEntity.getBlockPos(),
            state,
            back.getOpposite(),
            back,
            top,
            top.getOpposite(),
            left,
            left.getOpposite()
        ));
    }

    static <T> Optional<T> findUnique(Iterable<BlockPos> positions, Function<BlockPos, T> finder) {
        T found = null;
        for (BlockPos pos : positions) {
            T candidate = finder.apply(pos);
            if (candidate == null) {
                continue;
            }
            if (found != null) {
                return Optional.empty();
            }
            found = candidate;
        }
        return Optional.ofNullable(found);
    }

    protected static BiPredicate<BlockState, BlockPos> matchingStateFacing(
        Holder<Block> block,
        Direction facing
    ) {
        return (state, pos) -> state.is(block)
            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    protected record ControllerContext<T extends NEBlockEntity<?, ?>>(
        T controller,
        BlockPos position,
        BlockState state,
        Direction front,
        Direction back,
        Direction top,
        Direction down,
        Direction left,
        Direction right
    ) {
    }
}
