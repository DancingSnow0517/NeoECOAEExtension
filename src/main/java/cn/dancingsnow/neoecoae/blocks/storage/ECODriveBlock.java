package cn.dancingsnow.neoecoae.blocks.storage;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
        registerDefaultState(getStateDefinition()
                .any()
                .setValue(HAS_CELL, false)
                .setValue(FORMED, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (ECOStorageCells.isCellHandled(heldItem)
                && ECOStorageCells.getHandler(heldItem) != null
                && ECOStorageCells.getCellInventory(heldItem, null) != null) {
            if (level.getBlockEntity(pos) instanceof ECODriveBlockEntity be) {
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
        if (level.getBlockEntity(pos) instanceof ECODriveBlockEntity be) {
            if (be.getCellStack() != null && player.isShiftKeyDown()) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                ItemStack cellStack = be.getCellStack().copyWithCount(1);
                be.setCellStack(null);
                giveCellToPlayer(player, hand, cellStack);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    private static void giveCellToPlayer(Player player, InteractionHand hand, ItemStack cellStack) {
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, cellStack);
        } else if (!player.getInventory().add(cellStack)) {
            player.drop(cellStack, false);
        }
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
