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
import cn.dancingsnow.neoecoae.all.NETooltips;
import cn.dancingsnow.neoecoae.api.integration.IntegrationManager;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.datagen.AAERecipeData;
import cn.dancingsnow.neoecoae.datagen.EAERecipeData;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NeoECOAE.MOD_ID)
public class NeoECOAE {
    private final Logger logger = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static final IntegrationManager integrationManager = new IntegrationManager();

    public static final String MOD_ID = "neoecoae";
    public static IEventBus MOD_BUS = null;

    public static final NERegistrate REGISTRATE = NERegistrate.create(MOD_ID);

    public NeoECOAE() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MOD_BUS = modBus;
        REGISTRATE.registerEventListeners(modBus);

        NECreativeTabs.register(modBus);
        NEItems.register();
        NEBlocks.register();
        NEFluids.register();
        NEBlockEntities.register();
        // Datagen uses newer 1.21 recipe/provider APIs. Keep runtime registration
        // independent while the 1.20.1 data providers are rewritten.
        AAERecipeData.register();
        EAERecipeData.register();
        NEGridServices.register();
        NEEcoTiers.register();
        NECellTypes.register();
        NERecipeTypes.register(modBus);

        // Native UI menu types
        NENativeMenus.MENUS.register(modBus);

        // Network
        NENetwork.register();

        // Data components are a 1.20.5+ API. The 1.20.1 port will restore this
        // behavior through item NBT or Forge capabilities later.

        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Loading Integrations");
        integrationManager.compileContent();
        integrationManager.loadAllIntegrations();
        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Integrations Load Complete");
        modBus.addListener(NEConfig::onLoad);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, NEConfig.SPEC);

        modBus.addListener(NeoECOAE::initUpgrades);
        modBus.addListener(NeoECOAE::initStorageCells);
        modBus.addListener(NeoECOAE::newRegistry);
        modBus.addListener(NeoECOAE::addClassicPack);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.dancingsnow.neoecoae.client.NeoECOAEClient.init(modBus);
        }
        MinecraftForge.EVENT_BUS.addListener(NETooltips::register);
        MinecraftForge.EVENT_BUS.addListener(NeoECOAE::onTagsUpdated);
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

    private static void addClassicPack(AddPackFindersEvent event) {
        // TODO 1.20.1: Recreate the built-in client resource pack via
        // AddPackFindersEvent#addRepositorySource.
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
    }
}
