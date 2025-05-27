package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

public class MachineCasingBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, MachineCasingBlockEntity<C>> {

    public MachineCasingBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<MachineCasingBlockEntity<C>, C> factory
    ) {
        super(type, pos, blockState, factory.create());
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }

        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        if (level != null) {
            for (Direction value : Direction.values()) {
                if (level.getBlockEntity(this.worldPosition.relative(value)) instanceof NEBlockEntity) {
                    directions.add(value);
                }
            }
        }
        return directions;
    }
}
