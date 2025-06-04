package cn.dancingsnow.neoecoae;


import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEGridServices;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.all.NETooltips;
import cn.dancingsnow.neoecoae.api.integration.IntegrationManager;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.data.NEDataGen;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.common.NeoForge;
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

    public NeoECOAE(IEventBus modBus, ModContainer modContainer) {
        MOD_BUS = modBus;
        
        NECreativeTabs.register();
        NEBlocks.register();
        NEItems.register();
        NEBlockEntities.register();
        NEDataGen.configureDataGen();
        NEGridServices.register();
        NERecipeTypes.register(modBus);

        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Loading Integrations");
        integrationManager.compileContent();
        integrationManager.loadAllIntegrations();
        StartupNotificationManager.addModMessage("[Neo ECO AE Extension] Integrations Load Complete");
        modContainer.registerConfig(ModConfig.Type.COMMON, NEConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(NETooltips::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
