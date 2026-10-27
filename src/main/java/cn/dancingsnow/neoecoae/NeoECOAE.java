package cn.dancingsnow.neoecoae;


import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseInvBlockEntity;
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
import cn.dancingsnow.neoecoae.all.NEMenus;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETooltips;
import cn.dancingsnow.neoecoae.api.integration.IntegrationManager;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.command.NECommands;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.data.NEDataGen;
import cn.dancingsnow.neoecoae.event.ECOStorageLifecycleEvents;
import cn.dancingsnow.neoecoae.util.DrainOnlyFluidHandler;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.network.ECONetwork;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import com.tterrag.registrate.util.entry.ItemEntry;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
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

    public NeoECOAE() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MOD_BUS = modBus;
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")
                && net.minecraftforge.fml.ModList.get().isLoaded("useless_stretcher")) {
            cn.dancingsnow.neoecoae.integration.mekanism.MilkInfusionIntegration.register(modBus);
        }
        NECreativeTabs.register();
        NEMenus.register(modBus);
        NEItems.register();
        NEBlocks.register();
        NEFluids.register();
        NEBlockEntities.register();
        NEDataGen.configureDataGen();
        NEGridServices.register();
        NEEcoTiers.register();
        NECellTypes.register();
        NERecipeTypes.register(modBus);
        cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes.configure(() -> NEConfig.largeWorkstationIgnoreUpgradeCards);

        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Loading Integrations");
        integrationManager.compileContent();
        integrationManager.loadAllIntegrations();
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> cn.dancingsnow.neoecoae.client.NeoECOAEClient.initialize(modBus));
        cn.dancingsnow.neoecoae.compat.ae2.AE2PatternOptimizationIntegration.init();
        cn.dancingsnow.neoecoae.compat.ECOPatternIntegrations.initialize();
        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Integrations Load Complete");
        // These values govern server-authoritative gameplay and are also read by client-side
        // previews/tooltips. SERVER configs are synchronized to joining clients by Forge.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NEConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
            cn.dancingsnow.neoecoae.config.RecipeProfileConfig.SPEC, "neoecoae-recipes-common.toml");

        modBus.addListener(NeoECOAE::registerCapabilities);
        modBus.addListener(NeoECOAE::initUIFactories);
        modBus.addListener(NeoECOAE::initUpgrades);
        modBus.addListener(NeoECOAE::initStorageCells);
        modBus.addListener(NeoECOAE::newRegistry);
        modBus.addListener(NeoECOAE::addClassicPack);
        modBus.addListener(cn.dancingsnow.neoecoae.recipe.profile.GTRecipeProfiles::addPackFinders);
        ECONetwork.register();
        cn.dancingsnow.neoecoae.platform.sync.ManagedRpcNetwork.register(
            net.minecraftforge.fml.ModList.get().getModContainerById(MOD_ID).orElseThrow()
                .getModInfo().getVersion().toString());
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class,
            ForgeBlockCapabilities::attach);
        MinecraftForge.EVENT_BUS.addListener(cn.dancingsnow.neoecoae.network.MenuDataTransport::tick);
        MinecraftForge.EVENT_BUS.addListener(cn.dancingsnow.neoecoae.network.MenuDataTransport::stopped);
        MinecraftForge.EVENT_BUS.addListener(NETooltips::register);
        MinecraftForge.EVENT_BUS.addListener(NECommands::register);
        MinecraftForge.EVENT_BUS.addListener(NeoECOAE::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(ECOStorageLifecycleEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(cn.dancingsnow.neoecoae.platform.diagnostics.StorageSavedDataServerProbe::started);
        MinecraftForge.EVENT_BUS.addListener(cn.dancingsnow.neoecoae.platform.diagnostics.Legacy2042ServerProbe::started);
        MinecraftForge.EVENT_BUS.addListener(cn.dancingsnow.neoecoae.platform.diagnostics.OptionalStorageServerProbe::started);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static void registerCapabilities(FMLCommonSetupEvent event) {
        ForgeBlockCapabilities.register(
            ForgeCapabilities.ITEM_HANDLER,
            NEBlockEntities.ECO_DRIVE.get(),
            (be, side) -> be.HANDLER
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.FLUID_HANDLER,
            NEBlockEntities.INPUT_HATCH.get(),
            (be, side) -> be.tank
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.FLUID_HANDLER,
            NEBlockEntities.OUTPUT_HATCH.get(),
            (be, side) -> new DrainOnlyFluidHandler(be.tank)
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.FLUID_HANDLER,
            NEBlockEntities.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.get(),
            (be, side) -> be.tank
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.FLUID_HANDLER,
            NEBlockEntities.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.get(),
            (be, side) -> new DrainOnlyFluidHandler(be.tank)
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.ITEM_HANDLER,
            NEBlockEntities.CRAFTING_PATTERN_BUS.get(),
            (be, side) -> be.itemHandler
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.ITEM_HANDLER,
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            (be, unused) -> be.getItemHandler()
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.ITEM_HANDLER,
            NEBlockEntities.INTEGRATED_WORKING_STATION_BLOCK.get(),
            (be, side) -> be.getInternalInventory().toItemHandler()
        );
        ForgeBlockCapabilities.register(
            appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST,
            NEBlockEntities.INTEGRATED_WORKING_STATION_BLOCK.get(),
            (be, side) -> be
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.FLUID_HANDLER,
            NEBlockEntities.INTEGRATED_WORKING_STATION_BLOCK.get(),
            (be, side) -> be.getFluidCombined()
        );
        ForgeBlockCapabilities.register(
            ForgeCapabilities.ENERGY,
            NEBlockEntities.INTEGRATED_WORKING_STATION_BLOCK.get(),
            ECOLargeIntegratedWorkingStationBlockEntity::getEnergyStorage
        );
    }

    private static void initUIFactories(FMLCommonSetupEvent event) {
        // A remote client must know the factory before receiving LDLib's UI-open packet.
        // Enqueue on both physical sides instead of relying on a server-side openUI call.
        event.enqueueWork(BlockUIMenuType::register);
    }

    private static void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            Upgrades.add(AEItems.SPEED_CARD, NEBlocks.INTEGRATED_WORKING_STATION.get(), 4);
            Upgrades.add(AEItems.CAPACITY_CARD, NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.get(), 4);

            List<ItemEntry<ECOStorageCellItem>> cells = List.of(
                NEItems.ECO_ITEM_CELL_16M, NEItems.ECO_ITEM_CELL_64M, NEItems.ECO_ITEM_CELL_256M,
                NEItems.ECO_BULK_ITEM_CELL_16M, NEItems.ECO_BULK_ITEM_CELL_64M, NEItems.ECO_BULK_ITEM_CELL_256M,
                NEItems.ECO_FLUID_CELL_16M, NEItems.ECO_FLUID_CELL_64M, NEItems.ECO_FLUID_CELL_256M
            );
            for (ItemEntry<ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.asItem(), cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private static void initStorageCells(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOStorageCellItem.Handler.INSTANCE);
            ECOStorageCells.register(ECOCreativeCell.Handler.INSTANCE);
        });
    }

    private static void newRegistry(NewRegistryEvent event) {
        NERegistries.create(event);
    }

    private static void addClassicPack(AddPackFindersEvent event) {
        if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
        event.addRepositorySource(consumer->{
            var root=net.minecraftforge.fml.ModList.get().getModFileById(MOD_ID).getFile().findResource("classic_pack");
            var pack=Pack.readMetaAndCreate("neoecoae:classic_pack",Component.translatable("neoecoae.classic_pack"),false,
                id->new net.minecraftforge.resource.PathPackResources(id,true,root),PackType.CLIENT_RESOURCES,Pack.Position.TOP,PackSource.BUILT_IN);
            if(pack!=null)consumer.accept(pack);
        });
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes.invalidate();
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
    }
}
