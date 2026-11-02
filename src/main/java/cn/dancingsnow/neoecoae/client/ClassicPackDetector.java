package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.Minecraft;

public final class ClassicPackDetector {

    private ClassicPackDetector() {}

    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
            && minecraft.getResourceManager().getResource(NeoECOAE.id("classic_pack_marker")).isPresent();
    }
}
