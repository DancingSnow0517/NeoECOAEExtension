package cn.dancingsnow.neoecoae.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ECOComputationModels {
    private static final Map<Holder<Item>, Entry> deferredRegistration = new ConcurrentHashMap<>();
    // Written on the mod loading thread, read from the chunk meshing threads.
    private static final Map<Item, Entry> map = new ConcurrentHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new ConcurrentHashMap<>();

    public static void registerCellModel(Holder<Item> item, ResourceLocation normalModel, ResourceLocation formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, ResourceLocation normalModel, ResourceLocation formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    @Nullable
    public static ResourceLocation getNormalModel(Item item) {
        Entry entry = item == null ? null : map.get(item);
        return entry == null ? null : entry.normalModel;
    }

    @Nullable
    public static ResourceLocation getFormedModel(Item item) {
        Entry entry = item == null ? null : map.get(item);
        return entry == null ? null : entry.formedModel;
    }

    @Nullable
    public static ResourceLocation getCableDisconnectedModel(IECOTier tier) {
        Entry entry = tier == null ? null : cableModels.get(tier);
        return entry == null ? null : entry.normalModel;
    }

    @Nullable
    public static ResourceLocation getCableConnectedModel(IECOTier tier) {
        Entry entry = tier == null ? null : cableModels.get(tier);
        return entry == null ? null : entry.formedModel;
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
