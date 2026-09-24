package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class ECOLargeIntegratedWorkingStationOutputHatch
        extends NEBlock<ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> {
    public ECOLargeIntegratedWorkingStationOutputHatch(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
