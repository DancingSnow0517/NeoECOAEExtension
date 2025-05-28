package cn.dancingsnow.neoecoae;


import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEBlockEntities;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.data.NEDataGen;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NeoECOAE.MOD_ID)
public class NeoECOAE {
    private final Logger logger = LoggerFactory.getLogger(MOD_ID);
    public static final String MOD_ID = "neoecoae";

    public static final NERegistrate REGISTRATE = NERegistrate.create(MOD_ID);

    public NeoECOAE(IEventBus modBus, ModContainer modContainer) {
        NECreativeTabs.register();
        NEItems.register();
        NEBlocks.register();
        NEBlockEntities.register();
        NEDataGen.configureDataGen();
        logger.info("Hello World!");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
