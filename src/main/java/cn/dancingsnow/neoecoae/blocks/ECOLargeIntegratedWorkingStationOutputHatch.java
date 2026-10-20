package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ECOLargeIntegratedWorkingStationOutputHatch
    extends NEBlock<ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> {
    public ECOLargeIntegratedWorkingStationOutputHatch(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hideWhenFormed() {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof ECOLargeIntegratedWorkingStationOutputHatchBlockEntity blockEntity) {
                BlockEntityUIFactory.INSTANCE.openUI(blockEntity, serverPlayer);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

}
