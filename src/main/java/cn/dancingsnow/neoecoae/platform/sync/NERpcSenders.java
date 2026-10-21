package cn.dancingsnow.neoecoae.platform.sync;
import com.lowdragmc.lowdraglib.syncdata.rpc.RPCSender;
import net.minecraft.server.level.ServerPlayer;
public final class NERpcSenders {
    private NERpcSenders(){}
    public static ServerPlayer asPlayer(RPCSender sender){return sender instanceof RPCSender.ClientRPCSender client&&client.player() instanceof ServerPlayer player?player:null;}
}
