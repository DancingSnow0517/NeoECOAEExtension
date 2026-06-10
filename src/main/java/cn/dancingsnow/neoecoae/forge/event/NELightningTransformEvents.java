package cn.dancingsnow.neoecoae.forge.event;

import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public final class NELightningTransformEvents {
    private static final Map<Block, Block> TRANSFORM_MAP = Map.of(
            AEBlocks.FLAWLESS_BUDDING_QUARTZ.block(), NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.FLAWED_BUDDING_QUARTZ.block(), NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.CHIPPED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.DAMAGED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get());

    private NELightningTransformEvents() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled()
                || event.loadedFromDisk()
                || !(event.getEntity() instanceof LightningBolt lightning)
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 strikePosition = lightning.position();
        serverLevel.getServer().executeIfPossible(() -> transformBuddingQuartz(serverLevel, strikePosition));
    }

    private static void transformBuddingQuartz(ServerLevel level, Vec3 strikePosition) {
        BlockPos strikePos = getStrikePosition(strikePosition);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos targetPos = strikePos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(targetPos);
                    Block replacement = TRANSFORM_MAP.get(state.getBlock());
                    if (replacement == null) {
                        continue;
                    }

                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (level.getRandom().nextInt(2 + distance) == 0) {
                        level.setBlockAndUpdate(targetPos, replacement.defaultBlockState());
                    }
                }
            }
        }
    }

    private static BlockPos getStrikePosition(Vec3 pos) {
        return BlockPos.containing(pos.x, pos.y - 1.0E-6D, pos.z);
    }
}
