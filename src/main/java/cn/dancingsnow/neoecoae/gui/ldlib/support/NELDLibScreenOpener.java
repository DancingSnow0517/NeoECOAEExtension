package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class NELDLibScreenOpener {

    public static InteractionResult openBlockEntityUi(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IUIHolder)) {
            return InteractionResult.PASS;
        }
        return BlockEntityUIFactory.INSTANCE.openUI(blockEntity, serverPlayer)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    public static InteractionResult openHeldItemUi(Player player, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return HeldItemUIFactory.INSTANCE.openUI(serverPlayer, hand)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    private NELDLibScreenOpener() {}
}
