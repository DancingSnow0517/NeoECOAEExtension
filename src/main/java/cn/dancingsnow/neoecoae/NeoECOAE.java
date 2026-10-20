package cn.dancingsnow.neoecoae;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Minimal Forge entry point used to verify the target environment before importing main. */
@Mod(NeoECOAE.MOD_ID)
public final class NeoECOAE {
    public static final String MOD_ID = "neoecoae";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoECOAE() {
        LOGGER.info("NeoECOAE Forge 1.20.1 environment bootstrap loaded; feature migration has not started.");
        if (Boolean.getBoolean("neoecoae.bootstrapSmoke")) {
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
                LOGGER.info("NEOECOAE_BOOTSTRAP_SERVER_READY");
                event.getServer().halt(false);
            });
        }
    }
}
