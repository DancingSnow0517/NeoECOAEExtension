package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class MachineCasing<C extends NECluster<C>> extends NEBlock<MachineCasingBlockEntity<C>> {

    public MachineCasing(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (state.getValue(FORMED)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }
}
