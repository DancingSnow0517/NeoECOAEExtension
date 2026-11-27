package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;

public class ECOLargeIntegratedWorkingStationInterface
    extends ECOMachineInterface<NEIntegratedWorkingStationCluster> {
    public ECOLargeIntegratedWorkingStationInterface(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
