package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin120 extends Entity {
    @Unique private static final Map<Block, Block> NEOECOAE_TRANSFORM_MAP = Map.of(
            AEBlocks.FLAWLESS_BUDDING_QUARTZ.block(), NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.FLAWED_BUDDING_QUARTZ.block(), NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.CHIPPED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get(),
            AEBlocks.DAMAGED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get());

    public LightningBoltMixin120(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "m_8119_",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/LightningBolt;m_147150_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"),
            require = 1)
    private void neoecoae$transformBuddingQuartz(CallbackInfo ci) {
        Level level = this.level();
        if (level.isClientSide()) {
            return;
        }

        BlockPos strikePos = neoecoae$getStrikePosition();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos targetPos = strikePos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(targetPos);
                    Block replacement = NEOECOAE_TRANSFORM_MAP.get(state.getBlock());
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

    @Unique private BlockPos neoecoae$getStrikePosition() {
        Vec3 pos = this.position();
        return BlockPos.containing(pos.x, pos.y - 1.0E-6D, pos.z);
    }
}
