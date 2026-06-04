package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber
public class ECOCellModels {
    @Getter
    private static final Map<Item, ResourceLocation> registry = new IdentityHashMap<>();

    public static final ResourceLocation STORAGE_CELL_L4_ITEM = NeoECOAE.id("block/cell/storage_cell_l4_item");
    public static final ResourceLocation STORAGE_CELL_L6_ITEM = NeoECOAE.id("block/cell/storage_cell_l6_item");
    public static final ResourceLocation STORAGE_CELL_L9_ITEM = NeoECOAE.id("block/cell/storage_cell_l9_item");
    public static final ResourceLocation STORAGE_CELL_L4_FLUID = NeoECOAE.id("block/cell/storage_cell_l4_fluid");
    public static final ResourceLocation STORAGE_CELL_L6_FLUID = NeoECOAE.id("block/cell/storage_cell_l6_fluid");
    public static final ResourceLocation STORAGE_CELL_L9_FLUID = NeoECOAE.id("block/cell/storage_cell_l9_fluid");
    public static final ResourceLocation STORAGE_CELL_L4_CHEMICAL = NeoECOAE.id("block/cell/storage_cell_l4_chemical");
    public static final ResourceLocation STORAGE_CELL_L6_CHEMICAL = NeoECOAE.id("block/cell/storage_cell_l6_chemical");
    public static final ResourceLocation STORAGE_CELL_L9_CHEMICAL = NeoECOAE.id("block/cell/storage_cell_l9_chemical");
    public static final ResourceLocation DEFAULT_MODEL = STORAGE_CELL_L4_ITEM;
    private static final List<ResourceLocation> BUILTIN_MODELS = List.of(
            DEFAULT_MODEL,
            STORAGE_CELL_L6_ITEM,
            STORAGE_CELL_L9_ITEM,
            STORAGE_CELL_L4_FLUID,
            STORAGE_CELL_L6_FLUID,
            STORAGE_CELL_L9_FLUID,
            STORAGE_CELL_L4_CHEMICAL,
            STORAGE_CELL_L6_CHEMICAL,
            STORAGE_CELL_L9_CHEMICAL);

    public static ResourceLocation getModelLocation(Item item) {
        if (item == null) {
            return DEFAULT_MODEL;
        }
        return registry.getOrDefault(item, DEFAULT_MODEL);
    }

    public static void register(Item item, ResourceLocation model) {
        registry.put(item, model);
    }

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        register(NEItems.ECO_ITEM_CELL_16M.get(), STORAGE_CELL_L4_ITEM);
        register(NEItems.ECO_ITEM_CELL_64M.get(), STORAGE_CELL_L6_ITEM);
        register(NEItems.ECO_ITEM_CELL_256M.get(), STORAGE_CELL_L9_ITEM);

        register(NEItems.ECO_FLUID_CELL_16M.get(), STORAGE_CELL_L4_FLUID);
        register(NEItems.ECO_FLUID_CELL_64M.get(), STORAGE_CELL_L6_FLUID);
        register(NEItems.ECO_FLUID_CELL_256M.get(), STORAGE_CELL_L9_FLUID);
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterAdditional e) {
        Set<ResourceLocation> models = new LinkedHashSet<>(BUILTIN_MODELS);
        models.addAll(registry.values());
        models.forEach(e::register);
    }
}
