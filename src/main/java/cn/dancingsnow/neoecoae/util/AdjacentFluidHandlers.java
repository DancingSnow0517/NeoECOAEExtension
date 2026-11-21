package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import cn.dancingsnow.neoecoae.storage.StoragePerformance.Counter;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class AdjacentFluidHandlers {
    private static final Direction[] FACES = Direction.values();
    private final EnumMap<Direction, Side> sides = new EnumMap<>(Direction.class);
    private ServerLevel level;
    private int candidateFaces = (1 << FACES.length) - 1;

    public int candidateFaces(ServerLevel level) {
        if (this.level != level) {
            this.level = level;
            sides.clear();
            candidateFaces = (1 << FACES.length) - 1;
        }
        return candidateFaces;
    }

    public static Direction firstFace(int faces) {
        return FACES[Integer.numberOfTrailingZeros(faces)];
    }

    public static int mask(Iterable<Direction> directions) {
        int mask = 0;
        for (var direction : directions) mask |= 1 << direction.ordinal();
        return mask;
    }

    @Nullable
    public IFluidHandler get(ServerLevel level, BlockPos origin, Direction direction) {
        StoragePerformance.count(Counter.HATCH_FACE_VISIT);
        var side = sides.computeIfAbsent(direction, face -> new Side(level, origin, face));
        if (side.dirty) {
            StoragePerformance.count(Counter.HATCH_CAPABILITY_REFRESH);
            side.handler = side.cache.getCapability();
            side.dirty = false;
            if (side.handler == null) candidateFaces &= ~(1 << direction.ordinal());
        }
        return side.handler;
    }

    private final class Side {
        private final BlockCapabilityCache<IFluidHandler, Direction> cache;
        private boolean dirty = true;
        private IFluidHandler handler;

        private Side(ServerLevel level, BlockPos origin, Direction direction) {
            cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, level, origin.relative(direction),
                direction.getOpposite(), () -> AdjacentFluidHandlers.this.level == level, () -> {
                    handler = null;
                    dirty = true;
                    candidateFaces |= 1 << direction.ordinal();
                });
        }
    }
}
