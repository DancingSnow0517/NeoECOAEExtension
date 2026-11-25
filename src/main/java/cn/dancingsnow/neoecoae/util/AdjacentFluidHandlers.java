package cn.dancingsnow.neoecoae.util;

import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class AdjacentFluidHandlers {
    private final EnumMap<Direction, Side> sides = new EnumMap<>(Direction.class);

    @Nullable
    public IFluidHandler get(Level level, BlockPos origin, Direction face) {
        BlockPos target = origin.relative(face);
        if (!level.hasChunkAt(target)) {
            sides.remove(face);
            return null;
        }
        BlockEntity entity = level.getBlockEntity(target);
        if (entity == null || entity.isRemoved()) {
            sides.remove(face);
            return null;
        }
        Side existing = sides.get(face);
        if (existing != null && existing.owner == entity && existing.handler.isPresent()) {
            return existing.handler.orElse(null);
        }
        LazyOptional<IFluidHandler> handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
        if (handler.isPresent()) sides.put(face, new Side(entity, handler));
        else sides.remove(face);
        return handler.orElse(null);
    }

    private record Side(BlockEntity owner, LazyOptional<IFluidHandler> handler) {}
}
