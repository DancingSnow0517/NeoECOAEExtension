package cn.dancingsnow.neoecoae.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.BiFunction;

/** Registers the port's per-block handlers through Forge's attach/invalidate lifecycle. */
public final class ForgeBlockCapabilities {
    private static final Map<BlockEntityType<?>, List<Binding<?, ?>>> BINDINGS = new IdentityHashMap<>();
    private static final Map<BlockEntity, WeakReference<Provider>> PROVIDERS = new WeakHashMap<>();

    private ForgeBlockCapabilities() {}

    public static synchronized <B extends BlockEntity, T> void register(
            Capability<T> capability, BlockEntityType<B> type, BiFunction<B, Direction, T> factory) {
        List<Binding<?, ?>> bindings = BINDINGS.computeIfAbsent(type, ignored -> new ArrayList<>());
        if (bindings.stream().anyMatch(binding -> binding.capability == capability)) {
            throw new IllegalStateException("Duplicate block capability registration: " + capability);
        }
        bindings.add(new Binding<>(capability, type, factory));
    }

    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        List<Binding<?, ?>> bindings = BINDINGS.get(event.getObject().getType());
        if (bindings == null) return;
        Provider provider = new Provider(event.getObject(), List.copyOf(bindings));
        PROVIDERS.put(event.getObject(), new WeakReference<>(provider));
        event.addCapability(new ResourceLocation("neoecoae", "block_handlers"), provider);
        event.addListener(provider::invalidate);
    }

    public static void refresh(BlockEntity host) {
        Provider provider = provider(host);
        if (provider != null) provider.clearCache();
    }

    public static void revive(BlockEntity host) {
        Provider provider = provider(host);
        if (provider != null) {
            provider.clearCache();
            provider.valid = true;
        }
    }

    private static Provider provider(BlockEntity host) {
        WeakReference<Provider> reference = PROVIDERS.get(host);
        return reference == null ? null : reference.get();
    }

    @Nullable
    public static <T> T find(Level level, BlockPos pos, Capability<T> capability, @Nullable Direction side) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null || blockEntity.isRemoved() ? null
            : blockEntity.getCapability(capability, side).orElse(null);
    }

    private record Binding<B extends BlockEntity, T>(Capability<T> capability, BlockEntityType<B> type,
            BiFunction<B, Direction, T> factory) {
        @SuppressWarnings("unchecked")
        T resolve(BlockEntity blockEntity, @Nullable Direction side) {
            // The type-keyed registration already checked this factory's host type. No world lookup is needed.
            return factory.apply((B) blockEntity, side);
        }
    }

    private record Query(Capability<?> capability, Direction side) {}

    private static final class Provider implements ICapabilityProvider {
        private final BlockEntity host;
        private final List<Binding<?, ?>> bindings;
        private final Map<Query, LazyOptional<?>> cache = new HashMap<>();
        private boolean valid = true;

        Provider(BlockEntity host, List<Binding<?, ?>> bindings) {
            this.host = host;
            this.bindings = bindings;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            if (!valid || host.isRemoved()) return LazyOptional.empty();
            for (Binding<?, ?> binding : bindings) {
                if (binding.capability == capability) {
                    Query query = new Query(capability, side);
                    Object value = binding.resolve(host, side);
                    if (value == null) return LazyOptional.empty();
                    return cache.computeIfAbsent(query, ignored -> LazyOptional.of(() -> value)).cast();
                }
            }
            return LazyOptional.empty();
        }

        void invalidate() {
            valid = false;
            clearCache();
        }

        void clearCache() {
            var previous = new ArrayList<>(cache.values());
            cache.clear();
            previous.forEach(LazyOptional::invalidate);
        }
    }
}
