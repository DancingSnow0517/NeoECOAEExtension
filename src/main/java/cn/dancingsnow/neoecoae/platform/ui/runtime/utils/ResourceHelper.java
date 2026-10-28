package cn.dancingsnow.neoecoae.platform.ui.runtime.utils;
public final class ResourceHelper {
    private ResourceHelper(){}
    public static net.minecraft.server.packs.resources.ResourceManager getResourceManager(){
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.CLIENT) return null;
        return net.minecraft.client.Minecraft.getInstance().getResourceManager();}
}
