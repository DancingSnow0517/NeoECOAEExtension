package cn.dancingsnow.neoecoae.multiblock.placement;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public final class MultiBlockItemFormResolver {
    private MultiBlockItemFormResolver() {}

    public static ItemStack requiredItem(BlockState blockState) {
        if (blockState == null || blockState.isAir()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = BlockInfo.fromBlockState(blockState).getItemStackForm();
        if (isUsable(stack)) {
            return stack.copyWithCount(1);
        }

        stack = blockState.getBlock().asItem().getDefaultInstance();
        if (isUsable(stack)) {
            return stack.copyWithCount(1);
        }

        return ItemStack.EMPTY;
    }

    private static boolean isUsable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() != Items.AIR;
    }
}
