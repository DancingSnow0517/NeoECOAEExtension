package com.lowdragmc.lowdraglib2.gui.factory;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockUIMenuType {
    public interface BlockUI {
        ModularUI createUI(BlockUIHolder holder);
    }

    public static class BlockUIHolder {
        public final Player player;
        public final BlockPos pos;

        public BlockUIHolder(Player player) {
            this.player = player;
            this.pos = BlockPos.ZERO;
        }
    }

    /**
     * LDLib2 compatibility shim for legacy machine UIs that have not been ported to LDLib1 yet.
     */
    public static boolean openUI(ServerPlayer player, BlockPos pos) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof com.lowdragmc.lowdraglib.gui.modular.IUIHolder) {
            return com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory.INSTANCE.openUI(blockEntity, player);
        }
        return false;
    }
}
