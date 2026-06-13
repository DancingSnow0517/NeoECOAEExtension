package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import lombok.Getter;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber
public class ECOCellModels {
    private static final StandaloneModelKey<QuadCollection> MODEL_KEY = new StandaloneModelKey<>(() -> "CellModel");

    private static final Map<Holder<Item>, Identifier> deferredRegistration = new HashMap<>();
    @Getter
    private static final Map<Item, Identifier> registry = new IdentityHashMap<>();
    public static final Identifier DEFAULT_MODEL = NeoECOAE.id("cell/storage_cell_default");

    static {
        register(NEItems.ECO_ITEM_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_item"));
        register(NEItems.ECO_ITEM_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_item"));
        register(NEItems.ECO_ITEM_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_item"));

        register(NEItems.ECO_FLUID_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_fluid"));
        register(NEItems.ECO_FLUID_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_fluid"));
        register(NEItems.ECO_FLUID_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_fluid"));
    }

    public static Identifier getModelLocation(Item item) {
        if (item == null) {
            return DEFAULT_MODEL;
        }
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(Holder<Item> item, Identifier model) {
        deferredRegistration.put(item, model);
    }

    public static void register(Item item, Identifier model) {
        registry.put(item, model);
    }

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        deferredRegistration.forEach((itemHolder, location) -> {
            register(itemHolder.value(), location);
        });
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterStandalone e) {
        registry.forEach((__, location) -> {
            e.register(MODEL_KEY, SimpleUnbakedStandaloneModel.quadCollection(location));
        });
        e.register(MODEL_KEY, SimpleUnbakedStandaloneModel.quadCollection(DEFAULT_MODEL));
    }

}
