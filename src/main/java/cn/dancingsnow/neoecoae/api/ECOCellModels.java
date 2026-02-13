package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import lombok.Getter;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

@EventBusSubscriber
public class ECOCellModels {
    private static final Map<Holder<Item>, ResourceLocation> deferredRegistration = new HashMap<>();
    @Getter
    private static final Map<Item, ResourceLocation> registry = new IdentityHashMap<>();
    public static final ResourceLocation DEFAULT_MODEL = NeoECOAE.id("cell/storage_cell_l4_item");

    static {
        register(NEItems.ECO_ITEM_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_item"));
        register(NEItems.ECO_ITEM_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_item"));
        register(NEItems.ECO_ITEM_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_item"));

        register(NEItems.ECO_FLUID_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_fluid"));
        register(NEItems.ECO_FLUID_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_fluid"));
        register(NEItems.ECO_FLUID_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_fluid"));
    }

    public static ResourceLocation getModelLocation(Item item) {
        if (item == null) {
            return DEFAULT_MODEL;
        }
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(Holder<Item> item, ResourceLocation model) {
        deferredRegistration.put(item, model);
    }

    public static void register(Item item, ResourceLocation model) {
        registry.put(item, model);
    }

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        deferredRegistration.forEach((itemHolder, location) -> {
            register(itemHolder.value(), location);
        });
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterAdditional e) {
        registry.forEach((__, location) -> {
            e.register(ModelResourceLocation.standalone(location));
        });
        e.register(ModelResourceLocation.standalone(DEFAULT_MODEL));
    }

}
