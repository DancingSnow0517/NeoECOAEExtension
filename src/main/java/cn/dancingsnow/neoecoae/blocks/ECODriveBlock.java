package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ECODriveBlock extends NEBlock<ECODriveBlockEntity> {
    public static final BooleanProperty HAS_CELL = BooleanProperty.create("has_cell");

    public ECODriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(HAS_CELL, false)
            .setValue(FORMED, false)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        );
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (heldItem.getItem() instanceof ECOStorageCellItem) {
            if (level.getBlockEntity(pos) instanceof ECODriveBlockEntity be) {
                if (be.getCellStack() == null) {
                    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
                    be.setCellStack(heldItem);
                    player.setItemInHand(hand, ItemStack.EMPTY);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ECODriveBlockEntity be) {
            if (be.getCellStack() != null && player.isShiftKeyDown()) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                ItemStack cellStack = be.getCellStack();
                be.setCellStack(null);
                player.setItemInHand(InteractionHand.MAIN_HAND, cellStack);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_CELL);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
