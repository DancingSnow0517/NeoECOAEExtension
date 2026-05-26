package cn.dancingsnow.neoecoae.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ECOComputationModels {
    private static final Map<Supplier<? extends Item>, Entry> deferredRegistration = new IdentityHashMap<>();
    private static final Map<Item, Entry> map = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new IdentityHashMap<>();

    public static void registerCellModel(Supplier<? extends Item> item, ResourceLocation normalModel, ResourceLocation formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, ResourceLocation normalModel, ResourceLocation formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    public static @Nullable ResourceLocation getNormalModel(Item item) {
        Entry entry = map.get(item);
        return entry == null ? null : entry.normalModel;
    }

    public static @Nullable ResourceLocation getFormedModel(Item item) {
        Entry entry = map.get(item);
        return entry == null ? null : entry.formedModel;
    }

    public static @Nullable ResourceLocation getCableDisconnectedModel(IECOTier tier) {
        if (tier == null) return null;
        Entry entry = cableModels.get(tier);
        return entry == null ? null : entry.normalModel;
    }

    public static @Nullable ResourceLocation getCableConnectedModel(IECOTier tier) {
        if (tier == null) return null;
        Entry entry = cableModels.get(tier);
        return entry == null ? null : entry.formedModel;
    }

    public static void runDeferredRegistration() {
        deferredRegistration.forEach((itemSupplier, entry) -> {
            map.put(itemSupplier.get(), entry);
        });
    }

    public record Entry(
        ResourceLocation normalModel,
        ResourceLocation formedModel
    ) {
    }
}
