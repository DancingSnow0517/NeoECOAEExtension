package cn.dancingsnow.neoecoae.blocks.crafting;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class ECOCraftingParallelCore extends NEBlock<ECOCraftingParallelCoreBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    @Getter
    private final IECOTier tier;

    public ECOCraftingParallelCore(Properties properties, IECOTier tier) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(FACING, Direction.NORTH)
        );
        this.tier = tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
