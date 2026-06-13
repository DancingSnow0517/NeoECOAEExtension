package cn.dancingsnow.neoecoae.blocks.computation;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ECOComputationDrive extends NEBlock<ECOComputationDriveBlockEntity> {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ECOComputationDrive(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.getItem() instanceof ECOComputationCellItem) {
            if (level.getBlockEntity(pos) instanceof ECOComputationDriveBlockEntity be) {
                if (be.getCellStack() == null) {
                    if (level.isClientSide()) return InteractionResult.SUCCESS;
                    be.setCellStack(heldItem);
                    player.setItemInHand(hand, ItemStack.EMPTY);
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ECOComputationDriveBlockEntity be) {
            if (be.getCellStack() != null && player.isShiftKeyDown()) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                ItemStack cellStack = be.getCellStack();
                be.setCellStack(null);
                player.setItemInHand(InteractionHand.MAIN_HAND, cellStack);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
