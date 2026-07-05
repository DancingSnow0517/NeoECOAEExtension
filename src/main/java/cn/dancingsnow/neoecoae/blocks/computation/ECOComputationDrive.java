package cn.dancingsnow.neoecoae.blocks.computation;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

public class ECOComputationDrive extends NEBlock<ECOComputationDriveBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_CELL = BooleanProperty.create("has_cell");

    public ECOComputationDrive(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition()
                .any()
                .setValue(FORMED, false)
                .setValue(HAS_CELL, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.getItem() instanceof ECOComputationCellItem) {
            if (level.getBlockEntity(pos) instanceof ECOComputationDriveBlockEntity be) {
                if (be.getCellStack() == null) {
                    if (level.isClientSide) return InteractionResult.SUCCESS;
                    be.setCellStack(heldItem.copyWithCount(1));
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof ECOComputationDriveBlockEntity be) {
            if (be.getCellStack() != null && player.isShiftKeyDown()) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                if (!be.canExtractCell()) {
                    player.displayClientMessage(
                            Component.translatable("gui.neoecoae.computation.cell_locked_active_job"), true);
                    return InteractionResult.CONSUME;
                }
                ItemStack cellStack = be.getCellStack();
                be.setCellStack(null);
                ItemHandlerHelper.giveItemToPlayer(player, cellStack);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_CELL);
    }
}
