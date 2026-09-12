package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ECOIntegratedWorkingStationDebugWandItem extends Item {
    public ECOIntegratedWorkingStationDebugWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
            || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos controllerPos = context.getClickedPos();
        if (!(level.getBlockState(controllerPos).getBlock() instanceof ECOIntegratedWorkingStation)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (!(blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            level,
            controllerPos,
            level.getBlockState(controllerPos),
            NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION,
            1,
            false
        );
        if (!plan.getConflictPositions().isEmpty()
            || !MultiBlockPlacementService.buildInstant(level, plan, player)) {
            return InteractionResult.FAIL;
        }
        controller.rebuildMultiblock();
        return InteractionResult.SUCCESS;
    }
}
