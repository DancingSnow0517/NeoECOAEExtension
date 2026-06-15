package cn.dancingsnow.neoecoae.api.client;

import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;

public class ECOComputationModels {
    private static final Map<Item, Entry> cellRegistry = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableRegistry = new IdentityHashMap<>();

    public static void registerCableModel(IECOTier tier, Identifier disconnectedCable, Identifier connectedCable) {
        cableRegistry.put(tier, new Entry(disconnectedCable, connectedCable));
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

    public static Map<IECOTier, Entry> getCableRegistry() {
        return new IdentityHashMap<>(cableRegistry);
    }

    public record Entry(Identifier normalModel, Identifier formedModel) {
        
    }
}
