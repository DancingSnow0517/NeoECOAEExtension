package cn.dancingsnow.neoecoae.platform.ui.runtime.utils;
public final class ResourceHelper {
    private ResourceHelper(){}
    public static net.minecraft.server.packs.resources.ResourceManager getResourceManager(){return net.minecraft.client.Minecraft.getInstance().getResourceManager();}
}
