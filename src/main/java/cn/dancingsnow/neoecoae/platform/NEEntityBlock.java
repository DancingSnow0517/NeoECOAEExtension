package cn.dancingsnow.neoecoae.platform;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Adapts the source version's split block interaction hooks to AE2 15's activation contract. */
public abstract class NEEntityBlock<T extends AEBaseBlockEntity> extends AEBaseEntityBlock<T> {
    protected NEEntityBlock(Properties properties) { super(properties); }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            ItemStack heldItem, BlockHitResult hit) {
        BlockState state = level.getBlockState(pos);
        if (heldItem != null && !heldItem.isEmpty()) {
            InteractionResult itemResult = useItemOn(heldItem, state, level, pos, player, hand, hit);
            if (itemResult != InteractionResult.PASS) return itemResult;
        }
        return hand == InteractionHand.MAIN_HAND ? useWithoutItem(state, level, pos, player, hit)
            : InteractionResult.PASS;
    }

    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
