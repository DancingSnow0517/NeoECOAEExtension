package cn.dancingsnow.neoecoae.util;

import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
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
    public ResourceHandler<FluidResource> get(ServerLevel level, BlockPos origin, Direction direction) {
        candidateFaces(level);
        if ((candidateFaces & (1 << direction.ordinal())) == 0) return null;
        var side = sides.computeIfAbsent(direction, face -> new Side(level, origin, face));
        if (side.dirty) {
            side.handler = side.cache.getCapability();
            side.dirty = false;
            if (side.handler == null) candidateFaces &= ~(1 << direction.ordinal());
        }
        return side.handler;
    }

    private final class Side {
        private final BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache;
        private boolean dirty = true;
        private ResourceHandler<FluidResource> handler;

        private Side(ServerLevel level, BlockPos origin, Direction direction) {
            cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, origin.relative(direction),
                direction.getOpposite(), () -> AdjacentFluidHandlers.this.level == level, () -> {
                    handler = null;
                    dirty = true;
                    candidateFaces |= 1 << direction.ordinal();
                });
        }
    }
}
