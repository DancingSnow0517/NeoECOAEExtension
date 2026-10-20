package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class NENetworkSwitchUtil {
    private static final IOrientationStrategy HORIZONTAL = OrientationStrategies.horizontalFacing();
    private NENetworkSwitchUtil() {}
    public static Direction rightOfController(BlockState state) { return HORIZONTAL.getSide(state, RelativeSide.RIGHT); }
    public static BlockPos switchPosition(BlockPos controllerPos, BlockState state, boolean mirrored) {
        Direction side = mirrored ? HORIZONTAL.getSide(state, RelativeSide.LEFT) : rightOfController(state);
        return controllerPos.relative(side);
    }
    public static boolean isSwitchPosition(BlockPos switchPos, BlockPos controllerPos, BlockState state) {
        return switchPos.equals(switchPosition(controllerPos, state, false))
            || switchPos.equals(switchPosition(controllerPos, state, true));
    }
    public static boolean canUseNetworkSwitch(IECOTier tier) { return tier.getTier() == ECOTier.L9.getTier(); }
    public static void setFormed(net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean formed) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(NENetworkSwitchBlock.FORMED) && state.getValue(NENetworkSwitchBlock.FORMED) != formed) level.setBlock(pos, state.setValue(NENetworkSwitchBlock.FORMED, formed), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    public static void clearFormed(net.minecraft.server.level.ServerLevel level, BlockPos controllerPos, BlockState state) {
        setFormed(level, switchPosition(controllerPos, state, false), false);
        setFormed(level, switchPosition(controllerPos, state, true), false);
    }
    public static void syncFormed(net.minecraft.server.level.ServerLevel level, BlockPos controllerPos, BlockState state, boolean mirrored) {
        setFormed(level, switchPosition(controllerPos, state, false), !mirrored);
        setFormed(level, switchPosition(controllerPos, state, true), mirrored);
    }
}
