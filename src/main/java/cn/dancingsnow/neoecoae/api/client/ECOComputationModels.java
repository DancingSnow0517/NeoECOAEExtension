package cn.dancingsnow.neoecoae.api.client;

import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;

public class ECOComputationModels {
    private static final Map<Holder<Item>, Entry> deferredRegistration = new IdentityHashMap<>();
    private static final Map<Item, Entry> map = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new IdentityHashMap<>();

    public static void registerCellModel(Holder<Item> item, Identifier normalModel, Identifier formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, Identifier normalModel, Identifier formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    public static Identifier getNormalModel(Item item) {
        return map.get(item).normalModel;
    }

    public static Identifier getFormedModel(Item item) {
        return map.get(item).formedModel;
    }

    public static Identifier getCableDisconnectedModel(IECOTier tier) {
        if (tier == null) return null;
        return cableModels.get(tier).normalModel;
    }

    public static Identifier getCableConnectedModel(IECOTier tier) {
        if (tier == null) return null;
        return cableModels.get(tier).formedModel;
    }

    public static void runDeferredRegistration() {
        deferredRegistration.forEach((itemSupplier, entry) -> {
            map.put(itemSupplier.value(), entry);
        });
    }

    public record Entry(
        Identifier normalModel,
        Identifier formedModel
    ) {
    }
}
