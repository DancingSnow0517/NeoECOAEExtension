package cn.dancingsnow.neoecoae.api.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;

public class ECOCellModels {

    public static final Identifier DEFAULT_MODEL = NeoECOAE.id("cell/storage_cell_default");

    private static final Map<Item, Identifier> registry = new IdentityHashMap<>();

    public static Identifier getModelLocation(Item item) {
        if (item == null) {
            return DEFAULT_MODEL;
        }
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(Holder<Item> item, Identifier model) {
        register(item.value(), model);
    }

    public static void register(Item item, Identifier model) {
        registry.put(item, model);
    }

    public static Map<Item, Identifier> getRegistry() {
        return new IdentityHashMap<>(registry);
    }
}
