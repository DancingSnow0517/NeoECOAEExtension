package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.iterators.ChainedIterator;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends AENetworkedBlockEntity
    implements IAEMultiBlock<C> {

    @Setter
    @Getter
    protected boolean formed = false;

    @Getter
    @Nullable
    protected C cluster;
    protected final NEClusterCalculator<C> calculator;

    public NEBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState);
        this.calculator = calculator.create(this);
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
        onGridConnectableSidesChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        onGridConnectableSidesChanged();
        if (level instanceof ServerLevel serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
        getMainNode().setIdlePowerUsage(16);
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (level instanceof ServerLevel serverLevel) {
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.updateState(false);
        }
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }

        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        if (level != null) {
            for (Direction value : Direction.values()) {
                if (level.getBlockEntity(this.worldPosition.relative(value)) instanceof NEBlockEntity) {
                    directions.add(value);
                }
            }
        }
        return directions;
    }

    @MustBeInvokedByOverriders
    public void updateState(boolean updateExposed) {
        if (this.level == null || this.notLoaded() || this.isRemoved()) {
            return;
        }
        BlockState newState = level.getBlockState(worldPosition);
        if (newState.hasProperty(NEBlock.FORMED)){
            newState = newState.setValue(NEBlock.FORMED, formed);
        }
        level.setBlock(
            worldPosition,
            newState,
            Block.UPDATE_CLIENTS
        );
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

    public void updateCluster(@Nullable C cluster) {
        this.cluster = cluster;
        formed = cluster != null;
        updateState(true);
    }

    @Override
    public void disconnect(boolean update) {
        if (this.cluster != null) {
            this.cluster.destroy();
            formed = false;
            if (update) {
                updateState(true);
            }
        }
    }

    @Override
    public boolean isValid() {
        return !this.isRemoved();
    }

    public void breakCluster() {
        if (this.cluster != null){
            cluster.destroy();
        }
    }
}
