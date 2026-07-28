package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
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
        return switchPosition(controllerPos, controllerState, false);
    }

    /** Mirrored structures place the switch to the controller's left. */
    public static BlockPos switchPosition(BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        Direction switchSide = mirrored
            ? HORIZONTAL.getSide(controllerState, RelativeSide.LEFT)
            : rightOfController(controllerState);
        return controllerPos.relative(switchSide);
    }

    public static boolean isSwitchPosition(BlockPos switchPos, BlockPos controllerPos, BlockState controllerState) {
        return switchPos.equals(switchPosition(controllerPos, controllerState, false))
            || switchPos.equals(switchPosition(controllerPos, controllerState, true));
    }

    public static void clearFormed(ServerLevel level, BlockPos controllerPos, BlockState controllerState) {
        setFormed(level, switchPosition(controllerPos, controllerState, false), false);
        setFormed(level, switchPosition(controllerPos, controllerState, true), false);
    }

    public static void syncFormed(
        ServerLevel level,
        BlockPos controllerPos,
        BlockState controllerState,
        boolean mirrored
    ) {
        setFormed(level, switchPosition(controllerPos, controllerState, false), !mirrored);
        setFormed(level, switchPosition(controllerPos, controllerState, true), mirrored);
    }

    public static boolean canUseNetworkSwitch(IECOTier tier) {
        return tier.getTier() == ECOTier.L9.getTier();
    }

    public static void setFormed(ServerLevel level, BlockPos switchPos, boolean formed) {
        BlockState state = level.getBlockState(switchPos);
        if (!state.hasProperty(NENetworkSwitchBlock.FORMED)) {
            return;
        }
        if (state.getValue(NENetworkSwitchBlock.FORMED) != formed) {
            BlockState newState = state.setValue(NENetworkSwitchBlock.FORMED, formed);
            level.setBlock(switchPos, newState, Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(switchPos) instanceof ECOCraftingNetworkSwitchBlockEntity switchBlockEntity) {
            switchBlockEntity.onFormedStateChanged();
        } else if (level.getBlockEntity(switchPos)
            instanceof ECOComputationNetworkSwitchBlockEntity switchBlockEntity) {
            switchBlockEntity.onFormedStateChanged();
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof NEBlockEntity<?, ?> blockEntity) {
                blockEntity.refreshGridConnections();
            }
        }
    }
}
