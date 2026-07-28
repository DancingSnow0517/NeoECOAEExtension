package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
}
