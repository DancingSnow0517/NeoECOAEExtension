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
    public enum Kind {
        CRAFTING("crafting_parallel_core_", "block/crafting_core/parallel_core_"),
        COMPUTATION("computation_parallel_core_", "block/computation_core/parallel_core_");

        /** Registry id prefix, followed by the level suffix (e.g. "l4"). */
        public final String idPrefix;
        /** Model path prefix, followed by the level suffix (and optionally "_formed"). */
        public final String modelPrefix;

        Kind(String idPrefix, String modelPrefix) {
            this.idPrefix = idPrefix;
            this.modelPrefix = modelPrefix;
        }
    }

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
