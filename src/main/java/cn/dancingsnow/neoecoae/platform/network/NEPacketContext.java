package cn.dancingsnow.neoecoae.platform.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Provides the sender or the local client only on the packet's registered receiving side. */
public record NEPacketContext(NetworkEvent.Context context) {
    public Player player() {
        return context.getDirection() == NetworkDirection.PLAY_TO_SERVER ? context.getSender() : NEClientPacketContext.player();
    }

    public void enqueueWork(Runnable task) {
        context.enqueueWork(() -> {
            if (player() != null) task.run();
        });
    }
}
