package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.util.NEBlockEntityTicker;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class NEBlockEntityBuilder<T extends NEBlockEntity<?, T>, P> extends BlockEntityBuilder<T, P> {
    private BlockEntry<? extends NEBlock<T>> blockEntry;

    @Nullable private BlockEntityTicker<T> clientTicker;

    @Nullable private BlockEntityTicker<T> serverTicker;

    public interface ClusterBlockEntityFactory<T extends NEBlockEntity<C, T>, C extends NECluster<C>> {
        T create(BlockEntityType<T> type, BlockPos pos, BlockState state, NEClusterCalculator.Factory<C> tcFactory);
    }

    public interface TierBlockEntityFactory<T extends NEBlockEntity<C, T>, C extends NECluster<C>> {
        T create(BlockEntityType<T> type, BlockPos pos, BlockState state, IECOTier tier);
    }

    protected NEBlockEntityBuilder(
            AbstractRegistrate<?> owner,
            P parent,
            String name,
            BuilderCallback callback,
            BlockEntityFactory<T> factory) {
        super(owner, parent, name, callback, factory);
    }

    public NEBlockEntityBuilder<T, P> forBlock(BlockEntry<? extends NEBlock<T>> blockSupplier) {
        this.blockEntry = blockSupplier;
        return this;
    }

    public NEBlockEntityBuilder<T, P> clientTicker(BlockEntityTicker<T> ticker) {
        clientTicker = ticker;
        return this;
    }

    public NEBlockEntityBuilder<T, P> serverTicker(BlockEntityTicker<T> ticker) {
        serverTicker = ticker;
        return this;
    }

    public NEBlockEntityBuilder<T, P> clientTicker(Consumer<T> ticker) {
        clientTicker = (level, blockPos, blockState, t) -> ticker.accept(t);
        return this;
    }

    public NEBlockEntityBuilder<T, P> serverTicker(Consumer<T> ticker) {
        serverTicker = (level, blockPos, blockState, t) -> ticker.accept(t);
        return this;
    }

    public NEBlockEntityBuilder<T, P> clientTicker(NEBlockEntityTicker<T> ticker) {
        clientTicker = (level, blockPos, blockState, t) -> ticker.tick(t, level, blockPos, blockState);
        return this;
    }

    public NEBlockEntityBuilder<T, P> serverTicker(NEBlockEntityTicker<T> ticker) {
        serverTicker = (level, blockPos, blockState, t) -> ticker.tick(t, level, blockPos, blockState);
        return this;
    }

    public static <T extends NEBlockEntity<?, T>, P> NEBlockEntityBuilder<T, P> createMy(
            AbstractRegistrate<?> owner,
            P parent,
            String name,
            BuilderCallback callback,
            BlockEntityFactory<T> factory) {
        return new NEBlockEntityBuilder<>(owner, parent, name, callback, factory);
    }

    @Override
    public NEBlockEntityBuilder<T, P> renderer(
            NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<? super T>>>
                    renderer) {
        return (NEBlockEntityBuilder<T, P>) super.renderer(renderer);
    }

    /** @deprecated Forge 1.20.1 capabilities are exposed by block entities through getCapability. */
    @Deprecated
    public NEBlockEntityBuilder<T, P> registerCapability(
            Consumer<CapabilityRegistrationEvent> registerCapabilitiesEvent) {
        // Forge 1.20.1 exposes block-entity capabilities through getCapability.
        // Keep the builder chain source-compatible while the actual providers
        // are moved onto the block entities.
        return this;
    }

    @Override
    public NEBlockEntityEntry<T> register() {
        return (NEBlockEntityEntry<T>) super.register();
    }

    @Override
    public NEBlockEntityBuilder<T, P> validBlock(NonNullSupplier<? extends Block> block) {
        return (NEBlockEntityBuilder<T, P>) super.validBlock(block);
    }

    @Override
    protected RegistryEntry<BlockEntityType<T>> createEntryWrapper(RegistryObject<BlockEntityType<T>> delegate) {
        return new NEBlockEntityEntry<>(getOwner(), delegate, blockEntry, clientTicker, serverTicker);
    }

    public static class CapabilityRegistrationEvent {
        public <C, BE extends BlockEntity> void registerBlockEntity(
                Capability<C> capability,
                BlockEntityType<BE> blockEntityType,
                BiFunction<BE, @Nullable Direction, C> provider) {}
    }
}
