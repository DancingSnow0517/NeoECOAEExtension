package cn.dancingsnow.neoecoae.bootstrap;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in environment check; reaches the real title screen, then closes the bootstrap client. */
@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class BootstrapClientSmoke {
    private static boolean reported;

    private BootstrapClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (reported || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("neoecoae.bootstrapSmoke")) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen) {
            reported = true;
            LogUtils.getLogger().info("NEOECOAE_BOOTSTRAP_CLIENT_TITLE_READY");
            minecraft.stop();
        }
    }
}
