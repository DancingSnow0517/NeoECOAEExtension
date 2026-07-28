package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.orientation.BlockOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

public class ECOComputationNetworkSwitchBlockEntity
    extends AbstractComputationBlockEntity<ECOComputationNetworkSwitchBlockEntity> {
    public ECOComputationNetworkSwitchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : EnumSet.noneOf(Direction.class);
    }
}
