package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import lombok.Getter;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public class ECOCellModels {
    /**
     * Filled by {@code <clinit>} and by the integrations, which {@code NeoECOAE}'s constructor loads via
     * {@code IntegrationManager#loadAllIntegrations}. Both happen during mod construction, i.e. strictly before
     * the initial resource reload starts, so this map is complete by the time models are baked.
     */
    private static final Map<Holder<Item>, ResourceLocation> deferredRegistration = new ConcurrentHashMap<>();

    /**
     * Item -> model, resolved from {@link #deferredRegistration} once the item registry is frozen. Written on the
     * mod loading thread and read from the chunk meshing threads, hence concurrent.
     */
    @Getter
    private static final Map<Item, ResourceLocation> registry = new ConcurrentHashMap<>();

    public static final ResourceLocation DEFAULT_MODEL = NeoECOAE.id("block/cell/storage_cell_default");

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

    public static void runDeferredRegistration() {
        deferredRegistration.forEach((itemHolder, location) -> {
            register(itemHolder.value(), location);
        });
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterAdditional e) {
        // Must NOT read `registry` alone: it is only filled by runDeferredRegistration() at FMLClientSetupEvent,
        // which NeoForge dispatches from the *prepare* stage of the initial resource reload (ClientModLoader is
        // itself a reload listener). That runs concurrently with the ModelBakery construction firing this event,
        // on a different executor and with no ordering guarantee - losing that race left every storage cell model
        // out of the bake, and the drives rendered the magenta missing model instead.
        // `deferredRegistration` is complete before any reload begins, so register from it as well.
        deferredRegistration.values().forEach(location -> {
            e.register(ModelResourceLocation.standalone(location));
        });
        registry.values().forEach(location -> {
            e.register(ModelResourceLocation.standalone(location));
        });
        e.register(ModelResourceLocation.standalone(DEFAULT_MODEL));
    }

}
