package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.orientation.BlockOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;
import java.util.Set;

public class ECOCraftingNetworkSwitchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingNetworkSwitchBlockEntity> {
    public ECOCraftingNetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
    }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) { return formed ? EnumSet.allOf(Direction.class) : EnumSet.noneOf(Direction.class); }
}
