package cn.dancingsnow.neoecoae.blocks.crafting;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ECOCraftingPatternBus extends NEBlock<ECOCraftingPatternBusBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ECOCraftingPatternBus(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ECOCraftingPatternBusBlockEntity bus) {
            CraftingPatternMenu.openBus(serverPlayer, bus, bus.getInitialMenuPage());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

}
