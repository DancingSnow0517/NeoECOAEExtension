package cn.dancingsnow.neoecoae.api.client;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side computation drive models. Register during client setup, before model baking.
 * <p>
 * Cable models resolve in this order: dynamic providers (by priority), {@code (tier, host block)} entries,
 * {@code tier} entries, then the default entry. The host is the computation drive block being rendered.
 * Every model a provider can return must be declared with {@link #registerAdditionalModel} so it is baked;
 * undeclared models are skipped at render time.
 */
public class ECOComputationModels {
    private static final Map<Item, Entry> cellRegistry = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableRegistry = new IdentityHashMap<>();
    private static final Map<Block, Map<IECOTier, Entry>> hostCableRegistry = new IdentityHashMap<>();
    private static final ECOExtensionRegistry<CableModelProvider> providers = new ECOExtensionRegistry<>();
    private static final Set<Identifier> additionalModels = new HashSet<>();
    @Nullable private static Entry defaultCableModels;

    @FunctionalInterface
    public interface CableModelProvider {
        /** Read-only lookup; return null to defer to lower-priority providers and static entries. */
        @Nullable Entry models(IECOTier tier, @Nullable Block host);
    }

    public static void registerCableModel(IECOTier tier, Identifier disconnectedCable, Identifier connectedCable) {
        cableRegistry.put(tier, new Entry(disconnectedCable, connectedCable));
    }

    /** Cable models used only when {@code host} is the rendered computation drive block. */
    public static void registerCableModel(IECOTier tier, Block host, Identifier disconnectedCable, Identifier connectedCable) {
        hostCableRegistry.computeIfAbsent(host, ignored -> new IdentityHashMap<>())
            .put(tier, new Entry(disconnectedCable, connectedCable));
    }

    /** Fallback for tiers without any registered cable model. */
    public static void registerDefaultCableModel(Identifier disconnectedCable, Identifier connectedCable) {
        defaultCableModels = new Entry(disconnectedCable, connectedCable);
    }

    public static void registerCableModelProvider(Identifier id, int priority, CableModelProvider provider) {
        providers.register(id, priority, provider);
    }

    public static boolean unregisterCableModelProvider(Identifier id) {
        return providers.unregister(id);
    }

    /** Declares a model a dynamic provider may return, so it is baked. Explicit registrations need no call. */
    public static void registerAdditionalModel(Identifier model) {
        additionalModels.add(model);
    }

    public static void registerCellModel(Holder<Item> item, Identifier unformedCell, Identifier formedCell) {
        cellRegistry.put(item.value(), new Entry(unformedCell, formedCell));
    }

    public static void registerCellModel(Item item, Identifier unformedCell, Identifier formedCell) {
        cellRegistry.put(item, new Entry(unformedCell, formedCell));
    }

    public static Map<Item, Entry> getCellRegistry() {
        return new IdentityHashMap<>(cellRegistry);
    }

    /** Tier-only cable entries; host-specific, default and provider entries are resolved by {@link #resolveCableModels}. */
    public static Map<IECOTier, Entry> getCableRegistry() {
        return new IdentityHashMap<>(cableRegistry);
    }

    /** Every cable model that must be baked: explicit tier, host and default entries plus declared additional models. */
    public static Set<Identifier> getCableModels() {
        var result = new HashSet<>(additionalModels);
        java.util.function.Consumer<Entry> add = entry -> {
            result.add(entry.normalModel());
            result.add(entry.formedModel());
        };
        cableRegistry.values().forEach(add);
        hostCableRegistry.values().forEach(models -> models.values().forEach(add));
        if (defaultCableModels != null) add.accept(defaultCableModels);
        return Set.copyOf(result);
    }

    @Nullable
    public static Identifier getCableDisconnectedModel(IECOTier tier, @Nullable Block host) {
        Entry entry = resolveCableModels(tier, host);
        return entry == null ? null : entry.normalModel();
    }

    @Nullable
    public static Identifier getCableConnectedModel(IECOTier tier, @Nullable Block host) {
        Entry entry = resolveCableModels(tier, host);
        return entry == null ? null : entry.formedModel();
    }

    @Nullable
    public static Entry resolveCableModels(@Nullable IECOTier tier, @Nullable Block host) {
        if (tier == null) return null;
        for (var entry : providers.entries()) {
            Entry models = entry.extension().models(tier, host);
            if (models != null) return models;
        }
        if (host != null) {
            var hostModels = hostCableRegistry.get(host);
            Entry models = hostModels == null ? null : hostModels.get(tier);
            if (models != null) return models;
        }
        return cableRegistry.getOrDefault(tier, defaultCableModels);
    }

    public record Entry(Identifier normalModel, Identifier formedModel) {
    }
}
