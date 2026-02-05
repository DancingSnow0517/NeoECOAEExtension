package cn.dancingsnow.neoecoae.blocks.crafting;

import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ECOFluidInputHatchBlock extends NEBlock<ECOFluidInputHatchBlockEntity> implements BlockUIMenuType.BlockUI {
    public ECOFluidInputHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            BlockUIMenuType.openUI(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (state.getValue(FORMED)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return state.getValue(FORMED);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 1 : 0.2f;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED);
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof ECOFluidInputHatchBlockEntity be) {
            return be.createUI(holder);
        }
        return null;
    }
}
