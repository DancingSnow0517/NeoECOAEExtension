package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;

public class ECOLargeIntegratedWorkingStationOutputHatch
    extends NEBlock<ECOLargeIntegratedWorkingStationOutputHatchBlockEntity> {
    public ECOLargeIntegratedWorkingStationOutputHatch(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hideWhenFormed() {
        return true;
    }
}
