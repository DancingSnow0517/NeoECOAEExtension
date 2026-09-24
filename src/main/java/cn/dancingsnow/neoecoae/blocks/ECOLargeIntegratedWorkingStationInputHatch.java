package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class ECOLargeIntegratedWorkingStationInputHatch
        extends NEBlock<ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public ECOLargeIntegratedWorkingStationInputHatch(Properties properties) { super(properties); }

    @Override public RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
