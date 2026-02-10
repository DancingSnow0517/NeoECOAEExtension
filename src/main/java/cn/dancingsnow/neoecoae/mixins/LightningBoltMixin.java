package cn.dancingsnow.neoecoae.mixins;

import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin extends Entity {

    @Unique
    private static final Map<Block, Block> TRANSFORM_MAP = Map.of(
        AEBlocks.FLAWLESS_BUDDING_QUARTZ.block(), NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL.get(),
        AEBlocks.FLAWED_BUDDING_QUARTZ.block(), NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get(),
        AEBlocks.CHIPPED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get(),
        AEBlocks.DAMAGED_BUDDING_QUARTZ.block(), NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get()
    );

    public LightningBoltMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    protected abstract BlockPos getStrikePosition();

    @Inject(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;clearCopperOnLightningStrike(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
    )
    private void onLightningLand(CallbackInfo ci) {
        Level level = this.level();
        BlockPos landPos = getStrikePosition();
        for (int dx = -1; dx<=1;dx++) {
            for (int dy = -1; dy<=1;dy++) {
                for (int dz = -1; dz<=1;dz++) {
                    BlockPos offset = landPos.offset(dx, dy, dz);
                    BlockState blockState = level.getBlockState(offset);
                    if (TRANSFORM_MAP.containsKey(blockState.getBlock())) {
                        int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        int chance = 2 + distance;
                        if (level.getRandom().nextInt(chance) == 0) {
                            level.setBlockAndUpdate(offset, TRANSFORM_MAP.get(blockState.getBlock()).defaultBlockState());
                        }
                    }
                }
            }
        }
    }
}
