package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.cluster.IAEMultiBlock;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends AENetworkedBlockEntity
    implements IAEMultiBlock<C> {

    protected boolean formed = false;
    protected C cluster;
    protected final NEClusterCalculator<E, C> calculator;

    public NEBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator<E, C> calculator) {
        super(type, pos, blockState);
        this.calculator = calculator;
    }

    public boolean isCoreBlock() {
        return false;
    }

    public void updateCluster(C cluster) {
        this.cluster = cluster;
    }

    @Override
    public C getCluster() {
        return cluster;
    }

    @Override
    public void disconnect(boolean update) {
        if (this.cluster != null) {
            this.cluster.destroy();
        }
    }

    @Override
    public boolean isValid() {
        return !this.isRemoved();
    }
}
