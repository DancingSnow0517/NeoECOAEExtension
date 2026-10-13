package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECOParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class ECOParallelCoreBlock<C extends NECluster<C>> extends NEBlock<ECOParallelCoreBlockEntity<C>> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Distinguishes crafting and computation cores for structure checks and tooltips. */
    public enum Kind { CRAFTING, COMPUTATION }

    @Getter
    private final IECOTier tier;
    @Getter
    private final Kind kind;

    public ECOParallelCoreBlock(Properties properties, IECOTier tier, Kind kind) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(FACING, Direction.NORTH)
        );
        this.tier = tier;
        this.kind = kind;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
