package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.iterators.ChainedIterator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends AENetworkedBlockEntity
    implements IAEMultiBlock<C> {

    protected boolean formed = false;
    protected C cluster;
    protected final NEClusterCalculator<E, C> calculator;

    public NEBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator<E, C> calculator) {
        super(type, pos, blockState);
        this.calculator = calculator;
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
        onGridConnectableSidesChanged();
    }

    @Override
    public void onReady() {
        onGridConnectableSidesChanged();
        super.onReady();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.updateState(false);
        }
    }

    protected void updateState(boolean updateExposed) {
        if (updateExposed) {
            onGridConnectableSidesChanged();
        }
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (cluster == null) {
            return new ChainedIterator<>();
        }
        List<IGridNode> nodes = new ArrayList<>();
        Iterator<? extends NEBlockEntity<C, ?>> it = cluster.getBlockEntities();
        while (it.hasNext()) {
            IGridNode node = it.next().getGridNode();
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes.listIterator();

    }

    public boolean isCoreBlock() {
        return false;
    }

    public void updateCluster(C cluster) {
        this.cluster = cluster;
        updateState(true);
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
