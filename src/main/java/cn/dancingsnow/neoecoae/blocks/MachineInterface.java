package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.MachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;

public class MachineInterface<C extends NECluster<C>> extends NEBlock<MachineInterfaceBlockEntity<C>>{
    public MachineInterface(Properties properties) {
        super(properties);
    }
}
