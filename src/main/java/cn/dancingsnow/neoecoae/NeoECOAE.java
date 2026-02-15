package cn.dancingsnow.neoecoae;


import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEGridServices;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETooltips;
import cn.dancingsnow.neoecoae.api.integration.IntegrationManager;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.data.NEDataGen;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod(NeoECOAE.MOD_ID)
public class NeoECOAE {
    private final Logger logger = LoggerFactory.getLogger(MOD_ID);
    @Getter
    private static final IntegrationManager integrationManager = new IntegrationManager();
    public static final String MOD_ID = "neoecoae";
    public static IEventBus MOD_BUS = null;

    public static final NERegistrate REGISTRATE = NERegistrate.create(MOD_ID);

    public NeoECOAE(IEventBus modBus, ModContainer modContainer) {
        MOD_BUS = modBus;

        NECreativeTabs.register();
        NEItems.register();
        NEBlocks.register();
        NEBlockEntities.register();
        NEDataGen.configureDataGen();
        NEGridServices.register();
        NECellTypes.register(modBus);
        NERecipeTypes.register(modBus);

        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Loading Integrations");
        integrationManager.compileContent();
        integrationManager.loadAllIntegrations();
        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Integrations Load Complete");
        modContainer.registerConfig(ModConfig.Type.COMMON, NEConfig.SPEC);

        modBus.addListener(NeoECOAE::initUpgrades);
        modBus.addListener(NeoECOAE::initStorageCells);
        modBus.addListener(NeoECOAE::newRegistry);
        modBus.addListener(NeoECOAE::addClassicPack);
        NeoForge.EVENT_BUS.addListener(NETooltips::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            Upgrades.add(AEItems.SPEED_CARD, NEBlocks.INTEGRATED_WORKING_STATION.get(), 4);

            List<ItemEntry<ECOStorageCellItem>> cells = List.of(
                NEItems.ECO_ITEM_CELL_16M, NEItems.ECO_ITEM_CELL_64M, NEItems.ECO_ITEM_CELL_256M,
                NEItems.ECO_FLUID_CELL_16M, NEItems.ECO_ITEM_CELL_64M, NEItems.ECO_FLUID_CELL_256M
            );
            for (ItemEntry<ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private static void initStorageCells(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOStorageCellItem.Handler.INSTANCE);
        });
    }

    private static void newRegistry(NewRegistryEvent event) {
        event.register(NERegistries.CELL_TYPE);
    }

    private static void addClassicPack(AddPackFindersEvent event) {
        event.addPackFinders(
            NeoECOAE.id("classic_pack"),
            PackType.CLIENT_RESOURCES,
            Component.translatable("neoecoae.classic_pack"),
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP
        );
    }
}
