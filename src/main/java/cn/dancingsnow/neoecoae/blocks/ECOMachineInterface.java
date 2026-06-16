package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

public class ECOMachineInterface<C extends NECluster<C>> extends NEBlock<ECOMachineInterfaceBlockEntity<C>> {
    public ECOMachineInterface(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (state.getValue(FORMED)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return state.getValue(FORMED);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 1 : 0.2f;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ECOMachineInterfaceBlockEntity<?> machineInterface
                && machineInterface.supportsStorageInterfaceUi()
                && player instanceof ServerPlayer serverPlayer) {
            BlockEntityUIFactory.INSTANCE.openUI(machineInterface, serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
