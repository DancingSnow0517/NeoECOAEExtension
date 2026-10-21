package cn.dancingsnow.neoecoae.gui.ldlib.support;
public final class NELDLibErrors {
    private NELDLibErrors(){}
    public static void notifyClientError(){var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null)player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.neoecoae.sync.too_large"),false);}
}
