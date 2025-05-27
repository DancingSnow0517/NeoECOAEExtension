package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;

public class MachineCasing<C extends NECluster<C>> extends NEBlock<MachineCasingBlockEntity<C>> {

    public MachineCasing(Properties properties) {
        super(properties);
    }
}
