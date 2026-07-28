package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Geometry shared by the F and C multiblock validators. */
public final class NENetworkSwitchUtil {
    private static final IOrientationStrategy HORIZONTAL = OrientationStrategies.horizontalFacing();

    private NENetworkSwitchUtil() {
    }

    /** Returns the side that is right when looking at the controller front. */
    public static Direction rightOfController(BlockState controllerState) {
        return HORIZONTAL.getSide(controllerState, RelativeSide.RIGHT);
    }

    public static BlockPos switchPosition(BlockPos controllerPos, BlockState controllerState) {
        return controllerPos.relative(rightOfController(controllerState));
    }

    public static boolean canUseNetworkSwitch(IECOTier tier) {
        return tier.getTier() == ECOTier.L9.getTier();
    }

    public static void setFormed(ServerLevel level, BlockPos switchPos, boolean formed) {
        BlockState state = level.getBlockState(switchPos);
        if (!state.hasProperty(NENetworkSwitchBlock.FORMED)) {
            return;
        }
        BlockState newState = state.setValue(NENetworkSwitchBlock.FORMED, formed);
        if (newState != state) {
            level.setBlock(switchPos, newState, Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(switchPos) instanceof ECOCraftingNetworkSwitchBlockEntity switchBlockEntity) {
            switchBlockEntity.onFormedStateChanged();
        } else if (level.getBlockEntity(switchPos)
            instanceof ECOComputationNetworkSwitchBlockEntity switchBlockEntity) {
            switchBlockEntity.onFormedStateChanged();
        }
    }
}
