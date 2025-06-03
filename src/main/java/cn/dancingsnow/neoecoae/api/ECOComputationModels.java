package cn.dancingsnow.neoecoae.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;

public class ECOComputationModels {
    private static final Map<Holder<Item>, Entry> deferredRegistration = new IdentityHashMap<>();
    private static final Map<Item, Entry> map = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new IdentityHashMap<>();

    public static void registerCellModel(Holder<Item> item, ResourceLocation normalModel, ResourceLocation formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, ResourceLocation normalModel, ResourceLocation formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    public static ResourceLocation getNormalModel(Item item) {
        return map.get(item).normalModel;
    }

    public static ResourceLocation getFormedModel(Item item) {
        return map.get(item).formedModel;
    }

    public static ResourceLocation getCableDisconnectedModel(IECOTier tier) {
        return cableModels.get(tier).normalModel;
    }

    public static ResourceLocation getCableConnectedModel(IECOTier tier) {
        return cableModels.get(tier).formedModel;
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
