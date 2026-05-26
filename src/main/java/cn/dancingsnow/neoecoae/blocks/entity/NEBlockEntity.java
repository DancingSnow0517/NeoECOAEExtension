package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.iterators.ChainedIterator;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends AENetworkBlockEntity implements IAEMultiBlock<C> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_FORMED_UPDATES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_REBUILDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_GRID_STATES = ConcurrentHashMap.newKeySet();

    @Setter
    @Getter
    protected boolean formed = false;

    @Getter
    @Nullable
    protected C cluster;
    @Getter
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
        logGridState("onReady", true);
        if (level instanceof ServerLevel serverLevel) {
            logRebuild("onReady");
            calculator.calculateMultiblock(serverLevel, worldPosition);
            serverLevel.getServer().executeIfPossible(() -> {
                if (level instanceof ServerLevel delayedLevel && !isRemoved()) {
                    logRebuild("onReadyDelayed");
                    calculator.calculateMultiblock(delayedLevel, worldPosition);
                }
            });
        }
        getMainNode().setIdlePowerUsage(16);
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (level instanceof ServerLevel serverLevel) {
            logRebuild("neighborChanged:" + changedPos);
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    public void rebuildMultiblock() {
        if (level instanceof ServerLevel serverLevel) {
            logRebuild("rebuildMultiblock");
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        logGridState("onMainNodeStateChanged:" + reason, false);
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
        if (this.level == null || this.isRemoved()) {
            return;
        }
        logGridState("updateState", updateExposed);
        BlockState oldState = level.getBlockState(worldPosition);
        BlockState newState = oldState;
        if (newState.hasProperty(NEBlock.FORMED)) {
            boolean oldFormed = newState.getValue(NEBlock.FORMED);
            newState = newState.setValue(NEBlock.FORMED, formed);
            if (!FMLEnvironment.production && oldFormed != formed) {
                String logKey = getClass().getName() + "|" + worldPosition + "|" + oldFormed + ">" + formed;
                if (LOGGED_FORMED_UPDATES.add(logKey)) {
                    LOGGER.info(
                        "NE multiblock formed update: be={}, pos={}, block={}, oldFormed={}, newFormed={}, hasCluster={}, updateExposed={}, oldState={}, newState={}",
                        getClass().getSimpleName(),
                        worldPosition,
                        ForgeRegistries.BLOCKS.getKey(oldState.getBlock()),
                        oldFormed,
                        formed,
                        cluster != null,
                        updateExposed,
                        oldState,
                        newState
                    );
                }
            }
        }
        if (!oldState.equals(newState)) {
            level.setBlock(
                worldPosition,
                newState,
                Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS
            );
        }
        if (updateExposed) {
            onGridConnectableSidesChanged();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (this instanceof ISyncMangedHolder syncMangedHolder) {
            tag.put(syncMangedHolder.getSyncTag(), syncMangedHolder.serializeInitialData(null));
        }
        return tag;
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
        logGridState("updateCluster", true);
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
        if (this.cluster != null) {
            cluster.destroy();
        }
    }

    private void logRebuild(String source) {
        if (FMLEnvironment.production) {
            return;
        }
        String key = getClass().getName() + "|" + worldPosition + "|" + source;
        if (LOGGED_REBUILDS.add(key)) {
            LOGGER.info(
                "NE multiblock rebuild requested: source={}, be={}, pos={}, block={}, hasCluster={}, formed={}",
                source,
                getClass().getSimpleName(),
                worldPosition,
                level == null ? null : ForgeRegistries.BLOCKS.getKey(level.getBlockState(worldPosition).getBlock()),
                cluster != null,
                formed
            );
        }
    }

    private void logGridState(String source, boolean updateExposed) {
        if (FMLEnvironment.production) {
            return;
        }
        IGridNode gridNode = getGridNode();
        BlockState state = level == null ? getBlockState() : level.getBlockState(worldPosition);
        boolean blockstateFormed = state.hasProperty(NEBlock.FORMED) && state.getValue(NEBlock.FORMED);
        Set<Direction> connectableSides = level == null
            ? Set.of()
            : getGridConnectableSides(getOrientation());
        String key = getClass().getName()
            + "|" + worldPosition
            + "|" + source
            + "|" + formed
            + "|" + (cluster != null)
            + "|" + (gridNode != null)
            + "|" + getMainNode().isOnline()
            + "|" + getMainNode().isActive()
            + "|" + connectableSides
            + "|" + updateExposed
            + "|" + blockstateFormed;
        if (LOGGED_GRID_STATES.add(key)) {
            LOGGER.info(
                "NE grid state: source={}, be={}, pos={}, formed={}, hasCluster={}, gridNode={}, mainOnline={}, mainActive={}, connectableSides={}, updateExposed={}, blockstateFormed={}",
                source,
                getClass().getSimpleName(),
                worldPosition,
                formed,
                cluster != null,
                gridNode != null,
                getMainNode().isOnline(),
                getMainNode().isActive(),
                connectableSides,
                updateExposed,
                blockstateFormed
            );
        }
    }
}
