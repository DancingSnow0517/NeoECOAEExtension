package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClassicPackDetector {

    private ClassicPackDetector() {}

    public static boolean isActive() {
        return Minecraft.getInstance().getResourceManager().getResource(NeoECOAE.id("classic_pack_marker")).isPresent();
    }
}
