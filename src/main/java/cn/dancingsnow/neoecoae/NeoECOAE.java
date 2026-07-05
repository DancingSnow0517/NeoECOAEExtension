package cn.dancingsnow.neoecoae;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEEcoTiers;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.all.NEGridServices;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.integration.IntegrationManager;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.forge.event.ECOStorageLifecycleEvents;
import cn.dancingsnow.neoecoae.forge.event.NELightningTransformEvents;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

@Mod(NeoECOAE.MOD_ID)
public class NeoECOAE {
    @Getter
    private static final IntegrationManager integrationManager = new IntegrationManager();

    public static final String MOD_ID = "neoecoae";
    public static IEventBus MOD_BUS = null;

    public static final NERegistrate REGISTRATE = NERegistrate.create(MOD_ID);

    public NeoECOAE(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        MOD_BUS = modBus;
        REGISTRATE.registerEventListeners(modBus);

        NECreativeTabs.register(modBus);
        NEItems.register();
        NEBlocks.register();
        NEFluids.register();
        NEBlockEntities.register();
        NEGridServices.register();
        NEEcoTiers.register();
        NECellTypes.register();
        NERecipeTypes.register(modBus);

        // Data components are a 1.20.5+ API. The 1.20.1 port will restore this
        // behavior through item NBT or Forge capabilities later.

        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Loading Integrations");
        integrationManager.compileContent();
        integrationManager.loadAllIntegrations();
        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Integrations Load Complete");
        modBus.addListener(NEConfig::onLoad);
        context.registerConfig(ModConfig.Type.COMMON, NEConfig.SPEC);

        modBus.addListener(NeoECOAE::initUpgrades);
        modBus.addListener(NeoECOAE::initStorageCells);
        modBus.addListener(NeoECOAE::newRegistry);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.dancingsnow.neoecoae.client.NeoECOAEClient.init(modBus, context);
        }
        MinecraftForge.EVENT_BUS.addListener(NeoECOAE::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onLevelSave);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, NELightningTransformEvents::onEntityJoinLevel);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            Upgrades.add(AEItems.SPEED_CARD, NEBlocks.INTEGRATED_WORKING_STATION.get(), 4);

            List<ItemEntry<ECOStorageCellItem>> cells = List.of(
                    NEItems.ECO_ITEM_CELL_16M,
                    NEItems.ECO_ITEM_CELL_64M,
                    NEItems.ECO_ITEM_CELL_256M,
                    NEItems.ECO_FLUID_CELL_16M,
                    NEItems.ECO_FLUID_CELL_64M,
                    NEItems.ECO_FLUID_CELL_256M);
            for (ItemEntry<ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private static void initStorageCells(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOStorageCellItem.ItemCellHandler.INSTANCE);
            ECOStorageCells.register(ECOStorageCellItem.FluidCellHandler.INSTANCE);
        });
    }

    private static void newRegistry(NewRegistryEvent event) {
        event.create(RegistryBuilder.of(NERegistries.Keys.ECO_TIER.location()).setMaxID(256));
        event.create(RegistryBuilder.of(NERegistries.Keys.CELL_TYPE.location()).setMaxID(256));
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
    }
}
