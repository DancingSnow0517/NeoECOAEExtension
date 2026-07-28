package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Marker block entity used by AE2's bounds scanner. The switch is not a
 * member of the physical cluster and has no independent subsystem logic.
 */
public class ECOComputationNetworkSwitchBlockEntity extends AENetworkedBlockEntity {
    public ECOComputationNetworkSwitchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!getBlockState().getValue(NENetworkSwitchBlock.FORMED) || level == null) {
            return EnumSet.noneOf(Direction.class);
        }
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction))
                instanceof AbstractComputationBlockEntity<?>) {
                directions.add(direction);
            }
        }
        return directions;
    }

    @Override
    public void onReady() {
        super.onReady();
        onGridConnectableSidesChanged();
    }

    public void onFormedStateChanged() {
        onGridConnectableSidesChanged();
    }
}
