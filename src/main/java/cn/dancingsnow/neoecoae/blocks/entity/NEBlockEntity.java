package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.cluster.IAEMultiBlock;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.util.iterators.ChainedIterator;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends AENetworkedBlockEntity implements IAEMultiBlock<C> {

    private static final IGridNodeListener<NEBlockEntity<?, ?>> NODE_LISTENER =
        new BlockEntityNodeListener<>() {
            @Override
            public void onGridChanged(NEBlockEntity<?, ?> nodeOwner, IGridNode node) {
                nodeOwner.onMainNodeGridChanged();
            }
        };

    @Setter
    @Getter
    protected boolean formed = false;

    @Getter
    @Nullable
    protected C cluster;
    @Getter
    protected final NEClusterCalculator<C> calculator;

    /**
     * Calculators chosen per block entity type instead of per block entity class.
     *
     * <p>An addon that registers its own {@link BlockEntityType} still has to use this mod's block entity
     * classes, because AE2 files grid nodes under {@code owner.getClass()} and looks machines up by that
     * exact key - so the only place left where an addon can reach the multiblock logic is the type, which
     * it does own. Without this the addon has to replace the whole {@code verifyInternalStructure}, and
     * every side effect that method grows later (mirrored flags, network switch state) is silently lost.
     */
    private static final Map<BlockEntityType<?>, NEClusterCalculator.Factory<?>> CALCULATOR_FACTORIES =
        new ConcurrentHashMap<>();

    /**
     * Uses {@code factory} instead of this mod's own calculator for one block entity type. The factory is
     * called with the block entity the same way the built-in one is, so implementations are free to
     * subclass {@code NEComputationClusterCalculator} / {@code NECraftingClusterCalculator} and override
     * only {@code verifyStructure}.
     */
    public static <C extends NECluster<C>> void registerCalculatorFactory(
        BlockEntityType<?> type, NEClusterCalculator.Factory<C> factory) {
        CALCULATOR_FACTORIES.put(type, factory);
    }

    @SuppressWarnings("unchecked")
    private static <C extends NECluster<C>> NEClusterCalculator<C> createCalculator(
        BlockEntityType<?> type, NEBlockEntity<C, ?> blockEntity, NEClusterCalculator.Factory<C> fallback) {
        NEClusterCalculator.Factory<?> registered = CALCULATOR_FACTORIES.get(type);
        NEClusterCalculator.Factory<C> factory =
            registered == null ? fallback : (NEClusterCalculator.Factory<C>) registered;
        return factory.create(blockEntity);
    }

    public NEBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState);
        this.calculator = createCalculator(type, this, calculator);
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
            // During chunk loading, neighboring block entities may not be available yet. A single
            // immediate calculation can therefore leave a valid structure disconnected until a
            // block update occurs. Retry after the load queue has finished restoring the chunk.
            serverLevel.getServer().executeIfPossible(() -> {
                if (!isRemoved() && level == serverLevel && hasLevel() && level.hasChunkAt(worldPosition)) {
                    calculator.calculateMultiblock(serverLevel, worldPosition);
                }
            });
        }
        getMainNode().setIdlePowerUsage(16);
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (isServerStopping()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    public void rebuildMultiblock() {
        if (isServerStopping()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.updateState(false);
        }
    }

    protected void onMainNodeGridChanged() {
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        if (isServerStopping()) {
            return EnumSet.noneOf(Direction.class);
        }

        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        if (level != null) {
            for (Direction value : Direction.values()) {
                BlockPos adjacentPos = this.worldPosition.relative(value);
                if (level.hasChunkAt(adjacentPos)
                    && level.getBlockEntity(adjacentPos) instanceof IGridConnectedBlockEntity) {
                    directions.add(value);
                }
            }
        }
        return directions;
    }

    @MustBeInvokedByOverriders
    public void updateState(boolean updateExposed) {
        if (this.level == null || this.notLoaded() || this.isRemoved() || isServerStopping()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        BlockState newState = state;
        if (state.hasProperty(NEBlock.FORMED)) {
            newState = state.setValue(NEBlock.FORMED, formed);
        } else if (state.hasProperty(NENetworkSwitchBlock.FORMED)) {
            newState = state.setValue(NENetworkSwitchBlock.FORMED, formed);
        }
        if (newState != state) {
            level.setBlock(
                worldPosition,
                newState,
                Block.UPDATE_CLIENTS
            );
        }
        if (updateExposed) {
            onGridConnectableSidesChanged();
        }
    }

    /**
     * AEBaseBlockEntity overrides getUpdateTag without calling BlockEntity#getUpdateTag, so LDLib's
     * BlockEntity mixin cannot append the initial values of our managed sync fields. Keep the bridge
     * here so clients receive cell, multiblock and orientation data before section geometry is built.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (this instanceof ISyncMangedHolder syncManagedHolder) {
            tag.put(syncManagedHolder.getSyncTag(), syncManagedHolder.serializeInitialData(registries));
        }
        return tag;
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (cluster == null) {
            return new ChainedIterator<>();
        }
        List<IGridNode> nodes = new ArrayList<>();
        Iterator<? extends net.minecraft.world.level.block.entity.BlockEntity> it = cluster.getBlockEntities();
        while (it.hasNext()) {
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = it.next();
            IGridNode node = blockEntity instanceof NEBlockEntity<?, ?> member ? member.getGridNode() : null;
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

    protected boolean isServerStopping() {
        return this.level instanceof ServerLevel serverLevel && serverLevel.getServer().isStopped();
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
            cluster.breakCluster();
        }
    }
}
