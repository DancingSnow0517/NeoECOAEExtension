package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import lombok.Getter;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public class ECOCellModels {
    @Getter
    private static final Map<Item, ResourceLocation> registry = new IdentityHashMap<>();
    private static final Map<Item, BakedModel> bakedModels = new IdentityHashMap<>();
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
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(ItemLike item, ResourceLocation model) {
        registry.put(item.asItem(), model);
    }

    public static BakedModel getModel(
        Item item,
        Function<ResourceLocation, BakedModel> modelBaker
    ) {
        return bakedModels.computeIfAbsent(
            item,
            it -> modelBaker.apply(getModelLocation(item))
        );
    }
}
