package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class NEBlockEntityBuilder<T extends NEBlockEntity<?, T>, P> extends BlockEntityBuilder<T, P> {
    private Supplier<NEBlock<T>> blockSupplier;
    
    protected NEBlockEntityBuilder(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback, BlockEntityFactory<T> factory) {
        super(owner, parent, name, callback, factory);
    }

    public NEBlockEntityBuilder<T, P> forBlock(Supplier<? extends NEBlock<T>> blockSupplier) {
        //noinspection unchecked
        this.blockSupplier = (Supplier<NEBlock<T>>) blockSupplier;
        return this;
    }

    public static <T extends NEBlockEntity<?, T>, P> NEBlockEntityBuilder<T, P> createMy(
        AbstractRegistrate<?> owner,
        P parent,
        String name,
        BuilderCallback callback,
        BlockEntityFactory<T> factory
    ) {
        return new NEBlockEntityBuilder<>(owner, parent, name, callback, factory);
    }

    @Override
    public NEBlockEntityBuilder<T, P> renderer(NonNullSupplier<NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<? super T>>> renderer) {
        return (NEBlockEntityBuilder<T, P>) super.renderer(renderer);
    }

    @Override
    public NEBlockEntityBuilder<T, P> registerCapability(Consumer<RegisterCapabilitiesEvent> registerCapabilitiesEvent) {
        return (NEBlockEntityBuilder<T, P>) super.registerCapability(registerCapabilitiesEvent);
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
    protected RegistryEntry<BlockEntityType<?>, BlockEntityType<T>> createEntryWrapper(DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> delegate) {
        return new NEBlockEntityEntry<>(getOwner(), delegate, blockSupplier);
    }
}
