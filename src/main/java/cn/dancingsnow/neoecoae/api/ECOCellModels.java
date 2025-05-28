package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import lombok.Getter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
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
        register(NEItems.ECO_ITEM_CELL_16M, NeoECOAE.id("cell/storage_cell_l4_item"));
        register(NEItems.ECO_ITEM_CELL_64M, NeoECOAE.id("cell/storage_cell_l6_item"));
        register(NEItems.ECO_ITEM_CELL_256M, NeoECOAE.id("cell/storage_cell_l9_item"));

        register(NEItems.ECO_FLUID_CELL_16M, NeoECOAE.id("cell/storage_cell_l4_fluid"));
        register(NEItems.ECO_FLUID_CELL_64M, NeoECOAE.id("cell/storage_cell_l6_fluid"));
        register(NEItems.ECO_FLUID_CELL_256M, NeoECOAE.id("cell/storage_cell_l9_fluid"));
    }

    public static ResourceLocation getModelLocation(Item item) {
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(ItemLike item, ResourceLocation model) {
        registry.put(item.asItem(), model);
    }

    public static BakedModel getModel(
        Item item,
        ModelState modelState,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> textureGetter
    ) {
        return bakedModels.computeIfAbsent(
            item,
            it -> baker.bake(
                getModelLocation(item),
                modelState,
                textureGetter
            )
        );
    }
}
