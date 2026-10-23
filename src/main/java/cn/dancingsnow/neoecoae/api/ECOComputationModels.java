package cn.dancingsnow.neoecoae.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;

import java.util.IdentityHashMap;
import java.util.Map;

public class ECOComputationModels {
    private static final Map<Holder<Item>, Entry> deferredRegistration = new IdentityHashMap<>();
    private static final Map<Item, Entry> map = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new IdentityHashMap<>();
    private static final Map<Block, Map<IECOTier, Entry>> hostCableModels = new IdentityHashMap<>();
    private static final ECOExtensionRegistry<CableModelProvider> providers = new ECOExtensionRegistry<>();
    @Nullable private static Entry defaultCableModels;
    private static final java.util.Set<ResourceLocation> additionalModels = new java.util.HashSet<>();

    /** Declare dynamic provider model IDs before model baking. Explicit cell/cable registrations are included automatically. */
    public static void registerAdditionalModel(ResourceLocation model) { additionalModels.add(model); }

    public static java.util.Set<ResourceLocation> getAdditionalModels() {
        var result = new java.util.HashSet<>(additionalModels);
        java.util.function.Consumer<Entry> add = entry -> {
            if (entry.normalModel() != null) result.add(entry.normalModel());
            if (entry.formedModel() != null) result.add(entry.formedModel());
        };
        deferredRegistration.values().forEach(add);
        cableModels.values().forEach(add);
        hostCableModels.values().forEach(models -> models.values().forEach(add));
        if (defaultCableModels != null) add.accept(defaultCableModels);
        return java.util.Set.copyOf(result);
    }

    @FunctionalInterface
    public interface CableModelProvider {
        @Nullable Entry models(IECOTier tier, @Nullable Block host);
    }

    public static void registerCableModelProvider(ResourceLocation id, int priority, CableModelProvider provider) {
        providers.register(id, priority, provider);
    }

    public static boolean unregisterCableModelProvider(ResourceLocation id) { return providers.unregister(id); }

    public static void registerCableModel(IECOTier tier, Block host, ResourceLocation normalModel, ResourceLocation formedModel) {
        hostCableModels.computeIfAbsent(host, ignored -> new IdentityHashMap<>()).put(tier, new Entry(normalModel, formedModel));
    }

    public static void registerDefaultCableModel(ResourceLocation normalModel, ResourceLocation formedModel) {
        defaultCableModels = new Entry(normalModel, formedModel);
    }

    public static void registerCellModel(Holder<Item> item, ResourceLocation normalModel, ResourceLocation formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, ResourceLocation normalModel, ResourceLocation formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    public static ResourceLocation getNormalModel(Item item) {
        Entry entry = map.get(item);
        return entry == null ? null : entry.normalModel;
    }

    public static ResourceLocation getFormedModel(Item item) {
        Entry entry = map.get(item);
        return entry == null ? null : entry.formedModel;
    }

    public static ResourceLocation getCableDisconnectedModel(IECOTier tier) {
        return getCableDisconnectedModel(tier, null);
    }

    public static ResourceLocation getCableConnectedModel(IECOTier tier) {
        return getCableConnectedModel(tier, null);
    }

    @Nullable
    public static ResourceLocation getCableDisconnectedModel(IECOTier tier, @Nullable Block host) {
        Entry entry = resolveCableModels(tier, host);
        return entry == null ? null : entry.normalModel;
    }

    @Nullable
    public static ResourceLocation getCableConnectedModel(IECOTier tier, @Nullable Block host) {
        Entry entry = resolveCableModels(tier, host);
        return entry == null ? null : entry.formedModel;
    }

    @Nullable
    private static Entry resolveCableModels(@Nullable IECOTier tier, @Nullable Block host) {
        if (tier == null) return null;
        for (var entry : providers.entries()) {
            Entry models = entry.extension().models(tier, host);
            if (models != null) return models;
        }
        Entry models = hostCableModels.getOrDefault(host, Map.of()).get(tier);
        if (models != null) return models;
        return cableModels.getOrDefault(tier, defaultCableModels);
    }

    public static void runDeferredRegistration() {
        deferredRegistration.forEach((itemSupplier, entry) -> {
            map.put(itemSupplier.value(), entry);
        });
    }

    public record Entry(
        ResourceLocation normalModel,
        ResourceLocation formedModel
    ) {
    }
}
